package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.CureConfirmRequest;
import com.chris64233.electionroster.api.CureResponse;
import com.chris64233.electionroster.api.CureSubmitRequest;
import com.chris64233.electionroster.domain.BallotContent;
import com.chris64233.electionroster.domain.CureRecord;
import com.chris64233.electionroster.domain.CureRecordStatus;
import com.chris64233.electionroster.domain.CureStatus;
import com.chris64233.electionroster.domain.EffectiveVoteSource;
import com.chris64233.electionroster.domain.Issuance;
import com.chris64233.electionroster.domain.IssuanceType;
import com.chris64233.electionroster.domain.ProvisionalRecord;
import com.chris64233.electionroster.domain.SubmissionRecord;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.domain.VoterStatus;
import com.chris64233.electionroster.repo.BallotContentRepository;
import com.chris64233.electionroster.repo.CureRecordRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.ProvisionalRecordRepository;
import com.chris64233.electionroster.repo.SubmissionRecordRepository;
import com.chris64233.electionroster.web.ConflictException;
import com.chris64233.electionroster.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 补正流程：邮寄票身份材料不全暂不计入、或临时票资格争议待裁定时，
 * 选民在截止前提交新身份材料，经确认后恢复<strong>原选票</strong>的有效性。
 *
 * 核心不变量：
 * - 补正材料以版本化的 CureRecord 提交，始终关联原选票的签发记录，
 *   只用于恢复原选票，绝不重新签发第二张票；
 * - 确认时结合截止时间、选民当前资格/选区与其他渠道投票事实作出最终决定；
 * - 补正确认、其他渠道投票登记、截止裁定（以及临时票裁定）之间，
 *   以签发记录行锁串行化 + effective_votes 唯一槽位原子仲裁，
 *   跨渠道凭证消费只能保留一个有效结果；
 * - 材料记录只含身份侧信息，与票面选择物理隔离：本服务不读取 choicesJson，
 *   审计 detail 只记 contentHash 等摘要，查询接口不暴露票面内容。
 */
@Service
public class CureService {

    private final IssuanceRepository issuanceRepository;
    private final ProvisionalRecordRepository provisionalRecordRepository;
    private final CureRecordRepository cureRecordRepository;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final BallotContentRepository ballotContentRepository;
    private final EffectiveVoteService effectiveVoteService;
    private final AuditService auditService;

    public CureService(IssuanceRepository issuanceRepository,
                       ProvisionalRecordRepository provisionalRecordRepository,
                       CureRecordRepository cureRecordRepository,
                       SubmissionRecordRepository submissionRecordRepository,
                       BallotContentRepository ballotContentRepository,
                       EffectiveVoteService effectiveVoteService,
                       AuditService auditService) {
        this.issuanceRepository = issuanceRepository;
        this.provisionalRecordRepository = provisionalRecordRepository;
        this.cureRecordRepository = cureRecordRepository;
        this.submissionRecordRepository = submissionRecordRepository;
        this.ballotContentRepository = ballotContentRepository;
        this.effectiveVoteService = effectiveVoteService;
        this.auditService = auditService;
    }

    /** 提交新材料版本。必须在截止前；旧的待确认版本被替代；不产生新签发。 */
    @Transactional
    public CureResponse submit(CureSubmitRequest request) {
        Issuance issuance = lockPendingIssuance(request.getIssuanceId());
        Instant now = Instant.now();
        if (issuance.getElection().getCureDeadline() != null
                && !now.isBefore(issuance.getElection().getCureDeadline())) {
            // 截止后提交：直接进入截止裁定，原选票作废，材料不予受理。
            ruleFailed(issuance, null, "超过补正截止时间");
            return failed(issuance, null, "超过补正截止时间");
        }

        // 旧版本（若仍待确认）标记为被新版本替代。
        cureRecordRepository.findByIssuanceIdAndStatus(issuance.getId(), CureRecordStatus.PENDING)
                .ifPresent(CureRecord::supersede);

        int nextVersion = (int) cureRecordRepository.countByIssuanceId(issuance.getId()) + 1;
        CureRecord record = new CureRecord(issuance, nextVersion, request.getMaterialNotes());
        cureRecordRepository.saveAndFlush(record);

        auditService.append("CURE_SUBMIT", issuance.getEventNo(),
                "type=" + issuance.getType()
                        + ";version=" + nextVersion
                        + ";district=" + issuance.getDistrict().getCode());
        return new CureResponse(issuance.getId(), nextVersion, currentState(issuance),
                "SUBMITTED", null, record.getCreatedAt());
    }

