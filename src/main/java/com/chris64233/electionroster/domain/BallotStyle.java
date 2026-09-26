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

@Entity
@Table(name = "ballot_style", uniqueConstraints =
        @UniqueConstraint(name = "uk_style_election_code", columnNames = {"election_id", "code"}))
public class BallotStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id")
    private Election election;

    @Column(nullable = false)
    private String code;

    private String description;

    protected BallotStyle() {
    }

    public BallotStyle(Election election, String code, String description) {
        this.election = election;
        this.code = code;
        this.description = description;
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

    public String getDescription() {
        return description;
    }
}
