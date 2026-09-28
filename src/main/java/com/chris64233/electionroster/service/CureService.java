package com.chris64233.electionroster.service;

import com.chris64233.electionroster.api.CureConfirmationResponse;
import com.chris64233.electionroster.api.CureMaterialResponse;
import com.chris64233.electionroster.api.ExternalVoteResponse;
import com.chris64233.electionroster.domain.BallotContent;
import com.chris64233.electionroster.domain.CureMaterial;
import com.chris64233.electionroster.domain.CureRecord;
import com.chris64233.electionroster.domain.CureStatus;
import com.chris64233.electionroster.domain.ExternalVoteRecord;
import com.chris64233.electionroster.domain.Issuance;
import com.chris64233.electionroster.domain.SubmissionRecord;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.domain.VoterStatus;
import com.chris64233.electionroster.repo.BallotContentRepository;
import com.chris64233.electionroster.repo.CureMaterialRepository;
import com.chris64233.electionroster.repo.CureRecordRepository;
import com.chris64233.electionroster.repo.ExternalVoteRecordRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.SubmissionRecordRepository;
import com.chris64233.electionroster.repo.VoterRepository;
import com.chris64233.electionroster.web.ConflictException;
import com.chris64233.electionroster.web.NotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 身份材料补正与跨渠道投票裁决。
 *
 * <p>三类流程并发竞争同一名选民的“唯一有效结果”：
 * <ol>
 *   <li><b>补正确认</b>：恢复原暂存选票并计入；</li>
 *   <li><b>其他渠道有效投票</b>：登记外部结果并令本地待决选票失效；</li>
 *   <li><b>截止裁定</b>：逾期未补正的暂存选票作废。</li>
 * </ol>
 * 三者都先对选民行加写锁（SELECT … FOR UPDATE），跨渠道凭证消费在同一事务内原子完成；
 * 外部投票另有 {@code (election,voter)} 唯一约束兜底，保证最多一个有效结果。
 *
 * <p>补正全程只操作原签发/原选票：提交新材料只追加关联到原补正记录的版本，
 * 绝不重新签发第二张票。身份材料与票面选择隔离，本服务不读取票面内容。
 */
@Service
public class CureService {

    private final CureRecordRepository cureRecordRepository;
    private final CureMaterialRepository cureMaterialRepository;
    private final ExternalVoteRecordRepository externalVoteRecordRepository;
    private final IssuanceRepository issuanceRepository;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final BallotContentRepository ballotContentRepository;
    private final VoterRepository voterRepository;
    private final AuditService auditService;
    private final EntityManager entityManager;

    public CureService(CureRecordRepository cureRecordRepository,
                       CureMaterialRepository cureMaterialRepository,
                       ExternalVoteRecordRepository externalVoteRecordRepository,
                       IssuanceRepository issuanceRepository,
                       SubmissionRecordRepository submissionRecordRepository,
                       BallotContentRepository ballotContentRepository,
                       VoterRepository voterRepository,
                       AuditService auditService,
                       EntityManager entityManager) {
        this.cureRecordRepository = cureRecordRepository;
        this.cureMaterialRepository = cureMaterialRepository;
        this.externalVoteRecordRepository = externalVoteRecordRepository;
        this.issuanceRepository = issuanceRepository;
        this.submissionRecordRepository = submissionRecordRepository;
        this.ballotContentRepository = ballotContentRepository;
        this.voterRepository = voterRepository;
        this.auditService = auditService;
        this.entityManager = entityManager;
    }

    /**
     * 选民在截止前提交补正新材料。形成新版本并关联原补正记录（即原选票），不重新签发。
     * 材料只存说明与哈希，不接触票面选择。
     */
    @Transactional
    public CureMaterialResponse submitMaterial(Long issuanceId, String materialNotes, String materialContent) {
        CureRecord cure = requireCure(issuanceId);
        // 与确认/外部投票/截止裁定竞争同一选民行锁，避免在补正被终局的同时插入新材料。
        lockVoter(cure.getIssuance());
        entityManager.refresh(cure);
        if (cure.getStatus() != CureStatus.PENDING) {
            throw new ConflictException("补正已结束，不能再提交材料: " + cure.getStatus());
        }
        Instant now = Instant.now();
        if (now.isAfter(cure.getCureDeadline())) {
            throw new ConflictException("已超过补正截止时间，不能再提交材料");
        }

        int version = (int) cureMaterialRepository.countByCureRecordId(cure.getId()) + 1;
        String materialHash = AuditService.sha256(
                materialContent != null ? materialContent : (materialNotes != null ? materialNotes : ""));
        String notes = materialNotes != null ? materialNotes : "";
        CureMaterial saved = cureMaterialRepository
                .save(new CureMaterial(cure, version, notes, materialHash));

        auditService.append("CURE_MATERIAL", String.valueOf(issuanceId),
                "version=" + version + ";materialHash=" + materialHash);
        return new CureMaterialResponse(issuanceId, version, materialHash,
                cure.getStatus().name(), cure.getCureDeadline(), saved.getSubmittedAt());
    }

