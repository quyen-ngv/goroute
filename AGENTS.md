# GoRoute backend engineering rules

These rules are mandatory for code under this backend. They complement the
workspace-level `AGENTS.md`; the more specific rule wins when the two differ.

This file owns **how to write backend code**. What the backend currently *does* —
business rules, state machines, data model, API surface, flows — is documented in
[`../.agents/docs/`](../.agents/docs/), one file per epic. Read the epic that owns the
area before editing it, and update it in the same change when the change makes it wrong;
[`../.agents/docs/MAINTENANCE.md`](../.agents/docs/MAINTENANCE.md) maps each kind of
change to the file and section to update.

## Supported stack

- Java 21, Spring Boot 3.x, Spring MVC, Spring Security, Bean Validation,
  Flyway, PostgreSQL, and MyBatis XML mappers.
- MyBatis remains the persistence technology. Do not introduce JPA/Hibernate
  or a second persistence abstraction for new features.
- Prefer Spring-managed, constructor-injected components. Do not add static
  service locators or field injection.

## Required request flow and ownership

Use this dependency direction:

`controller -> application service -> repository -> MyBatis mapper -> database`

- Controllers own HTTP only: binding, validation, authentication context,
  status codes, headers, and response DTOs. They must not call a mapper or
  repository, open transactions, construct entities, or implement business
  workflows.
- Services own use cases, object-level authorization, business validation,
  transactions, idempotency, orchestration, and entity/DTO mapping.
- Repositories own persistence-facing operations and hide mapper details from
  services. Keep one repository per table/entity, wrapping its own mapper; do
  not merge repositories into large aggregates. A repository may use another
  table's mapper only to read data tightly bound to its entity (for example
  `TripRepository.findWithExpenses` using `ExpenseMapper`), batched, with no
  business branching; writes still go through each table's own repository.
  Repositories never hold business rules, authorization, service calls or
  event publishing — shared multi-table business logic belongs in a service or
  a domain guard that calls several repositories. New services must not import
  `com.ds.goroute.mapper`.
- MyBatis mapper interfaces and XML own SQL only. They must not encode access
  policy that is invisible to the service name or contract.
- External clients live under `thirdparty`; wrap them behind a service when a
  controller needs the capability.
- DTOs cross the API boundary. Never return persistence entities from a public
  or partner API.
- Keep one public top-level type per file. Production Java source must be
  normally formatted; minified one-line classes are forbidden.

## API rules

- Keep the existing `/v1/api` and `BaseResponse<T>` contract unless an API
  migration is explicitly approved. Always use parameterized generic types;
  raw `ResponseEntity`, `BaseResponse`, `Map`, and `List` are forbidden.
- HTTP status must describe the outcome even when `BaseResponse.meta.code` is
  present: 400 validation, 401 unauthenticated, 403 unauthorized, 404 missing,
  409 conflict/idempotency, and 500 unexpected failure. Never convert errors
  to HTTP 200.
- Use 201 for successful resource creation. Do not expose exception messages,
  SQL, tokens, filesystem paths, or downstream response bodies in 5xx output.
- Validate every request body with `@Valid`. Validate query/path parameters at
  the controller boundary. Handle both `MethodArgumentNotValidException` and
  `HandlerMethodValidationException` centrally.
- All collection endpoints must be bounded. Page is zero-based and nonnegative;
  size/limit must be positive and capped. Default size is 20, admin default is
  50, and the normal hard cap is 100. A documented selection/catalog use case
  may allow up to the configured global cap of 500. Avoid APIs that return an
  entire growing table.
- Use enums for closed request values such as status, role, mode, type, and
  sort field. Convert to a database string only at the persistence boundary.
- Use request DTOs for commands with more than one business parameter. Avoid
  mutation commands expressed as loose query parameters.
- Keep OpenAPI annotations aligned with actual security, status codes, and
  validation. Changing an endpoint requires checking known frontend/admin/job
  consumers before changing its wire contract.

## Authentication and authorization

- Default deny. Public routes must be explicitly listed by HTTP method and
  exact route family; never make a broad mutable route family `permitAll`.
