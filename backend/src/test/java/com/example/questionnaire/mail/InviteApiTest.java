package com.example.questionnaire.mail;

import com.example.questionnaire.repository.CampaignRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/campaigns/{id}/invites end to end, against the fake SMTP server (never a real provider). */
@SpringBootTest
@AutoConfigureMockMvc
class InviteApiTest {

    static final FakeSmtpServer SMTP;

    static {
        try {
            SMTP = new FakeSmtpServer();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry r) {
        r.add("app.mail.host", () -> "127.0.0.1");
        r.add("app.mail.port", SMTP::port);
        r.add("app.mail.tls", () -> "none");
        r.add("app.mail.from", () -> "surveys@example.com");
        r.add("app.mail.max-recipients", () -> 3);
        r.add("app.mail.rate-per-recipient-per-hour", () -> 2);
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

    static RequestPostProcessor user(String sub, String name, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())).toList();
        return oidcLogin().authorities(authorities)
                .idToken(t -> t.subject(sub).claim("preferred_username", name).claim("roles", List.of(roles)));
    }

    static final RequestPostProcessor ADMIN = user("pau_admin", "chris", "admin");
    static final RequestPostProcessor CREATOR = user("pau_creator", "carol", "creator");
    static final RequestPostProcessor OTHER_CREATOR = user("pau_creator2", "dave", "creator");
    static final RequestPostProcessor RESPONDENT = user("pau_resp", "rita", "respondent");

    @BeforeEach
    void clean() {
        responses.deleteAll();
        campaigns.deleteAll();
        SMTP.clear();
    }

    long create(RequestPostProcessor who) throws Exception {
        MvcResult r = mvc.perform(post("/api/campaigns").with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Lunch\\r\\nBcc: x@evil.com","description":"d","active":true,"questions":[
                                  {"text":"Why","type":"TEXT","required":false,"orderIndex":1,"options":[]}]}"""))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    MvcResult invite(RequestPostProcessor who, long id, String body, int expect) throws Exception {
        return mvc.perform(post("/api/campaigns/" + id + "/invites").with(who).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expect)).andReturn();
    }

    @Test
    void ownerEmailsTheLinkToEachAddress() throws Exception {
        long id = create(CREATOR);
        mvc.perform(get("/api/mail/status")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));

        MvcResult r = invite(CREATOR, id, """
                {"to":"sam@example.com, not-an-address\\nSAM@example.com\\nkim@example.com","note":"Please answer by Friday."}""", 200);
        JsonNode out = json.readTree(r.getResponse().getContentAsString());
        assertThat(out.get("sent").asInt()).isEqualTo(2);
        assertThat(out.get("notSent").asInt()).isEqualTo(1);
        assertThat(out.get("results").get(1).get("error").asText()).contains("plain email address");

        String token = campaigns.findById(id).orElseThrow().getShareToken();
        assertThat(SMTP.messages()).hasSize(2);
        FakeSmtpServer.Message m = SMTP.messages().get(0);
        assertThat(m.to()).containsExactly("sam@example.com");
        assertThat(m.data())
                .contains("Subject: Survey: Lunch Bcc: x@evil.com")
                .doesNotContain("\r\nBcc:")
                .contains("carol invited you")
                .contains("Please answer by Friday.")
                .contains("https://survey.example.test/r/" + token);
    }

    @Test
    void onlyTheOwnerOrAnAdmin() throws Exception {
        long id = create(CREATOR);
        invite(OTHER_CREATOR, id, "{\"to\":[\"sam@example.com\"]}", 404);
        invite(RESPONDENT, id, "{\"to\":[\"sam@example.com\"]}", 403);
        mvc.perform(post("/api/campaigns/" + id + "/invites").contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":[\"sam@example.com\"]}").with(csrf())).andExpect(status().isUnauthorized());
        invite(ADMIN, id, "{\"to\":[\"sam@example.com\"]}", 200);
        assertThat(SMTP.messages()).hasSize(1);
    }

    @Test
    void needsTheCsrfToken() throws Exception {
        long id = create(CREATOR);
        mvc.perform(post("/api/campaigns/" + id + "/invites").with(CREATOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":[\"sam@example.com\"]}")).andExpect(status().isForbidden());
        assertThat(SMTP.messages()).isEmpty();
    }

    @Test
    void refusals() throws Exception {
        long id = create(CREATOR);
        invite(CREATOR, id, "{\"to\":[]}", 400);
        invite(CREATOR, id, "{\"to\":\"a@example.com b@example.com c@example.com d@example.com\"}", 400);
        invite(CREATOR, id, "{\"to\":[\"a@example.com\"],\"note\":\"" + "x".repeat(InviteService.MAX_NOTE + 1) + "\"}", 400);
        mvc.perform(post("/api/campaigns/" + id + "/share/close").with(CREATOR).with(csrf())).andExpect(status().isOk());
        invite(CREATOR, id, "{\"to\":[\"a@example.com\"]}", 400);
        assertThat(SMTP.messages()).isEmpty();
    }

    @Test
    void perAddressLimit() throws Exception {
        long id = create(CREATOR);
        invite(CREATOR, id, "{\"to\":[\"lim@example.com\"]}", 200);
        invite(CREATOR, id, "{\"to\":[\"lim@example.com\"]}", 200);
        MvcResult r = invite(CREATOR, id, "{\"to\":[\"lim@example.com\"]}", 200);
        JsonNode out = json.readTree(r.getResponse().getContentAsString());
        assertThat(out.get("sent").asInt()).isZero();
        assertThat(out.get("results").get(0).get("error").asText()).contains("too often");
    }
}
