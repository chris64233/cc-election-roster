package com.chris64233.electionroster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 审计链事件：追加式、哈希链（prevHash -> eventHash）。
 * detail 只允许出现摘要（如 contentHash），绝不包含票面选择内容。
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventType;

    /** 业务对象标识（签发号、回执号等），不含票面内容。 */
    @Column(nullable = false)
    private String refId;

    @Column(nullable = false, length = 1000)
    private String detail;

    @Column(nullable = false)
    private String prevHash;

    @Column(nullable = false)
    private String eventHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(String eventType, String refId, String detail, String prevHash, String eventHash) {
        this.eventType = eventType;
        this.refId = refId;
        this.detail = detail;
        this.prevHash = prevHash;
        this.eventHash = eventHash;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getRefId() {
        return refId;
    }

    public String getDetail() {
        return detail;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getEventHash() {
        return eventHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
