package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.AdjudicationResponse;
import com.chris64233.electionroster.domain.Adjudication;
import com.chris64233.electionroster.domain.BallotContent;
import com.chris64233.electionroster.domain.Issuance;
import com.chris64233.electionroster.domain.ProvisionalRecord;
import com.chris64233.electionroster.repo.BallotContentRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.ProvisionalRecordRepository;
import com.chris64233.electionroster.repo.SubmissionRecordRepository;
import com.chris64233.electionroster.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 临时票裁定。裁定一次性、不可逆：
 * - ACCEPTED：已提交的选票计入选区；尚未提交的待提交时直接计入；
 * - REJECTED：已提交的选票永久作废；尚未提交的凭证立即作废，禁止再提交。
 */
@Service
public class AdjudicationService {

    private final ProvisionalRecordRepository provisionalRecordRepository;
    private final IssuanceRepository issuanceRepository;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final BallotContentRepository ballotContentRepository;
    private final AuditService auditService;

    public AdjudicationService(ProvisionalRecordRepository provisionalRecordRepository,
                               IssuanceRepository issuanceRepository,
                               SubmissionRecordRepository submissionRecordRepository,
                               BallotContentRepository ballotContentRepository,
                               AuditService auditService) {
        this.provisionalRecordRepository = provisionalRecordRepository;
        this.issuanceRepository = issuanceRepository;
        this.submissionRecordRepository = submissionRecordRepository;
        this.ballotContentRepository = ballotContentRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AdjudicationResponse adjudicate(Long issuanceId, boolean accepted, String reason) {
        ProvisionalRecord record = provisionalRecordRepository.findByIssuanceId(issuanceId)
                .orElseThrow(() -> new NotFoundException("临时票记录不存在: " + issuanceId));
        Issuance issuance = record.getIssuance();

        Adjudication decision = accepted ? Adjudication.ACCEPTED : Adjudication.REJECTED;
        record.decide(decision, reason);

        var submission = submissionRecordRepository.findByCredentialToken(issuance.getCredentialToken());
        String auditDetail;
        if (submission.isPresent()) {
            BallotContent ballot = ballotContentRepository.findById(submission.get().getBallotId()).orElseThrow();
            if (accepted) {
                ballot.markCounted();
            } else {
                ballot.markVoided();
            }
            auditDetail = "decision=" + decision + ";contentHash=" + ballot.getContentHash();
        } else {
            if (!accepted) {
                // 尚未提交即拒绝：凭证永久作废，之后任何提交都被拒绝。
                issuance.markVoided();
            }
            auditDetail = "decision=" + decision + ";submitted=false";
        }

        auditService.append("ADJUDICATE", issuance.getEventNo(), auditDetail);
        return new AdjudicationResponse(issuanceId, decision.name(), reason, record.getDecidedAt());
    }
}
