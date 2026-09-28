package com.chris64233.electionroster.service;

import com.chris64233.electionroster.domain.Adjudication;
import com.chris64233.electionroster.domain.CureStatus;
import com.chris64233.electionroster.domain.IssuanceType;
import com.chris64233.electionroster.repo.ElectionRepository;
import com.chris64233.electionroster.repo.IssuanceRepository;
import com.chris64233.electionroster.repo.ProvisionalRecordRepository;
import com.chris64233.electionroster.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 补正截止裁定：选举补正期限到期后，所有身份材料不全、仍未确认的邮寄票，
 * 以及仍处于 PENDING 的临时票，一律补正失败、原选票永久作废。
 * 逐张对签发记录加行锁，与补正确认、临时票裁定、其他渠道投票登记互斥——
 * 并发时谁先提交谁生效，绝无两个有效结果。
 */
@Service
public class DeadlineService {

    private final ElectionRepository electionRepository;
    private final IssuanceRepository issuanceRepository;
    private final ProvisionalRecordRepository provisionalRecordRepository;
    private final CureService cureService;

    public DeadlineService(ElectionRepository electionRepository,
                           IssuanceRepository issuanceRepository,
                           ProvisionalRecordRepository provisionalRecordRepository,
                           CureService cureService) {
        this.electionRepository = electionRepository;
        this.issuanceRepository = issuanceRepository;
        this.provisionalRecordRepository = provisionalRecordRepository;
        this.cureService = cureService;
    }

    /** 对某次选举执行截止裁定，返回本次裁定作废的选票数量。 */
    @Transactional
    public int ruleOverdueCures(Long electionId) {
        electionRepository.findById(electionId)
                .orElseThrow(() -> new NotFoundException("选举不存在: " + electionId));

        int ruled = 0;
        // 先只取 ID；每张票在 cureService 内首次读取即加行锁并重新检查状态。
        List<Long> mailIds = issuanceRepository.findIdsByElectionIdAndTypeAndCureStatus(
                electionId, IssuanceType.MAIL, CureStatus.PENDING);
        for (Long issuanceId : mailIds) {
            if (cureService.adjudicateDeadline(issuanceId)) {
                ruled++;
            }
        }
        List<Long> provisionalIds = provisionalRecordRepository
                .findIssuanceIdsByElectionIdAndAdjudication(electionId, Adjudication.PENDING);
        for (Long issuanceId : provisionalIds) {
            if (cureService.adjudicateDeadline(issuanceId)) {
                ruled++;
            }
        }
        return ruled;
    }
}
