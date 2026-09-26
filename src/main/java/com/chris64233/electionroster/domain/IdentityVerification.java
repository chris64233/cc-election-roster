package com.chris64233.electionroster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 临时票签发时的身份核验记录。与票面内容（{@link ProvisionalBallot}）分表保存。
 */
@Entity
@Table(name = "identity_verification", uniqueConstraints =
        @UniqueConstraint(name = "uk_verification_issuance", columnNames = "issuance_id"))
public class IdentityVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issuance_id", updatable = false)
    private BallotIssuance issuance;

    @Column(nullable = false, updatable = false)
    private String method;

    @Column(name = "verifier_ref", nullable = false, updatable = false)
    private String verifierRef;

    @Column(updatable = false)
    private String notes;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;

    protected IdentityVerification() {
    }

    public IdentityVerification(BallotIssuance issuance, String method, String verifierRef, String notes) {
        this.issuance = issuance;
        this.method = method;
        this.verifierRef = verifierRef;
        this.notes = notes;
        this.verifiedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public BallotIssuance getIssuance() {
        return issuance;
    }

    public String getMethod() {
        return method;
    }

    public String getVerifierRef() {
        return verifierRef;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