    /** 确认补正：截止时间、选民状态、选区、其他渠道投票任一不满足即补正失败。 */
    @Transactional
    public CureResponse confirm(CureConfirmRequest request) {
        Issuance issuance = lockPendingIssuance(request.getIssuanceId());
        CureRecord record = resolvePendingRecord(issuance, request.getVersion());

        // 1) 截止时间裁定
        Instant deadline = issuance.getElection().getCureDeadline();
        if (deadline != null && !Instant.now().isBefore(deadline)) {
            ruleFailed(issuance, record, "超过补正确认截止时间");
            return failed(issuance, record, "超过补正确认截止时间");
        }

        // 2) 确认前重新检查选民状态与选区（读取名册最新状态）。
        //    补正确认意味着身份材料补齐、资格争议解除：名册状态必须恢复为 ELIGIBLE。
        Voter voter = issuance.getVoter();
        if (voter.getStatus() != VoterStatus.ELIGIBLE) {
            ruleFailed(issuance, record, "选民资格未恢复有效: " + voter.getStatus());
            return failed(issuance, record, "选民资格未恢复有效: " + voter.getStatus());
        }
        if (!voter.getDistrict().getCode().equals(issuance.getDistrict().getCode())) {
            ruleFailed(issuance, record, "名册选区与原选票选区不一致");
            return failed(issuance, record, "名册选区与原选票选区不一致");
        }
        if (request.getExpectedDistrictCode() != null
                && !request.getExpectedDistrictCode().equals(voter.getDistrict().getCode())) {
            ruleFailed(issuance, record, "确认时核验选区与名册不一致");
            return failed(issuance, record, "确认时核验选区与名册不一致");
        }

        // 3) 跨渠道原子仲裁：行锁已将本地并发串行化，唯一槽位保证只保留一个有效结果。
        EffectiveVoteService.ClaimOutcome outcome = effectiveVoteService.tryClaim(
                issuance.getElection(), voter,
                EffectiveVoteSource.CURE_CONFIRMED, "cure-v" + record.getVersion());
        if (!outcome.claimed()) {
            ruleFailed(issuance, record, "选民已通过其他渠道有效投票");
            return failed(issuance, record, "选民已通过其他渠道有效投票，补正失败");
        }

        // 补正只恢复原选票：不重新签发凭证、不创建第二张选票。
        Optional<SubmissionRecord> submission =
                submissionRecordRepository.findByCredentialToken(issuance.getCredentialToken());
        if (issuance.getType() == IssuanceType.MAIL) {
            issuance.confirmCure();
        } else {
            ProvisionalRecord provisional = provisionalRecordRepository
                    .findByIssuanceId(issuance.getId()).orElseThrow();
            provisional.decide(com.chris64233.electionroster.domain.Adjudication.ACCEPTED,
                    "补正材料核验通过 v" + record.getVersion());
        }
        record.confirm();

        // 原选票已提交的恢复计入选区（凭证在提交时即已消费）；尚未提交的，凭证保持有效，提交时直接计入。
        String ballotState = "submitted=false";
        if (submission.isPresent()) {
            BallotContent ballot = ballotContentRepository
                    .findById(submission.get().getBallotId()).orElseThrow();
            ballot.markCounted();
            ballotState = "contentHash=" + ballot.getContentHash();
        }

        auditService.append("CURE_CONFIRM", issuance.getEventNo(),
                "type=" + issuance.getType()
                        + ";version=" + record.getVersion()
                        + ";district=" + issuance.getDistrict().getCode()
                        + ";" + ballotState);
        return new CureResponse(issuance.getId(), record.getVersion(), currentState(issuance),
                "CONFIRMED", null, record.getDecidedAt());
    }

