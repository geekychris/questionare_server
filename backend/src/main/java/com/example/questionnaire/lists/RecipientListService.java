package com.example.questionnaire.lists;

import com.example.questionnaire.mail.MailException;
import com.example.questionnaire.mail.MailService;
import com.example.questionnaire.model.RecipientList;
import com.example.questionnaire.repository.RecipientListRepository;
import com.example.questionnaire.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Recipient lists (README "Recipient lists"). Owned like campaigns: a creator sees and changes only
 * their own, an admin all; anyone else gets 404. Names are unique per owner. Addresses are checked
 * with {@link MailService#checkAddress}, de-duplicated ignoring case, and capped at {@link #MAX_ENTRIES}.
 */
@Service
public class RecipientListService {

    public static final int MAX_ENTRIES = 1000;
    public static final int MAX_NAME = 120;

    /** {@code Name <email>} as pasted from a mail client. */
    private static final Pattern NAMED = Pattern.compile("^\\s*\"?([^\"<]*?)\"?\\s*<([^>]+)>\\s*$");

    private final RecipientListRepository repo;
    private final Clock clock;

    public RecipientListService(RecipientListRepository repo) {
        this.repo = repo;
        this.clock = Clock.systemUTC();
    }

    /** A member as given: email plus optional display name. */
    public record Member(String email, String name) { }

    public record Summary(Long id, String name, String ownerName, int size, LocalDateTime updatedAt) { }

    public record Detail(Long id, String name, String ownerName, List<Member> members,
                         LocalDateTime createdAt, LocalDateTime updatedAt) { }

    @Transactional(readOnly = true)
    public List<Summary> list(CurrentUser user) {
        requireManager(user);
        List<RecipientList> all = user.isAdmin() ? repo.findAllByOrderByNameAsc() : repo.findByOwnerSubOrderByNameAsc(user.id());
        return all.stream().map(RecipientListService::summary).toList();
    }

    @Transactional(readOnly = true)
    public Detail get(Long id, CurrentUser user) {
        return detail(load(id, user));
    }

    @Transactional
    public Detail create(String name, List<Member> members, CurrentUser user) {
        requireManager(user);
        String n = cleanName(name);
        if (repo.findByOwnerSubAndName(user.id(), n).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "you already have a list called \"" + n + "\"");
        }
        RecipientList l = new RecipientList();
        l.setName(n);
        l.setOwnerSub(user.id());
        l.setOwnerName(user.name());
        LocalDateTime now = LocalDateTime.now(clock);
        l.setCreatedAt(now);
        l.setUpdatedAt(now);
        l.setEntries(toEntries(normalise(members == null ? List.of() : members)));
        return detail(repo.save(l));
    }

    /** Renames and/or replaces the members; a null field is left as it is. */
    @Transactional
    public Detail update(Long id, String name, List<Member> members, CurrentUser user) {
        RecipientList l = load(id, user);
        if (name != null) {
            String n = cleanName(name);
            repo.findByOwnerSubAndName(l.getOwnerSub(), n)
                    .filter(o -> !Objects.equals(o.getId(), l.getId()))
                    .ifPresent(o -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "there is already a list called \"" + n + "\"");
                    });
            l.setName(n);
        }
        if (members != null) {
            l.setEntries(toEntries(normalise(members)));
        }
        l.setUpdatedAt(LocalDateTime.now(clock));
        return detail(repo.save(l));
    }

    @Transactional
    public Detail addMembers(Long id, List<Member> members, CurrentUser user) {
        RecipientList l = load(id, user);
        List<Member> merged = new ArrayList<>(detail(l).members());
        merged.addAll(members == null ? List.of() : members);
        l.setEntries(toEntries(normalise(merged)));
        l.setUpdatedAt(LocalDateTime.now(clock));
        return detail(repo.save(l));
    }

    /** Removes the given addresses (ignoring case); addresses not on the list are ignored. */
    @Transactional
    public Detail removeMembers(Long id, Collection<String> emails, CurrentUser user) {
        RecipientList l = load(id, user);
        Set<String> drop = (emails == null ? List.<String>of() : emails).stream()
                .filter(Objects::nonNull).map(e -> e.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        l.setEntries(new ArrayList<>(l.getEntries().stream()
                .filter(e -> !drop.contains(e.getEmail().toLowerCase(Locale.ROOT))).toList()));
        l.setUpdatedAt(LocalDateTime.now(clock));
        return detail(repo.save(l));
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        repo.delete(load(id, user));
    }

    /** The addresses on the given lists, in order, for invites (404 for any list the caller can't see). */
    @Transactional(readOnly = true)
    public List<String> addresses(Collection<Long> ids, CurrentUser user) {
        List<String> out = new ArrayList<>();
        if (ids == null) return out;
        for (Long id : ids) {
            load(id, user).getEntries().forEach(e -> out.add(e.getEmail()));
        }
        return out;
    }

    /**
     * Checks every address, drops duplicates (first one wins, a later name fills a missing one)
     * and enforces the cap. Any bad address fails the whole call with a 400 naming it.
     */
    public static List<Member> normalise(List<Member> in) {
        Map<String, Member> seen = new LinkedHashMap<>();
        List<String> bad = new ArrayList<>();
        for (Member m : in) {
            if (m == null) continue;
            String email;
            try {
                email = MailService.checkAddress(m.email());
            } catch (MailException e) {
                bad.add(m.email() == null ? "(empty)" : m.email());
                continue;
            }
            String name = cleanMemberName(m.name());
            String key = email.toLowerCase(Locale.ROOT);
            Member prev = seen.get(key);
            if (prev == null) {
                seen.put(key, new Member(email, name));
            } else if (prev.name() == null && name != null) {
                seen.put(key, new Member(prev.email(), name));
            }
        }
        if (!bad.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "not plain email addresses: " + String.join(", ", bad.stream().limit(10).toList())
                            + (bad.size() > 10 ? " (and " + (bad.size() - 10) + " more)" : ""));
        }
        if (seen.size() > MAX_ENTRIES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a list holds at most " + MAX_ENTRIES + " addresses");
        }
        return new ArrayList<>(seen.values());
    }

    /** Parses pasted lines: {@code email}, or {@code Name <email>}; commas or semicolons also separate. */
    public static List<Member> parse(String text) {
        List<Member> out = new ArrayList<>();
        if (text == null) return out;
        for (String line : text.split("[\\n;,]+")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            Matcher m = NAMED.matcher(t);
            out.add(m.matches() ? new Member(m.group(2).trim(), m.group(1).trim()) : new Member(t, null));
        }
        return out;
    }

    /** One line per member, in the form {@link #parse} reads. */
    public static String format(List<Member> members) {
        return members.stream().map(m -> m.name() == null ? m.email() : m.name() + " <" + m.email() + ">")
                .collect(Collectors.joining("\n"));
    }

    private RecipientList load(Long id, CurrentUser user) {
        requireManager(user);
        RecipientList l = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "List not found"));
        if (!user.isAdmin() && !Objects.equals(l.getOwnerSub(), user.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "List not found");
        }
        return l;
    }

    private static void requireManager(CurrentUser user) {
        if (user == null || !user.canManageCampaigns()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin or creator role required");
        }
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.replaceAll("[\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(n) || n.length() > MAX_NAME) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required (max " + MAX_NAME + " chars)");
        }
        return n;
    }

    private static String cleanMemberName(String name) {
        if (name == null) return null;
        String n = name.replaceAll("[\\p{Cntrl}<>\"]+", " ").replaceAll("\\s+", " ").trim();
        if (n.isEmpty()) return null;
        return n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n;
    }

    private static List<RecipientList.Entry> toEntries(List<Member> members) {
        return new ArrayList<>(members.stream().map(m -> new RecipientList.Entry(m.email(), m.name())).toList());
    }

    private static Summary summary(RecipientList l) {
        return new Summary(l.getId(), l.getName(), l.getOwnerName(), l.getEntries().size(), l.getUpdatedAt());
    }

    private static Detail detail(RecipientList l) {
        return new Detail(l.getId(), l.getName(), l.getOwnerName(),
                l.getEntries().stream().map(e -> new Member(e.getEmail(), e.getName())).toList(),
                l.getCreatedAt(), l.getUpdatedAt());
    }
}
