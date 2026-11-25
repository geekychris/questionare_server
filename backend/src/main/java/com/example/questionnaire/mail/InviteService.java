package com.example.questionnaire.mail;

import com.example.questionnaire.dto.CampaignDTO;
import com.example.questionnaire.security.CurrentUser;
import com.example.questionnaire.service.CampaignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Emails a campaign's share link ({@code /r/<token>}) to a list of addresses, one message each.
 * <p>
 * Only someone who may manage the campaign (an admin, or the creator who owns it) can send;
 * anyone else gets the same 404 as for any other campaign call. A closed campaign is refused.
 * The message is fixed text: who invites, the survey's title, the optional note, the link and
 * when the survey closes. Logs name the campaign and the recipient, never the link.
 */
@Service
public class InviteService {

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);

    public static final int MAX_NOTE = 1000;
    private static final DateTimeFormatter CLOSES = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH);

    private final MailService mail;
    private final CampaignService campaigns;

    public InviteService(MailService mail, CampaignService campaigns) {
        this.mail = mail;
        this.campaigns = campaigns;
    }

    /** The outcome for one address: sent, or why not (safe to show). */
    public record Result(String to, boolean sent, String error) { }

    /**
     * Splits pasted addresses (commas, semicolons, spaces or new lines), drops duplicates
     * (ignoring case) and keeps the order. Each piece is checked later, one by one.
     */
    public static List<String> splitAddresses(String raw) {
        Map<String, String> seen = new LinkedHashMap<>();
        if (raw == null) return List.of();
        for (String part : raw.split("[,;\\s]+")) {
            String a = part.trim();
            if (!a.isEmpty()) seen.putIfAbsent(a.toLowerCase(Locale.ROOT), a);
        }
        return new ArrayList<>(seen.values());
    }

    /**
     * Sends the invite to each address.
     *
     * @param campaignId the campaign
     * @param addresses  raw addresses (as typed)
     * @param note       optional personal note (plain text, at most {@link #MAX_NOTE} characters)
     * @param user       who sends (must be allowed to manage the campaign)
     * @param origin     scheme://host for a share link that is still relative (no APP_PUBLIC_URL)
     */
    public List<Result> invite(Long campaignId, List<String> addresses, String note, CurrentUser user, String origin) {
        return invite(campaignId, addresses, note, user, origin, null);
    }

    /** As above; {@code inviterName}, when set, replaces the sender's name in the message. */
    public List<Result> invite(Long campaignId, List<String> addresses, String note, CurrentUser user, String origin,
                               String inviterName) {
        String reason = mail.unavailableReason();
        if (reason != null) {
            throw new MailException(MailException.Kind.NOT_CONFIGURED, "email isn't configured: " + reason);
        }
        // Access check first (404 for a campaign the caller may not manage).
        CampaignDTO c = campaigns.getCampaign(campaignId, user);
        if (addresses == null || addresses.isEmpty()) {
            throw new MailException(MailException.Kind.INVALID, "add at least one email address");
        }
        int max = mail.properties().getMaxRecipients();
        if (max > 0 && addresses.size() > max) {
            throw new MailException(MailException.Kind.INVALID, "at most " + max + " addresses at a time");
        }
        String cleanNote = cleanNote(note);
        if (!c.isAcceptingResponses()) {
            throw new MailException(MailException.Kind.INVALID, "this survey isn't accepting responses; reopen it first");
        }
        String link = absolute(c.getShareUrl(), origin);
        if (link == null || !link.startsWith("http")) {
            throw new MailException(MailException.Kind.NOT_CONFIGURED, "the survey's public address isn't known");
        }
        String subject = "Survey: " + oneLine(c.getTitle(), 150);
        String text = body(inviterName != null ? inviterName : user.name(), c, cleanNote, link);

        List<Result> out = new ArrayList<>();
        for (String a : addresses) {
            try {
                String to = MailService.checkAddress(a);
                mail.send(to, subject, text);
                out.add(new Result(to, true, null));
            } catch (MailException e) {
                out.add(new Result(a, false, e.getMessage()));
            }
        }
        long ok = out.stream().filter(Result::sent).count();
        log.info("campaign {} invites by {}: {} sent, {} not sent", campaignId, user.name(), ok, out.size() - ok);
        return out;
    }

    static String cleanNote(String note) {
        if (note == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : note.replace("\r\n", "\n").toCharArray()) {
            b.append(ch == '\n' || ch == '\t' || (ch >= 0x20 && ch != 0x7f) ? ch : ' ');
        }
        String n = b.toString().strip();
        if (n.length() > MAX_NOTE) {
            throw new MailException(MailException.Kind.INVALID, "the note is longer than " + MAX_NOTE + " characters");
        }
        return n;
    }

    /** A title or name made safe for a header: control characters become spaces, then trimmed. */
    static String oneLine(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("[\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;
    }

    static String absolute(String shareUrl, String origin) {
        if (shareUrl == null) return null;
        if (shareUrl.startsWith("/") && origin != null) return origin + shareUrl;
        return shareUrl;
    }

    private static String body(String inviter, CampaignDTO c, String note, String link) {
        StringBuilder b = new StringBuilder();
        b.append(oneLine(inviter, 80)).append(" invited you to answer a survey: ")
                .append(oneLine(c.getTitle(), 200)).append(".\n\n");
        if (!note.isEmpty()) {
            b.append(note).append("\n\n");
        }
        b.append("Open the survey:\n").append(link).append("\n\n");
        if (c.getEndDate() != null) {
            b.append("It closes on ").append(CLOSES.format(c.getEndDate())).append(".\n");
        }
        if (c.isRequireLogin()) {
            b.append("You'll be asked to sign in before answering.\n");
        }
        b.append("This link is for answering this survey only. If you weren't expecting it, you can ignore this email.\n");
        return b.toString();
    }
}
