package com.example.questionnaire.webhook;

import com.example.questionnaire.repository.CampaignRepository;
import com.example.questionnaire.repository.UserResponseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Campaign webhooks against a local receiver: settings, signing, delivery records, retries, events. */
@SpringBootTest
@AutoConfigureMockMvc
class WebhookApiTest {

    static final String TOKEN = "test-token-0123456789abcdefghij";
    static final String SECRET = "webhook-test-secret";

    record Received(Map<String, String> headers, String body) {}

    static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();
    static final AtomicInteger REPLY = new AtomicInteger(200);
    static final HttpServer RECEIVER;

    static {
        try {
            RECEIVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        RECEIVER.createContext("/hook", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> h = new java.util.HashMap<>();
            ex.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), v.get(0)));
            RECEIVED.add(new Received(h, body));
            byte[] out = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(REPLY.get(), out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        RECEIVER.start();
    }

    static String hookUrl() {
        return "http://127.0.0.1:" + RECEIVER.getAddress().getPort() + "/hook";
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.api-tokens", () -> "faas=" + TOKEN);
        r.add("app.webhooks.allowed-hosts", () -> "127.0.0.1, .example.org");
        r.add("app.webhooks.secret", () -> SECRET);
        r.add("app.webhooks.poll-millis", () -> "100");
        r.add("app.public-url", () -> "https://survey.example.org");
    }

