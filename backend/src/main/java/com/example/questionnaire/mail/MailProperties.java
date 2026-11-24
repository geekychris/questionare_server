package com.example.questionnaire.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Outgoing mail for emailing a campaign's share link (app.mail.*, or the environment:
 * QUESTIONNAIRE_MAIL_HOST, QUESTIONNAIRE_MAIL_PORT, ...).
 * <p>
 * The password is not a property: it is read from {@link #passwordFile} for each send (a
 * Kubernetes Secret mounted as a file), so it never sits in the environment, the configuration
 * or a log. With no host, or an account but no password, mail is off and the UI hides the button.
 * A relay that accepts the site by its address (Google Workspace's SMTP relay) needs no account.
 */
@Component
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    /** SMTP server, e.g. smtp-relay.gmail.com. Empty: mail is off. */
    private String host = "";
    /** 587 for STARTTLS, 465 for TLS. 0: from the TLS mode. */
    private int port = 0;
    /** starttls (default), tls, or none (a test catcher such as Mailpit; no sign-in then). */
    private String tls = "starttls";
    /** Account to sign in as; empty: no sign-in. */
    private String username = "";
    /** Sender address; empty: the account. */
    private String from = "";
    /** Display name shown with From. */
    private String fromName = "Surveys";
    /** Where replies go, optional. */
    private String replyTo = "";
    /** The file holding the password (only read when an account is set). */
    private String passwordFile = "/var/run/questionnaire/smtp/password";
    /** Emails in an hour, in total and to one address. */
    private int ratePerHour = 50;
    private int ratePerRecipientPerHour = 3;
    /** Addresses one invite request may name. */
    private int maxRecipients = 50;
    /** Connection and read timeout, milliseconds. */
    private int timeoutMillis = 20000;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = trim(host); }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getTls() { return tls; }
    public void setTls(String tls) { this.tls = trim(tls).toLowerCase(Locale.ROOT); }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = trim(username); }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = trim(from); }
    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = trim(fromName); }
    public String getReplyTo() { return replyTo; }
    public void setReplyTo(String replyTo) { this.replyTo = trim(replyTo); }
    public String getPasswordFile() { return passwordFile; }
    public void setPasswordFile(String passwordFile) { this.passwordFile = trim(passwordFile); }
    public int getRatePerHour() { return ratePerHour; }
    public void setRatePerHour(int ratePerHour) { this.ratePerHour = ratePerHour; }
    public int getRatePerRecipientPerHour() { return ratePerRecipientPerHour; }
    public void setRatePerRecipientPerHour(int v) { this.ratePerRecipientPerHour = v; }
    public int getMaxRecipients() { return maxRecipients; }
    public void setMaxRecipients(int maxRecipients) { this.maxRecipients = maxRecipients; }
    public int getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(int timeoutMillis) { this.timeoutMillis = timeoutMillis; }

    /** The port, filled from the TLS mode when unset. */
    public int effectivePort() {
        if (port > 0) return port;
        return switch (tls) {
            case "tls" -> 465;
            case "none" -> 25;
            default -> 587;
        };
    }

    /** The sender address: From, or else the account. */
    public String effectiveFrom() {
        return from.isEmpty() ? username : from;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
