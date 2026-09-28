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
import com.chris64233.electionroster.web.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 名册管理端（演示/测试用）：登记选举、选区、选票样式与选民，
 * 设置补正截止时间，以及在补正确认前模拟名册状态/选区修正。
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
                .orElseGet(() -> electionRepository.save(new Election(request.getElectionName())));

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

    /** 设置补正截止时间（ISO-8601）。 */
    @PostMapping("/elections/{electionId}/cure-deadline")
    public Map<String, Object> setCureDeadline(@PathVariable Long electionId,
                                               @RequestParam String deadline) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new NotFoundException("选举不存在: " + electionId));
        election.setCureDeadline(Instant.parse(deadline));
        return Map.of("electionId", electionId, "cureDeadline", election.getCureDeadline().toString());
    }

    /** 模拟名册修正：更新选民资格状态，可选修正选区（补正确认前重新核验用）。 */
    @PostMapping("/elections/{electionId}/voters/{voterRef}/roster")
    public Map<String, Object> updateRoster(@PathVariable Long electionId,
                                            @PathVariable String voterRef,
                                            @RequestParam VoterStatus status,
                                            @RequestParam(required = false) String districtCode) {
        Voter voter = voterRepository.findByElectionIdAndVoterRef(electionId, voterRef)
                .orElseThrow(() -> new NotFoundException("选民不在名册中: " + voterRef));
        voter.setStatus(status);
        if (districtCode != null) {
            District district = districtRepository
                    .findByElectionIdAndCode(electionId, districtCode)
                    .orElseThrow(() -> new NotFoundException("选区不存在: " + districtCode));
            voter.setDistrict(district);
        }
        return Map.of("voterRef", voterRef,
                "voterStatus", voter.getStatus().name(),
                "districtCode", voter.getDistrict().getCode());
    }
}
