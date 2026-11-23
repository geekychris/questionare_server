package com.example.questionnaire.security;

import com.example.questionnaire.repository.CampaignRepository;
import com.example.questionnaire.repository.UserResponseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    CampaignRepository campaigns;
    @Autowired
    UserResponseRepository responses;
    @Autowired
    org.springframework.security.oauth2.client.registration.ClientRegistrationRepository registrations;

    static RequestPostProcessor user(String sub, String name, String... roles) {
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())).toList();
        return oidcLogin().authorities(authorities)
                .idToken(t -> t.subject(sub).claim("preferred_username", name).claim("roles", java.util.List.of(roles)));
    }

    static final RequestPostProcessor ADMIN = user("pau_admin", "chris", "admin");
    static final RequestPostProcessor CREATOR = user("pau_creator", "carol", "creator");
    static final RequestPostProcessor OTHER_CREATOR = user("pau_creator2", "dave", "creator");
    static final RequestPostProcessor RESPONDENT = user("pau_resp", "rita", "respondent");

    static final String CAMPAIGN = """
            {"title":"T","description":"d","active":true,"questions":[
              {"text":"Pick","type":"MULTIPLE_CHOICE","required":true,"orderIndex":1,
               "options":[{"text":"A","orderIndex":1},{"text":"B","orderIndex":2}]},
              {"text":"Why","type":"TEXT","required":false,"orderIndex":2,"options":[]}]}""";

    @BeforeEach
    void clean() {
        responses.deleteAll();
        campaigns.deleteAll();
    }

    JsonNode create(RequestPostProcessor who, String body) throws Exception {
        MvcResult r = mvc.perform(post("/api/campaigns").with(who).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }

    // --- authentication entry points -------------------------------------------------------

    @Test
    void browserWithoutSessionIsRedirectedToTheProvider() throws Exception {
        mvc.perform(get("/")).andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/oauth2/authorization/cloudstack"));
        mvc.perform(get("/campaigns/edit")).andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/oauth2/authorization/cloudstack"));
    }

    @Test
    void apiWithoutSessionAnswers401() throws Exception {
        mvc.perform(get("/api/campaigns")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsOpen() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/env").with(ADMIN)).andExpect(status().isForbidden());
    }

    @Test
    void authorizationRedirectUsesPkceAndFollowsTheForwardedPort() throws Exception {
        mvc.perform(get("/oauth2/authorization/cloudstack")
                        .header("X-Forwarded-Host", "survey.hitorro.com:8443")
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("https://auth.example.test:8443/oidc/authorize?")))
                .andExpect(header().string("Location", containsString("code_challenge_method=S256")))
                .andExpect(header().string("Location", containsString("client_id=questionnaire")))
                .andExpect(header().string("Location",
                        containsString("redirect_uri=https://survey.hitorro.com:8443/login/oauth2/code/cloudstack")));
        mvc.perform(get("/oauth2/authorization/cloudstack")
                        .header("X-Forwarded-Host", "survey.hitorro.com")
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("https://auth.example.test/oidc/authorize?")))
                .andExpect(header().string("Location",
                        containsString("redirect_uri=https://survey.hitorro.com/login/oauth2/code/cloudstack")));
    }

    // --- /api/me and roles --------------------------------------------------------------------

    @Test
    void meReturnsUsernameAndRoles() throws Exception {
        mvc.perform(get("/api/me").with(CREATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("carol"))
                .andExpect(jsonPath("$.roles[0]").value("creator"));
        mvc.perform(get("/api/me").with(RESPONDENT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("respondent"));
    }

    @Test
    void respondentRoleCannotUseTheAdminSide() throws Exception {
        mvc.perform(get("/api/campaigns").with(RESPONDENT)).andExpect(status().isForbidden());
        mvc.perform(get("/").with(RESPONDENT)).andExpect(status().isForbidden());
    }

    @Test
    void creatorsSeeOnlyTheirOwnCampaignsAndCannotDelete() throws Exception {
        long mine = create(CREATOR, CAMPAIGN).get("id").asLong();
        long theirs = create(OTHER_CREATOR, CAMPAIGN).get("id").asLong();

        mvc.perform(get("/api/campaigns").with(CREATOR)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine))
                .andExpect(jsonPath("$[0].ownerName").value("carol"));
        mvc.perform(get("/api/campaigns/" + theirs).with(CREATOR)).andExpect(status().isNotFound());
        mvc.perform(get("/api/campaigns/" + theirs + "/results").with(CREATOR)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/campaigns/" + mine).with(CREATOR).with(csrf())).andExpect(status().isForbidden());

        mvc.perform(get("/api/campaigns").with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(delete("/api/campaigns/" + theirs).with(ADMIN).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void apiWritesNeedTheCsrfToken() throws Exception {
        mvc.perform(post("/api/campaigns").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(CAMPAIGN))
                .andExpect(status().isForbidden());

        // The flow a script uses: GET /api/csrf in the session, then send the header.
        MockHttpSession session = new MockHttpSession();
        MvcResult r = mvc.perform(get("/api/csrf").with(ADMIN).session(session))
                .andExpect(status().isOk()).andReturn();
        JsonNode t = json.readTree(r.getResponse().getContentAsString());
        assertThat(t.get("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
        mvc.perform(post("/api/campaigns").with(ADMIN).session(session)
                        .header(t.get("headerName").asText(), t.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(CAMPAIGN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareUrl", containsString("https://survey.example.test/r/")));
    }

    @Test
    void shareLinkCanBeReadRotatedClosedAndReopened() throws Exception {
        long id = create(CREATOR, CAMPAIGN).get("id").asLong();
        MvcResult r = mvc.perform(get("/api/campaigns/" + id + "/share").with(CREATOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.requireLogin").value(false)).andReturn();
        String token = json.readTree(r.getResponse().getContentAsString()).get("token").asText();
        assertThat(token).matches("[A-Za-z0-9_-]{22}");

        r = mvc.perform(post("/api/campaigns/" + id + "/share/rotate").with(CREATOR).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        String rotated = json.readTree(r.getResponse().getContentAsString()).get("token").asText();
        assertThat(rotated).isNotEqualTo(token);
        mvc.perform(get("/r/" + token + "/form")).andExpect(status().isNotFound());
        mvc.perform(get("/r/" + rotated + "/form")).andExpect(status().isOk());

        mvc.perform(post("/api/campaigns/" + id + "/share/close").with(CREATOR).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.open").value(false));
        mvc.perform(get("/r/" + rotated + "/form")).andExpect(status().isGone());
        mvc.perform(get("/r/" + rotated)).andExpect(status().isGone());

        mvc.perform(post("/api/campaigns/" + id + "/share/open").with(CREATOR).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.open").value(true));
        mvc.perform(get("/r/" + rotated + "/form")).andExpect(status().isOk());
    }

    @Test
    void logoutEndsAtTheProvidersEndSessionEndpoint() throws Exception {
        var admin = oidcLogin().clientRegistration(registrations.findByRegistrationId("cloudstack"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .idToken(t -> t.subject("pau_admin").claim("preferred_username", "chris"));
        mvc.perform(post("/logout").with(admin).with(csrf())
                        .header("X-Forwarded-Host", "survey.hitorro.com:8443")
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("https://auth.example.test:8443/oidc/end_session?")))
                .andExpect(header().string("Location", containsString("post_logout_redirect_uri=https://survey.hitorro.com:8443/")))
                .andExpect(header().string("Location", not(containsString("auth.example.test/oidc"))));
    }

    @Test
    void resultsAggregateAnswers() throws Exception {
        JsonNode c = create(ADMIN, CAMPAIGN);
        long id = c.get("id").asLong();
        String token = c.get("shareToken").asText();
        long q1 = c.get("questions").get(0).get("id").asLong();
        long optA = c.get("questions").get(0).get("options").get(0).get("id").asLong();
        long q2 = c.get("questions").get(1).get("id").asLong();
        mvc.perform(post("/r/" + token + "/responses").header("X-Respondent", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":" + q1 + ",\"optionId\":" + optA + "},"
                                + "{\"questionId\":" + q2 + ",\"text\":\"because\"}]}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/campaigns/" + id + "/results").with(ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.submissions").value(1))
                .andExpect(jsonPath("$.questions[0].options[0].count").value(1))
                .andExpect(jsonPath("$.questions[1].textAnswers", hasItem("because")));
    }
}
