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
 * 签发凭证。创建后除状态单向流转外不可修改。
 *
 * 唯一性约束：
 * (election, voter) —— 正式签发、临时签发、邮寄登记共享，保证同一选民在一次选举中最多一张票；
 * (election, eventNo) —— 签发事件号，用于幂等；
 * (credentialToken) —— 凭证全局唯一。
 */
@Entity
@Table(name = "issuances", uniqueConstraints = {
        @UniqueConstraint(name = "uk_issuance_election_voter", columnNames = {"election_id", "voter_id"}),
        @UniqueConstraint(name = "uk_issuance_event", columnNames = {"election_id", "event_no"}),
        @UniqueConstraint(name = "uk_issuance_credential", columnNames = {"credential_token"})
})
public class Issuance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id")
    private Election election;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id")
    private Voter voter;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ballot_style_id")
    private BallotStyle ballotStyle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssuanceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssuanceStatus status;

    @Column(name = "event_no", nullable = false)
    private String eventNo;

    @Column(name = "credential_token", nullable = false)
    private String credentialToken;

    @Column(nullable = false)
    private String pollingPlace;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Issuance() {
    }

    public Issuance(Election election, Voter voter, District district, BallotStyle ballotStyle,
                    IssuanceType type, String eventNo, String credentialToken, String pollingPlace) {
        this.election = election;
        this.voter = voter;
        this.district = district;
        this.ballotStyle = ballotStyle;
        this.type = type;
        this.status = IssuanceStatus.ISSUED;
        this.eventNo = eventNo;
        this.credentialToken = credentialToken;
        this.pollingPlace = pollingPlace;
        this.createdAt = Instant.now();
    }

    public void markConsumed() {
        if (status != IssuanceStatus.ISSUED) {
            throw new IllegalStateException("凭证当前状态不允许消费: " + status);
        }
        this.status = IssuanceStatus.CONSUMED;
    }

    public void markVoided() {
        if (status != IssuanceStatus.ISSUED) {
            throw new IllegalStateException("凭证当前状态不允许作废: " + status);
        }
        this.status = IssuanceStatus.VOIDED;
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

    public District getDistrict() {
        return district;
    }

    public BallotStyle getBallotStyle() {
        return ballotStyle;
    }

    public IssuanceType getType() {
        return type;
    }

    public IssuanceStatus getStatus() {
        return status;
    }

    public String getEventNo() {
        return eventNo;
    }

    public String getCredentialToken() {
        return credentialToken;
    }

    public String getPollingPlace() {
        return pollingPlace;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
