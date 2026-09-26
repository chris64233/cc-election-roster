package com.chris64233.electionroster;

import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.domain.VoterStatus;
import com.chris64233.electionroster.service.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

/** 测试基类：每个用例创建独立选举，避免数据相互干扰。 */
@SpringBootTest
public abstract class ElectionTestSupport {

    @Autowired
    protected RegistrationService registrationService;

    protected Election newElection() {
        return registrationService.createElection("E-" + UUID.randomUUID());
    }

    protected void setupBallotStructure(Long electionId) {
        registrationService.createPrecinct(electionId, "P1", "第一选区");
        registrationService.createPrecinct(electionId, "P2", "第二选区");
        registrationService.createBallotStyle(electionId, "S1", "样式一");
        registrationService.createBallotStyle(electionId, "S2", "样式二");
    }

    protected void registerVoter(Long electionId, String voterRef, VoterStatus status) {
        registrationService.registerVoter(electionId, voterRef, "P1", "S1", status);
    }
}
