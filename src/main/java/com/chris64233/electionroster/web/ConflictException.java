package com.chris64233.electionroster.web;

/** 业务规则冲突（409）：重复签发、内容变化、状态不允许等。 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
