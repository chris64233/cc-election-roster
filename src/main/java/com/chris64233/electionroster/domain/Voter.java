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

@Entity
@Table(name = "voter", uniqueConstraints =
        @UniqueConstraint(name = "uk_voter_election_ref", columnNames = {"election_id", "voter_ref"}))
public class Voter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id")
    private Election election;

    /** 名册中的选民标识（对外引用，不直接暴露证件信息）。 */
    @Column(name = "voter_ref", nullable = false)
    private String voterRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "precinct_id")
    private Precinct precinct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ballot_style_id")
    private BallotStyle ballotStyle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoterStatus status;

    protected Voter() {
    }

    public Voter(Election election, String voterRef, Precinct precinct, BallotStyle ballotStyle, VoterStatus status) {
        this.election = election;
        this.voterRef = voterRef;
        this.precinct = precinct;
        this.ballotStyle = ballotStyle;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Election getElection() {
        return election;
    }

    public String getVoterRef() {
        return voterRef;
    }

    public Precinct getPrecinct() {
        return precinct;
    }

    public BallotStyle getBallotStyle() {
        return ballotStyle;
    }

    public VoterStatus getStatus() {
        return status;
    }
}
