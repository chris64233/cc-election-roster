package com.chris64233.electionroster.service;

import com.chris64233.electionroster.domain.AuditEvent;
import com.chris64233.electionroster.repo.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 追加式哈希链审计日志。
 * 每条事件 hash = sha256(prevHash | eventType | refId | detail)，detail 只允许摘要，
 * 严禁传入票面选择内容。append 使用独立事务并串行化，保证链的连续与可见。
 */
@Service
public class AuditService {

    private static final String GENESIS = "0".repeat(64);

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized void append(String eventType, String refId, String detail) {
        String prevHash = auditEventRepository.findTopByOrderByIdDesc()
                .map(AuditEvent::getEventHash)
                .orElse(GENESIS);
        String eventHash = sha256(prevHash + "|" + eventType + "|" + refId + "|" + detail);
        auditEventRepository.saveAndFlush(new AuditEvent(eventType, refId, detail, prevHash, eventHash));
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