- Every endpoint that accepts an object ID must perform object-level access
  checks in the service before reading or mutating it. Passing `userId` into a
  controller and then ignoring it is a security defect.
- Admin endpoints need both the admin request rule and, where supported, the
  resource/action permission check. Partner endpoints need organization and
  resource-scope checks in the service.
- Internal callbacks must fail closed. A missing server token, missing request
  token, or mismatched token is never accepted. Compare secrets in constant
  time. Internal endpoints are authenticated by a dedicated filter and role,
  not repeated controller methods.
- A user-scoped update/delete statement must include the user/tenant owner in
  its predicate, and the service must verify that exactly one expected row was
  affected.
- Keep CORS origins in typed external configuration. Do not use wildcard
  origins with credentials. Expose only health/info publicly; metrics and other
  actuator endpoints require admin/operations authorization.
- Never commit real secrets. Production secrets have no usable fallback.
  Logs must not contain access/refresh tokens, passwords, API keys, complete
  third-party payloads, or personal data not required for diagnosis.

## MyBatis and transaction rules

- Use `#{value}` prepared parameters. `${value}` is forbidden for user or
  request data. If SQL metadata must be dynamic, map a closed enum to a
  hard-coded whitelist before it reaches the mapper.
- Prefer explicit column lists and reusable `<sql>` fragments over `SELECT *`.
  Use explicit `resultMap`s for nontrivial entities and DTO projections.
- Mapper parameters must be named with `@Param`; XML identifiers must match.
  SQL names use `snake_case`; Java names use `camelCase`.
- Put transaction boundaries on public service use cases. Use
  `@Transactional(readOnly = true)` for multi-query reads where consistency
  matters, and a normal transaction for multi-write commands. Do not rely on
  self-invocation of a transactional method.
- Avoid N+1 calls. Collect identifiers and batch-load related data. Use batch
  inserts/updates for bulk work, and chunk large jobs.
- Add/verify indexes for filters, joins, ownership predicates, job polling, and
  ordering used by a new or changed query. Inspect the PostgreSQL plan for
  high-volume queries. Keep large text/JSON/blob columns out of list projections.
- Every schema or seed change is an append-only Flyway migration. Never edit a
  migration that may already have run.

## Hard-coded values and configuration

Choose the owner by meaning, not merely because a value is a string or number:

- A closed domain vocabulary is an enum (status, role, type, transition).
- A protocol/schema invariant is a named code constant (header name, route
  segment, fixed algorithm limit).
- Deployment, connection, timeout, pool, CORS, and secret settings use validated
  `@ConfigurationProperties` backed by YAML/environment variables.
- A non-secret business policy that operators must change without deployment
  belongs in the `config` table (quota, threshold, reward, feature switch,
  scoring weight). Read it through a typed service with validation, a documented
  safe fallback or fail-fast policy, caching, and cache eviction after admin
  changes.
- Database integrity rules belong in constraints/indexes and an enum/check
  strategy compatible with migrations.

Do not put secrets or per-request data in the `config` table. Do not query the
table repeatedly inside a loop. Each DB-config key must document its unit,
allowed range, default/failure behavior, owner, and public/private visibility.

Current typed business keys (all registered in `BusinessConfigKey`, read through
`BusinessConfigService`, cached under `businessConfig` and evicted on admin edit):

