package com.example.questionnaire.respondent;

import com.example.questionnaire.model.AnswerOption;
import com.example.questionnaire.model.Campaign;
import com.example.questionnaire.model.Question;
import com.example.questionnaire.model.UserResponse;
import com.example.questionnaire.repository.CampaignRepository;
import com.example.questionnaire.repository.UserResponseRepository;
import com.example.questionnaire.security.ShareTokens;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Share-link access for respondents (docs/RESPONDENTS.md). */
@Service
public class RespondentService {

    public static final int MAX_TEXT = 2000;

    private final CampaignRepository campaigns;
    private final UserResponseRepository responses;

    public RespondentService(CampaignRepository campaigns, UserResponseRepository responses) {
        this.campaigns = campaigns;
        this.responses = responses;
    }

    /** The campaign behind a token: 404 for an unknown token, 410 when it no longer accepts responses. */
    @Transactional(readOnly = true)
    public Campaign open(String token) {
        if (!ShareTokens.looksValid(token)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown survey link");
        }
        Campaign c = campaigns.findByShareToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown survey link"));
        if (!c.isAcceptingResponses(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This survey is closed");
        }
        return c;
    }

    @Transactional(readOnly = true)
    public RespondentForm form(String token) {
        Campaign c = open(token);
        return new RespondentForm(c.getTitle(), c.getDescription(), c.isRequireLogin(),
                c.getQuestions().stream().map(q -> new RespondentForm.Question(
                        q.getId(), q.getText(), isText(q) ? "TEXT" : "CHOICE", q.isRequired(),
                        isText(q) ? List.of() : q.getOptions().stream()
                                .map(o -> new RespondentForm.Option(o.getId(), o.getText())).toList()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public boolean alreadyAnswered(Campaign c, String respondentId) {
        return responses.existsByUserIdAndQuestion_Campaign_Id(respondentId, c.getId());
    }

    /** Validates and stores one submission under {@code respondentId}. */
    @Transactional
    public void submit(String token, Submission submission, String respondentId) {
        Campaign c = open(token);
        if (submission == null || submission.answers() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "answers are required");
        }
        Map<Long, Question> byId = new HashMap<>();
        c.getQuestions().forEach(q -> byId.put(q.getId(), q));
        if (submission.answers().size() > byId.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "too many answers");
        }
        Set<Long> answered = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        List<UserResponse> rows = new java.util.ArrayList<>();
        for (Submission.Answer a : submission.answers()) {
            Question q = a == null ? null : byId.get(a.questionId());
            if (q == null || !answered.add(q.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown or repeated question");
            }
            UserResponse r = new UserResponse();
            r.setQuestion(q);
            r.setUserId(respondentId);
            r.setSubmissionDate(now);
            if (isText(q)) {
                String text = a.text() == null ? "" : a.text().trim();
                if (text.length() > MAX_TEXT) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "answer too long (max " + MAX_TEXT + ")");
                }
                if (text.isEmpty()) {
                    answered.remove(q.getId());
                    continue;
                }
                r.setTextResponse(text);
            } else {
                if (a.optionId() == null) {
                    answered.remove(q.getId());
                    continue;
                }
                AnswerOption opt = q.getOptions().stream().filter(o -> o.getId().equals(a.optionId())).findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown option"));
                r.setSelectedOption(opt);
            }
            rows.add(r);
        }
        for (Question q : c.getQuestions()) {
            if (q.isRequired() && !answered.contains(q.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question \"" + q.getText() + "\" is required");
            }
        }
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no answers");
        }
        if (alreadyAnswered(c, respondentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already answered");
        }
        responses.saveAll(rows);
    }

    static boolean isText(Question q) {
        return "TEXT".equalsIgnoreCase(q.getType()) || !StringUtils.hasText(q.getType()) && q.getOptions().isEmpty();
    }
}
