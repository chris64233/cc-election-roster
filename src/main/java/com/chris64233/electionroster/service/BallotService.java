package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.SubmitRequest;
import com.chris64233.electionroster.api.SubmitResponse;
import com.chris64233.electionroster.domain.Adjudication;
import com.chris64233.electionroster.domain.BallotContent;
import com.chris64233.electionroster.domain.CureRecord;
import com.chris64233.electionroster.domain.Issuance;
import com.chris64233.electionroster.domain.IssuanceStatus;
import com.chris64233.electionroster.domain.IssuanceType;
import com.chris64233.electionroster.domain.ProvisionalRecord;
import com.chris64233.electionroster.domain.SubmissionRecord;
import com.chris64233.electionroster.repo.BallotContentRepository;
import com.chris64233.electionroster.repo.CureRecordRepository;
import com.chris64233.electionroster.repo.ExternalVoteRecordRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.ProvisionalRecordRepository;
import com.chris64233.electionroster.repo.SubmissionRecordRepository;
import com.chris64233.electionroster.repo.VoterRepository;
import com.chris64233.electionroster.web.ConflictException;
import com.chris64233.electionroster.web.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 投票提交：
 * - 凭证只能消费一次（uk_submission_credential）；
 * - 重复提交相同内容（contentHash 一致）返回原回执；内容变化返回 409；
 * - 临时票内容在裁定通过前不计入，通过后计入选区，拒绝则永久作废；
 * - 邮寄票/临时票声明身份材料不全时选票“暂存（held）”，开启补正流程，截止前补正确认才恢复计入；
 * - 已通过其他渠道有效投票的选民不得再产生有效提交（与补正/裁定共用同一选民行锁串行裁决）；
 * - 票面内容与身份信息隔离存储，审计链只记录 contentHash。
 */
@Service
public class BallotService {

    private final IssuanceRepository issuanceRepository;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final BallotContentRepository ballotContentRepository;
    private final ProvisionalRecordRepository provisionalRecordRepository;
    private final CureRecordRepository cureRecordRepository;
    private final ExternalVoteRecordRepository externalVoteRecordRepository;
    private final VoterRepository voterRepository;
    private final AuditService auditService;

    public BallotService(IssuanceRepository issuanceRepository,
                         SubmissionRecordRepository submissionRecordRepository,
                         BallotContentRepository ballotContentRepository,
                         ProvisionalRecordRepository provisionalRecordRepository,
                         CureRecordRepository cureRecordRepository,
                         ExternalVoteRecordRepository externalVoteRecordRepository,
                         VoterRepository voterRepository,
                         AuditService auditService) {
        this.issuanceRepository = issuanceRepository;
        this.submissionRecordRepository = submissionRecordRepository;
        this.ballotContentRepository = ballotContentRepository;
        this.provisionalRecordRepository = provisionalRecordRepository;
        this.cureRecordRepository = cureRecordRepository;
        this.externalVoteRecordRepository = externalVoteRecordRepository;
        this.voterRepository = voterRepository;
        this.auditService = auditService;
    }

    @Transactional
    public SubmitResponse submit(SubmitRequest request) {
        Issuance issuance = issuanceRepository.findByCredentialToken(request.getCredentialToken())
                .orElseThrow(() -> new NotFoundException("签发凭证不存在"));

        var existing = submissionRecordRepository.findByCredentialToken(issuance.getCredentialToken());
        if (existing.isPresent()) {
            return replay(existing.get(), request.getChoicesJson());
        }

        if (issuance.getStatus() == IssuanceStatus.VOIDED) {
            throw new ConflictException("凭证已作废，不能提交选票");
        }

        boolean identityIncomplete = Boolean.TRUE.equals(request.getIdentityIncomplete());
        if (identityIncomplete && issuance.getType() == IssuanceType.OFFICIAL) {
            // 正式票现场核验身份，不存在事后补正；材料问题应在签发环节处理。
            throw new ConflictException("正式票不适用身份材料补正");
        }

        // 与“其他渠道投票 / 补正确认 / 截止裁定”竞争同一选民行锁，串行裁决唯一有效结果。
        voterRepository.findForLockById(issuance.getVoter().getId());
        if (externalVoteRecordRepository
                .findByElectionIdAndVoterId(issuance.getElection().getId(), issuance.getVoter().getId())
                .isPresent()) {
            throw new ConflictException("该选民已通过其他渠道有效投票，本渠道提交无效");
        }

        String contentHash = AuditService.sha256(request.getChoicesJson());

        // 身份材料不全：暂存并开启补正；否则按类型走“立即计入 / 临时票待裁定”。
        boolean held = identityIncomplete;
        boolean countNow;
        if (held) {
            countNow = false;
        } else if (issuance.getType() == IssuanceType.PROVISIONAL) {
            ProvisionalRecord provisional = provisionalRecordRepository
                    .findByIssuanceId(issuance.getId()).orElseThrow();
            countNow = provisional.getAdjudication() == Adjudication.ACCEPTED;
            if (provisional.getAdjudication() == Adjudication.REJECTED) {
                throw new ConflictException("临时票已被裁定拒绝，凭证已作废");
            }
        } else {
            countNow = true;
        }

        String ballotId = UUID.randomUUID().toString();
        String receiptId = UUID.randomUUID().toString();
        ballotContentRepository.save(new BallotContent(ballotId, issuance.getElection(),
                issuance.getDistrict(), request.getChoicesJson(), contentHash, countNow, held));
        try {
            submissionRecordRepository.saveAndFlush(new SubmissionRecord(
                    issuance.getCredentialToken(), contentHash, ballotId, receiptId));
        } catch (DataIntegrityViolationException e) {
            // 并发下另一请求已消费该凭证：按重放规则处理（同内容返回原结果，否则冲突）。
            return replay(submissionRecordRepository
                    .findByCredentialToken(issuance.getCredentialToken()).orElseThrow(),
                    request.getChoicesJson());
        }
        issuance.markConsumed();

        if (held) {
            Instant deadline = issuance.getElection().getCureDeadline();
            if (deadline == null) {
                throw new ConflictException("本次选举未配置补正截止时间，不能暂存选票");
            }
            String missing = request.getMissingMaterials() != null ? request.getMissingMaterials() : "";
            // 补正记录只关联原签发，绝不会重新签发第二张票。
            cureRecordRepository.save(new CureRecord(issuance, missing, deadline));
        }

        auditService.append("SUBMIT", receiptId,
                "district=" + issuance.getDistrict().getCode()
                        + ";provisional=" + (issuance.getType() == IssuanceType.PROVISIONAL)
                        + ";held=" + held
                        + ";counted=" + countNow
                        + ";contentHash=" + contentHash);
        return new SubmitResponse(receiptId, contentHash, countNow, false, held, issuance.getCreatedAt());
    }

    private SubmitResponse replay(SubmissionRecord record, String requestedChoices) {
        String requestedHash = AuditService.sha256(requestedChoices);
        if (!record.getContentHash().equals(requestedHash)) {
            throw new ConflictException("凭证已被消费，且本次提交内容与首次提交不一致");
        }
        BallotContent ballot = ballotContentRepository.findById(record.getBallotId()).orElseThrow();
        return new SubmitResponse(record.getReceiptId(), record.getContentHash(),
                ballot.isCounted(), true, ballot.isHeld(), record.getCreatedAt());
    }
}
