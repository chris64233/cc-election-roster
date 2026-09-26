package com.chris64233.electionroster.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 哈希工具，用于提交内容指纹与审计链。 */
final class Hashes {

    /** 审计链的创世前序哈希。 */
    static final String GENESIS = "0".repeat(64);

    private Hashes() {
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * 审计条目哈希：prevHash | action | refToken | detail。
     * 输入中不包含任何票面选择内容。
     */
    static String auditEntryHash(String prevHash, String action, String refToken, String detail) {
        String canonical = String.join("|",
                prevHash,
                nullToEmpty(action),
                nullToEmpty(refToken),
                nullToEmpty(detail));
        return sha256(canonical);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
