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

    /** 补正截止时间：身份材料不全被暂存的选票，须在此之前完成补正确认才可恢复计入。 */
    @Column(name = "cure_deadline")
    private Instant cureDeadline;

    protected Election() {
    }

    public Election(String name) {
        this.name = name;
    }

    public Election(String name, Instant cureDeadline) {
        this.name = name;
        this.cureDeadline = cureDeadline;
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
