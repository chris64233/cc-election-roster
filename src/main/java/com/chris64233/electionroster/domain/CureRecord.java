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
 * 身份材料补正记录（identity 侧）。
 *
 * <p>邮寄票或临时票因身份材料不全被暂存（选票不计入）时，针对<b>原签发</b>开启一条补正记录。
 * 补正的唯一作用是恢复原选票的有效性：确认时把原匿名选票置为 counted，
 * 任何情况下都不会重新签发第二张票。
 *
 * <p>本记录与选票内容（{@link BallotContent}）物理隔离：只保存身份侧信息，
 * 不持有、也不允许由其推断票面选择。
 */
@Entity
@Table(name = "cure_records", uniqueConstraints =
        @UniqueConstraint(name = "uk_cure_issuance", columnNames = {"issuance_id"}))
public class CureRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "issuance_id")
    private Issuance issuance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CureStatus status;

    /** 缺件说明（身份侧），不含任何票面选择。 */
    @Column(name = "missing_materials", nullable = false, length = 2000)
    private String missingMaterials;

    /** 补正截止时间（取提交暂存时选举配置的截止时间快照）。 */
    @Column(name = "cure_deadline", nullable = false)
    private Instant cureDeadline;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected CureRecord() {
    }

    public CureRecord(Issuance issuance, String missingMaterials, Instant cureDeadline) {
        this.issuance = issuance;
        this.missingMaterials = missingMaterials;
        this.cureDeadline = cureDeadline;
        this.status = CureStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /** 补正确认：恢复原选票有效（计入）。只能从 PENDING 迁移。 */
    public void confirm(String reason) {
        toTerminal(CureStatus.CONFIRMED, reason);
    }

    /** 补正被拒绝：原选票作废。 */
    public void reject(String reason) {
        toTerminal(CureStatus.REJECTED, reason);
    }

    /** 选民已通过其他渠道有效投票：本补正失效。 */
    public void supersede(String reason) {
        toTerminal(CureStatus.SUPERSEDED, reason);
    }

    /** 逾期未补正确认：原选票作废。 */
    public void expire(String reason) {
        toTerminal(CureStatus.EXPIRED, reason);
    }

    private void toTerminal(CureStatus target, String reason) {
        if (this.status != CureStatus.PENDING) {
            throw new IllegalStateException("补正已结束（" + this.status + "），不可重复处理");
        }
        this.status = target;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Issuance getIssuance() {
        return issuance;
    }

    public CureStatus getStatus() {
        return status;
    }

    public String getMissingMaterials() {
        return missingMaterials;
    }

    public Instant getCureDeadline() {
        return cureDeadline;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
