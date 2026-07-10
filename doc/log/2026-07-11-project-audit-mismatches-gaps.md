# Project Audit — Mismatches, Misleading Signals, Gaps, and Dormant Capabilities

**Date:** 2026-07-11
**Method:** Full read of all 8 modules, migrations, docs, and config; verified by
`./gradlew compileJava compileTestJava` (clean) and `./gradlew test` (19/19 green).

## TL;DR

The auth foundation (identity module) is genuinely solid, but the platform's core
paywall is currently **theater in both directions**: nothing can ever grant an
unlock (billing has zero service code), and if an unlock existed, anyone could
claim it (identity is a spoofable `?readerId=` query param on a public route).
Docs are the biggest source of misleading signals — `doc/mvp-feature-breakdown.md`
still describes a design that was deleted on June 21. And there's one immediate
breakage: an uncommitted typo in `compose.yaml` that will stop the app from booting.

---

## 1. Broken right now

- **`compose.yaml:15` — `ports:/` instead of `ports:`** (uncommitted working-tree
  edit, probably a stray keystroke). Invalid YAML, so `spring-boot-docker-compose`
  will fail to start Postgres/Redis on the next `bootRun`. One character to fix.
- **`doc/gg.txt` is an empty junk file** committed to the docs folder.

## 2. Mismatches (docs/claims vs. actual code)

- **`doc/mvp-feature-breakdown.md` describes the pre-June-21 design that no longer
  exists.** It still specifies: author request + admin approval (Feature 5 — the
  schema deliberately has no `author_requests` table; onboarding is open),
  `users.role` field (roles are derived, not stored), `payment_transactions`
  per-post checkout (Feature 13 — actual schema is wallet + double-entry ledger,
  and the doc even says "wallet top-up, ledger splits" are out of scope while the
  DB only supports that model), and `posts.like_count` updates. The ironic part:
  the handoff log says this doc's twin was deleted *for being stale beyond
  repair*, yet it's back — and `doc/task/mvp-feature-breakdown.md` is a second,
  *different* copy of it (the two files diff). Anyone onboarding from these docs
  will build against a dead schema.
- **`doc/log/SESSION_HANDOFF.md` is stale on facts it presents as current:** it
  says the Postgres host port was remapped to 5433 (compose.yaml is back at
  `5432:5432`), and that only identity has an HTTP surface (studio/catalog/reading
  all have controllers now). It's dated 2026-06-21; treat it as history, not
  state — but nothing in it says so.
- **Feature 3.5 "Logout: revoke refresh + blacklist JWT"** — logout revokes the
  refresh token and session row, but access tokens are never blacklisted; a leaked
  15-minute token survives logout. Deliberate trade-off probably, but it
  contradicts the doc's checked scope.
- **SRS ID-FR-01 mandates BD phone regex + password complexity; the code has none
  of it.** `UserSignUpRequestDto` has zero validation annotations,
  `IdentityController.signUp` has no `@Valid`, and `IdentityServiceImpl` encodes
  the password directly (a null password NPEs). Feature 2 is marked "Partially
  done" with 2.3/2.4 open, so it's a known hole — but signup itself is checked ✅
  while accepting empty phone/password. This is a trust-boundary gap, not a
  nice-to-have.
- **Route says `tags`, domain says `categories`.**
  `StudioRouteConstant.CATEGORIES = "/{writingId}/tags"`, and catalog's
  `TagService` is a rename shim over studio's `CategoryService`. Works, but every
  new reader will trip over it.
- **`WritingStatus.UNFINISHED_PREVIEW` and `COMPLETED` are unreachable** — nothing
  ever sets them (publish→`PUBLISHED`, unpublish→`DRAFT`), yet
  `WritingContentServiceImpl` dutifully filters for `COMPLETED`. Dead states
  masquerading as features.

## 3. Misleading / security-significant

These share one root cause: **JWT auth is fully built (login, refresh rotation,
sessions, MFA, audit log, a `roles` claim, an `authorProfileId` claim, a
roles→authorities converter in SecurityConfig) — and almost no controller uses
it.** `AccountSecurityController` does it right with `@AuthenticationPrincipal
Jwt`. Everything else trusts the client:

- **Paywall bypass:** `GET /api/v1/posts/{slug}/content?readerId=N` is
  `permitAll()`, and `readerId` is whatever the caller types. Pass any paying
  reader's ID → full body of LOCKED content, as a guest.
- **Admin bypass:** `AdminAuthorController` takes `?adminUserId=`. `ensureAdmin()`
  checks that *the ID you supplied* belongs to an admin — not that *you* are that
  admin. Any authenticated user who guesses an admin's ID (they're sequential
  longs) can deactivate authors.
