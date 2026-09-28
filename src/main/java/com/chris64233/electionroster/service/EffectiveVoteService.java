package com.chris64233.electionroster.service;

import com.chris64233.electionroster.domain.EffectiveVote;
import com.chris64233.electionroster.domain.EffectiveVoteSource;
import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.repo.EffectiveVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 有效投票结果槽位的原子仲裁。
 *
 * 同一选民在一次选举中只保留一个有效结果：补正确认、临时票裁定通过、
 * 常规提交计入与其他渠道投票登记都必须先抢到这里的唯一槽位
 * （uk_effective_vote_election_voter）。
 *
 * 调用方在进入本方法前已对同一签发记录加行级写锁串行化，因此先查后插是确定性的；
 * 本方法参与调用方事务（同提交同回滚），再以数据库唯一约束作为最终兜底：
 * 任何跨渠道并发下最多一个事务能登记成功，冲突事务整体回滚。
 */
@Service
public class EffectiveVoteService {

    /** 抢槽结果：claimed=true 表示本渠道登记成功；否则返回已占用槽位的来源与引用。 */
    public record ClaimOutcome(boolean claimed, EffectiveVoteSource existingSource, String existingRef) {
    }

    private final EffectiveVoteRepository effectiveVoteRepository;

    public EffectiveVoteService(EffectiveVoteRepository effectiveVoteRepository) {
        this.effectiveVoteRepository = effectiveVoteRepository;
    }

    @Transactional
    public ClaimOutcome tryClaim(Election election, Voter voter, EffectiveVoteSource source, String refId) {
        Optional<EffectiveVote> existing =
                effectiveVoteRepository.findByElectionIdAndVoterId(election.getId(), voter.getId());
        if (existing.isPresent()) {
            EffectiveVote winner = existing.get();
            return new ClaimOutcome(false, winner.getSource(), winner.getRefId());
        }
        effectiveVoteRepository.saveAndFlush(new EffectiveVote(election, voter, source, refId));
        return new ClaimOutcome(true, null, null);
    }

    @Transactional(readOnly = true)
    public Optional<EffectiveVote> find(Long electionId, Long voterId) {
        return effectiveVoteRepository.findByElectionIdAndVoterId(electionId, voterId);
    }
}
