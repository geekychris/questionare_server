# Respondents and share links

People answering a survey need no account. Each campaign has a **share link**:

    https://survey.<zone>/r/<token>

`<token>` is 128 random bits from `SecureRandom`, base64url without padding
(22 characters, `[A-Za-z0-9_-]`). It is generated when the campaign is
created (campaigns that predate share links get one at startup) and is the
only thing that identifies the campaign to a respondent: campaign ids,
owners, results and other campaigns are never exposed under `/r/`.

## Why everything is under `/r/`

The cloud-stack proxy puts the app behind sign-in (`exposure: login`) and
serves only the path prefix `/r/` without it (`publicPaths: [/r/]`). So the
whole respondent surface lives there:

| Path | |
|---|---|
| `GET /r/{token}` | the survey page (server-rendered HTML shell; strict CSP, `no-store`) |
| `GET /r/assets/respond.js`, `/r/assets/respond.css` | the page's script and stylesheet (plain DOM, no libraries) |
| `GET /r/{token}/form` | the questions, JSON: `{title, description, requireLogin, questions:[{id, text, type: "CHOICE"\|"TEXT", required, options:[{id, text}]}]}` |
| `POST /r/{token}/responses` | submit, JSON `{answers:[{questionId, optionId}\|{questionId, text}]}` with headers `Content-Type: application/json` and `X-Respondent: 1`; 201 `{status:"recorded"}` |

Answers: | status | meaning |
|---|---|
| 404 | unknown (or rotated-away) token |
| 410 | the campaign is closed (closed by its owner, or outside its start/end dates) |
| 401 / login redirect | the campaign requires sign-in and there is no session |
| 400 | invalid answers (unknown question or option, missing required answer, text over 2000 characters, malformed JSON) |
| 409 | already answered (see below) |
| 413 | body over 64 KiB |
| 403 / 415 | missing `X-Respondent: 1` header / not JSON |

Only plain paths are used (the proxy ignores `publicPaths` for paths with
`..`, `//` or percent-encoding), which is why the token is base64url.

## Owner controls (admin UI and API)

The campaign list in the admin UI shows each link with **copy** and
**rotate** buttons and a **Close / Reopen** action. The same through the API
(admin or the campaign's creator; CSRF token required, see the README):

| | |
|---|---|
| `GET /api/campaigns/{id}/share` | `{token, url, open, requireLogin}` |
| `POST /api/campaigns/{id}/share/rotate` | new token; the old link answers 404 from now on |
| `POST /api/campaigns/{id}/share/close` | stop accepting answers (the link answers 410) |
| `POST /api/campaigns/{id}/share/open` | accept answers again |

"Open" is the campaign's `active` flag plus its optional start/end dates.
`url` is `APP_PUBLIC_URL + /r/<token>` (relative when `APP_PUBLIC_URL` is unset).

## `requireLogin`

Per campaign, default off. When on, `/r/<token>` needs an OIDC session with
any questionnaire role (`admin`, `creator` or `respondent`): the page
redirects to the sign-in and comes back to the link; the JSON calls answer
401. The answers are stored under the user's OIDC subject (`user:<sub>`) and
each user can answer once. The `respondent` role exists for exactly this: it
grants nothing else (no admin UI, no API).

For campaigns without `requireLogin`, nothing about the respondent is stored:
each submission gets a random id (`anon:<uuid>`), even if the person happens
to be signed in.

## Abuse limits (deliberately minimal)

- Closed or expired campaigns refuse submissions (410); questions lock once
  the first answer arrives, so answers always match the questions.
- Bodies over 64 KiB, more answers than questions, repeated questions,
  options of other questions and text over 2000 characters are refused.
- One response per browser: after a submission an HttpOnly cookie `qr_done`,
  scoped to `/r/<token>`, makes the next submission from that browser 409.
  It is a courtesy against double submissions, not a guarantee (a private
  window gets around it); campaigns that need one-answer-per-person should
  use `requireLogin`.
- CSRF: the submit endpoint is exempt from Spring's CSRF token (anonymous
  pages carry no session) and instead requires `Content-Type:
  application/json` and the custom `X-Respondent` header. A cross-site page
  can send neither without a CORS preflight, and the app allows no CORS on
  `/r/`. This matters for `requireLogin` campaigns, where a forged request
  would otherwise answer in the user's name.
- There is no rate limiting in the app; the proxy is the place for that.
