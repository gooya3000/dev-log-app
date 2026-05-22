package com.example.devlogapp.service;

/**
 * 옛 passphrase 불일치 또는 DEK unwrap 실패 시 던지는 예외.
 * message 에 passphrase 원문 절대 포함하지 말 것 (PLAN.md §5.6).
 */
public class PassphraseMismatchException extends RuntimeException {

    public PassphraseMismatchException(String message) {
        super(message);
    }

    public PassphraseMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
