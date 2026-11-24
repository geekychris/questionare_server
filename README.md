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

## API (signed in)

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
GET    /api/mail/status                      {enabled}
POST   /api/campaigns/{id}/invites           {to: [addresses] or "a@x, b@y", note?} -> {results:[{to, sent, error}], sent, notSent}
```

Campaign JSON includes `shareToken`, `shareUrl`, `acceptingResponses`,
`ownerName` and `responseCount`. Respondent endpoints: docs/RESPONDENTS.md.

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
