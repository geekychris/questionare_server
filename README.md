<p align="center">
  <img src="assets/logo.png" alt="Questionare Server logo" width="180">
</p>

# Questionnaire Application

Create survey campaigns (multiple-choice and free-text questions), share them
with a link, and read the results.

- **Admin UI** (Vaadin, served by the backend at `/`): for users with the
  `admin` or `creator` role, signed in with OpenID Connect.
- **Respondents** need no account: each campaign has an unguessable share link
  `/r/<token>`; a campaign can optionally require sign-in. See
  [docs/RESPONDENTS.md](docs/RESPONDENTS.md).
- **JSON API** under `/api` for the same operations (session + CSRF token).

The backend (`backend/`) is the whole deployed app: Java 17+, Spring Boot 3.5,
Spring Security OAuth2 client, Spring Data JPA, Vaadin 24.9, MySQL (H2 for
tests and the dev profile). `frontend/` holds an older React/MUI SPA over the
same API; it is **not deployed** and is unmaintained (its "take survey" page
used an endpoint that no longer exists — respondents use `/r/<token>`).

## Authentication

Sign-in is OIDC against the site's cloud-stack provider (authorization code +
PKCE S256, Spring registration id `cloudstack`, scopes `openid profile email`).
The client is built from environment variables (no discovery at startup):

| Variable | | |
|---|---|---|
| `OIDC_ISSUER` | required | e.g. `https://auth.hitorro.com`; browser-facing endpoints (`/oidc/authorize`, `/oidc/end_session`) and the expected `iss` |
| `OIDC_BACKCHANNEL_URL` | default: the issuer | base for token and JWKS calls from the server (e.g. `https://auth.hitorro.com:8443`) |
| `OIDC_CLIENT_ID` | default `questionnaire` | |
| `OIDC_CLIENT_SECRET` | required | |
| `APP_PUBLIC_URL` | optional | base of the share links, e.g. `https://survey.hitorro.com` (else relative) |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | `DB_PASSWORD` required | MySQL |
| `APP_CORS_ORIGINS` | optional | exact origins allowed to call `/api` cross-origin (SPA development only; default none) |

Outside the `dev` profile the app refuses to start without `OIDC_ISSUER`,
`OIDC_CLIENT_SECRET` and `DB_PASSWORD`.

