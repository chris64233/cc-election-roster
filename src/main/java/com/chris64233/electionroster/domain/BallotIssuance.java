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
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * 不可修改的签发凭证。
 *
 * <p>唯一性约束：
 * <ul>
 *   <li>(election_id, voter_id)：同一选民在一次选举中最多一张签发，
 *       正式签发、临时签发、邮寄登记共享该约束；</li>
 *   <li>event_id：签发事件号，保证签发请求幂等。</li>
 * </ul>
 * 核心字段 {@code updatable = false}，签发后不可修改。
 */
@Entity
@Table(name = "ballot_issuance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_issuance_election_voter", columnNames = {"election_id", "voter_id"}),
        @UniqueConstraint(name = "uk_issuance_event", columnNames = {"event_id"})
})
public class BallotIssuance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 提交选票时出示的凭证令牌。 */
    @Column(name = "credential_token", nullable = false, unique = true, updatable = false)
    private String credentialToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", updatable = false)
    private Election election;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voter_id", updatable = false)
    private Voter voter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "precinct_id", updatable = false)
    private Precinct precinct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ballot_style_id", updatable = false)
    private BallotStyle ballotStyle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private IssuanceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssuanceStatus status;

    /** 签发事件号（客户端幂等键）。 */
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "polling_place", updatable = false)
    private String pollingPlace;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 消费时提交内容的哈希，用于重复提交的幂等判定与冲突检测。 */
    @Column(name = "consumed_content_hash")
    private String consumedContentHash;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    /** 乐观锁：并发提交同一凭证时最多一个事务成功。 */
    @Version
    private long version;

    protected BallotIssuance() {
    }

    public BallotIssuance(Election election, Voter voter, IssuanceType type, String eventId, String pollingPlace) {
        this.credentialToken = UUID.randomUUID().toString();
        this.election = election;
        this.voter = voter;
        this.precinct = voter.getPrecinct();
        this.ballotStyle = voter.getBallotStyle();
        this.type = type;
        this.status = IssuanceStatus.ISSUED;
        this.eventId = eventId;
        this.pollingPlace = pollingPlace;
        this.createdAt = Instant.now();
    }

    public void consume(String contentHash) {
        if (this.status != IssuanceStatus.ISSUED) {
            throw new IllegalStateException("issuance is not in ISSUED status");
        }
        this.status = IssuanceStatus.CONSUMED;
        this.consumedContentHash = contentHash;
        this.consumedAt = Instant.now();
    }

    public void voidPermanently() {
        this.status = IssuanceStatus.VOIDED;
    }

    public Long getId() {
        return id;
    }

    public String getCredentialToken() {
        return credentialToken;
    }

    public Election getElection() {
        return election;
    }

    public Voter getVoter() {
        return voter;
    }

    public Precinct getPrecinct() {
        return precinct;
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

    public String getEventId() {
        return eventId;
    }

    public String getPollingPlace() {
        return pollingPlace;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getConsumedContentHash() {
        return consumedContentHash;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
