package com.chris64233.electionroster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 有效投票结果登记：同一选民在一次选举中只保留一个有效结果。
 *
 * (election_id, voter_id) 唯一约束是跨渠道的原子仲裁点：
 * 补正确认与其他渠道投票登记在同一事务内插入本表，
 * 并发下只有一个事务能成功，其余收到冲突——保证只保留一个有效结果。
 */
@Entity
@Table(name = "effective_votes", uniqueConstraints =
        @UniqueConstraint(name = "uk_effective_vote_election_voter", columnNames = {"election_id", "voter_id"}))
public class EffectiveVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id")
    private Election election;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id")
    private Voter voter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EffectiveVoteSource source;

    /** 来源引用（补正记录号或外部渠道凭证号），仅作追踪，不含票面内容。 */
    @Column(nullable = false)
    private String refId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected EffectiveVote() {
    }

    public EffectiveVote(Election election, Voter voter, EffectiveVoteSource source, String refId) {
        this.election = election;
        this.voter = voter;
        this.source = source;
        this.refId = refId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Election getElection() {
        return election;
    }

    public Voter getVoter() {
        return voter;
    }

    public EffectiveVoteSource getSource() {
        return source;
    }

    public String getRefId() {
        return refId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
