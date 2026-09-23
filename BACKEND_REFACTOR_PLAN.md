> SUPERSEDED 2026-09-18 by [BACKEND_REFACTOR_PLAN_2026-09.md](BACKEND_REFACTOR_PLAN_2026-09.md).
> Phase 0 below is complete and verified. Several unchecked boxes are stale: the @CurrentUser
> resolver and all six Phase 2 typed @ConfigurationProperties groups already ship. See
> docs/audit-2026-09/01-architecture.md for the item-by-item reconciliation.

# GoRoute backend refactor plan

## Baseline audit (2026-08-23)

Scope inspected: all 66 controller classes and all 399 mapped controller
methods, plus the security chain, authentication filters, exception handling,
application configuration, config-table implementation, affected services,
repositories, mapper XML, Flyway migrations, and tests.

The API surface is feature-rich, but controller style and enforcement are
inconsistent. The highest-risk findings are:

1. Internal callback endpoints are globally permitted and their controller
   token checks accept requests when the configured token is blank.
2. Notification read/delete operations are not scoped by the authenticated
   user; notification push endpoints named `admin` only require authentication.
3. Trip activities, check-ins, and expense list reads accept a user identity at
   the HTTP boundary but do not pass it to the service access check.
4. The global exception handler returns HTTP 200 for validation, authorization,
   not-found, conflict, and unexpected server errors.
5. Broad security matchers make every location-image route and most
   place-review maintenance routes public. Actuator metrics are public and
   health details are always exposed.
6. Pagination/limit validation is inconsistent; several reads are unbounded,
   including the public `GET /v1/api/places` table read.
7. Several controllers call mappers/repositories, implement transactions or
   upload workflows, return entities/raw generic types, or are minified into
   one-line Java files.
8. Domain states are often accepted as arbitrary strings despite existing
   enums. Numbers such as page sizes, review limits, upload limits, and worker
   timeouts are repeated without a clear configuration owner.
9. Production-capable configuration contains usable secret/password fallbacks
   and a manually pinned Tomcat major version outside Spring Boot dependency
   management.
10. MyBatis uses prepared `#{...}` parameters (good), but many mappers use
    `SELECT *`; high-volume projections and indexes need gradual tightening.

## Implemented in the first refactor pass

- Central fail-closed authentication and `ROLE_INTERNAL` authorization for all
  internal callback routes; controller-local token checks were removed and
  outbound workers now reuse the same typed token source.
- City Story create/delete now use the existing `location-images` create/delete
  permissions, closing the path where any signed-in user could mutate a story
  for an arbitrary location.
- Authentication and access-denied failures at the Spring Security filter chain
  now use the same JSON `BaseResponse` contract with real 401/403 statuses.
- Admin media import from a URL now enforces HTTPS/public targets on every
  redirect, uses typed timeout/byte/download limits, streams instead of
  `readAllBytes()`, and never forwards the caller's bearer token to the remote
  origin.
- Object-level access was restored for activity, check-in, expense,
  notification, and device operations. Notification/device mutations now use
  owner-scoped SQL and affected-row checks.
- Validation and exception responses now use real HTTP 4xx/5xx statuses;
  unexpected failures are logged server-side and return a sanitized message.
- Global API `page`/`size`/`limit` bounds were added. Public place listing and
  notification listing now paginate in SQL rather than loading a growing table
  into memory. Supporting notification indexes were added in `V114`.
- Device and file-upload workflows were moved out of controllers. Uploads now
  have typed limits, whole-batch validation, content signature checks,
  content-derived extensions, bounded compression output, and sanitized errors.
- Mutable place-review refresh count moved to the existing `config` table via
  `V113`, a typed key, range-checked cached reader, and eviction after admin
  config changes. The value is now actually sent to the scrape worker.
- Hotel inventory upserts now persist `priceOverride`, `minStay`,
  `closedToArrival` and `closedToDeparture` as well as stock fields; a mapper
  regression test prevents a UI/API field from silently being discarded.
- CORS, JWT, internal tokens, API limits, worker/downstream timeouts, upload
  limits, and image-compression settings have typed configuration owners.
- The incompatible direct Tomcat 11 override and redundant validation API
  override were removed so Spring Boot manages compatible versions.

## Delivery phases

### Phase 0 — security and correctness (this change)

- [x] Make internal callback authentication centralized, constant-time, and
  fail-closed; require an internal authority for `/v1/api/internal/**`.
