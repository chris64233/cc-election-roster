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

import java.time.Instant;

/**
 * 审计链条目。每条记录通过 prevHash/entryHash 与前一条目链接，
 * 只记录动作与引用标识，绝不记录票面选择内容。
 */
@Entity
@Table(name = "audit_entry")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", updatable = false)
    private Election election;

    @Column(nullable = false, updatable = false)
    private String action;

    /** 关联对象标识（如签发凭证令牌、临时票 ID），不含票面内容。 */
    @Column(name = "ref_token", updatable = false)
    private String refToken;

    @Column(updatable = false)
    private String detail;

    @Column(name = "prev_hash", nullable = false, updatable = false)
    private String prevHash;

    @Column(name = "entry_hash", nullable = false, updatable = false)
    private String entryHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEntry() {
    }

    public AuditEntry(Election election, String action, String refToken, String detail,
                      String prevHash, String entryHash) {
        this.election = election;
        this.action = action;
        this.refToken = refToken;
        this.detail = detail;
        this.prevHash = prevHash;
        this.entryHash = entryHash;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Election getElection() {
        return election;
    }

    public String getAction() {
        return action;
    }

    public String getRefToken() {
        return refToken;
    }

    public String getDetail() {
        return detail;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getEntryHash() {
        return entryHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
