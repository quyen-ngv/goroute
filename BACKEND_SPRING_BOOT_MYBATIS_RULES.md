# GoRoute — Spring Boot + MyBatis engineering rules

This is the working contract for code under `goroute/`. It keeps the existing
Spring Boot and MyBatis architecture; it is not a migration guide to JPA.

## Ownership and package boundaries

- A controller owns HTTP concerns only: route, authentication context, request
  validation, status code and `BaseResponse<T>`. It must not call a mapper,
  open a URL, read a full upload, or contain business state transitions.
- A service owns a use case, authorization/ownership checks, transaction
  boundary, audit event and business invariants. Use `@Transactional(readOnly =
  true)` for reads and the narrowest practical write transaction for mutations.
- A repository owns persistence abstraction; a MyBatis mapper owns one table or
  aggregate's SQL and maps only persistence records. Do not expose mapper/entity
  objects from controllers.
- DTOs are the public contract. Keep request DTOs separate from response DTOs;
  never bind a request straight into an entity with sensitive/internal fields.

## Controller and API contract

- Every non-public endpoint is authenticated by default and has an explicit
  authorization rule. Use the existing `@adminAuthorization.can(...)` pattern
  for admin resources. Put object/tenant ownership checks in the service as
  well; a guessed UUID must never grant access.
- Use `@Valid` for bodies and `@Validated` plus `@Min`, `@Max`, `@NotBlank`,
  length and format constraints for path/query input. Collection endpoints must
  have a bounded page size.
- Return `201 Created` for successful creation, `204` only when the contract
  truly has no response body, and the shared `BaseResponse<T>` error/success
  shape everywhere else. Security failures must also produce the agreed JSON
  401/403 shape.
- Controllers must not expose exception messages, SQL details, credentials or
  stack traces. Log the exception with a correlation/audit identifier on the
  server and return a stable, localized safe error.
- A mutation that changes publication, permissions, account status or content
  visibility requires an explicit reason where the domain audits reasons, an
  optimistic version where supported, and a server-side state-transition check.

## Security and external I/O

- Enable and test method security. Apply the same rule in the service when the
  use case can be called other than through its controller.
- Treat URL-to-upload/import features as SSRF-sensitive: HTTPS-only, DNS/IP
  allow/deny validation after every redirect, block loopback/private/link-local
  targets, set connect/read timeouts, stream with a maximum byte limit and
  validate MIME/content before persistent storage.
- Secrets, tokens and infrastructure endpoints live only in environment-backed,
  typed configuration. They never belong in `app_config`, logs, response DTOs
  or admin URL parameters.
- Write tests for unauthenticated, unauthorized, cross-tenant/object, and
  permitted requests for every mutation endpoint.

## MyBatis rules

- Use `#{value}` bindings for all caller-controlled values. `${value}` is
  forbidden for values and is allowed for a dynamic identifier only after it is
  selected from a closed server-side allowlist.
- Keep SQL readable and named. Reuse `resultMap` and `<sql>` fragments where
  it removes real duplication; use `<where>`, `<set>`, `<choose>` and
  `<foreach>` instead of string concatenation for optional predicates/updates.
- Every list query has deterministic ordering, a bounded `limit`, validated
  offset/cursor input and indexes that match its filters/order. Avoid fetching
  an unbounded list merely to filter it in Java.
- Prevent N+1 access by projecting the required aggregate in one query or by a
  deliberate batch query. Add a query-count/performance test for hot paths.
- Keep mapper method parameters named with `@Param`, use explicit result maps
  for joins/JSON/date fields, and test null/empty list behavior.

## Enums, configuration and hard-coded values

| Kind of value | Correct owner | Examples |
| --- | --- | --- |
| Finite, code-defined domain state | Java enum/type | role, publication state, payment state, resource type |
| Runtime business policy an operator may change | `app_config` through a typed `BusinessConfigKey` and `BusinessConfigService` | default review count, feature threshold, business radius |
| Infrastructure/security/technical safety value | validated `@ConfigurationProperties` / environment | credentials, CORS, timeouts, upload size, worker pool |
| One-off implementation detail | local named constant | a parser delimiter or a layout-only technical value |

- Do not leave numeric literals in a controller/service when they alter business
  behavior. Add a typed `BusinessConfigKey` with label/key, safe default,
  minimum/maximum and invalid-value fallback. Invalidate/cache deliberately
  when an admin changes the value.
- Do not make security ceilings operator-controlled without a safe hard maximum.
  For example, an operator-configured page size must still be clamped by an
  immutable server ceiling.
- Prefer an enum over database configuration when accepting a new value would
  require a code path, schema or authorization change. Database config is for
  policy values, not a way to bypass compile-time domain validation.

## Reliability, performance and readability

- Name work by intent: `load…`, `find…`, `create…`, `update…`, `transition…`,
  `validate…`. A boolean starts with `is`, `has` or `can`.
- Keep an idempotency key for externally retried create/send/payment actions.
  Persist it with the request/result rather than relying on client timing.
- Use optimistic locking (`dataVersion` / `expectedVersion`) for mutable admin
  records. Surface a conflict as a deliberate client action, not a silent
  overwrite.
- Log structured audit events for sensitive reads and mutations, with actor,
  target, action, reason and request/correlation ID—never secrets or full
  customer content.
- Put timeouts, retry policy and circuit-breaking at the adapter boundary; do
  not scatter retry loops through controllers.

## Required change checklist

1. Locate the route, service use case, repository/mapper and caller before
   editing. Check the request/response contract and permissions end to end.
2. Add/adjust validation, authorization, state transition, audit and error
   handling with the use case.
3. Review SQL binding, pagination, indexes and transaction scope. Keep MyBatis
   mappings covered by an integration test when SQL changes.
4. Run the relevant backend test/compile task, a static search for the old
   hard-coded value, and an authorized/unauthorized API check.
5. Update this document when package ownership, response conventions or the
   configuration decision rule changes.

## Refactor roadmap

1. Fix confirmed authorization and external-I/O findings before cosmetic
   refactors.
2. Standardize the security 401/403 response and controller exception policy.
3. Move controller-owned I/O/mapper calls into application services, then
   standardize DTO validation and pagination contracts resource by resource.
4. Replace behavior-changing literals with enums or typed `BusinessConfigKey`
   entries, including a migration/seed and safe fallback for each new config.
5. Add API security, mapper integration and service transition tests before
   expanding the next resource family.

## References

- [Spring Boot externalized configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring Security method security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- [MyBatis mapper XML](https://mybatis.org/mybatis-3/sqlmap-xml.html)
- [MyBatis dynamic SQL](https://mybatis.org/mybatis-3/dynamic-sql.html)
