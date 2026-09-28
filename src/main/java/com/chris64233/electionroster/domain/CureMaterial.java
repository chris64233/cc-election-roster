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
 * 选民为补正提交的身份材料（identity 侧）。
 *
 * <p>每次提交形成一个新版本（version 递增），全部关联到同一条 {@link CureRecord}，
 * 即始终关联原选票，绝不产生新签发。
 *
 * <p>这里只保存材料说明与材料内容的哈希（用于核验/重放比对），不保存可与票面选择关联的信息。
 */
@Entity
@Table(name = "cure_materials")
public class CureMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cure_record_id")
    private CureRecord cureRecord;

    /** 版本号，从 1 起递增；新版本只补充材料，不改变原选票关联。 */
    @Column(nullable = false)
    private int version;

    /** 材料说明（如“新址水电费账单”），身份侧信息，不含票面选择。 */
    @Column(nullable = false, length = 2000)
    private String materialNotes;

    /** 材料内容哈希，仅用于核验，无法据此推断投票内容。 */
    @Column(name = "material_hash", nullable = false)
    private String materialHash;

    @Column(nullable = false, updatable = false)
    private Instant submittedAt;

    protected CureMaterial() {
    }

    public CureMaterial(CureRecord cureRecord, int version, String materialNotes, String materialHash) {
        this.cureRecord = cureRecord;
        this.version = version;
        this.materialNotes = materialNotes;
        this.materialHash = materialHash;
        this.submittedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CureRecord getCureRecord() {
        return cureRecord;
    }

    public int getVersion() {
        return version;
    }

    public String getMaterialNotes() {
        return materialNotes;
    }

    public String getMaterialHash() {
        return materialHash;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