| Label | Key | Unit/range | Failure behavior | Visibility |
|---|---|---|---|---|
| `TRIP` | `FREE_TRIP_QUOTA` | LEGACY-STAR-QUOTA: unused since V262 (trip creation costs credits); delete with the admin quota endpoint | safe fallback 3 | private/admin |
| `PLACE_REVIEW` | `DEFAULT_REFRESH_MAX_REVIEWS` | count, 1..200 | safe fallback 200 | private/admin |
| `PLACE_SEARCH` | `ELSEWHERE_ENABLED` / `ELSEWHERE_MAX` | boolean / count, 1..30 | safe fallback true / 10 | private/admin |
| `TRIP_MEMORY` | `FREE_TRIP_MEMORY_LIMIT` | count, 1..1000 | safe fallback 50 | private/admin |
| `TRIP_WEATHER` | `ALERT_ENABLED` | boolean (beta users always get it); sends the evening-before `TRIP_DAY_WEATHER_ALERT` to every trip member | safe fallback false | private/admin |
| `TRIP_WEATHER` | `ALERT_RAIN_PERCENT` | percent, 50..100; rain chance that triggers the alert without an advisory | safe fallback 80 | private/admin |
| `EXPENSE` | `PAYMENT_REMINDER_COOLDOWN_MINUTES` | minutes, 1..10080 | safe fallback 1440 | private/admin |
| `AI_CREDIT` | `MARKUP` / `USD_PER_CREDIT` | multiplier 1..10 / USD per credit 0.00001..1 | safe fallback 1.2 / 0.001 | private/admin |
| `AI_CREDIT` | `FREE_MONTHLY_CREDITS` | credits per month, 0..1000000 (PRO plans: `subscription_plans.ai_credits_per_refill`) | safe fallback 1000 | users via `GET /v1/api/me/ai-credits` (`planCredits`) |
| `AI_CREDIT` | `NEGATIVE_FLOOR` | credits below zero a running task may reach, 0..1000 | safe fallback 5 | users via `/me/ai-credits` (`negativeFloor`) |
| `AI_CREDIT` | `MIN_BALANCE_ASSISTANT` / `_AI_TRIP` / `_RECEIPT_SCAN` / `_SOCIAL_LOCATION` | credits needed to start, 0..100000 | safe fallback 20 / 100 / 10 / 10 | users via `/me/ai-credits` (`minBalance`) |
| `AI_CREDIT` | `FALLBACK_CREDITS_PER_CALL` | credits for a model call with no AI_PRICING row, 0..100000 | safe fallback 20 | private/admin |
| `AI_CREDIT` | `TRIP_CREATION_COST` | credits per trip creation (manual, clone, AI draft, Pengoo card), 0..100000 | safe fallback 50 | users via `/me/ai-credits` and `/me/stars` (`tripCreationCost`) |
| `AI_CREDIT` | `STAR_EXCHANGE_ENABLED` | boolean; Stars may be exchanged into credits | safe fallback true | users via `/me/stars` (`exchange.enabled`) |
| `AI_CREDIT` | `STAR_EXCHANGE_RATE` | credits per Star, 1..1000 | safe fallback 10 | users via `/me/stars` (`exchange.rate`) |
| `AI_CREDIT` | `STAR_EXCHANGE_MONTHLY_CAP` | credits from Stars per credit-wallet period, 0..100000 | safe fallback 500 | users via `/me/stars` (`exchange.monthlyCap`) |
| `MAP_SEARCH` | `RATE_LIMIT_ENABLED` / `EXEMPT_ADMINS` | boolean | safe fallback true / true | private/admin |
| `MAP_SEARCH` | `AUTOCOMPLETE_PER_MINUTE` / `PLACE_DETAIL_PER_MINUTE` | count/minute/user, 1..600 | safe fallback 30 / 30 | private/admin |
| `MAP_SEARCH` | `AUTOCOMPLETE_PER_DAY` / `PLACE_DETAIL_PER_DAY` | count/day/user, 1..100000 | safe fallback 300 / 200 | private/admin |
| `MAP_SEARCH` | `GEOCODE_PER_MINUTE` / `ROUTE_PER_MINUTE` | count/minute/user, 1..600 | safe fallback 20 / 30 | private/admin |
| `MAP_SEARCH` | `GEOCODE_PER_DAY` / `ROUTE_PER_DAY` | count/day/user, 1..100000 | safe fallback 200 / 300 | private/admin |
| `MAP_SEARCH` | `USAGE_RETENTION_DAYS` | days, 7..730 | safe fallback 90 | private/admin |
| `MAP_PROVIDER` | `USER_PROVIDER` / `BETA_PROVIDER` | `GOONG` or `MAPBOX` | safe fallback GOONG / MAPBOX | private/admin |
| `MAP_PROVIDER` | `FALLBACK_TO_GOONG` | boolean | safe fallback true | private/admin |
| `MAP_BUDGET` | `ENABLED` | boolean | safe fallback true | private/admin |
| `MAP_BUDGET` | `SEARCH_MONTHLY_CAP` / `GEOCODE_MONTHLY_CAP` / `DIRECTIONS_MONTHLY_CAP` | paid Mapbox calls/month, 0..100000000 | safe fallback 50000 / 100000 / 100000 | private/admin |
| `MAP_BUDGET` | `DAILY_CAP_PERCENT` / `WARN_PERCENT` | percent, 1..100 | safe fallback 10 / 80 | private/admin |
| `MODERATION` | `TEXT_FILTER_ENABLED` | boolean | safe fallback true | private/admin |
| `MODERATION` | `AI_TEXT_ENABLED` | boolean | safe fallback false | private/admin |
| `MODERATION` | `AI_TEXT_MAX_LENGTH` | characters, 200..20000 | safe fallback 4000 | private/admin |
| `MODERATION` | `IMAGE_MODERATION_ENABLED` | boolean | safe fallback false | private/admin |
| `MODERATION` | `IMAGE_THRESHOLD_*` | confidence, 0..1 | safe fallback per group | private/admin |
| `MODERATION` | `STRICTNESS_PUBLIC/GROUP/DIRECT/PRIVATE` | enum `ModerationStrictness` | safe fallback per tier | private/admin |
| `MODERATION` | `POLICY_VERSION` | text | safe fallback `1.0.0` | private/admin |
| `CHECKIN` | `CHECKIN_ENABLED` | boolean | safe fallback true | public |
| `CHECKIN` | `VERIFY_RADIUS_METERS` | metres, 20..5000 | safe fallback 200 | public |
| `CHECKIN` | `EVENT_VERIFY_RADIUS_METERS` | metres, 20..5000; radius around the venue of a local event with no catalogue place inside which a check-in made while the event is on counts as at it (V285) | safe fallback 150 | private/admin |
| `CHECKIN` | `MAX_ACCURACY_METERS` | metres, 5..2000 | safe fallback 100 | public |
| `CHECKIN` | `MAX_PHOTOS` | count, 1..20 | safe fallback 6 | public |
| `CHECKIN` | `MAX_CAPTION_LENGTH` | characters, 100..5000 | safe fallback 2000 | public |
| `CHECKIN` | `VIDEO_ENABLED` | boolean (beta users always on); gates `POST /v1/api/checkins/videos` and a VIDEO item | safe fallback false | users via `GET /v1/api/checkins/context` (`videoAllowed`) |
| `CHECKIN` | `MAX_VIDEO_SECONDS` | seconds, 5..300 | safe fallback 60 | users via check-in context (`maxVideoSeconds`) |
| `CHECKIN` | `MUSIC_ENABLED` | boolean (beta users always on); gates adding a `musicTrackId` (V282) | safe fallback false | users via check-in context (`musicAllowed`) |
| `CHECKIN` | `LOCATION_KEY_PRECISION` | decimal places, 2..6 | safe fallback 4 | private/admin |
| `CHECKIN` | `GALLERY_ALLOWED_FOR_FREE` | boolean | safe fallback true | public |
| `CHECKIN` | `GUIDE_SCREEN_ENABLED` / `GUIDE_SCREEN_MAX_VIEWS` | boolean / count 0..50 | safe fallback true / 3 | public |
| `CHECKIN` | `REWARD_*` | points, multipliers, daily cap | safe fallback per key | public except cap |
| `CHECKIN` | `CLUSTER_MIN_USERS` / `CLUSTER_MIN_CHECKINS` | count | safe fallback 3 / 5 | private/admin |
| `PASSPORT` | `PASSPORT_ENABLED` | boolean | safe fallback true | public |
| `PASSPORT` | `PROVINCE_COVERAGE_THRESHOLD` | percent, 0..100 | safe fallback 95 | private/admin |
| `CHECKIN` | `REWARD_WARD_VERIFIED_MULTIPLIER` | multiplier, 0..5 | safe fallback 0.7 | public |
| `PASSPORT` | `TOTAL_PROVINCES` | count, 1..200 | safe fallback 34 | public |
| `POINTS` | `EXPIRY_DAYS` | days, 0..3650 (0 = never) | safe fallback 0 | public |
| `GUIDE` | `GUIDE_ENABLED` | boolean | safe fallback false | public |
| `GUIDE` | `PLATFORM_FEE_PERCENT` | percent, 0..100 | safe fallback 20 | public |
| `GUIDE` | `FEE_RULE_VERSION` | text | safe fallback `2026.08` | private/admin |
| `GUIDE` | `RESPONSE_DEADLINE_HOURS` | hours, 1..720 | safe fallback 48 | public |
| `GUIDE` | `PAYOUT_HOLD_DAYS` | days, 0..90 | safe fallback 7 | private/admin |
| `GUIDE` | `MIN_REVIEWS_TO_SHOW_RATING` | count, 1..100 | safe fallback 3 | public |
| `GUIDE` | `FREE_SERVICE_LIMIT` | count, 1..100 | safe fallback 3 | public |
| `GUIDE` | `PREMIUM_MONTHLY_PRICE_VND` / `PREMIUM_YEARLY_PRICE_VND` | VND | safe fallback per slide pricing | public |
| `TRACKER` | `ENABLED` | boolean (beta users always on) | safe fallback false | members via `GET /v1/api/trips/{tripId}/track/context` (`enabled`) |
| `TRACKER` | `WINDOW_PAD_DAYS` | days, 0..3 | safe fallback 1 | members via track context (`window.padDays`) |
| `TRACKER` | `MAX_ACCURACY_HIGH_M` / `_BALANCED_M` / `_SAVER_M` | metres, 5..500 | safe fallback 30 / 60 / 150 | members via track context |
| `TRACKER` | `DISTANCE_FILTER_HIGH_M` / `_BALANCED_M` / `_SAVER_M` | metres, 1..500 | safe fallback 10 / 25 / 100 | members via track context |
| `TRACKER` | `MAX_SPEED_KMH` | km/h, 50..1200 | safe fallback 300 | members via track context |
| `TRACKER` | `GAP_MINUTES` / `GAP_DISTANCE_M` | minutes 1..240 / metres 100..100000 | safe fallback 10 / 1000 | private/admin |
| `TRACKER` | `FLIGHT_MIN_KM` / `FLIGHT_MIN_KMH` | km 50..2000 / km/h 100..1200 | safe fallback 200 / 300 | private/admin |
| `TRACKER` | `STATIONARY_RADIUS_M` / `STATIONARY_MINUTES` | metres 5..200 / minutes 1..30 | safe fallback 30 / 3 | members via track context |
| `TRACKER` | `ARRIVAL_RADIUS_M` / `ARRIVAL_MINUTES` | metres 20..500 / minutes 1..60 | safe fallback 100 / 3 | members via track context |
| `TRACKER` | `NUDGE_COOLDOWN_MINUTES` / `NUDGE_DAILY_MAX` | minutes 0..240 / count 0..50 | safe fallback 20 / 6 | members via track context |
| `TRACKER` | `QUIET_START_HOUR` / `QUIET_END_HOUR` | hour of trip local time, 0..23 | safe fallback 22 / 7 | members via track context |
| `TRACKER` | `LOW_BATTERY_PCT` / `STOP_BATTERY_PCT` | percent 5..50 / 1..30 | safe fallback 15 / 5 | members via track context |
| `TRACKER` | `RAW_RETENTION_DAYS` | days after the trip's last day, 7..365 | safe fallback 90 | private/admin |
| `TRACKER` | `CHUNK_MAX_POINTS` | points per upload, 50..500 | safe fallback 500 | members via track context |
| `TRACKER` | `OFFPLAN_RADIUS_M` / `OFFPLAN_MIN_MINUTES` | metres 20..500 / minutes 1..120 | safe fallback 100 / 10 | members via track context (`offPlanRadiusM`, `offPlanMinMinutes`) |
| `TRACKER` | `OFFPLAN_NAME_RADIUS_M` | metres, 10..500 | safe fallback 60 | members via track context (`offPlanNameRadiusM`) |
| `EVENT` | `ENABLED` | boolean (beta users always see the tab; the app checks beta itself); hides/shows the Home "Sự kiện" tab only — `/v1/api/public/events/**` never checks it | safe fallback false | public (listed in `PUBLIC_CONFIG` by V210) |
| `EVENT` | `REMINDERS_ENABLED` | boolean; the job that reminds people of the events they saved (V285) | safe fallback true | private/admin |
| `EVENT` | `AUTO_TRANSLATE_ENABLED` | boolean; machine translation of a published event's summary, description and notes into the app languages (V285) | safe fallback true | private/admin |
| `MEETUP` | `ENABLED` | boolean (beta users always on); gates every `/v1/api/meetups/**` endpoint | safe fallback false | users via `GET /v1/api/meetups/context` (`enabled`) |
| `MEETUP` | `MAX_ACTIVE_PER_HOST` | count of PENDING_REVIEW/APPROVED meetups not yet past, 1..10 | safe fallback 2 | users via meetup context (`cannotHostReason = HOST_LIMIT`) |
| `MEETUP` | `MAX_SUBMISSIONS_PER_DAY` | submissions per host in the last 24 h, 1..50 | safe fallback 5 | private/admin |
| `MEETUP` | `MAX_DAYS_AHEAD` | days after today (Vietnam time), 0..14 | safe fallback 3 | users via meetup context (`maxDaysAhead`) |
| `MEETUP` | `EXACT_MIN_LEAD_MINUTES` | minutes, 0..720 | safe fallback 30 | users via meetup context (`exactMinLeadMinutes`) |
| `MEETUP` | `EXACT_VISIBLE_HOURS` | hours after the start, capped at local midnight, 1..12 | safe fallback 3 | users via meetup context (`exactVisibleHours`) |
| `MEETUP` | `MAX_OPEN_REQUESTS_PER_USER` | pending join requests on live meetups, 1..50 | safe fallback 5 | private/admin |
| `MEETUP` | `MAX_RESUBMITS` | resubmits after a rejection, 0..10 | safe fallback 2 | host via meetup detail (`resubmitsLeft`) |
| `PLACE_EDIT` | `ENABLED` | boolean (beta users always on); gates `POST /v1/api/places/{placeId}/edit-suggestions` | safe fallback false | public (`PUBLIC_CONFIG`, V288) and users via edit-suggestion context (`enabled`) |
| `PLACE_EDIT` | `MIN_ACCOUNT_AGE_HOURS` | hours, 0..720 | safe fallback 24 | users via context (`reason = ACCOUNT_TOO_NEW`) |
| `PLACE_EDIT` | `MAX_SUBMISSIONS_PER_DAY` / `MAX_PENDING_PER_USER` | suggestions per user in 24 h 1..100 / pending at once 1..500 | safe fallback 10 / 30 | private/admin |
| `PLACE_EDIT` | `MAX_PIN_MOVE_METERS` | metres a user may move a pin, 10..50000 | safe fallback 2000 | public (`PUBLIC_CONFIG`, V288) |
| `PLACE_EDIT` | `REWARD_STARS` / `MAX_REWARDS_PER_DAY` | Stars per approved suggestion 0..100 / rewarded suggestions per user in 24 h 0..100 | safe fallback 1 / 5 | private/admin |
| `PLACE_EDIT` | `WEBSITE_BLOCKED_HOSTS` | comma-separated hosts refused as a suggested website | safe fallback `bit.ly,tinyurl.com,t.co,goo.gl` | private/admin |
| `ASSISTANT` | `ENABLED` | boolean (beta users always on); gates asking Pengoo | safe fallback false | users via `GET /v1/api/assistant/me` (`available`) |
| `ASSISTANT` | `LOG_PAYLOADS` | boolean; prompts/answers kept in `ai_api_calls` (beta only) | safe fallback true | private/admin |
| `ASSISTANT` | `HISTORY_MESSAGES` | chat lines sent with a question, 0..50 | safe fallback 20 | private/admin |
| `ASSISTANT` | `WELCOME_ENABLED` | boolean | safe fallback true | private/admin |
| `ASSISTANT` | `FREE_MAX_TOOL_CALLS` / `PRO_MAX_TOOL_CALLS` | tool calls per question, 1..30 / 1..50 | safe fallback 6 / 20 | private/admin |
| `ASSISTANT` | `TURN_TIMEOUT_SECONDS` | seconds, 30..900 | safe fallback 300 | private/admin |
| `ASSISTANT` | `WEB_SEARCH_ENABLED` | boolean; one web search per answer when the catalogue has nothing (worker also needs a provider) | safe fallback true | private/admin |
| `ASSISTANT` | `MAP_SEARCH_ENABLED` | boolean; `search_places` also asks the map provider (Goong/Mapbox, `PlaceLookupRouter`) when fewer than 3 catalogue places match the name | safe fallback true | private/admin |
| `SOCIAL` | `FOLLOWER_FANOUT_ENABLED` | boolean (beta followers always told) | safe fallback false | users via `GET /v1/api/following/moments` (`enabled`) |
| `SOCIAL` | `FOLLOWER_PUSH_COOLDOWN_MINUTES` / `FOLLOWER_PUSH_DAILY_MAX` | minutes per author 0..1440 / pushes per recipient per 24 h 0..100 | safe fallback 30 / 10 | private/admin |
| `SOCIAL` | `FOLLOWER_FANOUT_BATCH_SIZE` / `FOLLOWER_FANOUT_PAUSE_MS` | followers per chunk 50..500 / ms between chunks 0..5000 | safe fallback 500 / 100 | private/admin |
| `SOCIAL` | `MOMENTS_WINDOW_HOURS` | hours, 1..336 | safe fallback 72 | private/admin |
| `FEED` | `IMPRESSION_RETENTION_DAYS` | days `feed_impressions` rows are kept, 7..365 | safe fallback 90 | private/admin |
| `FEED` | `HIDE_AUTHOR_DAYS` | days "hide posts from this person" lasts, 1..365 | safe fallback 30 | users via `POST /v1/api/feed/feedback` (`expiresAt`) |
| `FEED` | `RANKING_ENABLED` | boolean (beta users always on) | safe fallback false | users via `GET /v1/api/feed/for-you` (`enabled`) |
| `FEED` | `RANKING_PERCENT` | share of signed-in users on the ranked feed, 0..100 | safe fallback 0 | users via `GET /v1/api/feed/for-you` (`enabled`) |
| `FEED` | `EVENTS_ENABLED` | write `feed_events` (V267) | safe fallback `true` | private/admin |
| `FEED` | `EVENTS_RETENTION_DAYS` | days of `feed_events` daily partitions kept, 7..365 | safe fallback 30 | private/admin |
| `FEED` | `CANDIDATES_BACKEND` | `SQL` or `LUCENE` (feed Lucene index, falls back to SQL) | safe fallback `SQL` | private/admin |
| `FEED` | `RANKING_WEIGHTS` | JSON overriding `feed/feed-ranking-defaults.json` key by key | safe fallback `{}` (defaults) | private/admin |
| `POST` | `ENABLED` | boolean (beta users always on); posts in Feed/Place Detail/profile lists, `GET /posts/{id}` never gated | safe fallback false | users via `GET /v1/api/posts/policy` (`enabled`) |
| `POST` | `USER_WRITE_ENABLED` | boolean (beta users always on); gates creating/editing/publishing posts in the app, never the console | safe fallback false | users via post policy (`canWrite`) |
| `IMAGE_COMPRESSION` | `AUTO_ENABLED` | boolean; compress each upload in the background, overwriting the same key | safe fallback false | private/admin |
| `IMAGE_COMPRESSION` | `QUALITY_FREE` / `QUALITY_PRO` | WebP quality, 30..95; FREE only for images of free-plan users, PRO for everything else | safe fallback 70 / 82 | private/admin |
| `IMAGE_COMPRESSION` | `MAX_LONG_EDGE_FREE` / `MAX_LONG_EDGE_PRO` | pixels, 640..4096, never upscales | safe fallback 1600 / 2048 | private/admin |
| `IMAGE_COMPRESSION` | `MIN_SAVINGS_PERCENT` | percent, 0..90; a file is overwritten only when the WebP is at least this much smaller | safe fallback 10 | private/admin |
| `LEGAL` | `TERMS_VERSION` / `PRIVACY_VERSION` | text; the version of the bundled Terms / Privacy Policy. A user whose accepted version differs gets the consent screen again (`GET /v1/api/consents/me` → `needsAcceptance`); raise only with an app release carrying the new text | safe fallback `2.0` | users via `GET /v1/api/consents/me` |
| `POST` | `MAX_BODY_LENGTH` / `MAX_MEDIA` / `MAX_VIDEO_SECONDS` / `MAX_PLACES` | characters 1000..100000 / count 1..500 (a trip story borrows every check-in photo as a shared row, so the ceiling is generous) / seconds 5..900 / count 1..100 | safe fallback 30000 / 200 / 300 / 30 | users via post policy |

