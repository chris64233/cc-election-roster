package com.chris64233.electionroster.domain;

import jakarta.persistence.CascadeType;
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

/** 选区：属于一次选举，并绑定一种选票样式。 */
@Entity
@Table(name = "districts", uniqueConstraints =
        @UniqueConstraint(name = "uk_district_election_code", columnNames = {"election_id", "code"}))
public class District {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id")
    private Election election;

    @Column(nullable = false)
    private String code;

    @ManyToOne(optional = false, fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "ballot_style_id")
    private BallotStyle ballotStyle;

    protected District() {
    }

    public District(Election election, String code, BallotStyle ballotStyle) {
        this.election = election;
        this.code = code;
        this.ballotStyle = ballotStyle;
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

    public BallotStyle getBallotStyle() {
        return ballotStyle;
    }
}
