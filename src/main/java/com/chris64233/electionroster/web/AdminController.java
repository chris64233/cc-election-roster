package com.chris64233.electionroster.web;

import com.chris64233.electionroster.api.RegisterVoterRequest;
import com.chris64233.electionroster.domain.BallotStyle;
import com.chris64233.electionroster.domain.District;
import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.domain.Voter;
import com.chris64233.electionroster.domain.VoterStatus;
import com.chris64233.electionroster.repo.DistrictRepository;
import com.chris64233.electionroster.repo.ElectionRepository;
import com.chris64233.electionroster.repo.VoterRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 名册管理端（演示/测试用）：登记选举、选区、选票样式与选民。
 * 按 code 幂等复用已存在的选举/选区/样式。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ElectionRepository electionRepository;
    private final DistrictRepository districtRepository;
    private final VoterRepository voterRepository;

    public AdminController(ElectionRepository electionRepository,
                           DistrictRepository districtRepository,
                           VoterRepository voterRepository) {
        this.electionRepository = electionRepository;
        this.districtRepository = districtRepository;
        this.voterRepository = voterRepository;
    }

    public record RegisteredVoter(Long electionId, Long districtId, Long voterId) {
    }

    @PostMapping("/voters")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredVoter registerVoter(@Valid @RequestBody RegisterVoterRequest request) {
        VoterStatus status;
        try {
            status = VoterStatus.valueOf(request.getVoterStatus());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("非法选民状态: " + request.getVoterStatus());
        }

        Election election = electionRepository.findAll().stream()
                .filter(e -> e.getName().equals(request.getElectionName()))
                .findFirst()
                .map(existing -> {
                    // 允许通过登记接口设置/更新补正截止时间（演示/测试用）。
                    if (request.getCureDeadline() != null
                            && !request.getCureDeadline().equals(existing.getCureDeadline())) {
                        existing.setCureDeadline(request.getCureDeadline());
                        return electionRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> electionRepository.save(
                        new Election(request.getElectionName(), request.getCureDeadline())));

        District district = districtRepository.findAll().stream()
                .filter(d -> d.getElection().getId().equals(election.getId())
                        && d.getCode().equals(request.getDistrictCode()))
                .findFirst()
                .orElseGet(() -> {
                    BallotStyle style = new BallotStyle(election, request.getBallotStyleCode());
                    return districtRepository.save(new District(election, request.getDistrictCode(), style));
                });

        Voter voter = voterRepository
                .findByElectionIdAndVoterRef(election.getId(), request.getVoterRef())
                .orElseGet(() -> voterRepository.save(
                        new Voter(election, request.getVoterRef(), district, status)));
        return new RegisteredVoter(election.getId(), district.getId(), voter.getId());
    }
}
