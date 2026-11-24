package com.example.questionnaire.mail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MailService and InviteService's helpers on their own, against the fake SMTP server. */
class MailServiceTest {

    private FakeSmtpServer smtp;
    @TempDir Path dir;

    @BeforeEach
    void start() throws Exception {
        smtp = new FakeSmtpServer();
    }

    @AfterEach
    void stop() throws Exception {
        smtp.close();
    }

    private MailProperties catcher() {
        MailProperties p = new MailProperties();
        p.setHost("127.0.0.1");
        p.setPort(smtp.port());
        p.setTls("none");
        p.setFrom("surveys@example.com");
        p.setPasswordFile(dir.resolve("password").toString());
        return p;
    }

    @Test
    void addresses() {
        assertThat(MailService.checkAddress(" sam@example.com ")).isEqualTo("sam@example.com");
        for (String bad : new String[] {"", "sam", "sam@localhost", "sam@[127.0.0.1]", "Sam <sam@example.com>",
                "a@example.com, b@example.com", "sam@example.com\r\nBcc: x@evil.com", "\"a b\"@example.com", "a..b@example.com"}) {
            assertThatThrownBy(() -> MailService.checkAddress(bad)).as(bad).isInstanceOf(MailException.class);
        }
    }

    @Test
    void headerValuesRefuseLineBreaks() {
        assertThatThrownBy(() -> MailService.checkHeaderValue("subject", "a\r\nBcc: x@evil.com")).isInstanceOf(MailException.class);
        assertThatThrownBy(() -> MailService.checkHeaderValue("subject", "a\u0000b")).isInstanceOf(MailException.class);
        MailService.checkHeaderValue("subject", "Survey: fine – ünïcode");
    }

    @Test
    void offWithoutHost() {
        MailProperties p = new MailProperties();
        assertThat(new MailService(p).isEnabled()).isFalse();
        assertThatThrownBy(() -> new MailService(p).send("sam@example.com", "s", "t"))
                .isInstanceOf(MailException.class)
                .extracting(e -> ((MailException) e).getKind()).isEqualTo(MailException.Kind.NOT_CONFIGURED);
    }

    @Test
    void relayWithoutAccountIsOn() {
        // Google Workspace's SMTP relay accepts the site by its address: no account, no password.
        MailProperties p = new MailProperties();
        p.setHost("smtp-relay.gmail.com");
        p.setFrom("chris@hitorro.com");
        assertThat(new MailService(p).unavailableReason()).isNull();
        assertThat(p.effectivePort()).isEqualTo(587);
    }

    @Test
    void accountWithoutPasswordIsOff() {
        MailProperties p = catcher();
        p.setTls("starttls");
        p.setUsername("lab@example.com");
        assertThat(new MailService(p).unavailableReason()).contains("password");
    }

    @Test
    void signInInTheClearIsRefused() throws Exception {
        MailProperties p = catcher();
        p.setUsername("lab@example.com");
        Files.writeString(dir.resolve("password"), "pw");
        assertThat(new MailService(p).unavailableReason()).contains("TLS");
    }

    @Test
    void startTlsIsRequired() {
        // The fake server offers no STARTTLS: the client must stop rather than send in the clear.
        MailProperties p = catcher();
        p.setTls("starttls");
        assertThatThrownBy(() -> new MailService(p).send("sam@example.com", "s", "t"))
                .isInstanceOf(MailException.class)
                .extracting(e -> ((MailException) e).getKind()).isEqualTo(MailException.Kind.FAILED);
        assertThat(smtp.messages()).isEmpty();
    }

    @Test
    void sendsPlainText() {
        new MailService(catcher()).send("sam@example.com", "Survey: T", "hello\nhttps://x/r/abc");
        FakeSmtpServer.Message m = smtp.messages().get(0);
        assertThat(m.from()).isEqualTo("surveys@example.com");
        assertThat(m.to()).containsExactly("sam@example.com");
        assertThat(m.authUser()).isNull();
        assertThat(m.data()).contains("Subject: Survey: T").contains("Auto-Submitted: auto-generated");
    }

    @Test
    void rateLimits() {
        MailProperties p = catcher();
        p.setRatePerHour(4);
        p.setRatePerRecipientPerHour(2);
        Instant[] now = {Instant.parse("2026-11-24T10:00:00Z")};
        Clock clock = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return now[0]; }
        };
        MailService m = new MailService(p, clock);
        m.send("a@example.com", "s", "t");
        m.send("A@example.com", "s", "t");
        assertThatThrownBy(() -> m.send("a@example.com", "s", "t"))
                .extracting(e -> ((MailException) e).getKind()).isEqualTo(MailException.Kind.RATE_LIMITED);
        m.send("b@example.com", "s", "t");
        m.send("c@example.com", "s", "t");
        assertThatThrownBy(() -> m.send("d@example.com", "s", "t"))
                .extracting(e -> ((MailException) e).getKind()).isEqualTo(MailException.Kind.RATE_LIMITED);
        now[0] = now[0].plusSeconds(3601);
        m.send("a@example.com", "s", "t");
        assertThat(smtp.messages()).hasSize(5);
    }

    @Test
    void splitAddresses() {
        assertThat(InviteService.splitAddresses(" a@example.com, b@example.com\nA@EXAMPLE.com;c@example.com  d@example.com\n\n"))
                .containsExactly("a@example.com", "b@example.com", "c@example.com", "d@example.com");
        assertThat(InviteService.splitAddresses(null)).isEmpty();
    }

    @Test
    void notesAndTitles() {
        assertThat(InviteService.cleanNote(" hi\r\nthere\u0007 ")).isEqualTo("hi\nthere");
        assertThatThrownBy(() -> InviteService.cleanNote("x".repeat(InviteService.MAX_NOTE + 1))).isInstanceOf(MailException.class);
        assertThat(InviteService.oneLine("A\r\nBcc: x@evil.com", 100)).isEqualTo("A Bcc: x@evil.com");
        assertThat(InviteService.absolute("/r/abc", "https://h")).isEqualTo("https://h/r/abc");
        assertThat(InviteService.absolute("https://s/r/abc", "https://h")).isEqualTo("https://s/r/abc");
    }
}