## Build note

`.mvn/jvm.config` carries the `--add-opens jdk.compiler/...` flags Lombok needs when
maven-compiler-plugin runs javac in-process on JDK 16+. Without it every Lombok-generated
getter and logger is reported as a missing symbol. `pom.xml` also pins the Lombok version
inside `annotationProcessorPaths`, which does not read `dependencyManagement`.

## Naming and code quality

- Classes are nouns; use-case methods are verbs. Prefer descriptive names such
  as `organizationId`, `notificationId`, and `expectedVersion` over `id`, `s`,
  `r`, and `a` in nontrivial code.
- Use `Id` consistently in Java names and `id` on the wire according to the
  configured JSON naming strategy. Boolean names start with `is`, `has`, or
  `can` and do not encode negation twice.
- Remove unused parameters, annotations, loggers, and comments that restate the
  code. Comments explain policy or an unusual constraint.
- Do not catch `Exception` in controllers. Let the central handler sanitize the
  response; catch only when the use case has a real recovery action.
- Prefer immutable request/response models where practical. Never mutate an
  input DTO merely to merge a header; pass the resolved value into the service
  or expose an explicit normalization method.

## Tests and completion checklist

Before editing:

1. Identify route, controller, service, repository/mapper, migration, callers,
   and existing tests.