- **Author impersonation:** `AuthorWritingController`, `MediaUploadController`,
  `AuthorProfileController` all take `authorProfileId`/`requesterUserId` as
  request params — any logged-in reader can create, publish, delete another
  author's writings by passing their profile ID. The JWT already carries
  `authorProfileId`; the controllers just don't read it.
- **No role enforcement anywhere:** SecurityConfig has no `hasRole()` rules, and
  the `roles` claim never includes `ADMIN` even though `users.is_admin` exists
  (V10 migration). The whole roles pipeline is plumbing with no faucet.
- **Permanent zeros presented as data:** catalog DTOs return
  `likeCount`/`commentCount`, but the social module is entities+repositories
  only — nothing can ever write a like or comment. API consumers will render 0
  forever and not know why.
- **`viewCount` is real but trivially inflatable:** incremented synchronously on
  every public detail GET, no debounce (doc 11.7 asked for debounced), no dedup —
  a curl loop is a bestseller. Meanwhile the analytics tables built for deduped
  views (`ContentView`) sit unwritten.
- **Restart amnesia (documented, but worth surfacing):** the JWT keypair is
  generated in memory per boot — every deploy invalidates all outstanding access
  tokens; uploads go to a local `uploads/` dir served at `/uploads/**` — on
  Render's ephemeral filesystem, all media dies on redeploy.

## 4. Gaps (schema exists, behavior doesn't)

- **Billing is 100% schema.** No wallet creation on signup, no top-up, no unlock
  transaction, no `grantUnlock` contract in reading (SRS READ-FR-02). Nothing in
  the codebase ever inserts a `ContentAccess` row — so the SRS's *one core loop*
  ("reader pays → unlocks → author earns") cannot execute end-to-end. This is the
  single biggest gap given the MVP's stated hypothesis.
- **Social:** no like/comment endpoints (Features 14–15), though tables support
  threaded replies.
- **Follow:** `Follow` entity + repo exist, zero endpoints — and SRS CAT-FR-06
  puts follow *in* MVP scope.
- **Library/progress:** `LibraryEntry` unused (READ-FR-05/06).
- **No user profile endpoints at all** — Feature 4's `GET/PATCH /api/v1/users/me`
  doesn't exist.
- **No resend-OTP endpoint** (ID-FR-02b).
- **Projection durability:** publish/unpublish sync catalog via in-memory
  `AFTER_COMMIT` events with no Spring Modulith event registry
  (`spring-modulith-starter-jpa` isn't a dependency). A crash or listener
  exception between commit and projection silently desyncs studio↔catalog, with
  no retry and no test covering the flow.
- **Bengali slugs:** `slugify()` strips everything outside `[a-z0-9]`, so every
  Bengali title — on a Bangladesh-first platform — becomes
  `writing-1720650000000`. (FTS at least uses the `'simple'` config, so search
  tokenizes Bangla.)
- **Preview fallback emits broken JSON** — the 500-char substring of `bodyJson`
  (known `ponytail:` ceiling, flagging that clients will hit it the moment a
  locked post lacks an explicit preview).
- **Test coverage is identity-heavy:** studio/catalog (the entire publish
  pipeline), media upload, and admin paths have zero tests.

## 5. What more this system can do (already designed-in, waiting for code)

The June-21 schema was built well ahead of the service layer, so several "big"
features are mostly wiring, not modeling:

1. **The money loop** — wallet, top-up, idempotent 3-leg unlock (reader debit /
   author 85% / commission 15%), payout requests: all tables, enums, unique
   idempotency keys, and seeded system wallets exist. This is the highest-value
   build and it's service-code only.
2. **Books & serialized chapters** — `Writing` already has `type BOOK/CHAPTER`,
   self-FK `parent`, `orderIndex`; the projection carries `parentId`.
   Chapter-level selling is the schema's whole reason for existing.
3. **Real authorization** — one `@AuthenticationPrincipal` sweep across ~5
   controllers plus an `ADMIN` role claim would close every hole in section 3
   with the infrastructure already built.
4. **Likes, threaded comments, follows, personalized feed** — tables ready, SRS
   specs written.
5. **Library shelf + resume reading** — `LibraryEntry` ready.
6. **Analytics funnel** — `ContentView`/`ContentUnlock` dedup tables ready for
   event-driven tracking, which would also fix the inflatable view count.
7. **Already live and underused:** MFA, device/session management, security audit
   log, Google sign-in, rotating refresh tokens, OpenAPI UI — an "enterprise"
   auth surface a frontend can consume today.
8. **Deferred by design, structurally ready:** white-label multi-tenancy
   (`tenant_id` on every table), watermark-on-egress page server
   (READ-FR-03/04).

## Suggested build order

1. Fix the compose typo.
2. JWT-principal sweep (closes the security holes).
3. Billing unlock loop (makes the MVP's hypothesis testable).
4. Likes / comments / follow.
