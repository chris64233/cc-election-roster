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
@Table(name = "precinct", uniqueConstraints =
        @UniqueConstraint(name = "uk_precinct_election_code", columnNames = {"election_id", "code"}))
public class Precinct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id")
    private Election election;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    protected Precinct() {
    }

    public Precinct(Election election, String code, String name) {
        this.election = election;
        this.code = code;
        this.name = name;
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

    public String getName() {
        return name;
    }
}