2. Classify each changed value as enum, code constant, deployment property, or
   live business config.
3. Record the authorization rule and maximum resource consumption.

During editing:

1. Keep the controller thin and service transaction/authorization explicit.
2. Preserve API compatibility unless the task approves a migration.
3. Add focused unit tests and security/MockMvc tests for success, validation,
   unauthenticated, unauthorized, not-found, conflict, and ownership cases.
4. Add mapper XML tests for changed dynamic SQL and a Flyway migration for
   schema/config seeds.

Before handoff:

1. Search all controller mappings and confirm each is intentionally public,
   authenticated, admin, partner, or internal.
2. Search changed mapper XML for `${...}`, `SELECT *`, unbounded reads, and
   ownerless update/delete statements.
3. Run the full Maven test suite. Report any test that cannot run and why.
4. Update this file when architecture, ownership, security conventions, or the
   supported stack changes.
5. Update `../.agents/docs/` per `../.agents/docs/MAINTENANCE.md` when the change
   touches an endpoint, an access rule, an enum, a `BusinessConfigKey`, an error code,
   a migration, a scheduled job, an external integration, or any business rule,
   threshold, formula or state transition. Record out-of-scope findings in
   `../.agents/docs/ISSUES.md`.

## Primary references

- Spring Boot external configuration:
  https://docs.spring.io/spring-boot/reference/features/external-config.html
- Spring MVC validation and REST error responses:
  https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html
  and https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- Spring Security request authorization, CORS, and CSRF:
  https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html,
  https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html,
  and https://docs.spring.io/spring-security/reference/features/exploits/csrf.html
- Spring transaction management:
  https://docs.spring.io/spring-framework/reference/data-access/transaction.html
- MyBatis mapper XML and safe parameter binding:
  https://mybatis.org/mybatis-3/sqlmap-xml.html
- MyBatis Spring Boot starter compatibility:
  https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/
- OWASP API Security Top 10:
  https://owasp.org/API-Security/
