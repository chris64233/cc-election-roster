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

/** 名册中的选民：登记在某次选举的某个选区。 */
@Entity
@Table(name = "voters", uniqueConstraints =
        @UniqueConstraint(name = "uk_voter_election_ref", columnNames = {"election_id", "voter_ref"}))
public class Voter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id")
    private Election election;

    @Column(name = "voter_ref", nullable = false)
    private String voterRef;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoterStatus status;

    protected Voter() {
    }

    public Voter(Election election, String voterRef, District district, VoterStatus status) {
        this.election = election;
        this.voterRef = voterRef;
        this.district = district;
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

    public District getDistrict() {
        return district;
    }

    public VoterStatus getStatus() {
        return status;
    }

    public void setStatus(VoterStatus status) {
        this.status = status;
    }

    /** 名册修正选民所属选区（如补正确认前重新核验发现选区变化）。 */
    public void setDistrict(District district) {
        this.district = district;
    }
}
