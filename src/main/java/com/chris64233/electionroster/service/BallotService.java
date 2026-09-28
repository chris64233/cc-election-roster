package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.SubmitRequest;
import com.chris64233.electionroster.api.SubmitResponse;
import com.chris64233.electionroster.domain.Adjudication;
import com.chris64233.electionroster.domain.BallotContent;
import com.chris64233.electionroster.domain.CureStatus;
import com.chris64233.electionroster.domain.EffectiveVoteSource;
import com.chris64233.electionroster.domain.Issuance;
import com.chris64233.electionroster.domain.IssuanceStatus;
import com.chris64233.electionroster.domain.IssuanceType;
import com.chris64233.electionroster.domain.ProvisionalRecord;
import com.chris64233.electionroster.domain.SubmissionRecord;
import com.chris64233.electionroster.repo.BallotContentRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.ProvisionalRecordRepository;
import com.chris64233.electionroster.repo.SubmissionRecordRepository;
import com.chris64233.electionroster.web.ConflictException;
import com.chris64233.electionroster.web.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 投票提交：
 * - 凭证只能消费一次（uk_submission_credential）；
 * - 重复提交相同内容（contentHash 一致）返回原回执；内容变化返回 409；
 * - 临时票内容在裁定通过前不计入，通过后计入选区，拒绝则永久作废；
 * - 邮寄票身份材料不全时选票暂不计入，进入补正流程；补正确认只恢复这一张原选票；
 * - 计入即原子登记 effective_votes 有效结果槽位：已通过其他渠道有效投票时，
 *   本渠道提交被拒绝，绝不出现两个有效结果；
 * - 票面内容与身份信息隔离存储，审计链只记录 contentHash。
 */
@Service
public class BallotService {

    private final IssuanceRepository issuanceRepository;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final BallotContentRepository ballotContentRepository;
    private final ProvisionalRecordRepository provisionalRecordRepository;
    private final EffectiveVoteService effectiveVoteService;
    private final AuditService auditService;

    public BallotService(IssuanceRepository issuanceRepository,
                         SubmissionRecordRepository submissionRecordRepository,
                         BallotContentRepository ballotContentRepository,
                         ProvisionalRecordRepository provisionalRecordRepository,
                         EffectiveVoteService effectiveVoteService,
                         AuditService auditService) {
        this.issuanceRepository = issuanceRepository;
        this.submissionRecordRepository = submissionRecordRepository;
        this.ballotContentRepository = ballotContentRepository;
        this.provisionalRecordRepository = provisionalRecordRepository;
        this.effectiveVoteService = effectiveVoteService;
        this.auditService = auditService;
    }

