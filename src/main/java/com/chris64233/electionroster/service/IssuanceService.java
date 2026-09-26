package com.chris64233.electionroster.service;

import com.chris64233.electionroster.domain.BallotIssuance;
import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.domain.IdentityVerification;
import com.chris64233.electionroster.domain.IssuanceType;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.repository.BallotIssuanceRepository;
import com.chris64233.electionroster.repository.ElectionRepository;
import com.chris64233.electionroster.repository.IdentityVerificationRepository;
import com.chris64233.electionroster.repository.VoterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 选票签发服务。
 *
 * <p>业务规则：
 * <ul>
 *   <li>同一选民在一次选举中只能取得一张签发（正式、临时、邮寄共享唯一约束）；</li>
 *   <li>ELIGIBLE 选民可签发正式票或登记邮寄票；DISPUTED 选民只能签发临时票，
 *       且必须同时登记身份核验信息（与票面内容分表保存）；INELIGIBLE 选民禁止签发；</li>
 *   <li>签发事件号（eventId）保证幂等：重复提交同一事件号返回原签发结果；</li>
 *   <li>并发签发由数据库唯一约束兜底，同一选民最多一个事务成功。</li>
 * </ul>
 */
@Service
public class IssuanceService {

    private final ElectionRepository electionRepository;
    private final VoterRepository voterRepository;
    private final BallotIssuanceRepository issuanceRepository;
    private final IdentityVerificationRepository verificationRepository;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public IssuanceService(ElectionRepository electionRepository,
                           VoterRepository voterRepository,
                           BallotIssuanceRepository issuanceRepository,
                           IdentityVerificationRepository verificationRepository,
                           AuditService auditService,
                           TransactionTemplate transactionTemplate) {
        this.electionRepository = electionRepository;
        this.voterRepository = voterRepository;
        this.issuanceRepository = issuanceRepository;
        this.verificationRepository = verificationRepository;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 签发选票。本方法本身不开启事务：先按事件号做幂等检查，
     * 再在独立事务中写入；并发冲突时在新事务中解析最终结果。
     */
    public IssuanceResult issue(Long electionId, IssueCommand command) {
        var replay = issuanceRepository.findByEventId(command.eventId());
        if (replay.isPresent()) {
            return IssuanceResult.of(replay.get(), true);
        }
        try {
            return transactionTemplate.execute(status -> doIssue(electionId, command));
        } catch (DataIntegrityViolationException e) {
            // 并发下唯一约束兜底：同一事件号 → 幂等重放；同一选民 → 冲突。
            return issuanceRepository.findByEventId(command.eventId())
                    .map(issuance -> IssuanceResult.of(issuance, true))
                    .orElseThrow(() -> new ConflictException("该选民在本次选举中已经取得选票，禁止重复签发"));
        }
    }

    private IssuanceResult doIssue(Long electionId, IssueCommand command) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new NotFoundException("选举不存在: " + electionId));
        Voter voter = voterRepository.findByElectionIdAndVoterRef(electionId, command.voterRef())
                .orElseThrow(() -> new NotFoundException("选民不在本次选举名册中: " + command.voterRef()));

        // 核验所属选区：请求方声明的选区必须与名册一致
        if (command.precinctCode() != null
                && !command.precinctCode().equals(voter.getPrecinct().getCode())) {
            throw new UnprocessableException("选民所属选区与名册记录不符");
        }
        if (issuanceRepository.existsByElectionIdAndVoterId(electionId, voter.getId())) {
            throw new ConflictException("该选民在本次选举中已经取得选票，禁止重复签发");
        }

        IssuanceType type = resolveType(voter, command);
        BallotIssuance issuance = new BallotIssuance(
                election, voter, type, command.eventId(), command.pollingPlace());
        issuance = issuanceRepository.saveAndFlush(issuance);

        if (type == IssuanceType.PROVISIONAL) {
            // 临时票签发必须登记身份核验信息，与票面内容分表保存
            verificationRepository.save(new IdentityVerification(
                    issuance, command.verificationMethod(), command.verifierRef(), command.verificationNotes()));
        }

        auditService.record(election, "ISSUE_" + type.name(),
                issuance.getCredentialToken(),
                "voter=" + voter.getVoterRef() + ", precinct=" + voter.getPrecinct().getCode());
        return IssuanceResult.of(issuance, false);
    }

    private IssuanceType resolveType(Voter voter, IssueCommand command) {
        return switch (voter.getStatus()) {
            case INELIGIBLE -> throw new UnprocessableException("选民资格无效，禁止签发选票");
            case DISPUTED -> {
                // 资格存在争议时只能签发临时票
                if (command.verificationMethod() == null || command.verificationMethod().isBlank()
                        || command.verifierRef() == null || command.verifierRef().isBlank()) {
                    throw new UnprocessableException("签发临时票必须登记身份核验信息");
                }
                yield IssuanceType.PROVISIONAL;
            }
            case ELIGIBLE -> {
                IssuanceType requested = command.requestedType() == null
                        ? IssuanceType.OFFICIAL : command.requestedType();
                if (requested == IssuanceType.PROVISIONAL) {
                    throw new UnprocessableException("资格有效的选民不能签发临时票");
                }
                yield requested;
            }
        };
    }

    /** 签发命令。requestedType 仅允许 OFFICIAL / MAIL / null（默认 OFFICIAL）。 */
    public record IssueCommand(
            String voterRef,
            String eventId,
            IssuanceType requestedType,
            String precinctCode,
            String pollingPlace,
            String verificationMethod,
            String verifierRef,
            String verificationNotes) {
    }
}
