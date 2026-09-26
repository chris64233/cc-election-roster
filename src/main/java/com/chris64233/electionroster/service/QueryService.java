package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.DistrictSummaryResponse;
import com.chris64233.electionroster.api.VoterStatusResponse;
import com.chris64233.electionroster.domain.Adjudication;
import com.chris64233.electionroster.domain.AuditEvent;
import com.chris64233.electionroster.domain.District;
import com.chris64233.electionroster.domain.IssuanceStatus;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.repo.AuditEventRepository;
import com.chris64233.electionroster.repo.BallotContentRepository;
import com.chris64233.electionroster.repo.DistrictRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.ProvisionalRecordRepository;
import com.chris64233.electionroster.repo.VoterRepository;
import com.chris64233.electionroster.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 只读查询：选民签发状态、临时票裁定、选区汇总、审计链。均不返回票面选择内容。 */
@Service
public class QueryService {

    private final VoterRepository voterRepository;
    private final IssuanceRepository issuanceRepository;
    private final ProvisionalRecordRepository provisionalRecordRepository;
    private final DistrictRepository districtRepository;
    private final BallotContentRepository ballotContentRepository;
    private final AuditEventRepository auditEventRepository;

    public QueryService(VoterRepository voterRepository,
                        IssuanceRepository issuanceRepository,
                        ProvisionalRecordRepository provisionalRecordRepository,
                        DistrictRepository districtRepository,
                        BallotContentRepository ballotContentRepository,
                        AuditEventRepository auditEventRepository) {
        this.voterRepository = voterRepository;
        this.issuanceRepository = issuanceRepository;
        this.provisionalRecordRepository = provisionalRecordRepository;
        this.districtRepository = districtRepository;
        this.ballotContentRepository = ballotContentRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public VoterStatusResponse voterStatus(Long electionId, String voterRef) {
        Voter voter = voterRepository.findByElectionIdAndVoterRef(electionId, voterRef)
                .orElseThrow(() -> new NotFoundException("选民不在名册中: " + voterRef));
        var issuance = issuanceRepository.findByElectionIdAndVoterId(electionId, voter.getId());
        if (issuance.isEmpty()) {
            return new VoterStatusResponse(voterRef, voter.getStatus().name(), false,
                    null, null, voter.getDistrict().getCode(), null, null, null);
        }
        var i = issuance.get();
        String adjudication = provisionalRecordRepository.findByIssuanceId(i.getId())
                .map(p -> p.getAdjudication().name()).orElse(null);
        return new VoterStatusResponse(
                voterRef,
                voter.getStatus().name(),
                true,
                i.getType().name(),
                i.getStatus().name(),
                i.getDistrict().getCode(),
                i.getPollingPlace(),
                i.getCreatedAt(),
                adjudication);
    }

    @Transactional(readOnly = true)
    public DistrictSummaryResponse districtSummary(Long districtId) {
        District district = districtRepository.findById(districtId)
                .orElseThrow(() -> new NotFoundException("选区不存在: " + districtId));
        long consumed = issuanceRepository.countByDistrictIdAndStatus(districtId, IssuanceStatus.CONSUMED);
        long counted = ballotContentRepository.countByDistrictIdAndCountedTrue(districtId);
        long pending = provisionalRecordRepository
                .countByIssuance_District_IdAndAdjudication(districtId, Adjudication.PENDING);
        long issuedTotal = issuanceRepository.countByDistrictIdAndStatus(districtId, IssuanceStatus.ISSUED)
                + consumed
                + issuanceRepository.countByDistrictIdAndStatus(districtId, IssuanceStatus.VOIDED);
        return new DistrictSummaryResponse(district.getCode(), issuedTotal, consumed, counted, pending);
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> auditChain() {
        return auditEventRepository.findAllByOrderByIdAsc().stream()
                .map(e -> new AuditEventView(e.getId(), e.getEventType(), e.getRefId(),
                        e.getDetail(), e.getPrevHash(), e.getEventHash(), e.getCreatedAt()))
                .toList();
    }

    /** 审计链视图：detail 只有摘要，没有票面选择。 */
    public record AuditEventView(Long id, String eventType, String refId, String detail,
                                 String prevHash, String eventHash, java.time.Instant createdAt) {
    }
}
