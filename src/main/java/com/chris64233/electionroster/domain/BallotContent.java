package com.chris64233.electionroster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 选票内容。只含票面选择与所属选区，不保存任何选民/凭证身份引用，
 * 与身份核验信息（ProvisionalRecord）物理隔离。
 * 已计入（counted=true）的记录不得修改或撤回。
 */
@Entity
@Table(name = "ballot_contents")
public class BallotContent {

    /** 随机选票标识，与凭证/选民无任何对应关系。 */
    @Id
    private String ballotId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id")
    private Election election;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    @Column(nullable = false, length = 4000)
    private String choicesJson;

    @Column(nullable = false)
    private String contentHash;

    @Column(nullable = false)
    private boolean counted;

    /** 临时票裁定拒绝后置 true：永久作废，永不计入选区汇总。 */
    @Column(nullable = false)
    private boolean voided;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected BallotContent() {
    }

    public BallotContent(String ballotId, Election election, District district,
                         String choicesJson, String contentHash, boolean counted) {
        this.ballotId = ballotId;
        this.election = election;
        this.district = district;
        this.choicesJson = choicesJson;
        this.contentHash = contentHash;
        this.counted = counted;
        this.voided = false;
        this.createdAt = Instant.now();
    }

    /** 临时票裁定通过时计入；已计入的选票不允许再变更。 */
    public void markCounted() {
        if (this.counted) {
            throw new IllegalStateException("选票已计入，不可重复计入");
        }
        this.counted = true;
    }

    /** 临时票裁定拒绝时永久作废；已计入的选票不得作废。 */
    public void markVoided() {
        if (this.counted) {
            throw new IllegalStateException("已计入的选票不得作废");
        }
        this.voided = true;
    }

    public String getBallotId() {
        return ballotId;
    }

    public Election getElection() {
        return election;
    }

    public District getDistrict() {
        return district;
    }

    public String getChoicesJson() {
        return choicesJson;
    }

    public String getContentHash() {
        return contentHash;
    }

    public boolean isCounted() {
        return counted;
    }

    public boolean isVoided() {
        return voided;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