    /**
     * 截止裁定（截止批处理逐张调用；确认/提交时也会复用同一规则）：
     * 仍处于暂存/待裁定状态的原选票一律补正失败、永久作废。
     * 返回 true 表示本次执行了裁定；已被确认/其他渠道投票先行终结的返回 false。
     */
    @Transactional
    public boolean adjudicateDeadline(Long issuanceId) {
        Issuance issuance = issuanceRepository.findByIdForUpdate(issuanceId)
                .orElseThrow(() -> new NotFoundException("签发记录不存在: " + issuanceId));
        if (!isPending(issuance)) {
            return false;
        }
        CureRecord pending = cureRecordRepository
                .findByIssuanceIdAndStatus(issuanceId, CureRecordStatus.PENDING).orElse(null);
        ruleFailed(issuance, pending, "补正截止时仍未完成确认");
        auditService.append("DEADLINE_RULING", issuance.getEventNo(),
                "type=" + issuance.getType() + ";district=" + issuance.getDistrict().getCode());
        return true;
    }

    /** 锁定并校验：该签发记录必须仍在“身份材料不全、暂不计入”的流程中。 */
    private Issuance lockPendingIssuance(Long issuanceId) {
        Issuance issuance = issuanceRepository.findByIdForUpdate(issuanceId)
                .orElseThrow(() -> new NotFoundException("签发记录不存在: " + issuanceId));
        if (!isPending(issuance)) {
            throw new ConflictException("该签发记录当前状态不允许补正: "
                    + issuance.getType() + "/" + issuance.getCureStatus());
        }
        return issuance;
    }

    private boolean isPending(Issuance issuance) {
        if (issuance.getType() == IssuanceType.MAIL) {
            return issuance.getCureStatus() == CureStatus.PENDING;
        }
        if (issuance.getType() == IssuanceType.PROVISIONAL) {
            return provisionalRecordRepository.findByIssuanceId(issuance.getId())
                    .map(p -> p.getAdjudication() == com.chris64233.electionroster.domain.Adjudication.PENDING)
                    .orElse(false);
        }
        return false;
    }

    private String currentState(Issuance issuance) {
        if (issuance.getType() == IssuanceType.MAIL) {
            return issuance.getCureStatus().name();
        }
        return provisionalRecordRepository.findByIssuanceId(issuance.getId())
                .map(p -> p.getAdjudication().name()).orElse("NONE");
    }

    private CureRecord resolvePendingRecord(Issuance issuance, Integer requestedVersion) {
        if (requestedVersion != null) {
            CureRecord record = cureRecordRepository
                    .findByIssuanceIdAndVersion(issuance.getId(), requestedVersion)
                    .orElseThrow(() -> new NotFoundException(
                            "补正材料版本不存在: " + requestedVersion));
            if (record.getStatus() != CureRecordStatus.PENDING) {
                throw new ConflictException("该材料版本已处理: " + record.getStatus());
            }
            return record;
        }
        return cureRecordRepository
                .findByIssuanceIdAndStatus(issuance.getId(), CureRecordStatus.PENDING)
                .orElseThrow(() -> new ConflictException("没有待确认的补正材料"));
    }

    /** 补正失败的统一落库：材料记录失败；原选票已提交则票面 voided，未提交则凭证作废。 */
    private void ruleFailed(Issuance issuance, CureRecord record, String reason) {
        if (record != null) {
            record.fail(reason);
        }
        Optional<SubmissionRecord> submission =
                submissionRecordRepository.findByCredentialToken(issuance.getCredentialToken());
        if (submission.isPresent()) {
            ballotContentRepository.findById(submission.get().getBallotId())
                    .ifPresent(BallotContent::markVoided);
        }
        if (issuance.getType() == IssuanceType.MAIL) {
            issuance.failCure();
            if (submission.isEmpty()) {
                issuance.markVoided();
            }
        } else {
            provisionalRecordRepository.findByIssuanceId(issuance.getId())
                    .filter(p -> p.getAdjudication() == com.chris64233.electionroster.domain.Adjudication.PENDING)
                    .ifPresent(p -> p.decide(
                            com.chris64233.electionroster.domain.Adjudication.REJECTED, reason));
            if (submission.isEmpty()) {
                issuance.markVoided();
            }
        }
        auditService.append("CURE_FAIL", issuance.getEventNo(),
                "type=" + issuance.getType()
                        + ";reason=" + reason
                        + ";district=" + issuance.getDistrict().getCode());
    }

    private CureResponse failed(Issuance issuance, CureRecord record, String reason) {
        Instant at = record != null ? record.getDecidedAt() : Instant.now();
        Integer version = record != null ? record.getVersion() : null;
        return new CureResponse(issuance.getId(), version, currentState(issuance),
                "FAILED", reason, at);
    }
}
