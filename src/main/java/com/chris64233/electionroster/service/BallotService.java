package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.SubmitRequest;
import com.chris64233.electionroster.api.SubmitResponse;
import com.chris64233.electionroster.domain.Adjudication;
import com.chris64233.electionroster.domain.BallotContent;
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
 * - 票面内容与身份信息隔离存储，审计链只记录 contentHash。
 */
@Service
public class BallotService {

    private final IssuanceRepository issuanceRepository;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final BallotContentRepository ballotContentRepository;
    private final ProvisionalRecordRepository provisionalRecordRepository;
    private final AuditService auditService;

    public BallotService(IssuanceRepository issuanceRepository,
                         SubmissionRecordRepository submissionRecordRepository,
                         BallotContentRepository ballotContentRepository,
                         ProvisionalRecordRepository provisionalRecordRepository,
                         AuditService auditService) {
        this.issuanceRepository = issuanceRepository;
        this.submissionRecordRepository = submissionRecordRepository;
        this.ballotContentRepository = ballotContentRepository;
        this.provisionalRecordRepository = provisionalRecordRepository;
        this.auditService = auditService;
    }

    @Transactional
    public SubmitResponse submit(SubmitRequest request) {
        Issuance issuance = issuanceRepository.findByCredentialToken(request.getCredentialToken())
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
        boolean countNow = true;
        if (issuance.getType() == IssuanceType.PROVISIONAL) {
            ProvisionalRecord provisional = provisionalRecordRepository
                    .findByIssuanceId(issuance.getId()).orElseThrow();
            // 临时票：裁定前暂不计入；已拒绝则不允许再提交。
            countNow = provisional.getAdjudication() == Adjudication.ACCEPTED;
            if (provisional.getAdjudication() == Adjudication.REJECTED) {
                throw new ConflictException("临时票已被裁定拒绝，凭证已作废");
            }
        }

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
                throw new ConflictException("凭证已被消费，且本次提交内容与首次提交不一致");
            }
            BallotContent ballot = ballotContentRepository.findById(winner.getBallotId()).orElseThrow();
            return new SubmitResponse(winner.getReceiptId(), contentHash,
                    ballot.isCounted(), true, winner.getCreatedAt());
        }
        issuance.markConsumed();

        auditService.append("SUBMIT", receiptId,
                "district=" + issuance.getDistrict().getCode()
                        + ";provisional=" + (issuance.getType() == IssuanceType.PROVISIONAL)
                        + ";counted=" + countNow
                        + ";contentHash=" + contentHash);
        return new SubmitResponse(receiptId, contentHash, countNow, false, issuance.getCreatedAt());
    }
}
