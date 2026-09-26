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
import jakarta.persistence.UniqueConstraint;

/** 选票样式：同一次选举可有多种票面（不同选区候选项不同）。 */
@Entity
@Table(name = "ballot_styles", uniqueConstraints =
        @UniqueConstraint(name = "uk_style_election_code", columnNames = {"election_id", "code"}))
public class BallotStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id")
    private Election election;

    @Column(nullable = false)
    private String code;

    protected BallotStyle() {
    }

    public BallotStyle(Election election, String code) {
        this.election = election;
        this.code = code;
    }

    public Long getId() {
        return id;
    }

    public Election getElection() {
        return election;
    }

    public String getCode() {
        return code;
    }
}