- [x] Correct public/admin matcher scope for location images, place-review
  maintenance, notification admin pushes, and actuator endpoints.
- [x] Scope notification mutation by user and enforce affected-row checks.
- [x] Pass the authenticated user into trip activity/check-in/expense reads and
  enforce object-level access in services.
- [x] Return correct HTTP status codes from the central exception handler and
  sanitize unexpected errors.
- [x] Add global bounded pagination validation and focused security/access tests.

### Phase 1 — controller boundary cleanup (this change where low-risk)

- [ ] Introduce one authenticated-user resolver/helper and remove duplicated
  `UUID.fromString(authentication.getName())` code.
- [ ] Replace raw response generics and format minified controllers.
- [ ] Move mapper/repository and upload workflow code out of controllers.
  Device and file-upload workflows are complete; admin management/media/plan
  and several partner upload controllers remain.
- [ ] Replace status/role/mode strings with existing enums without changing the
  serialized values.
- [ ] Remove unused request identities and controller `try/catch Exception`.

### Phase 2 — typed configuration and business policy

- [ ] Add validated `@ConfigurationProperties` groups for CORS, internal auth,
  uploads, downstream HTTP timeouts, JWT, and worker/SSE settings.
- [x] Add a cached typed reader for non-secret `config` table policies, with
  range validation and cache eviction on admin updates.
- [x] Seed only live business policies (for example quotas/rewards/refresh
  thresholds) through append-only Flyway migrations. Keep protocol and
  operational values out of the table.
- [x] Remove usable database, JWT, and object-storage credential fallbacks from
  runtime application configuration.

### Phase 3 — persistence and performance

- [ ] Replace unbounded reads with page DTOs/cursors and total/count contracts
  only where clients need them.
- [ ] Replace `SELECT *` in high-volume paths with explicit projections and
  reusable column fragments.
- [ ] Audit query plans and indexes for every list/search/job polling endpoint.
- [ ] Batch-load related entities to remove N+1 mappings and add mapper XML
  contract tests for dynamic filters.
  Notification mapper contract coverage is complete; other high-volume mappers
  remain in the phase backlog.

### Phase 4 — API consistency and observability

- [ ] Consolidate duplicate device APIs and keep a documented compatibility
  route during client migration.
- [ ] Standardize create/update/delete status semantics and error metadata.
- [ ] Generate and diff an OpenAPI route inventory in CI so an endpoint cannot
  become public or disappear unnoticed.
- [ ] Add low-cardinality Micrometer observations for external calls and
  business jobs; keep user IDs out of metric tags.

### Phase 5 — dependency lifecycle

- [x] Remove direct servlet-container version overrides and let Spring Boot
  manage compatible dependencies.
- [ ] Upgrade from the end-of-support Spring Boot 3.4 line to a supported 3.5
  patch in a dedicated change, following every intermediate release note.
- [ ] Upgrade MyBatis Spring Boot starter within its Boot-3-compatible 3.0 line,
  then run mapper integration and full application tests.

## Blocked business decision

- Room-inventory daily overrides are now stored and returned, but public
  `findAvailability` currently derives restriction/price behavior from
  `rate_plan_daily_rates` only. Before combining the two sources, product must
  define precedence and composition for price, minimum stay, closed-to-arrival
  and closed-to-departure; changing quote/booking behavior without that rule is
  unsafe.

## Exit criteria

- Every one of the 399 endpoints has an explicit public/authenticated/admin/
  partner/internal classification.
- No user-owned mutation can succeed with another user's identifier.
- No public list can request unbounded rows or an excessive page size.
- All new closed domain values use enums; all mutable business policy values
  have a typed config-table definition; all operational settings are typed
  external configuration.
- Controller packages have no mapper/repository dependency, raw generics,
  minified source, or broad exception catch.
- Full Maven tests and security route tests pass, and changed SQL has mapper
  coverage.

## Verification baseline

The pre-change full suite had 92 tests with two errors unrelated to this
refactor: `MarketplacePublicAccessServiceImplTest` fails in existing activity
pricing behavior, and `TicketmasterApplicationTests` requires PostgreSQL on
`localhost:5432`. Focused tests added by this pass do not require a database;
the full suite must be rerun with the integration database available after the
existing marketplace test is corrected.

Final focused verification for this pass: 17 tests passed with no failures or
errors, covering internal authentication, pagination limits, error statuses,
notification/check-in ownership, upload validation, typed business config,
activity access, and Notification MyBatis XML.
