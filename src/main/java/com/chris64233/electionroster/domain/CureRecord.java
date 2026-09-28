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
 * 补正材料记录：选民在截止前提交的身份材料新版本。
 * 通过 (issuance, version) 关联原选票对应的签发记录——补正只恢复原选票的有效性，
 * 绝不重新签发第二张票。
 *
 * 与 ProvisionalRecord 一样只保存身份侧信息（材料说明），
 * 与票面选择（BallotContent）物理隔离，二者之间没有直接外键；
 * 审计与查询接口不得由此推断或暴露投票内容。
 */
@Entity
@Table(name = "cure_records", uniqueConstraints =
        @UniqueConstraint(name = "uk_cure_issuance_version", columnNames = {"issuance_id", "version"}))
public class CureRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 原选票的签发记录；补正不创建新的签发。 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "issuance_id")
    private Issuance issuance;

    /** 材料版本号，同一签发记录内递增。 */
    @Column(nullable = false)
    private int version;

    /** 身份材料说明，不含任何票面选择。 */
    @Column(nullable = false, length = 2000)
    private String materialNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CureRecordStatus status;

    @Column(length = 1000)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant decidedAt;

    protected CureRecord() {
    }

    public CureRecord(Issuance issuance, int version, String materialNotes) {
        this.issuance = issuance;
        this.version = version;
        this.materialNotes = materialNotes;
        this.status = CureRecordStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void confirm() {
        if (this.status != CureRecordStatus.PENDING) {
            throw new IllegalStateException("补正记录已处理，不可重复确认: " + this.status);
        }
        this.status = CureRecordStatus.CONFIRMED;
        this.decidedAt = Instant.now();
    }

    public void fail(String reason) {
        if (this.status != CureRecordStatus.PENDING) {
            throw new IllegalStateException("补正记录已处理，不可重复裁定: " + this.status);
        }
        this.status = CureRecordStatus.FAILED;
        this.failureReason = reason;
        this.decidedAt = Instant.now();
    }

    /** 提交更新版本的材料时，旧的待确认版本被替代。 */
    public void supersede() {
        if (this.status != CureRecordStatus.PENDING) {
            throw new IllegalStateException("只有待确认的补正记录可以被替代: " + this.status);
        }
        this.status = CureRecordStatus.SUPERSEDED;
        this.decidedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Issuance getIssuance() {
        return issuance;
    }

    public int getVersion() {
        return version;
    }

    public String getMaterialNotes() {
        return materialNotes;
    }

    public CureRecordStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
