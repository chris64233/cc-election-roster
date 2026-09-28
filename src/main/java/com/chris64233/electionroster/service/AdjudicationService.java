package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.AdjudicationResponse;
import com.chris64233.electionroster.domain.Adjudication;
import com.chris64233.electionroster.domain.BallotContent;
import com.chris64233.electionroster.domain.CureRecord;
import com.chris64233.electionroster.domain.CureStatus;
import com.chris64233.electionroster.domain.Issuance;
import com.chris64233.electionroster.domain.ProvisionalRecord;
import com.chris64233.electionroster.repo.BallotContentRepository;
import com.chris64233.electionroster.repo.CureRecordRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.ProvisionalRecordRepository;
import com.chris64233.electionroster.repo.SubmissionRecordRepository;
import com.chris64233.electionroster.web.ConflictException;
import com.chris64233.electionroster.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 临时票裁定。裁定一次性、不可逆：
 * - ACCEPTED：已提交的选票计入选区；尚未提交的待提交时直接计入；
 * - REJECTED：已提交的选票永久作废；尚未提交的凭证立即作废，禁止再提交。
 *
 * <p>若选票因身份材料不全处于补正暂存（held）状态，则其计入只能由补正确认完成，
 * 裁定路径不得直接计入；裁定拒绝仍可作废该选票并终结补正（唯一终局）。
 */
@Service
public class AdjudicationService {

    private final ProvisionalRecordRepository provisionalRecordRepository;
    private final IssuanceRepository issuanceRepository;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final BallotContentRepository ballotContentRepository;
    private final CureRecordRepository cureRecordRepository;
    private final AuditService auditService;

    public AdjudicationService(ProvisionalRecordRepository provisionalRecordRepository,
                               IssuanceRepository issuanceRepository,
                               SubmissionRecordRepository submissionRecordRepository,
                               BallotContentRepository ballotContentRepository,
                               CureRecordRepository cureRecordRepository,
                               AuditService auditService) {
        this.provisionalRecordRepository = provisionalRecordRepository;
        this.issuanceRepository = issuanceRepository;
        this.submissionRecordRepository = submissionRecordRepository;
        this.ballotContentRepository = ballotContentRepository;
        this.cureRecordRepository = cureRecordRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AdjudicationResponse adjudicate(Long issuanceId, boolean accepted, String reason) {
        ProvisionalRecord record = provisionalRecordRepository.findByIssuanceId(issuanceId)
                .orElseThrow(() -> new NotFoundException("临时票记录不存在: " + issuanceId));
        Issuance issuance = record.getIssuance();

        Optional<CureRecord> cure = cureRecordRepository.findByIssuanceId(issuanceId);
        var submission = submissionRecordRepository.findByCredentialToken(issuance.getCredentialToken());

        // 暂存选票只能由补正确认恢复计入，避免双重裁决（在改变裁定状态前拒绝）。
        if (accepted && submission.isPresent()) {
            BallotContent current = ballotContentRepository
                    .findById(submission.get().getBallotId()).orElseThrow();
            if (current.isHeld()) {
                throw new ConflictException("选票处于身份补正暂存，须通过补正确认恢复，不能直接裁定计入");
            }
        }

        Adjudication decision = accepted ? Adjudication.ACCEPTED : Adjudication.REJECTED;
        record.decide(decision, reason);

        String auditDetail;
        if (submission.isPresent()) {
            BallotContent ballot = ballotContentRepository.findById(submission.get().getBallotId()).orElseThrow();
            if (accepted) {
                ballot.markCounted();
            } else {
                ballot.markVoided();
                cure.filter(c -> c.getStatus() == CureStatus.PENDING)
                        .ifPresent(c -> c.reject("临时票裁定拒绝"));
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
