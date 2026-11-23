package com.example.questionnaire.respondent;

import com.example.questionnaire.dto.AnswerOptionDTO;
import com.example.questionnaire.dto.CampaignDTO;
import com.example.questionnaire.dto.QuestionDTO;
import com.example.questionnaire.repository.CampaignRepository;
import com.example.questionnaire.repository.UserResponseRepository;
import com.example.questionnaire.security.CurrentUser;
import com.example.questionnaire.service.CampaignService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RespondentFlowTest {

    static final CurrentUser OWNER = new CurrentUser("pau_owner", "olga", List.of("creator"));

    @Autowired
    MockMvc mvc;
    @Autowired
    CampaignService campaigns;
    @Autowired
    CampaignRepository campaignRepo;
    @Autowired
    UserResponseRepository responses;
    @Autowired
    ObjectMapper json;

    @BeforeEach
    void clean() {
        responses.deleteAll();
        campaignRepo.deleteAll();
    }

    CampaignDTO campaign(boolean requireLogin) {
        CampaignDTO c = new CampaignDTO();
        c.setTitle("Lunch <survey>");
        c.setDescription("Where shall we eat?");
        c.setActive(true);
        c.setRequireLogin(requireLogin);
        QuestionDTO choice = new QuestionDTO();
        choice.setText("Place");
        choice.setType("MULTIPLE_CHOICE");
        choice.setRequired(true);
        choice.setOrderIndex(1);
        AnswerOptionDTO a = new AnswerOptionDTO();
        a.setText("Pizza");
        a.setOrderIndex(1);
        AnswerOptionDTO b = new AnswerOptionDTO();
        b.setText("Sushi");
        b.setOrderIndex(2);
        choice.setOptions(List.of(a, b));
        QuestionDTO text = new QuestionDTO();
        text.setText("Comments");
        text.setType("TEXT");
        text.setOrderIndex(2);
        c.setQuestions(List.of(choice, text));
        return campaigns.createCampaign(c, OWNER);
    }

    static String answers(CampaignDTO c, int option, String text) {
        QuestionDTO q1 = c.getQuestions().get(0);
        QuestionDTO q2 = c.getQuestions().get(1);
        return "{\"answers\":[{\"questionId\":" + q1.getId() + ",\"optionId\":" + q1.getOptions().get(option).getId() + "},"
                + "{\"questionId\":" + q2.getId() + ",\"text\":\"" + text + "\"}]}";
    }

    static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submit(String token, String body) {
        return post("/r/" + token + "/responses").header("X-Respondent", "1")
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void anonymousRespondentLoadsAndSubmitsWithoutLogin() throws Exception {
        CampaignDTO c = campaign(false);
        String token = c.getShareToken();

        mvc.perform(get("/r/" + token)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Lunch &lt;survey&gt;")))
                .andExpect(content().string(containsString("/r/assets/respond.js")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")));
        mvc.perform(get("/r/assets/respond.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/responses")));
        mvc.perform(get("/r/assets/respond.css")).andExpect(status().isOk());
        mvc.perform(get("/r/assets/other.js")).andExpect(status().isNotFound());

        mvc.perform(get("/r/" + token + "/form")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Lunch <survey>"))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].type").value("CHOICE"))
                .andExpect(jsonPath("$.questions[0].options[1].text").value("Sushi"))
                .andExpect(jsonPath("$.questions[1].type").value("TEXT"))
                // nothing about the owner, the token or other campaigns
                .andExpect(content().string(not(containsString("olga"))))
                .andExpect(content().string(not(containsString("pau_owner"))));

        MvcResult r = mvc.perform(submit(token, answers(c, 1, "fresh")))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("qr_done"))
                .andExpect(cookie().path("qr_done", "/r/" + token))
                .andReturn();
        assertThat(responses.countSubmissions(c.getId())).isEqualTo(1);
        assertThat(responses.findAll()).allSatisfy(x -> assertThat(x.getUserId()).startsWith("anon:"));

        // same browser again → 409
        mvc.perform(submit(token, answers(c, 0, "again")).cookie(new Cookie("qr_done", "1")))
                .andExpect(status().isConflict());
        // another browser → fine
        mvc.perform(submit(token, answers(c, 0, ""))).andExpect(status().isCreated());
        assertThat(responses.countSubmissions(c.getId())).isEqualTo(2);
        assertThat(r.getResponse().getCookie("qr_done").isHttpOnly()).isTrue();
    }

    @Test
    void unknownAndClosedTokens() throws Exception {
        mvc.perform(get("/r/AAAAAAAAAAAAAAAAAAAAAA")).andExpect(status().isNotFound());
        mvc.perform(get("/r/AAAAAAAAAAAAAAAAAAAAAA/form")).andExpect(status().isNotFound());
        mvc.perform(get("/r/not-a-token/form")).andExpect(status().isNotFound());
        mvc.perform(submit("AAAAAAAAAAAAAAAAAAAAAA", "{\"answers\":[]}")).andExpect(status().isNotFound());

        CampaignDTO c = campaign(false);
        campaigns.setOpen(c.getId(), false, OWNER);
        mvc.perform(get("/r/" + c.getShareToken())).andExpect(status().isGone());
        mvc.perform(get("/r/" + c.getShareToken() + "/form")).andExpect(status().isGone());
        mvc.perform(submit(c.getShareToken(), answers(c, 0, "x"))).andExpect(status().isGone());

        // past its end date → closed too
        CampaignDTO d = campaign(false);
        d.setEndDate(LocalDateTime.now().minusMinutes(1));
        campaigns.updateCampaign(d.getId(), d, OWNER);
        mvc.perform(get("/r/" + d.getShareToken() + "/form")).andExpect(status().isGone());
    }

    @Test
    void submissionsAreValidated() throws Exception {
        CampaignDTO c = campaign(false);
        String token = c.getShareToken();
        // CSRF substitute: header and JSON content type are required
        mvc.perform(post("/r/" + token + "/responses").contentType(MediaType.APPLICATION_JSON)
                .content(answers(c, 0, "x"))).andExpect(status().isForbidden());
        mvc.perform(post("/r/" + token + "/responses").header("X-Respondent", "1")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("a=b"))
                .andExpect(status().isUnsupportedMediaType());
        // required question missing
        long q2 = c.getQuestions().get(1).getId();
        mvc.perform(submit(token, "{\"answers\":[{\"questionId\":" + q2 + ",\"text\":\"hi\"}]}"))
                .andExpect(status().isBadRequest());
        // option of another question / unknown question
        mvc.perform(submit(token, "{\"answers\":[{\"questionId\":" + c.getQuestions().get(0).getId() + ",\"optionId\":999999}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(submit(token, "{\"answers\":[{\"questionId\":999999,\"text\":\"x\"}]}"))
                .andExpect(status().isBadRequest());
        // too long
        mvc.perform(submit(token, answers(c, 0, "x".repeat(2001)))).andExpect(status().isBadRequest());
        // body too large
        mvc.perform(submit(token, "{\"answers\":[],\"pad\":\"" + "x".repeat(70_000) + "\"}"))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(submit(token, "{not json")).andExpect(status().isBadRequest());
        assertThat(responses.count()).isZero();
    }

    @Test
    void requireLoginCampaignRedirectsToLoginAndAcceptsAnyRole() throws Exception {
        CampaignDTO c = campaign(true);
        String token = c.getShareToken();
        mvc.perform(get("/r/" + token)).andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/oauth2/authorization/cloudstack"));
        mvc.perform(get("/r/" + token + "/form")).andExpect(status().isUnauthorized());
        mvc.perform(submit(token, answers(c, 0, "x"))).andExpect(status().isUnauthorized());

        RequestPostProcessor respondent = oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_RESPONDENT"))
                .idToken(t -> t.subject("pau_resp").claim("preferred_username", "rita"));
        mvc.perform(get("/r/" + token).with(respondent)).andExpect(status().isOk());
        mvc.perform(get("/r/" + token + "/form").with(respondent)).andExpect(status().isOk())
                .andExpect(jsonPath("$.requireLogin").value(true));
        mvc.perform(submit(token, answers(c, 0, "x")).with(respondent)).andExpect(status().isCreated())
                .andExpect(cookie().doesNotExist("qr_done"));
        assertThat(responses.findAll()).allSatisfy(x -> assertThat(x.getUserId()).isEqualTo("user:pau_resp"));
        // one response per signed-in user
        mvc.perform(submit(token, answers(c, 1, "y")).with(respondent)).andExpect(status().isConflict());
        // the respondent role still has no admin access
        mvc.perform(get("/api/campaigns").with(respondent)).andExpect(status().isForbidden());
    }

    @Test
    void questionsLockOnceAnswered() throws Exception {
        CampaignDTO c = campaign(false);
        mvc.perform(submit(c.getShareToken(), answers(c, 0, "x"))).andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> campaigns.replaceQuestions(c.getId(), List.of(), OWNER))
                .hasMessageContaining("locked");
    }

    @SuppressWarnings("unused")
    private JsonNode read(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }
}
