package com.example.kintai.shared.domain.exception;

/**
 * ドメインルール違反例外
 *
 * <p>ドメインのビジネスルール（不変条件・事前条件）に違反した場合にスローする。
 * GlobalExceptionHandler で 409 Conflict にマッピングされる。</p>
 */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
