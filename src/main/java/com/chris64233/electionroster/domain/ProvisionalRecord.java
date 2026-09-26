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

import java.time.Instant;

/**
 * 临时票身份核验记录。只保存身份侧信息（核验备注、裁定），
 * 与选票内容（BallotContent）分离保存，二者之间没有直接外键。
 */
@Entity
@Table(name = "provisional_records")
public class ProvisionalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "issuance_id", unique = true)
    private Issuance issuance;

    /** 身份核验信息（争议原因、核验材料说明等），不含任何票面选择。 */
    @Column(nullable = false, length = 2000)
    private String identityNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Adjudication adjudication;

    @Column(length = 1000)
    private String decisionReason;

    private Instant decidedAt;

    protected ProvisionalRecord() {
    }

    public ProvisionalRecord(Issuance issuance, String identityNotes) {
        this.issuance = issuance;
        this.identityNotes = identityNotes;
        this.adjudication = Adjudication.PENDING;
    }

    public void decide(Adjudication decision, String reason) {
        if (this.adjudication != Adjudication.PENDING) {
            throw new IllegalStateException("临时票已裁定，不可重复裁定");
        }
        if (decision == Adjudication.PENDING) {
            throw new IllegalArgumentException("裁定结果必须为 ACCEPTED 或 REJECTED");
        }
        this.adjudication = decision;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Issuance getIssuance() {
        return issuance;
    }

    public String getIdentityNotes() {
        return identityNotes;
    }

    public Adjudication getAdjudication() {
        return adjudication;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
