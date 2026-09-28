package com.chris64233.electionroster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 跨渠道有效投票记录：登记某选民已通过<b>其他渠道</b>（如另一投票点/外部计票系统）有效投票。
 *
 * <p>对 {@code (election_id, voter_id)} 的唯一约束保证一名选民最多只有一条跨渠道有效结果，
 * 它与补正确认、截止裁定竞争同一个“唯一有效结果”，跨渠道凭证消费在同一事务内原子完成。
 *
 * <p>本表只记录“已在别处投票”的事实与渠道，不含票面选择。
 */
@Entity
@Table(name = "external_vote_records", uniqueConstraints =
        @UniqueConstraint(name = "uk_external_vote_election_voter",
                columnNames = {"election_id", "voter_id"}))
public class ExternalVoteRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id")
    private Election election;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id")
    private Voter voter;

    /** 其他投票渠道标识。 */
    @Column(nullable = false)
    private String channel;

    /** 外部凭证号，用于跨渠道凭证消费追踪，不与票面内容关联。 */
    @Column(name = "external_ref", nullable = false)
    private String externalRef;

    @Column(nullable = false, updatable = false)
    private Instant votedAt;

    protected ExternalVoteRecord() {
    }

    public ExternalVoteRecord(Election election, Voter voter, String channel, String externalRef) {
        this.election = election;
        this.voter = voter;
        this.channel = channel;
        this.externalRef = externalRef;
        this.votedAt = Instant.now();
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

    public String getChannel() {
        return channel;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public Instant getVotedAt() {
        return votedAt;
    }
}
