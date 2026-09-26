package com.chris64233.electionroster.service;

import com.chris64233.electionroster.domain.AdjudicationStatus;
import com.chris64233.electionroster.domain.CastBallot;
import com.chris64233.electionroster.domain.BallotIssuance;
import com.chris64233.electionroster.domain.IssuanceType;
import com.chris64233.electionroster.domain.ProvisionalBallot;
import com.chris64233.electionroster.repository.CastBallotRepository;
import com.chris64233.electionroster.repository.ProvisionalBallotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 临时票裁定服务。
 *
 * <p>业务规则：
 * <ul>
 *   <li>只有 PENDING 状态的临时票可以裁定，裁定结果不可更改；</li>
 *   <li>裁定通过：票面内容计入对应选区（生成 CastBallot，不关联选民身份）；</li>
 *   <li>裁定拒绝：临时票永久作废，对应签发凭证一并作废，永不计入选区汇总。</li>
 * </ul>
 */
@Service
public class AdjudicationService {

    private final ProvisionalBallotRepository provisionalBallotRepository;
    private final CastBallotRepository castBallotRepository;
    private final AuditService auditService;

    public AdjudicationService(ProvisionalBallotRepository provisionalBallotRepository,
                               CastBallotRepository castBallotRepository,
                               AuditService auditService) {
        this.provisionalBallotRepository = provisionalBallotRepository;
        this.castBallotRepository = castBallotRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AdjudicationResult adjudicate(Long provisionalBallotId, boolean accept, String reason) {
        ProvisionalBallot ballot = provisionalBallotRepository.findById(provisionalBallotId)
                .orElseThrow(() -> new NotFoundException("临时票不存在: " + provisionalBallotId));
        if (ballot.getAdjudicationStatus() != AdjudicationStatus.PENDING) {
            throw new ConflictException("临时票已完成裁定，结果不可更改");
        }

        BallotIssuance issuance = ballot.getIssuance();
        Long castBallotId = null;
        if (accept) {
            ballot.adjudicate(AdjudicationStatus.ACCEPTED, reason);
            // 计入对应选区：只携带选区/样式/内容，不携带选民身份
            CastBallot castBallot = castBallotRepository.save(new CastBallot(
                    issuance.getElection(), issuance.getPrecinct(), issuance.getBallotStyle(),
                    IssuanceType.PROVISIONAL, ballot.getChoicePayload(), ballot.getContentHash()));
            castBallotId = castBallot.getId();
        } else {
            ballot.adjudicate(AdjudicationStatus.REJECTED, reason);
            // 永久作废：凭证一并作废，防止再次提交
            issuance.voidPermanently();
        }

        auditService.record(issuance.getElection(),
                accept ? "ADJUDICATE_ACCEPT" : "ADJUDICATE_REJECT",
                issuance.getCredentialToken(),
                "provisionalBallot=" + provisionalBallotId);
        return new AdjudicationResult(provisionalBallotId,
                ballot.getAdjudicationStatus().name(), castBallotId);
    }

    public record AdjudicationResult(Long provisionalBallotId, String status, Long castBallotId) {
    }
}
