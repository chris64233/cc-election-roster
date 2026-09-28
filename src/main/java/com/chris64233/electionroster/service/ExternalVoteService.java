package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.ExternalVoteRequest;
import com.chris64233.electionroster.api.ExternalVoteResponse;
import com.chris64233.electionroster.domain.Adjudication;
import com.chris64233.electionroster.domain.BallotContent;
import com.chris64233.electionroster.domain.CureRecord;
import com.chris64233.electionroster.domain.CureRecordStatus;
import com.chris64233.electionroster.domain.CureStatus;
import com.chris64233.electionroster.domain.EffectiveVoteSource;
import com.chris64233.electionroster.domain.Issuance;
import com.chris64233.electionroster.domain.IssuanceStatus;
import com.chris64233.electionroster.domain.SubmissionRecord;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.repo.BallotContentRepository;
import com.chris64233.electionroster.repo.CureRecordRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.ProvisionalRecordRepository;
import com.chris64233.electionroster.repo.SubmissionRecordRepository;
import com.chris64233.electionroster.repo.VoterRepository;
import com.chris64233.electionroster.web.ConflictException;
import com.chris64233.electionroster.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * “已通过其他渠道有效投票”的登记。
 *
 * 与补正确认、临时票裁定通过、常规提交计入竞争同一有效结果槽位
 * （effective_votes 唯一约束）：本登记先抢槽成功，则该选民名下
 * 暂存/待裁定的原选票立即按失败路径作废，补正失败；
 * 槽位已被本选民原渠道占用时，本登记幂等冲突返回，绝不产生第二个有效结果。
 */
@Service
public class ExternalVoteService {

    private final VoterRepository voterRepository;
    private final IssuanceRepository issuanceRepository;
    private final ProvisionalRecordRepository provisionalRecordRepository;
    private final CureRecordRepository cureRecordRepository;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final BallotContentRepository ballotContentRepository;
    private final EffectiveVoteService effectiveVoteService;
    private final AuditService auditService;

    public ExternalVoteService(VoterRepository voterRepository,
                               IssuanceRepository issuanceRepository,
                               ProvisionalRecordRepository provisionalRecordRepository,
                               CureRecordRepository cureRecordRepository,
                               SubmissionRecordRepository submissionRecordRepository,
                               BallotContentRepository ballotContentRepository,
                               EffectiveVoteService effectiveVoteService,
                               AuditService auditService) {
        this.voterRepository = voterRepository;
        this.issuanceRepository = issuanceRepository;
        this.provisionalRecordRepository = provisionalRecordRepository;
        this.cureRecordRepository = cureRecordRepository;
        this.submissionRecordRepository = submissionRecordRepository;
        this.ballotContentRepository = ballotContentRepository;
        this.effectiveVoteService = effectiveVoteService;
        this.auditService = auditService;
    }

    @Transactional
    public ExternalVoteResponse register(Long electionId, ExternalVoteRequest request) {
        Voter voter = voterRepository.findByElectionIdAndVoterRef(electionId, request.getVoterRef())
                .orElseThrow(() -> new NotFoundException("选民不在名册中: " + request.getVoterRef()));

        // 存在本系统内的签发记录时，首次读取即加行锁，与补正确认/裁定/截止裁定互斥。
        Issuance locked = issuanceRepository
                .findByElectionIdAndVoterIdForUpdate(electionId, voter.getId())
                .orElse(null);

        EffectiveVoteService.ClaimOutcome outcome = effectiveVoteService.tryClaim(
                locked != null ? locked.getElection() : voter.getElection(),
                voter, EffectiveVoteSource.EXTERNAL_CHANNEL, request.getChannelRef());

        if (!outcome.claimed()) {
            if (outcome.existingSource() == EffectiveVoteSource.EXTERNAL_CHANNEL
                    && outcome.existingRef() != null
                    && outcome.existingRef().equals(request.getChannelRef())) {
                // 同一外部渠道凭证重放：幂等返回。
                return new ExternalVoteResponse(request.getVoterRef(),
                        EffectiveVoteSource.EXTERNAL_CHANNEL.name(),
                        request.getChannelRef(), Instant.now());
            }
            throw new ConflictException("该选民已存在有效投票结果（"
                    + outcome.existingSource() + "），不能通过其他渠道重复投票");
        }

        if (locked != null) {
            invalidateLocalPendingVote(locked, request.getChannelRef());
        }

        auditService.append("EXTERNAL_VOTE", request.getChannelRef(),
                "voterRef=" + request.getVoterRef()
                        + (locked != null ? ";localBallot=" + locked.getType() : ";localBallot=NONE"));
        return new ExternalVoteResponse(request.getVoterRef(),
                EffectiveVoteSource.EXTERNAL_CHANNEL.name(),
                request.getChannelRef(), Instant.now());
    }

    /**
     * 其他渠道已构成有效投票：本系统内该选民暂存/待裁定的原选票一律失败作废，
     * 不重新签发、不保留第二个有效结果。
     */
    private void invalidateLocalPendingVote(Issuance issuance, String channelRef) {
        Optional<SubmissionRecord> submission =
                submissionRecordRepository.findByCredentialToken(issuance.getCredentialToken());

        provisionalRecordRepository.findByIssuanceId(issuance.getId()).ifPresent(provisional -> {
            if (provisional.getAdjudication() == Adjudication.PENDING) {
                provisional.decide(Adjudication.REJECTED, "已通过其他渠道有效投票: " + channelRef);
                submission.flatMap(s -> ballotContentRepository.findById(s.getBallotId()))
                        .ifPresent(BallotContent::markVoided);
            }
        });

        if (issuance.getCureStatus() == CureStatus.PENDING) {
            issuance.failCure();
            cureRecordRepository.findByIssuanceIdAndStatus(
                            issuance.getId(), CureRecordStatus.PENDING)
                    .ifPresent((CureRecord r) -> r.fail("已通过其他渠道有效投票: " + channelRef));
            submission.flatMap(s -> ballotContentRepository.findById(s.getBallotId()))
                    .ifPresent(BallotContent::markVoided);
        }

        // 尚未提交的凭证立即作废；已提交的由票面 voided 表达，凭证保持 CONSUMED。
        if (submission.isEmpty() && issuance.getStatus() == IssuanceStatus.ISSUED) {
            issuance.markVoided();
        }
    }
}
