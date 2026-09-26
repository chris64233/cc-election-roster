package com.chris64233.electionroster.service;

/**
 * 请求语义不合法，映射为 HTTP 422。包括：
 * 选民资格与签发类型不符（如对 ELIGIBLE 选民签临时票、对 INELIGIBLE 选民签发）、
 * 凭证状态不允许提交、选票样式与签发不符等。
 */
public class UnprocessableException extends RuntimeException {

    public UnprocessableException(String message) {
        super(message);
    }
}