Behind the proxy: `server.forward-headers-strategy=framework`, so redirect URIs
use the public name from `X-Forwarded-Host`/`-Proto`. When the request came in
on an explicit non-443 https port (Belmont's LAN path `survey.hitorro.com:8443`),
the authorization and end-session URLs get the same port. Sign-out is local plus
RP-initiated logout (`/oidc/end_session`, back to `{baseUrl}/`).
`X-Cloudstack-User`/`-Roles` headers are ignored.

**Roles** (the ID token's `roles` claim, falling back to `groups`; unknown names ignored):

| Role | Can |
|---|---|
| `admin` | everything: all campaigns, delete, results, `/api/responses` |
| `creator` | create campaigns; edit, close/reopen, rotate the link of and read results of **their own** campaigns (no delete) |
| `respondent` | only answer campaigns that require sign-in |

A login with none of these roles is refused (403 page).

**Endpoint rules:** `/actuator/health` (and `/readiness`, `/liveness`) public;
`/r/**` public, with the controller enforcing token state and `requireLogin`;
`/api/me`, `/api/csrf` any signed-in user; `DELETE /api/campaigns/*` and
`/api/responses/**` admin; other `/api/**` and the admin UI admin or creator;
everything else needs a session. Browsers without a session are redirected to
`/oauth2/authorization/cloudstack`; `/api/**` answers 401.

**CSRF** is on. Vaadin handles its own; JSON clients get the token from
`GET /api/csrf` (`{headerName, token}`) and send it as `X-CSRF-TOKEN`. The
respondent submit is exempt and uses a JSON-content-type + custom-header check
instead (docs/RESPONDENTS.md).

## API (signed in, or with an API token)

```
GET    /api/me                               {username, roles}
GET    /api/csrf                             {headerName, token}
GET    /api/campaigns                        admin: all; creator: own
POST   /api/campaigns                        {title, description, active, requireLogin, startDate, endDate, questions:[{text, type: MULTIPLE_CHOICE|TEXT, required, orderIndex, options:[{text, orderIndex}]}]}
GET    /api/campaigns/{id}
PUT    /api/campaigns/{id}                   campaign fields only
PUT    /api/campaigns/{id}/questions         replace all questions (409 once answered)
DELETE /api/campaigns/{id}                   admin
GET    /api/campaigns/{id}/share             {token, url, open, requireLogin}
POST   /api/campaigns/{id}/share/rotate|close|open
GET    /api/campaigns/{id}/results           counts per option, free-text answers
GET    /api/mail/status                      {enabled} (public: only says whether mail is configured)
POST   /api/campaigns/{id}/invites           {to?: [addresses] or "a@x, b@y", listIds?: [ids], note?, from?} -> {results:[{to, sent, error}], sent, notSent}
GET    /api/recipient-lists                  [{id, name, ownerName, size, updatedAt}]  admin: all; creator: own
POST   /api/recipient-lists                  {name, members?} -> {id, name, ownerName, members:[{email, name}], createdAt, updatedAt}
GET    /api/recipient-lists/{id}
PUT    /api/recipient-lists/{id}             {name?, members?}  rename and/or replace the members
DELETE /api/recipient-lists/{id}
POST   /api/recipient-lists/{id}/members     {members}  add
DELETE /api/recipient-lists/{id}/members     {members}  remove (by address, ignoring case)
```

Members are strings (`"sam@example.com"` or `"Sam Lee <sam@example.com>"`) or
objects `{email, name}`.

Campaign JSON includes `shareToken`, `shareUrl`, `acceptingResponses`,
`ownerName` and `responseCount`. Respondent endpoints: docs/RESPONDENTS.md.

### API tokens

For programs (the site's FaaS functions, scripts), `API_TOKENS` holds
`label=token` pairs separated by commas (each token at least 24 characters;
empty = off). A request to `/api/**` with `Authorization: Bearer <token>` acts
as the service principal `api:<label>` with the **creator** role: it owns the
campaigns and lists it creates (admins see them too), cannot delete campaigns
or read `/api/responses`, and never reaches the admin UI. Nothing is kept in a
session, and only requests a token actually authenticated skip the CSRF check;
a missing or unknown token is handled exactly as before (401 or CSRF 403).
Only SHA-256 hashes of the tokens are kept in memory, compared in constant
time. An invite sent with a token may set `from`, the inviter's name in the
email (people signing in are always named as themselves).

The cloud-stack deploy generates one token per site (label `faas`) and gives
it to the site's FaaS server as the `survey` connection.

## Recipient lists

**Recipient lists** (header link) are named address lists, owned like
campaigns: a creator sees and edits their own, an admin all. Paste one address
per line (`name@example.com` or `Sam Lee <sam@example.com>`); addresses are
checked, duplicates dropped ignoring case, at most 1,000 a list, and names are
unique per owner. Pick lists in a campaign's "Email the link" dialog, or send
`listIds` to the invite API: the addresses of all lists and `to` are merged and
each gets one email (the per-request limit below applies to the merged total).

## Emailing invites

On the campaign list, the envelope next to an open campaign's share link emails
that link to the addresses you paste (one per line or comma separated), each
person in their own message, with an optional note. The result for every
address is shown; the ones that didn't go stay in the box. The button only
appears when mail is configured.

Only an admin, or the creator who owns the campaign, can send. The message is
fixed text (who invites, the title, the note, the link, when it closes); the
title is reduced to one line for the subject, and the app logs the recipient,
never the link. Limits: 50 addresses a request, 50 emails an hour, 3 to one
address an hour (in memory, per instance).

| Setting (env) | Default | |
|---|---|---|
| `QUESTIONNAIRE_MAIL_HOST` | empty (mail off) | e.g. `smtp-relay.gmail.com` |
| `QUESTIONNAIRE_MAIL_PORT` | from TLS (587 / 465 / 25) | |
| `QUESTIONNAIRE_MAIL_TLS` | `starttls` | STARTTLS required and the certificate checked; `tls`; `none` only for a test catcher without an account |
| `QUESTIONNAIRE_MAIL_USERNAME` | empty (no sign-in) | a relay that accepts the site's address needs none |
| `QUESTIONNAIRE_MAIL_FROM` | the account | |
| `QUESTIONNAIRE_MAIL_FROM_NAME` | `Surveys` | |
| `QUESTIONNAIRE_MAIL_REPLY_TO` | empty | |
| `QUESTIONNAIRE_MAIL_PASSWORD_FILE` | `/var/run/questionnaire/smtp/password` | read for each send, only with an account |

## Webhooks

A campaign can notify another system when someone answers: give it a **webhook URL** and each
response is POSTed there as signed JSON (for example to a FaaS function's route, which can then
email, file a ticket, or update a sheet). It is optional and per campaign.

**Setting it.** The owner or an admin, in the campaign editor's **Webhook** section (with
**Send test** and the recent deliveries), or through the API:

| | |
|---|---|
| `GET /api/campaigns/{id}/webhook` | `{enabled, allowedHosts, url, events, knownEvents, deliveries}` (last 20) |
| `PUT /api/campaigns/{id}/webhook` | `{"url": "...", "events": ["response.created", "campaign.closed"]}`; `url` `""` removes it |
| `POST /api/campaigns/{id}/webhook/test` | queue a `ping` delivery |
| `POST /api/campaigns` / `PUT /api/campaigns/{id}` | also take `webhookUrl` and `webhookEvents` (on update, a missing `webhookUrl` leaves it alone) |

Events: `response.created` (default) and `campaign.closed` (the survey stops accepting responses);
`ping` comes only from **Send test**.

**What is sent.** `POST <url>` with `Content-Type: application/json` and:

| header | |
|---|---|
| `X-Questionnaire-Event` | the event |
| `X-Questionnaire-Delivery` | a UUID per delivery (the same on every retry) |
| `Idempotency-Key` | the same UUID, so a FaaS route runs a retried delivery once |
| `X-Questionnaire-Signature` | `sha256=<hex HMAC-SHA256 of the raw body>` with the site's `WEBHOOK_SECRET` |

```json
{"event": "response.created", "deliveryId": "6f1c…",
 "campaign": {"id": 6, "title": "FaaS survey test", "link": "https://survey.hitorro.com/r/…"},
 "response": {"id": 41, "submittedAt": "2026-11-24T22:10:03Z",
   "respondent": null,
   "answers": [{"questionId": 12, "question": "Did this arrive?", "type": "choice", "value": "Yes", "optionId": 30},
               {"questionId": 13, "question": "Comments", "type": "text", "value": "…"}]}}
```

`respondent` is `{name, email}` only when the campaign requires sign-in; anonymous responses
stay anonymous. `campaign.closed` and `ping` carry `event`, `deliveryId` and `campaign` (ping adds
`message`).

**Delivery.** Responses are queued after they are stored and sent by a background thread every
couple of seconds, so a respondent is never slowed or failed by a webhook. A 2xx is success;
anything else (or no answer within 10 s) is retried after 10 s, 1 min, 5 min and 30 min, then
marked failed (5 tries, `app.webhooks.max-attempts`). Redirects are not followed. Every delivery
is recorded (status, tries, last HTTP code and error) and shown on the campaign page. The queue is
in the database, so restarts don't lose deliveries; it assumes one replica.

**Verifying.** Compute HMAC-SHA256 of the exact body bytes with the shared secret, hex-encode,
prefix `sha256=`, and compare in constant time with `X-Questionnaire-Signature`. A FaaS
http trigger does this itself when its config has `"verify": "<webhook connection>"` (faas
`docs/CONNECTORS.md`, "webhook"). Signatures carry no timestamp, so dedupe on the delivery id if
replays matter.

**Site settings (off unless both are set):**

| Variable | |
|---|---|
| `WEBHOOK_ALLOWED_HOSTS` | hosts a webhook may name, comma-separated: exact (`192.168.1.177`) or a leading-dot suffix (`.hitorro.com`, subdomains only) |
| `WEBHOOK_SECRET` | the HMAC key |

The allowlist is the guard against a creator pointing the server at internal addresses: only the
URL's host name is checked, not what it resolves to, so list names you control (or IPs).

## Local development

Without MySQL or an OIDC provider, use the `dev` profile (H2 in memory, a local
form login with users `admin`, `creator` and `respondent`):

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev DEV_PASSWORD=<pick one> ./mvnw spring-boot:run
# or without DEV_PASSWORD: a random password is printed at startup
```

Open http://localhost:8080/ . The dev login is for local use only.

Against a real provider: set the variables above (and a MySQL), e.g. with
docker compose: copy `.env.example` to `.env` (gitignored), fill it in, then
`docker compose up --build`. The provider must have
`http://localhost:18007/login/oauth2/code/cloudstack` as a redirect URI.

## Tests and builds

```bash
cd backend
./mvnw test                                  # H2, a dummy provider (no network)
./mvnw -Pproduction -DskipTests package      # jar with the Vaadin production bundle (fetches Node)
```

Vaadin is pinned to 24.9.18: later Vaadin 24 patches need a commercial license
to build in production mode. The free path forward is Vaadin 25 with Spring Boot 4.

## Deployment

In Chris's lab the app is deployed by cloud-stack (`deploy/questionnaire/`),
which builds from `main`, runs MySQL, registers the OIDC client and creates the
Secrets. `k8s/` and `docker-compose.yml` here are generic examples; neither
contains credentials (Secrets / `.env`).

## License

This project is licensed under the MIT License - see the LICENSE file for details.
