package com.chris64233.electionroster.service;

import com.chris64233.electionroster.domain.AuditEntry;
import com.chris64233.electionroster.domain.Election;
import com.chris64233.electionroster.repository.AuditEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计链服务。每条审计记录通过 prevHash/entryHash 与前序记录链接，
 * 形成仅追加、可校验的哈希链。记录内容只包含动作与引用标识，
 * 绝不写入票面选择内容。
 */
@Service
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;

    public AuditService(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    /**
     * 追加一条审计记录。参与调用方事务，与业务写入同生共死。
     * synchronized 保证单节点内链式追加串行化。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public synchronized AuditEntry record(Election election, String action, String refToken, String detail) {
        String prevHash = auditEntryRepository.findTopByElectionIdOrderByIdDesc(election.getId())
                .map(AuditEntry::getEntryHash)
                .orElse(Hashes.GENESIS);
        String entryHash = Hashes.auditEntryHash(prevHash, action, refToken, detail);
        return auditEntryRepository.save(new AuditEntry(election, action, refToken, detail, prevHash, entryHash));
    }
}
