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
  services where a repository already exists for the aggregate.
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
| `PLACE_REVIEW` | `DEFAULT_REFRESH_MAX_REVIEWS` | count, 1..200 | safe fallback 200 | private/admin |
| `TRIP_MEMORY` | `FREE_TRIP_MEMORY_LIMIT` | count, 1..1000 | safe fallback 50 | private/admin |
| `MODERATION` | `TEXT_FILTER_ENABLED` | boolean | safe fallback true | private/admin |
| `MODERATION` | `AI_TEXT_ENABLED` | boolean | safe fallback false | private/admin |
| `MODERATION` | `AI_TEXT_MAX_LENGTH` | characters, 200..20000 | safe fallback 4000 | private/admin |
| `MODERATION` | `IMAGE_MODERATION_ENABLED` | boolean | safe fallback false | private/admin |
| `MODERATION` | `IMAGE_THRESHOLD_*` | confidence, 0..1 | safe fallback per group | private/admin |
| `MODERATION` | `STRICTNESS_PUBLIC/GROUP/DIRECT/PRIVATE` | enum `ModerationStrictness` | safe fallback per tier | private/admin |
| `MODERATION` | `POLICY_VERSION` | text | safe fallback `1.0.0` | private/admin |
| `CHECKIN` | `CHECKIN_ENABLED` | boolean | safe fallback true | public |
| `CHECKIN` | `VERIFY_RADIUS_METERS` | metres, 20..5000 | safe fallback 200 | public |
| `CHECKIN` | `MAX_ACCURACY_METERS` | metres, 5..2000 | safe fallback 100 | public |
| `CHECKIN` | `MAX_PHOTOS` | count, 1..20 | safe fallback 6 | public |
| `CHECKIN` | `MAX_CAPTION_LENGTH` | characters, 100..5000 | safe fallback 2000 | public |
| `CHECKIN` | `LOCATION_KEY_PRECISION` | decimal places, 2..6 | safe fallback 4 | private/admin |
| `CHECKIN` | `GALLERY_ALLOWED_FOR_FREE` | boolean | safe fallback true | public |
| `CHECKIN` | `GUIDE_SCREEN_ENABLED` / `GUIDE_SCREEN_MAX_VIEWS` | boolean / count 0..50 | safe fallback true / 3 | public |
| `CHECKIN` | `REWARD_*` | points, multipliers, daily cap | safe fallback per key | public except cap |
| `CHECKIN` | `CLUSTER_MIN_USERS` / `CLUSTER_MIN_CHECKINS` | count | safe fallback 3 / 5 | private/admin |
| `PASSPORT` | `PASSPORT_ENABLED` | boolean | safe fallback true | public |
| `PASSPORT` | `PROVINCE_COVERAGE_THRESHOLD` | percent, 0..100 | safe fallback 95 | private/admin |
| `PASSPORT` | `TOTAL_PROVINCES` | count, 1..200 | safe fallback 63 | public |
| `POINTS` | `EXPIRY_DAYS` | days, 0..3650 (0 = never) | safe fallback 0 | public |
| `GUIDE` | `GUIDE_ENABLED` | boolean | safe fallback false | public |
| `GUIDE` | `PLATFORM_FEE_PERCENT` | percent, 0..100 | safe fallback 20 | public |
| `GUIDE` | `FEE_RULE_VERSION` | text | safe fallback `2026.08` | private/admin |
| `GUIDE` | `RESPONSE_DEADLINE_HOURS` | hours, 1..720 | safe fallback 48 | public |
| `GUIDE` | `PAYOUT_HOLD_DAYS` | days, 0..90 | safe fallback 7 | private/admin |
| `GUIDE` | `MIN_REVIEWS_TO_SHOW_RATING` | count, 1..100 | safe fallback 3 | public |
| `GUIDE` | `FREE_SERVICE_LIMIT` | count, 1..100 | safe fallback 3 | public |
| `GUIDE` | `PREMIUM_MONTHLY_PRICE_VND` / `PREMIUM_YEARLY_PRICE_VND` | VND | safe fallback per slide pricing | public |

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