    /**
     * 补正确认：最终决定原暂存选票是否恢复计入。确认前重新检查
     * 截止时间、选民状态、选区一致性，以及是否已通过其他渠道有效投票。
     *
     * <p>失败结论（其他渠道/逾期/资格不符/选区不符）是需要落库的终局业务结果，
     * 因此 ConflictException 不触发回滚：补正终态与选票作废一并提交，仍以 409 返回。
     */
    @Transactional(noRollbackFor = ConflictException.class)
    public CureConfirmationResponse confirm(Long issuanceId, String reason) {
        Issuance issuance = issuanceRepository.findById(issuanceId)
                .orElseThrow(() -> new NotFoundException("签发记录不存在: " + issuanceId));
        // 先竞争选民行锁：与其他渠道投票登记、截止裁定串行。
        Voter voter = lockVoter(issuance);
        // 锁后再读取补正记录，确保拿到并发流程刚提交的终态，而不是加锁前的旧值。
        CureRecord cure = requireCure(issuanceId);

        // 终态不可重复确认（原子状态迁移，重复请求直接冲突）。
        if (cure.getStatus() != CureStatus.PENDING) {
            throw new ConflictException("补正已结束: " + cure.getStatus());
        }

        if (externalVoteRecordRepository
                .findByElectionIdAndVoterId(issuance.getElection().getId(), voter.getId()).isPresent()) {
            cure.supersede("已通过其他渠道有效投票");
            voidHeldBallot(issuance);
            auditService.append("CURE_SUPERSEDE", String.valueOf(issuanceId),
                    "reason=externalVote");
            throw new ConflictException("该选民已通过其他渠道有效投票，补正失败");
        }

        if (Instant.now().isAfter(cure.getCureDeadline())) {
            cure.expire("超过补正截止时间");
            voidHeldBallot(issuance);
            auditService.append("CURE_EXPIRE", String.valueOf(issuanceId), "at=confirm");
            throw new ConflictException("已超过补正截止时间，补正失败");
        }

        if (voter.getStatus() == VoterStatus.INELIGIBLE) {
            cure.reject("选民已被裁定无投票资格");
            voidHeldBallot(issuance);
            auditService.append("CURE_REJECT", String.valueOf(issuanceId), "reason=ineligible");
            throw new ConflictException("选民无投票资格，补正失败");
        }

        if (!voter.getDistrict().getId().equals(issuance.getDistrict().getId())) {
            cure.reject("名册选区与原选票选区不一致");
            voidHeldBallot(issuance);
            auditService.append("CURE_REJECT", String.valueOf(issuanceId), "reason=districtMismatch");
            throw new ConflictException("选区核验不一致，补正失败");
        }

        if (cureMaterialRepository.countByCureRecordId(cure.getId()) == 0) {
            throw new ConflictException("尚未提交补正材料，不能确认");
        }

        SubmissionRecord submission = submissionRecordRepository
                .findByCredentialToken(issuance.getCredentialToken())
                .orElseThrow(() -> new IllegalStateException("暂存选票缺少提交记录"));
        BallotContent ballot = ballotContentRepository.findById(submission.getBallotId()).orElseThrow();
        if (ballot.isCounted() || ballot.isVoided()) {
            // 防御：已被其他流程终局处理，不允许再次计入。
            throw new ConflictException("原选票已终局处理，不能再通过补正计入");
        }

        ballot.markCounted();   // 恢复的是原选票
        cure.confirm(reason);
        auditService.append("CURE_CONFIRM", String.valueOf(issuanceId),
                "district=" + issuance.getDistrict().getCode()
                        + ";contentHash=" + ballot.getContentHash());
        return new CureConfirmationResponse(issuanceId, cure.getStatus().name(), reason,
                cure.getDecidedAt(), true);
    }

