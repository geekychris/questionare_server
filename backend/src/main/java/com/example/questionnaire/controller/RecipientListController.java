package com.example.questionnaire.controller;

import com.example.questionnaire.lists.RecipientListService;
import com.example.questionnaire.lists.RecipientListService.Detail;
import com.example.questionnaire.lists.RecipientListService.Member;
import com.example.questionnaire.lists.RecipientListService.Summary;
import com.example.questionnaire.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recipient lists API (roles admin/creator; a creator only their own). Members are given as
 * strings ({@code "sam@example.com"} or {@code "Sam <sam@example.com>"}) or objects
 * {@code {email, name}}. State-changing calls need the CSRF token, or an API token.
 */
@RestController
@RequestMapping("/api/recipient-lists")
public class RecipientListController {

    private final RecipientListService lists;

    public RecipientListController(RecipientListService lists) {
        this.lists = lists;
    }

    public record ListRequest(String name, List<Object> members) { }

    public record MembersRequest(List<Object> members) { }

    @GetMapping
    public List<Summary> all(Authentication auth) {
        return lists.list(CurrentUser.from(auth));
    }

    @PostMapping
    public Detail create(@RequestBody ListRequest req, Authentication auth) {
        return lists.create(req.name(), members(req.members()), CurrentUser.from(auth));
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable Long id, Authentication auth) {
        return lists.get(id, CurrentUser.from(auth));
    }

    /** Renames and/or replaces the members (an omitted field is unchanged). */
    @PutMapping("/{id}")
    public Detail update(@PathVariable Long id, @RequestBody ListRequest req, Authentication auth) {
        return lists.update(id, req.name(), req.members() == null ? null : members(req.members()), CurrentUser.from(auth));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        lists.delete(id, CurrentUser.from(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members")
    public Detail add(@PathVariable Long id, @RequestBody MembersRequest req, Authentication auth) {
        return lists.addMembers(id, members(req.members()), CurrentUser.from(auth));
    }

    @DeleteMapping("/{id}/members")
    public Detail remove(@PathVariable Long id, @RequestBody MembersRequest req, Authentication auth) {
        return lists.removeMembers(id, members(req.members()).stream().map(Member::email).toList(), CurrentUser.from(auth));
    }

    static List<Member> members(List<Object> raw) {
        List<Member> out = new ArrayList<>();
        if (raw == null) return out;
        for (Object o : raw) {
            if (o instanceof String s) {
                out.addAll(RecipientListService.parse(s));
            } else if (o instanceof Map<?, ?> m) {
                Object email = m.get("email");
                Object name = m.get("name");
                out.add(new Member(email == null ? null : String.valueOf(email), name == null ? null : String.valueOf(name)));
            } else if (o != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "members are strings or {email, name}");
            }
        }
        return out;
    }
}
