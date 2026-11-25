package com.example.questionnaire.controller;

import com.example.questionnaire.lists.RecipientListService;
import com.example.questionnaire.mail.InviteService;
import com.example.questionnaire.mail.MailException;
import com.example.questionnaire.mail.MailService;
import com.example.questionnaire.security.ApiTokenFilter;
import com.example.questionnaire.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Emailing a campaign's share link (roles admin/creator, like the rest of /api; a creator only
 * for their own campaigns). Needs the CSRF token like every other state-changing call.
 */
@RestController
public class MailController {

    private final MailService mail;
    private final InviteService invites;
    private final RecipientListService lists;

    public MailController(MailService mail, InviteService invites, RecipientListService lists) {
        this.mail = mail;
        this.invites = invites;
        this.lists = lists;
    }

    /** {@code {enabled}}: whether invites can be emailed (the UI hides the button otherwise). */
    @GetMapping("/api/mail/status")
    public Map<String, Object> status() {
        return Map.of("enabled", mail.isEnabled());
    }

    /**
     * Request: {@code to} (a list, or one string of addresses separated by commas or new lines),
     * {@code listIds} (recipient lists whose addresses are added; duplicates are sent once), an
     * optional {@code note}, and {@code from}: the inviter's name shown in the email, honoured only
     * for API-token callers (people are always named as themselves).
     */
    public record InviteRequest(Object to, String note, List<Long> listIds, String from) { }

    /**
     * Sends the share link to each address. Answers {@code {results:[{to, sent, error}], sent, notSent}};
     * 400 for no or too many addresses, a closed survey or a bad note, 404 for a campaign the caller
     * can't manage, 503 when mail isn't configured. A per-address problem (invalid, rate-limited,
     * refused by the server) is in that address's result.
     */
    @PostMapping("/api/campaigns/{id}/invites")
    public Map<String, Object> invite(@PathVariable Long id, @RequestBody InviteRequest req,
                                      Authentication auth, HttpServletRequest http) {
        List<String> to;
        Object raw = req.to();
        if (raw == null) {
            to = List.of();
        } else if (raw instanceof String s) {
            to = InviteService.splitAddresses(s);
        } else if (raw instanceof List<?> l) {
            to = InviteService.splitAddresses(String.join("\n", l.stream().map(String::valueOf).toList()));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be a list of addresses or a string");
        }
        CurrentUser user = CurrentUser.from(auth);
        if (req.listIds() != null && !req.listIds().isEmpty()) {
            List<String> all = new ArrayList<>(to);
            all.addAll(lists.addresses(req.listIds(), user));
            to = InviteService.splitAddresses(String.join("\n", all));
        }
        String inviter = user != null && user.id().startsWith(ApiTokenFilter.PREFIX) && req.from() != null && !req.from().isBlank()
                ? req.from() : null;
        String origin = ServletUriComponentsBuilder.fromContextPath(http).build().toUriString();
        try {
            List<InviteService.Result> results = invites.invite(id, to, req.note(), user, origin, inviter);
            long sent = results.stream().filter(InviteService.Result::sent).count();
            return Map.of("results", results, "sent", sent, "notSent", results.size() - sent);
        } catch (MailException e) {
            HttpStatus s = switch (e.getKind()) {
                case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
                case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
                case FAILED -> HttpStatus.BAD_GATEWAY;
                default -> HttpStatus.BAD_REQUEST;
            };
            throw new ResponseStatusException(s, e.getMessage());
        }
    }
}
