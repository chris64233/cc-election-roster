package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.AdjudicationResponse;
import com.chris64233.electionroster.domain.Adjudication;
import com.chris64233.electionroster.domain.BallotContent;
import com.chris64233.electionroster.domain.EffectiveVoteSource;
import com.chris64233.electionroster.domain.Issuance;
import com.chris64233.electionroster.domain.ProvisionalRecord;
import com.chris64233.electionroster.repo.BallotContentRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.ProvisionalRecordRepository;
import com.chris64233.electionroster.repo.SubmissionRecordRepository;
import com.chris64233.electionroster.web.ConflictException;
import com.chris64233.electionroster.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 临时票裁定。裁定一次性、不可逆：
 * - ACCEPTED：已提交的选票计入选区；尚未提交的待提交时直接计入；
 * - REJECTED：已提交的选票永久作废；尚未提交的凭证立即作废，禁止再提交。
 *
 * 与补正确认、其他渠道投票登记并发时：先锁签发记录串行化，
 * 再原子消费 effective_votes 有效结果槽位——若选民已通过其他渠道有效投票，
 * 裁定通过被拒绝，只能保留一个有效结果。
 */
@Service
public class AdjudicationService {

    private final ProvisionalRecordRepository provisionalRecordRepository;
    private final IssuanceRepository issuanceRepository;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final BallotContentRepository ballotContentRepository;
    private final EffectiveVoteService effectiveVoteService;
    private final AuditService auditService;

    public AdjudicationService(ProvisionalRecordRepository provisionalRecordRepository,
                               IssuanceRepository issuanceRepository,
                               SubmissionRecordRepository submissionRecordRepository,
                               BallotContentRepository ballotContentRepository,
                               EffectiveVoteService effectiveVoteService,
                               AuditService auditService) {
        this.provisionalRecordRepository = provisionalRecordRepository;
        this.issuanceRepository = issuanceRepository;
        this.submissionRecordRepository = submissionRecordRepository;
        this.ballotContentRepository = ballotContentRepository;
        this.effectiveVoteService = effectiveVoteService;
        this.auditService = auditService;
    }

    @Transactional
    public AdjudicationResponse adjudicate(Long issuanceId, boolean accepted, String reason) {
        Issuance issuance = issuanceRepository.findByIdForUpdate(issuanceId)
                .orElseThrow(() -> new NotFoundException("临时票记录不存在: " + issuanceId));
        ProvisionalRecord record = provisionalRecordRepository.findByIssuanceId(issuanceId)
                .orElseThrow(() -> new NotFoundException("临时票记录不存在: " + issuanceId));

        Adjudication decision = accepted ? Adjudication.ACCEPTED : Adjudication.REJECTED;
        // 一次性裁定校验（重复裁定直接 409）；后续若抢槽失败，同一事务回滚不会留下该决定。
        record.decide(decision, reason);

        var submission = submissionRecordRepository.findByCredentialToken(issuance.getCredentialToken());
        if (accepted) {
            // 跨渠道原子仲裁：已通过其他渠道有效投票（临时票通常已被同步拒绝）时不能再计入。
            EffectiveVoteService.ClaimOutcome outcome = effectiveVoteService.tryClaim(
                    issuance.getElection(), issuance.getVoter(),
                    EffectiveVoteSource.PROVISIONAL_ACCEPTED, issuance.getEventNo());
            if (!outcome.claimed()) {
                throw new ConflictException("选民已通过其他渠道有效投票，临时票不能裁定通过");
            }
        }

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
