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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 临时票票面内容。与身份核验信息（{@link IdentityVerification}）分表保存，
 * 二者仅各自关联签发凭证，互不直接引用。
 */
@Entity
@Table(name = "provisional_ballot", uniqueConstraints =
        @UniqueConstraint(name = "uk_prov_issuance", columnNames = "issuance_id"))
public class ProvisionalBallot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issuance_id", updatable = false)
    private BallotIssuance issuance;

    @Column(name = "choice_payload", nullable = false, updatable = false)
    private String choicePayload;

    @Column(name = "content_hash", nullable = false, updatable = false)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjudication_status", nullable = false)
    private AdjudicationStatus adjudicationStatus = AdjudicationStatus.PENDING;

    @Column(name = "adjudicated_at")
    private Instant adjudicatedAt;

    @Column(name = "adjudication_reason")
    private String adjudicationReason;

    protected ProvisionalBallot() {
    }

    public ProvisionalBallot(BallotIssuance issuance, String choicePayload, String contentHash) {
        this.issuance = issuance;
        this.choicePayload = choicePayload;
        this.contentHash = contentHash;
    }

    public void adjudicate(AdjudicationStatus decision, String reason) {
        if (this.adjudicationStatus != AdjudicationStatus.PENDING) {
            throw new IllegalStateException("provisional ballot already adjudicated");
        }
        if (decision == AdjudicationStatus.PENDING) {
            throw new IllegalArgumentException("decision must be ACCEPTED or REJECTED");
        }
        this.adjudicationStatus = decision;
        this.adjudicationReason = reason;
        this.adjudicatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public BallotIssuance getIssuance() {
        return issuance;
    }

    public String getChoicePayload() {
        return choicePayload;
    }

    public String getContentHash() {
        return contentHash;
    }

    public AdjudicationStatus getAdjudicationStatus() {
        return adjudicationStatus;
    }

    public Instant getAdjudicatedAt() {
        return adjudicatedAt;
    }

    public String getAdjudicationReason() {
        return adjudicationReason;
    }
}
