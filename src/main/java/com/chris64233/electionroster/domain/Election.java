package com.chris64233.electionroster.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "elections")
public class Election {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 补正材料提交/确认截止时间；为 null 表示本次选举未设置补正期限。 */
    private Instant cureDeadline;

    protected Election() {
    }

    public Election(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCureDeadline() {
        return cureDeadline;
    }

    public void setCureDeadline(Instant cureDeadline) {
        this.cureDeadline = cureDeadline;
    }
}
