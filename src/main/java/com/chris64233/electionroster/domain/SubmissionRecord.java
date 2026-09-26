package com.chris64233.electionroster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 提交消费记录：一个凭证只对应一条记录（唯一约束保证只消费一次）。
 * 重复提交相同内容时按 contentHash 命中并返回原回执。
 */
@Entity
@Table(name = "submission_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_submission_credential", columnNames = {"credential_token"}),
        @UniqueConstraint(name = "uk_submission_receipt", columnNames = {"receipt_id"})
})
public class SubmissionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credential_token", nullable = false)
    private String credentialToken;

    @Column(nullable = false)
    private String contentHash;

    /** 指向匿名选票内容，仅用于消费追踪，不反向暴露身份。 */
    @Column(nullable = false)
    private String ballotId;

    @Column(name = "receipt_id", nullable = false)
    private String receiptId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected SubmissionRecord() {
    }

    public SubmissionRecord(String credentialToken, String contentHash, String ballotId, String receiptId) {
        this.credentialToken = credentialToken;
        this.contentHash = contentHash;
        this.ballotId = ballotId;
        this.receiptId = receiptId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCredentialToken() {
        return credentialToken;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getBallotId() {
        return ballotId;
    }

    public String getReceiptId() {
        return receiptId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
