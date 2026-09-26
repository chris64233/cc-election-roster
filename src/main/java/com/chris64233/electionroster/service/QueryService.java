package com.chris64233.electionroster.service;

import com.chris64233.electionroster.domain.AdjudicationStatus;
import com.chris64233.electionroster.domain.BallotIssuance;
import com.chris64233.electionroster.domain.IssuanceType;
import com.chris64233.electionroster.domain.Precinct;
import com.chris64233.electionroster.domain.ProvisionalBallot;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.repository.BallotIssuanceRepository;
import com.chris64233.electionroster.repository.CastBallotRepository;
import com.chris64233.electionroster.repository.PrecinctRepository;
import com.chris64233.electionroster.repository.ProvisionalBallotRepository;
import com.chris64233.electionroster.repository.VoterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 查询服务。所有视图都保持票面选择与身份信息隔离：
 * 选民签发状态不含票面内容，选区汇总不含选民身份。
 */
@Service
@Transactional(readOnly = true)
public class QueryService {

    private final VoterRepository voterRepository;
    private final BallotIssuanceRepository issuanceRepository;
    private final ProvisionalBallotRepository provisionalBallotRepository;
    private final CastBallotRepository castBallotRepository;
    private final PrecinctRepository precinctRepository;

    public QueryService(VoterRepository voterRepository,
                        BallotIssuanceRepository issuanceRepository,
                        ProvisionalBallotRepository provisionalBallotRepository,
                        CastBallotRepository castBallotRepository,
                        PrecinctRepository precinctRepository) {
        this.voterRepository = voterRepository;
        this.issuanceRepository = issuanceRepository;
        this.provisionalBallotRepository = provisionalBallotRepository;
        this.castBallotRepository = castBallotRepository;
        this.precinctRepository = precinctRepository;
    }

    /** 选民签发状态：只含签发/消费/作废状态，不含票面选择。 */
    public VoterIssuanceStatusView voterIssuanceStatus(Long electionId, String voterRef) {
        Voter voter = voterRepository.findByElectionIdAndVoterRef(electionId, voterRef)
                .orElseThrow(() -> new NotFoundException("选民不在本次选举名册中: " + voterRef));
        return issuanceRepository.findByElectionIdAndVoterId(electionId, voter.getId())
                .map(issuance -> new VoterIssuanceStatusView(
                        voter.getVoterRef(),
                        voter.getStatus().name(),
                        voter.getPrecinct().getCode(),
                        true,
                        issuance.getType().name(),
                        issuance.getStatus().name(),
                        issuance.getPollingPlace(),
                        issuance.getCreatedAt(),
                        issuance.getConsumedAt()))
                .orElseGet(() -> new VoterIssuanceStatusView(
                        voter.getVoterRef(),
                        voter.getStatus().name(),
                        voter.getPrecinct().getCode(),
                        false, null, null, null, null, null));
    }

    /** 临时票裁定查询：不含票面内容，不含选民身份。 */
    public List<ProvisionalBallotView> provisionalBallots(Long electionId, AdjudicationStatus status) {
        List<ProvisionalBallot> ballots = status == null
                ? provisionalBallotRepository.findByIssuanceElectionIdOrderById(electionId)
                : provisionalBallotRepository.findByIssuanceElectionIdAndAdjudicationStatusOrderById(
                        electionId, status);
        return ballots.stream()
                .map(ballot -> new ProvisionalBallotView(
                        ballot.getId(),
                        ballot.getIssuance().getPrecinct().getCode(),
                        ballot.getIssuance().getBallotStyle().getCode(),
                        ballot.getAdjudicationStatus().name(),
                        ballot.getAdjudicatedAt(),
                        ballot.getAdjudicationReason()))
                .toList();
    }

    /** 选区汇总：只含计数，不含任何票面内容与选民身份。 */
    public PrecinctSummaryView precinctSummary(Long electionId, String precinctCode) {
        Precinct precinct = precinctRepository.findByElectionIdAndCode(electionId, precinctCode)
                .orElseThrow(() -> new NotFoundException("选区不存在: " + precinctCode));
        Long precinctId = precinct.getId();

        long issuedOfficial = issuanceRepository.countByElectionIdAndPrecinctIdAndType(
                electionId, precinctId, IssuanceType.OFFICIAL);
        long issuedProvisional = issuanceRepository.countByElectionIdAndPrecinctIdAndType(
                electionId, precinctId, IssuanceType.PROVISIONAL);
        long issuedMail = issuanceRepository.countByElectionIdAndPrecinctIdAndType(
                electionId, precinctId, IssuanceType.MAIL);
        long countedOfficial = castBallotRepository.countByElectionIdAndPrecinctIdAndSourceType(
                electionId, precinctId, IssuanceType.OFFICIAL);
        long countedMail = castBallotRepository.countByElectionIdAndPrecinctIdAndSourceType(
                electionId, precinctId, IssuanceType.MAIL);
        long countedProvisional = castBallotRepository.countByElectionIdAndPrecinctIdAndSourceType(
                electionId, precinctId, IssuanceType.PROVISIONAL);
        long pendingProvisional = provisionalBallotRepository
                .countByIssuanceElectionIdAndIssuancePrecinctIdAndAdjudicationStatus(
                        electionId, precinctId, AdjudicationStatus.PENDING);
        long rejectedProvisional = provisionalBallotRepository
                .countByIssuanceElectionIdAndIssuancePrecinctIdAndAdjudicationStatus(
                        electionId, precinctId, AdjudicationStatus.REJECTED);

        return new PrecinctSummaryView(
                precinct.getCode(), precinct.getName(),
                issuedOfficial, issuedMail, issuedProvisional,
                countedOfficial, countedMail, countedProvisional,
                countedOfficial + countedMail + countedProvisional,
                pendingProvisional, rejectedProvisional);
    }

    public record VoterIssuanceStatusView(
            String voterRef,
            String rosterStatus,
            String precinctCode,
            boolean issued,
            String issuanceType,
            String issuanceStatus,
            String pollingPlace,
            Instant issuedAt,
            Instant consumedAt) {
    }

    public record ProvisionalBallotView(
            Long provisionalBallotId,
            String precinctCode,
            String ballotStyleCode,
            String adjudicationStatus,
            Instant adjudicatedAt,
            String adjudicationReason) {
    }

    public record PrecinctSummaryView(
            String precinctCode,
            String precinctName,
            long issuedOfficial,
            long issuedMail,
            long issuedProvisional,
            long countedOfficial,
            long countedMail,
            long countedProvisional,
            long countedTotal,
            long pendingProvisional,
            long rejectedProvisional) {
    }
}
