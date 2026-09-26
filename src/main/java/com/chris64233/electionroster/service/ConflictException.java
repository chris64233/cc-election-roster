package com.chris64233.electionroster.service;

/**
 * 业务冲突，映射为 HTTP 409。包括：
 * 选民已有签发（并发抢签发）、提交内容与原提交不一致、
 * 凭证已作废、临时票已裁定等。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
