package com.example.questionnaire.lists;

import com.example.questionnaire.mail.FakeSmtpServer;
import com.example.questionnaire.repository.CampaignRepository;
import com.example.questionnaire.repository.RecipientListRepository;
import com.example.questionnaire.repository.UserResponseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API tokens (bearer) and recipient lists, including invites to lists, against the fake SMTP server. */
@SpringBootTest
@AutoConfigureMockMvc
class RecipientListApiTest {

    static final String TOKEN = "test-token-0123456789abcdefghij";
    static final FakeSmtpServer SMTP;

    static {
        try {
            SMTP = new FakeSmtpServer();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.api-tokens", () -> "faas=" + TOKEN);
        r.add("app.mail.host", () -> "127.0.0.1");
        r.add("app.mail.port", SMTP::port);
        r.add("app.mail.tls", () -> "none");
        r.add("app.mail.from", () -> "surveys@example.com");
    }

    @AfterAll
    static void stop() throws IOException {
        SMTP.close();
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    CampaignRepository campaigns;
    @Autowired
    UserResponseRepository responses;
    @Autowired
    RecipientListRepository lists;

    static RequestPostProcessor user(String sub, String name, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())).toList();
        return oidcLogin().authorities(authorities)
                .idToken(t -> t.subject(sub).claim("preferred_username", name).claim("roles", List.of(roles)));
    }

    static final RequestPostProcessor ADMIN = user("pau_admin", "chris", "admin");
    static final RequestPostProcessor CREATOR = user("pau_creator", "carol", "creator");
    static final RequestPostProcessor OTHER_CREATOR = user("pau_creator2", "dave", "creator");

    static RequestPostProcessor bearer(String token) {
        return req -> {
            req.addHeader("Authorization", "Bearer " + token);
            return req;
        };
    }

    static final String CAMPAIGN = """
            {"title":"Lunch","description":"d","active":true,"questions":[
              {"text":"Pizza?","type":"MULTIPLE_CHOICE","required":true,"orderIndex":1,"options":[{"text":"Yes"},{"text":"No"}]}]}""";

    @BeforeEach
    void clean() {
        responses.deleteAll();
        campaigns.deleteAll();
        lists.deleteAll();
        SMTP.clear();
    }

    JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void bearerTokenCreatesWithoutCsrfOrSession() throws Exception {
        MvcResult r = mvc.perform(post("/api/campaigns").with(bearer(TOKEN))
                        .contentType(MediaType.APPLICATION_JSON).content(CAMPAIGN))
                .andExpect(status().isOk()).andReturn();
        assertThat(r.getRequest().getSession(false)).isNull();
        long id = body(r).get("id").asLong();
        assertThat(campaigns.findById(id).orElseThrow().getOwnerSub()).isEqualTo("api:faas");

        mvc.perform(get("/api/campaigns/" + id).with(bearer(TOKEN))).andExpect(status().isOk());
        mvc.perform(get("/api/campaigns").with(ADMIN)).andExpect(jsonPath("$.length()").value(1));
        // The token is a creator: someone else's campaign is invisible, admin-only calls are refused.
        mvc.perform(get("/api/responses").with(bearer(TOKEN))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/campaigns/" + id).with(bearer(TOKEN))).andExpect(status().isForbidden());
    }

    @Test
    void badOrMissingTokenKeepsTheOldRules() throws Exception {
        // An unknown token isn't an authentication, so CSRF still applies (as for any cookie-less POST).
        mvc.perform(post("/api/campaigns").with(bearer(TOKEN + "x"))
                .contentType(MediaType.APPLICATION_JSON).content(CAMPAIGN)).andExpect(status().isForbidden());
        mvc.perform(post("/api/campaigns").with(bearer(TOKEN + "x")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CAMPAIGN)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/campaigns").with(bearer("nope"))).andExpect(status().isUnauthorized());
        // A session user sending a junk bearer header still needs the CSRF token.
        mvc.perform(post("/api/campaigns").with(CREATOR).with(bearer("junk"))
                .contentType(MediaType.APPLICATION_JSON).content(CAMPAIGN)).andExpect(status().isForbidden());
        // Tokens never open the admin UI.
        mvc.perform(get("/").with(bearer(TOKEN))).andExpect(status().is3xxRedirection());
        assertThat(campaigns.count()).isZero();
    }

    @Test
    void listsAreOwnedDedupedAndChecked() throws Exception {
        MvcResult r = mvc.perform(post("/api/recipient-lists").with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Team","members":["sam@example.com","Kim Lee <kim@example.com>",{"email":"SAM@example.com","name":"Sam"}]}"""))
                .andExpect(status().isOk()).andReturn();
        JsonNode l = body(r);
        long id = l.get("id").asLong();
        assertThat(l.get("members")).hasSize(2);
        assertThat(l.get("members").get(0).get("name").asText()).isEqualTo("Sam");
        assertThat(l.get("members").get(1).get("name").asText()).isEqualTo("Kim Lee");

        mvc.perform(post("/api/recipient-lists").with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Team\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/recipient-lists").with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bad\",\"members\":[\"not an address\"]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/recipient-lists").with(CREATOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"NoCsrf\"}")).andExpect(status().isForbidden());

        mvc.perform(get("/api/recipient-lists/" + id).with(OTHER_CREATOR)).andExpect(status().isNotFound());
        mvc.perform(get("/api/recipient-lists").with(OTHER_CREATOR)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/recipient-lists").with(ADMIN)).andExpect(jsonPath("$[0].size").value(2));

        mvc.perform(post("/api/recipient-lists/" + id + "/members").with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"members\":[\"lee@example.com\",\"kim@example.com\"]}")).andExpect(jsonPath("$.members.length()").value(3));
        mvc.perform(delete("/api/recipient-lists/" + id + "/members").with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"members\":[\"SAM@example.com\"]}")).andExpect(jsonPath("$.members.length()").value(2));
        mvc.perform(put("/api/recipient-lists/" + id).with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Team 2\"}")).andExpect(jsonPath("$.name").value("Team 2")).andExpect(jsonPath("$.members.length()").value(2));
        mvc.perform(delete("/api/recipient-lists/" + id).with(OTHER_CREATOR).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/recipient-lists/" + id).with(CREATOR).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void capOnListSize() {
        List<RecipientListService.Member> many = new java.util.ArrayList<>();
        for (int i = 0; i <= RecipientListService.MAX_ENTRIES; i++) many.add(new RecipientListService.Member("u" + i + "@example.com", null));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> RecipientListService.normalise(many)).hasMessageContaining("at most");
    }

    @Test
    void inviteToListsWithTheToken() throws Exception {
        long list = body(mvc.perform(post("/api/recipient-lists").with(bearer(TOKEN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test-list\",\"members\":[\"a@example.com\",\"b@example.com\"]}")).andExpect(status().isOk()).andReturn())
                .get("id").asLong();
        long creatorList = body(mvc.perform(post("/api/recipient-lists").with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"carol's\",\"members\":[\"c@example.com\"]}")).andReturn()).get("id").asLong();
        long id = body(mvc.perform(post("/api/campaigns").with(bearer(TOKEN)).contentType(MediaType.APPLICATION_JSON).content(CAMPAIGN))
                .andReturn()).get("id").asLong();

        // Someone else's list is a 404, and nothing is sent.
        mvc.perform(post("/api/campaigns/" + id + "/invites").with(bearer(TOKEN)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"listIds\":[" + creatorList + "]}")).andExpect(status().isNotFound());
        assertThat(SMTP.messages()).isEmpty();

        MvcResult r = mvc.perform(post("/api/campaigns/" + id + "/invites").with(bearer(TOKEN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":[\"A@example.com\"],\"listIds\":[" + list + "],\"from\":\"Chris\",\"note\":\"hi\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(r).get("sent").asInt()).isEqualTo(2);
        assertThat(SMTP.messages()).hasSize(2);
        assertThat(SMTP.messages().get(0).data()).contains("Chris invited you");

        // People are always named as themselves, whatever "from" says.
        SMTP.clear();
        long mine = body(mvc.perform(post("/api/campaigns").with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CAMPAIGN))
                .andReturn()).get("id").asLong();
        mvc.perform(post("/api/campaigns/" + mine + "/invites").with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"listIds\":[" + creatorList + "],\"from\":\"Someone Else\"}")).andExpect(status().isOk());
        assertThat(SMTP.messages().get(0).data()).contains("carol invited you").doesNotContain("Someone Else");
    }
}
