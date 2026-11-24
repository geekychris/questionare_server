package com.example.questionnaire.mail;

/** A request that can't be sent; the message is safe to show to the person sending. */
public class MailException extends RuntimeException {

    public enum Kind { INVALID, NOT_CONFIGURED, RATE_LIMITED, FAILED }

    private final Kind kind;

    public MailException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
