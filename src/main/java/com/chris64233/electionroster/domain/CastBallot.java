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

import java.time.Instant;

/**
 * 已计入的选票。为保持票面选择与身份信息隔离，本表不保存选民或签发凭证的外键；
 * 消费一次的唯一性由签发凭证的状态机与乐观锁保证。
 * 计入后不可修改、不可撤回（无更新/删除入口，核心字段 updatable = false）。
 */
@Entity
@Table(name = "cast_ballot")
public class CastBallot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", updatable = false)
    private Election election;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "precinct_id", updatable = false)
    private Precinct precinct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ballot_style_id", updatable = false)
    private BallotStyle ballotStyle;

    /** 来源类型：正式票、邮寄票或裁定通过的临时票。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false)
    private IssuanceType sourceType;

    @Column(name = "choice_payload", nullable = false, updatable = false)
    private String choicePayload;

    @Column(name = "content_hash", nullable = false, updatable = false)
    private String contentHash;

    @Column(name = "cast_at", nullable = false, updatable = false)
    private Instant castAt;

    protected CastBallot() {
    }

    public CastBallot(Election election, Precinct precinct, BallotStyle ballotStyle,
                      IssuanceType sourceType, String choicePayload, String contentHash) {
        this.election = election;
        this.precinct = precinct;
        this.ballotStyle = ballotStyle;
        this.sourceType = sourceType;
        this.choicePayload = choicePayload;
        this.contentHash = contentHash;
        this.castAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Election getElection() {
        return election;
    }

    public Precinct getPrecinct() {
        return precinct;
    }

    public BallotStyle getBallotStyle() {
        return ballotStyle;
    }

    public IssuanceType getSourceType() {
        return sourceType;
    }

    public String getChoicePayload() {
        return choicePayload;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getCastAt() {
        return castAt;
    }
}
