package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.IssuanceResponse;
import com.chris64233.electionroster.api.IssueRequest;
import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.domain.Issuance;
import com.chris64233.electionroster.domain.IssuanceType;
import com.chris64233.electionroster.domain.ProvisionalRecord;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.domain.VoterStatus;
import com.chris64233.electionroster.repo.ElectionRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.ProvisionalRecordRepository;
import com.chris64233.electionroster.repo.VoterRepository;
import com.chris64233.electionroster.web.ConflictException;
import com.chris64233.electionroster.web.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 选票签发。核心不变量：
 * - 同一选民在一次选举中最多一张票（正式/临时/邮寄共享 uk_issuance_election_voter）；
 * - 签发事件号幂等：相同 eventNo 重复请求返回首次结果；
 * - 凭证创建后不可修改，只允许状态单向流转。
 */
@Service
public class IssuanceService {

    private final ElectionRepository electionRepository;
    private final VoterRepository voterRepository;
    private final IssuanceRepository issuanceRepository;
    private final ProvisionalRecordRepository provisionalRecordRepository;
    private final AuditService auditService;

    public IssuanceService(ElectionRepository electionRepository,
                           VoterRepository voterRepository,
                           IssuanceRepository issuanceRepository,
                           ProvisionalRecordRepository provisionalRecordRepository,
                           AuditService auditService) {
        this.electionRepository = electionRepository;
        this.voterRepository = voterRepository;
        this.issuanceRepository = issuanceRepository;
        this.provisionalRecordRepository = provisionalRecordRepository;
        this.auditService = auditService;
    }

    @Transactional
    public IssuanceResponse issue(IssueRequest request) {
        Election election = electionRepository.findById(request.getElectionId())
                .orElseThrow(() -> new NotFoundException("选举不存在: " + request.getElectionId()));
        IssuanceType type = parseType(request.getType());

        // 幂等：同一事件号的重复请求返回首次签发结果；事件号被不同请求占用则冲突。
        var byEventNo = issuanceRepository.findByElectionIdAndEventNo(election.getId(), request.getEventNo());
        if (byEventNo.isPresent()) {
            Issuance existing = byEventNo.get();
            if (!existing.getVoter().getVoterRef().equals(request.getVoterRef()) || existing.getType() != type) {
                throw new ConflictException("签发事件号已被其他请求使用: " + request.getEventNo());
            }
            return toResponse(existing);
        }

        Voter voter = voterRepository.findByElectionIdAndVoterRef(election.getId(), request.getVoterRef())
                .orElseThrow(() -> new NotFoundException("选民不在本次选举名册中: " + request.getVoterRef()));

        // 状态与选区核验
        if (voter.getStatus() == VoterStatus.INELIGIBLE) {
            throw new ConflictException("选民无投票资格: " + request.getVoterRef());
        }
        if (voter.getStatus() == VoterStatus.DISPUTED && type != IssuanceType.PROVISIONAL) {
            throw new ConflictException("选民资格存在争议，只能签发临时票");
        }
        if (voter.getStatus() == VoterStatus.ELIGIBLE && type == IssuanceType.PROVISIONAL) {
            throw new ConflictException("选民资格无争议，不应签发临时票");
        }
        if (request.getExpectedDistrictCode() != null
                && !request.getExpectedDistrictCode().equals(voter.getDistrict().getCode())) {
            throw new ConflictException("投票点核验选区与名册不一致: " + request.getExpectedDistrictCode());
        }

        Issuance issuance = new Issuance(election, voter, voter.getDistrict(),
                voter.getDistrict().getBallotStyle(), type, request.getEventNo(),
                UUID.randomUUID().toString(), request.getPollingPlace());
        try {
            issuance = issuanceRepository.saveAndFlush(issuance);
        } catch (DataIntegrityViolationException e) {
            // 并发：另一投票点已为该选民完成签发（共享唯一约束），本请求失败。
            throw new ConflictException("该选民在本次选举中已签发选票: " + request.getVoterRef());
        }

        if (type == IssuanceType.PROVISIONAL) {
            String notes = request.getIdentityNotes() != null ? request.getIdentityNotes() : "";
            provisionalRecordRepository.save(new ProvisionalRecord(issuance, notes));
        }

        auditService.append("ISSUE", issuance.getEventNo(),
                "type=" + type + ";district=" + voter.getDistrict().getCode()
                        + ";pollingPlace=" + request.getPollingPlace());
        return toResponse(issuance);
    }

    private IssuanceType parseType(String raw) {
        try {
            return IssuanceType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("非法签发类型: " + raw);
        }
    }

    static IssuanceResponse toResponse(Issuance issuance) {
        return new IssuanceResponse(
                issuance.getId(),
                issuance.getEventNo(),
                issuance.getCredentialToken(),
                issuance.getType().name(),
                issuance.getStatus().name(),
                issuance.getDistrict().getCode(),
                issuance.getBallotStyle().getCode(),
                issuance.getPollingPlace(),
                issuance.getCreatedAt());
    }
}