    @Transactional
    public SubmitResponse submit(SubmitRequest request) {
        // 行锁作为首次读取：与补正确认、临时票裁定、其他渠道登记、截止裁定互斥。
        Issuance issuance = issuanceRepository.findByCredentialTokenForUpdate(request.getCredentialToken())
                .orElseThrow(() -> new NotFoundException("签发凭证不存在"));

        var existing = submissionRecordRepository.findByCredentialToken(issuance.getCredentialToken());
        if (existing.isPresent()) {
            SubmissionRecord record = existing.get();
            String requestedHash = AuditService.sha256(request.getChoicesJson());
            if (!record.getContentHash().equals(requestedHash)) {
                throw new ConflictException("凭证已被消费，且本次提交内容与首次提交不一致");
            }
            BallotContent ballot = ballotContentRepository.findById(record.getBallotId()).orElseThrow();
            return new SubmitResponse(record.getReceiptId(), record.getContentHash(),
                    ballot.isCounted(), true, record.getCreatedAt());
        }

        if (issuance.getStatus() == IssuanceStatus.VOIDED) {
            throw new ConflictException("凭证已作废，不能提交选票");
        }

        String contentHash = AuditService.sha256(request.getChoicesJson());
        EffectiveVoteSource countSource = null;
        if (issuance.getType() == IssuanceType.PROVISIONAL) {
            ProvisionalRecord provisional = provisionalRecordRepository
                    .findByIssuanceId(issuance.getId()).orElseThrow();
            // 临时票：裁定前暂不计入；已拒绝则不允许再提交。
            if (provisional.getAdjudication() == Adjudication.ACCEPTED) {
                countSource = EffectiveVoteSource.PROVISIONAL_ACCEPTED;
            } else if (provisional.getAdjudication() == Adjudication.REJECTED) {
                throw new ConflictException("临时票已被裁定拒绝，凭证已作废");
            }
        } else if (issuance.getType() == IssuanceType.MAIL) {
            if (issuance.getCureStatus() == CureStatus.PENDING) {
                // 邮寄票身份材料不全：选票暂不计入，等待补正确认恢复（不重新签发）。
            } else if (issuance.getCureStatus() == CureStatus.FAILED) {
                throw new ConflictException("邮寄票补正已失败，原选票已作废");
            } else {
                countSource = EffectiveVoteSource.DIRECT_SUBMISSION;
            }
        } else {
            countSource = EffectiveVoteSource.DIRECT_SUBMISSION;
        }

        // 计入与有效结果槽位消费原子进行：
        // - 槽位已被本选票的补正确认/临时票通过预先占用时，直接计入这一张原选票；
        // - 槽位被其他渠道占用时，本渠道提交失败；绝不出现两个有效结果。
        if (countSource != null) {
            var existingVote = effectiveVoteService
                    .find(issuance.getElection().getId(), issuance.getVoter().getId());
            if (existingVote.isPresent()) {
                EffectiveVoteSource source = existingVote.get().getSource();
                if (source != EffectiveVoteSource.CURE_CONFIRMED
                        && source != EffectiveVoteSource.PROVISIONAL_ACCEPTED) {
                    throw new ConflictException("该选民已通过其他渠道有效投票，本渠道提交失败");
                }
            } else {
                EffectiveVoteService.ClaimOutcome outcome = effectiveVoteService.tryClaim(
                        issuance.getElection(), issuance.getVoter(), countSource, issuance.getEventNo());
                if (!outcome.claimed()) {
                    throw new ConflictException("该选民已通过其他渠道有效投票，本渠道提交失败");
                }
            }
        }

        boolean countNow = countSource != null;
        String ballotId = UUID.randomUUID().toString();
        String receiptId = UUID.randomUUID().toString();
        ballotContentRepository.save(new BallotContent(ballotId, issuance.getElection(),
                issuance.getDistrict(), request.getChoicesJson(), contentHash, countNow));
        try {
            submissionRecordRepository.saveAndFlush(new SubmissionRecord(
                    issuance.getCredentialToken(), contentHash, ballotId, receiptId));
        } catch (DataIntegrityViolationException e) {
            // 并发下另一请求已消费该凭证：按重放规则处理（同内容返回原结果，否则冲突）。
            SubmissionRecord winner = submissionRecordRepository
                    .findByCredentialToken(issuance.getCredentialToken()).orElseThrow();
            if (!winner.getContentHash().equals(contentHash)) {
                throw new ConflictException("凭证已被消费，且本次提交内容与首笔提交不一致");
            }
            BallotContent ballot = ballotContentRepository.findById(winner.getBallotId()).orElseThrow();
            return new SubmitResponse(winner.getReceiptId(), contentHash,
                    ballot.isCounted(), true, winner.getCreatedAt());
        }
        issuance.markConsumed();

        auditService.append("SUBMIT", receiptId,
                "district=" + issuance.getDistrict().getCode()
                        + ";provisional=" + (issuance.getType() == IssuanceType.PROVISIONAL)
                        + ";held=" + (issuance.getCureStatus() == CureStatus.PENDING)
                        + ";counted=" + countNow
                        + ";contentHash=" + contentHash);
        return new SubmitResponse(receiptId, contentHash, countNow, false, issuance.getCreatedAt());
    }
}
