package com.chris64233.electionroster.service;

import com.chris64233.electionroster.domain.BallotIssuance;
import com.chris64233.electionroster.domain.CastBallot;
import com.chris64233.electionroster.domain.IssuanceStatus;
import com.chris64233.electionroster.domain.IssuanceType;
import com.chris64233.electionroster.domain.ProvisionalBallot;
import com.chris64233.electionroster.repository.BallotIssuanceRepository;
import com.chris64233.electionroster.repository.CastBallotRepository;
import com.chris64233.electionroster.repository.ProvisionalBallotRepository;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 投票提交服务。
 *
 * <p>业务规则：
 * <ul>
 *   <li>每次提交必须消费一张有效的签发凭证，凭证只能被消费一次；</li>
 *   <li>重复提交相同内容 → 幂等返回原结果；内容发生变化 → 409 冲突；</li>
 *   <li>正式票/邮寄票提交后直接计入对应选区；临时票提交后进入待裁定状态，
 *       票面内容与身份核验信息分表保存，裁定前不计入选区汇总；</li>
 *   <li>并发提交由凭证乐观锁兜底，同一凭证最多一个提交事务成功。</li>
 * </ul>
 */
@Service
public class BallotSubmissionService {

    private final BallotIssuanceRepository issuanceRepository;
    private final CastBallotRepository castBallotRepository;
    private final ProvisionalBallotRepository provisionalBallotRepository;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public BallotSubmissionService(BallotIssuanceRepository issuanceRepository,
                                   CastBallotRepository castBallotRepository,
                                   ProvisionalBallotRepository provisionalBallotRepository,
                                   AuditService auditService,
                                   TransactionTemplate transactionTemplate) {
        this.issuanceRepository = issuanceRepository;
        this.castBallotRepository = castBallotRepository;
        this.provisionalBallotRepository = provisionalBallotRepository;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
    }

    public SubmissionResult submit(String credentialToken, String choicePayload) {
        if (choicePayload == null || choicePayload.isBlank()) {
            throw new UnprocessableException("票面内容不能为空");
        }
        String contentHash = Hashes.sha256(choicePayload);
        BallotIssuance issuance = issuanceRepository.findByCredentialToken(credentialToken)
                .orElseThrow(() -> new NotFoundException("签发凭证不存在"));

        if (issuance.getStatus() != IssuanceStatus.ISSUED) {
            return resolveRepeat(issuance, contentHash);
        }
        try {
            return transactionTemplate.execute(status -> doSubmit(issuance.getId(), choicePayload, contentHash));
        } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
            // 并发提交同一凭证：最多一个成功，其余按重放/冲突规则解析
            BallotIssuance current = issuanceRepository.findById(issuance.getId())
                    .orElseThrow(() -> new NotFoundException("签发凭证不存在"));
            return resolveRepeat(current, contentHash);
        }
    }

    private SubmissionResult doSubmit(Long issuanceId, String choicePayload, String contentHash) {
        BallotIssuance issuance = issuanceRepository.findById(issuanceId)
                .orElseThrow(() -> new NotFoundException("签发凭证不存在"));
        if (issuance.getStatus() != IssuanceStatus.ISSUED) {
            throw new ConflictException("签发凭证已被消费或作废");
        }
        issuance.consume(contentHash);

        if (issuance.getType() == IssuanceType.PROVISIONAL) {
            ProvisionalBallot provisional = provisionalBallotRepository.save(
                    new ProvisionalBallot(issuance, choicePayload, contentHash));
            auditService.record(issuance.getElection(), "SUBMIT_PROVISIONAL",
                    issuance.getCredentialToken(), "contentHash=" + contentHash);
            return SubmissionResult.provisional(provisional.getId(), contentHash, false);
        }

        CastBallot castBallot = castBallotRepository.save(new CastBallot(
                issuance.getElection(), issuance.getPrecinct(), issuance.getBallotStyle(),
                issuance.getType(), choicePayload, contentHash));
        auditService.record(issuance.getElection(), "SUBMIT_" + issuance.getType().name(),
                issuance.getCredentialToken(), "contentHash=" + contentHash);
        return SubmissionResult.counted(castBallot.getId(), contentHash, false);
    }

    /** 已消费/已作废凭证的重复提交：内容一致 → 返回原结果；内容变化 → 冲突。 */
    private SubmissionResult resolveRepeat(BallotIssuance issuance, String contentHash) {
        if (issuance.getStatus() == IssuanceStatus.VOIDED) {
            throw new ConflictException("签发凭证已作废，不能用于提交");
        }
        if (!contentHash.equals(issuance.getConsumedContentHash())) {
            throw new ConflictException("同一凭证提交了不同的票面内容");
        }
        if (issuance.getType() == IssuanceType.PROVISIONAL) {
            ProvisionalBallot provisional = provisionalBallotRepository.findByIssuanceId(issuance.getId())
                    .orElseThrow(() -> new NotFoundException("临时票记录不存在"));
            return SubmissionResult.provisional(provisional.getId(), contentHash, true);
        }
        return SubmissionResult.counted(null, contentHash, true);
    }
}