    /**
     * 登记选民已通过其他渠道有效投票。跨渠道凭证消费与本地选票处置在同一事务原子完成：
     * 本地已有有效（已计入）选票则拒绝外部登记；有待决/暂存选票则令其失效。
     */
    @Transactional(noRollbackFor = ConflictException.class)
    public ExternalVoteResponse recordExternalVote(Long electionId, String voterRef,
                                                   String channel, String externalRef) {
        Voter voter = voterRepository.findByElectionIdAndVoterRef(electionId, voterRef)
                .orElseThrow(() -> new NotFoundException("选民不在名册中: " + voterRef));
        // 先持有选民行锁：与补正确认、截止裁定以及并发外部登记串行，下面的判定即为权威结果。
        voterRepository.findForLockById(voter.getId());

        // 行锁下预查：同凭证重放幂等返回；不同凭证说明已在别处有效投票，直接冲突。
        // 不依赖插入冲突（那会使会话留下空 id 实体），(election,voter) 唯一约束仍作最终兜底。
        var prior = externalVoteRecordRepository.findByElectionIdAndVoterId(electionId, voter.getId());
        if (prior.isPresent()) {
            ExternalVoteRecord existing = prior.get();
            if (existing.getExternalRef().equals(externalRef) && existing.getChannel().equals(channel)) {
                return new ExternalVoteResponse(electionId, voterRef, existing.getChannel(),
                        existing.getExternalRef(), "DUPLICATE", existing.getVotedAt());
            }
            throw new ConflictException("该选民已通过其他渠道有效投票: " + existing.getChannel());
        }

        var issuanceOpt = issuanceRepository.findByElectionIdAndVoterId(electionId, voter.getId());
        String outcome = "RECORDED";
        if (issuanceOpt.isPresent()) {
            Issuance issuance = issuanceOpt.get();
            var submissionOpt = submissionRecordRepository
                    .findByCredentialToken(issuance.getCredentialToken());
            if (submissionOpt.isPresent()) {
                BallotContent ballot = ballotContentRepository
                        .findById(submissionOpt.get().getBallotId()).orElseThrow();
                if (ballot.isCounted()) {
                    // 本地有效结果已成立，外部结果必须回滚：唯一有效结果归本地。
                    throw new ConflictException("本地渠道已形成有效投票，其他渠道登记失败");
                }
                if (!ballot.isVoided()) {
                    ballot.markVoided();
                }
                outcome = "LOCAL_BALLOT_VOIDED";
            } else if (issuance.getStatus().name().equals("ISSUED")) {
                issuance.markVoided();
                outcome = "UNCONSUMED_CREDENTIAL_VOIDED";
            }
            // 有待决补正则一并终结，使其不可能再被确认计入。
            cureRecordRepository.findByIssuanceId(issuance.getId()).ifPresent(cure -> {
                if (cure.getStatus() == CureStatus.PENDING) {
                    cure.supersede("已通过其他渠道有效投票");
                    auditService.append("CURE_SUPERSEDE", String.valueOf(issuance.getId()),
                            "reason=externalVote:" + channel);
                }
            });
        }

        try {
            externalVoteRecordRepository.saveAndFlush(
                    new ExternalVoteRecord(voter.getElection(), voter, channel, externalRef));
        } catch (DataIntegrityViolationException e) {
            // 理论上被行锁串行化排除；保留唯一约束兜底，若发生则交由唯一有效结果裁决。
            throw new ConflictException("该选民已通过其他渠道有效投票（并发裁决）");
        }

        auditService.append("EXTERNAL_VOTE", externalRef,
                "channel=" + channel + ";outcome=" + outcome);
        return new ExternalVoteResponse(electionId, voterRef, channel, externalRef,
                outcome, Instant.now());
    }

    /**
     * 截止裁定：把所有已过截止时间仍 PENDING 的补正作废（原暂存选票永久失效）。
     * 逐个在选民行锁下复核，若期间出现其他渠道有效投票则按 SUPERSEDED 处理。
     * 返回被终局处理的补正数。
     */
    @Transactional
    public int expireOverdueCures() {
        Instant now = Instant.now();
        List<Long> pendingIds = cureRecordRepository.findByStatus(CureStatus.PENDING).stream()
                .filter(c -> now.isAfter(c.getCureDeadline()))
                .map(CureRecord::getId)
                .toList();
        int decided = 0;
        for (Long cureId : pendingIds) {
            CureRecord cure = cureRecordRepository.findById(cureId).orElseThrow();
            Issuance issuance = cure.getIssuance();
            lockVoter(issuance);
            // 锁后重读最新状态：可能已被并发的确认/外部投票终局。
            entityManager.refresh(cure);
            if (cure.getStatus() != CureStatus.PENDING) {
                continue;
            }
            boolean external = externalVoteRecordRepository
                    .findByElectionIdAndVoterId(issuance.getElection().getId(), issuance.getVoter().getId())
                    .isPresent();
            if (external) {
                cure.supersede("已通过其他渠道有效投票");
                auditService.append("CURE_SUPERSEDE", String.valueOf(issuance.getId()),
                        "at=deadlineSweep");
            } else {
                cure.expire("超过补正截止时间");
                auditService.append("CURE_EXPIRE", String.valueOf(issuance.getId()), "at=deadlineSweep");
            }
            voidHeldBallot(issuance);
            decided++;
        }
        return decided;
    }

    private CureRecord requireCure(Long issuanceId) {
        return cureRecordRepository.findByIssuanceId(issuanceId)
                .orElseThrow(() -> new NotFoundException("补正记录不存在: " + issuanceId));
    }

    private Voter lockVoter(Issuance issuance) {
        return voterRepository.findForLockById(issuance.getVoter().getId()).orElseThrow();
    }

    private void voidHeldBallot(Issuance issuance) {
        submissionRecordRepository.findByCredentialToken(issuance.getCredentialToken())
                .ifPresent(submission -> ballotContentRepository
                        .findById(submission.getBallotId())
                        .ifPresent(ballot -> {
                            if (!ballot.isCounted() && !ballot.isVoided()) {
                                ballot.markVoided();
                            }
                        }));
    }
}