    @AfterAll
    static void stop() {
        RECEIVER.stop(0);
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
    WebhookDeliveryRepository deliveries;

    static RequestPostProcessor user(String sub, String name, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())).toList();
        return oidcLogin().authorities(authorities)
                .idToken(t -> t.subject(sub).claim("preferred_username", name).claim("email", name + "@example.com")
                        .claim("roles", List.of(roles)));
    }

    static final RequestPostProcessor CREATOR = user("pau_creator", "carol", "creator");
    static final RequestPostProcessor OTHER_CREATOR = user("pau_creator2", "dave", "creator");
    static final RequestPostProcessor RESPONDENT = user("pau_resp", "rita", "respondent");

    static final RequestPostProcessor BEARER = req -> {
        req.addHeader("Authorization", "Bearer " + TOKEN);
        return req;
    };

    static RequestPostProcessor bearer() {
        return BEARER;
    }

    static final String CAMPAIGN = """
            {"title":"Lunch","description":"d","active":true,"questions":[
              {"text":"Pizza?","type":"MULTIPLE_CHOICE","required":true,"orderIndex":1,"options":[{"text":"Yes"},{"text":"No"}]},
              {"text":"Why?","type":"TEXT","required":false,"orderIndex":2,"options":[]}]}""";

    @BeforeEach
    void clean() {
        deliveries.deleteAll();
        responses.deleteAll();
        campaigns.deleteAll();
        RECEIVED.clear();
        REPLY.set(200);
    }

    JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    long create(RequestPostProcessor who, String extra) throws Exception {
        String content = extra == null ? CAMPAIGN : CAMPAIGN.substring(0, CAMPAIGN.length() - 1) + "," + extra + "}";
        var req = post("/api/campaigns").with(who).contentType(MediaType.APPLICATION_JSON).content(content);
        if (who != bearer()) req.with(csrf());
        return body(mvc.perform(req).andExpect(status().isOk()).andReturn()).get("id").asLong();
    }

    void answer(long id, RequestPostProcessor who) throws Exception {
        JsonNode c = body(mvc.perform(get("/api/campaigns/" + id).with(bearer())).andReturn());
        String token = c.get("shareToken").asText();
        JsonNode q = c.get("questions");
        String submission = "{\"answers\":[{\"questionId\":" + q.get(0).get("id") + ",\"optionId\":" + q.get(0).get("options").get(0).get("id")
                + "},{\"questionId\":" + q.get(1).get("id") + ",\"text\":\"tasty\"}]}";
        var req = post("/r/" + token + "/responses").header("X-Respondent", "1").contentType(MediaType.APPLICATION_JSON).content(submission);
        if (who != null) req.with(who);
        mvc.perform(req).andExpect(status().isCreated());
    }

    static <T> T await(Supplier<T> get, java.util.function.Predicate<T> done) throws InterruptedException {
        long until = System.currentTimeMillis() + 10_000;
        T v = get.get();
        while (!done.test(v) && System.currentTimeMillis() < until) {
            Thread.sleep(50);
            v = get.get();
        }
        return v;
    }

    @Test
    void settingsAreCheckedAndOwnerScoped() throws Exception {
        long id = create(CREATOR, null);
        mvc.perform(get("/api/campaigns/" + id + "/webhook").with(CREATOR))
                .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.url").doesNotExist());
        for (String bad : List.of("http://10.0.0.1/x", "ftp://127.0.0.1/x", "http://user:pw@127.0.0.1/x", "http://example.org/x", "not a url")) {
            mvc.perform(put("/api/campaigns/" + id + "/webhook").with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("url", bad)))).andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/campaigns/" + id + "/webhook").with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://faas.example.org/fn/x\",\"events\":[\"nope\"]}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/campaigns/" + id + "/webhook").with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://faas.example.org/fn/x\",\"events\":[\"response.created\",\"campaign.closed\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://faas.example.org/fn/x"))
                .andExpect(jsonPath("$.events.length()").value(2));
        // Someone else's campaign is invisible; CSRF applies to sessions.
        mvc.perform(get("/api/campaigns/" + id + "/webhook").with(OTHER_CREATOR)).andExpect(status().isNotFound());
        mvc.perform(put("/api/campaigns/" + id + "/webhook").with(CREATOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"\"}")).andExpect(status().isForbidden());
        // An edit of the campaign that doesn't mention the webhook keeps it.
        mvc.perform(put("/api/campaigns/" + id).with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Lunch 2\",\"active\":true}")).andExpect(jsonPath("$.webhookUrl").value("https://faas.example.org/fn/x"));
        mvc.perform(put("/api/campaigns/" + id + "/webhook").with(CREATOR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"\"}")).andExpect(jsonPath("$.url").doesNotExist());
        assertThat(campaigns.findById(id).orElseThrow().getWebhookUrl()).isNull();
    }

    @Test
    void responseIsDeliveredSignedAndRecorded() throws Exception {
        long id = create(bearer(), "\"webhookUrl\":\"" + hookUrl() + "\"");
        answer(id, null);

        List<Received> got = await(() -> RECEIVED, l -> !l.isEmpty());
        assertThat(got).hasSize(1);
        Received r = got.get(0);
        assertThat(r.headers().get("x-questionnaire-event")).isEqualTo("response.created");
        assertThat(r.headers().get("x-questionnaire-signature")).isEqualTo(WebhookService.signature(SECRET, r.body()));
        assertThat(r.headers().get("idempotency-key")).isEqualTo(r.headers().get("x-questionnaire-delivery"));
        JsonNode b = json.readTree(r.body());
        assertThat(b.get("event").asText()).isEqualTo("response.created");
        assertThat(b.get("campaign").get("id").asLong()).isEqualTo(id);
        assertThat(b.get("campaign").get("link").asText()).startsWith("https://survey.example.org/r/");
        assertThat(b.get("response").get("respondent").isNull()).isTrue();
        JsonNode answers = b.get("response").get("answers");
        assertThat(answers).hasSize(2);
        assertThat(answers.get(0).get("question").asText()).isEqualTo("Pizza?");
        assertThat(answers.get(0).get("type").asText()).isEqualTo("choice");
        assertThat(answers.get(0).get("value").asText()).isEqualTo("Yes");
        assertThat(answers.get(1).get("value").asText()).isEqualTo("tasty");

        WebhookDelivery d = await(() -> deliveries.findAll().get(0), x -> x.getStatus() == WebhookDelivery.Status.SUCCEEDED);
        assertThat(d.getStatus()).isEqualTo(WebhookDelivery.Status.SUCCEEDED);
        assertThat(d.getAttempts()).isEqualTo(1);
        assertThat(d.getLastStatusCode()).isEqualTo(200);
        mvc.perform(get("/api/campaigns/" + id + "/webhook").with(bearer()))
                .andExpect(jsonPath("$.deliveries[0].status").value("SUCCEEDED"));
    }

    @Test
    void signedInRespondentIsIncludedOnlyWhenLoginIsRequired() throws Exception {
        long id = create(bearer(), "\"requireLogin\":true,\"webhookUrl\":\"" + hookUrl() + "\"");
        answer(id, RESPONDENT);
        List<Received> got = await(() -> RECEIVED, l -> !l.isEmpty());
        JsonNode who = json.readTree(got.get(0).body()).get("response").get("respondent");
        assertThat(who.get("name").asText()).isEqualTo("rita");
        assertThat(who.get("email").asText()).isEqualTo("rita@example.com");
    }

    @Test
    void failureIsRetriedLaterAndTheRespondentIsUnaffected() throws Exception {
        REPLY.set(500);
        long id = create(bearer(), "\"webhookUrl\":\"" + hookUrl() + "\"");
        answer(id, null);
        WebhookDelivery d = await(() -> deliveries.findAll().get(0), x -> x.getAttempts() >= 1);
        assertThat(d.getStatus()).isEqualTo(WebhookDelivery.Status.PENDING);
        assertThat(d.getLastStatusCode()).isEqualTo(500);
        assertThat(d.getLastError()).startsWith("HTTP 500");
        assertThat(d.getNextAttemptAt()).isAfter(d.getUpdatedAt().plusSeconds(5));
        assertThat(WebhookService.backoff(4)).isGreaterThan(WebhookService.backoff(1));
    }

    @Test
    void pingAndCampaignClosed() throws Exception {
        long id = create(bearer(), "\"webhookUrl\":\"" + hookUrl() + "\",\"webhookEvents\":[\"response.created\",\"campaign.closed\"]");
        mvc.perform(post("/api/campaigns/" + id + "/webhook/test").with(bearer())).andExpect(jsonPath("$.event").value("ping"));
        mvc.perform(post("/api/campaigns/" + id + "/share/close").with(bearer())).andExpect(status().isOk());
        List<Received> got = await(() -> RECEIVED, l -> l.size() >= 2);
        assertThat(got.stream().map(r -> r.headers().get("x-questionnaire-event")).toList())
                .containsExactlyInAnyOrder("ping", "campaign.closed");
        // Closing an already closed campaign sends nothing more.
        mvc.perform(post("/api/campaigns/" + id + "/share/close").with(bearer())).andExpect(status().isOk());
        Thread.sleep(400);
        assertThat(RECEIVED).hasSize(2);
    }

    @Test
    void noWebhookNoDelivery() throws Exception {
        long id = create(bearer(), null);
        answer(id, null);
        mvc.perform(post("/api/campaigns/" + id + "/webhook/test").with(bearer())).andExpect(status().isBadRequest());
        Thread.sleep(300);
        assertThat(deliveries.count()).isZero();
    }

    @Test
    void allowlistAndOffSwitch() {
        WebhookProperties p = new WebhookProperties();
        assertThat(p.enabled()).isFalse();
        p.setAllowedHosts(List.of("faas.lab, .Example.org"));
        assertThat(p.enabled()).isFalse();
        p.setSecret("s");
        assertThat(p.enabled()).isTrue();
        assertThat(p.hostAllowed("faas.lab")).isTrue();
        assertThat(p.hostAllowed("x.faas.lab")).isFalse();
        assertThat(p.hostAllowed("a.example.org")).isTrue();
        assertThat(p.hostAllowed("example.org")).isFalse();
        assertThat(p.hostAllowed("evilexample.org")).isFalse();
        WebhookProperties off = new WebhookProperties();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> WebhookService.validateUrl(off, "http://faas.lab/x"))
                .hasMessageContaining("off");
    }
}
