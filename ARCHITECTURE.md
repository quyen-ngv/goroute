# GoRoute backend architecture

This document is the first reference for any future backend change. It describes the current system, the intended Clean Architecture boundaries, and the rules for adding or modifying code without changing existing business behaviour.

## 1. System overview

The backend is a Spring Boot 3.4 application on Java 21. It exposes REST endpoints and WebSocket support for the GoRoute travel product. PostgreSQL is the system of record; Flyway owns schema evolution; MyBatis is the SQL access layer. Mapper interfaces live in `src/main/java/com/ds/goroute/mapper` and XML statements in `src/main/resources/mapper`.

Important integrations include Firebase Cloud Messaging, AWS S3/MinIO, Google Maps, Goong, Anthropic, a scrape service, an image compression service, and local Caffeine caching. Redis is disabled in the current configuration.

The application is a modular monolith. It runs as one process, but auth, trips, places, expenses, reviews, notifications, food, bookings, media, and admin/import jobs should be treated as separate business modules.

## 2. Request and data flow

```text
HTTP/WebSocket request
  -> security/filter/interceptor
  -> controller (HTTP DTO validation and response envelope)
  -> service interface / implementation (use case and transaction)
  -> repository or MyBatis mapper (persistence)
  -> PostgreSQL master or read replica
```

Cross-cutting concerns are provided by `config`, security, AOP logging, exception handling, caching, and database routing. External systems must be called through `thirdparty` adapters or service implementations, never directly from a controller or mapper.

`DataSourceConfig.ReplicationRoutingDataSource` routes a `readOnly` transaction to `SLAVE`; writes and the default route use `MASTER`. Flyway always migrates the master. A read-after-write operation requiring immediate consistency must not be put in a read-only transaction.

## 3. Layer responsibilities

### Presentation: `controller`, `dto/request`, `dto/response`

Controllers map HTTP input to a service call and map results to the existing `BaseResponse` envelope. They may use validation, path/query/header parameters, OpenAPI annotations, and HTTP status selection. They must not contain SQL, transaction boundaries, persistence decisions, or multi-step business rules.

### Application/use cases: `service`, `service/impl`

Service interfaces define use cases. Implementations coordinate validation, authorization, domain decisions, persistence, integrations, events, and transactions. Put `@Transactional` on public service methods and `readOnly = true` on queries that do not write. Avoid holding a transaction while waiting on a slow external API unless the invariant requires it.

The codebase has both concrete services directly under `service` and interface/implementation pairs under `service/impl`. Preserve existing packages when modifying a feature. New substantial features should use an interface plus `impl` and expose cohesive use-case methods.

### Domain: `entity`, `type`, pure `util`/`utils`

Entities represent persisted business data and enums represent controlled states. Rules that do not need Spring, HTTP, SQL, servlet state, or an SDK belong in small domain methods or pure utilities. Keep pure business objects free of framework dependencies where possible.

### Persistence: `mapper`, `repository`, `entity`, `resources/mapper`

Repositories are the boundary used by services. Existing repository classes may wrap MyBatis mappers; mapper interfaces are the SQL port and XML is the SQL adapter. Keep SQL in XML, bind values with `#{...}`, never concatenate request data, and keep `@Param` names identical to XML names.

### Infrastructure: `config`, `thirdparty`, `job`, `service/external`, `service/redis`

This layer owns Spring configuration, security, data source routing, storage, HTTP/SDK clients, scheduled work, serialization, and caching. A business service should depend on a port/interface and should not construct SDK clients or read servlet state directly.

## 4. Clean Architecture rules

Dependency direction is toward the use case/domain:

```text
presentation -> application -> domain
infrastructure -> application/domain ports
domain -> no Spring, HTTP, SQL, servlet, or SDK dependency
```

The current package layout is layer-oriented rather than feature-oriented, so this is an incremental target, not a reason to move hundreds of files in one change. For a new feature, keep request/response DTOs at the edge, define a service use case, isolate persistence in a mapper/repository, and put pure rules in a domain type or utility. Split a god class only when the extracted collaborator has a clear invariant, integration, or transaction boundary.

## 5. MyBatis XML query standard

Each XML file must use the fully qualified mapper interface as `namespace` and one statement id per interface method. Prefer reusable columns and result maps:

```xml
<mapper namespace="com.ds.goroute.mapper.ExampleMapper">
  <sql id="ExampleColumns">id, owner_id, title, created_at, updated_at</sql>
  <resultMap id="ExampleResultMap" type="com.ds.goroute.entity.Example">
    <id property="id" column="id"/>
    <result property="ownerId" column="owner_id"/>
    <result property="title" column="title"/>
  </resultMap>
  <select id="findById" resultMap="ExampleResultMap">
    SELECT <include refid="ExampleColumns"/> FROM examples WHERE id = #{id}
  </select>
</mapper>
```

Use explicit columns for new or heavily used queries. Replace legacy `SELECT *` incrementally when a mapper is already being changed; a broad rewrite is risky because result maps and migrations are numerous.

Use `#{value}` for values. `${value}` is forbidden for request data and is only acceptable for a reviewed, allow-listed SQL identifier. Use `<if>` for optional predicates, `<where>` to manage `AND`, `<trim>` for assembled clauses, and `<foreach>` for collections. Guard empty collections:

```xml
<select id="findByIds" resultMap="ExampleResultMap">
  SELECT <include refid="ExampleColumns"/> FROM examples
  <choose>
    <when test="ids != null and ids.size() > 0">
      WHERE id IN
      <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
    </when>
    <otherwise>WHERE 1 = 0</otherwise>
  </choose>
</select>
```

For optional filters prefer `<where><if test="keyword != null and keyword != ''">AND title ILIKE CONCAT('%', #{keyword}, '%')</if></where>`. PostgreSQL casts/operators such as `::jsonb` and XML-escaped `&gt;`, `&lt;`, `@&gt;` are valid; document non-obvious usage. Use UUID/enum type handlers rather than Java-side conversion. Pagination must be deterministic: `ORDER BY` plus a stable tie-breaker, then `LIMIT #{limit} OFFSET #{offset}`. Validate or cap page size in the service.

For performance and correctness, filter before expensive geographic calculations where possible. Prefer `EXISTS` over a many-to-many join when only existence is needed, because a join can duplicate parent rows. Verify indexes in Flyway migrations for equality, foreign-key, status, timestamp, full-text, and spatial searches. Use `EXPLAIN (ANALYZE, BUFFERS)` on representative data before claiming an optimization.

## 6. Database and migrations

All schema changes go in a new monotonically numbered `src/main/resources/db/migration/V###__description.sql`. Never edit an applied migration. Prefer additive, deploy-safe changes: add nullable columns, backfill, deploy code, then add constraints later when needed. Document backfills, locking risk, indexes, and rollback implications. The schema contains historical tables without foreign keys; do not perform a destructive FK/normalization sweep during a feature refactor.

## 7. Clean-code conventions

- Prefer constructor injection (`@RequiredArgsConstructor`) and final fields.
- Keep methods small enough to explain with one sentence and name them after the use case.
- Preserve null/empty semantics, error codes, `BaseResponse`, and public JSON shape.
- Use `BigDecimal` for money and coordinates where the existing contract does.
- Use structured logging; no `System.out`, `printStackTrace`, or secrets/PII in logs.
- Add new localized error codes to both `errors_vi.properties` and `errors_en.properties`.
- Keep external calls timeout-bounded and make retries/idempotency explicit.
- Test pure rules, authorization, transactions, empty lists, null filters, duplicate requests, and pagination limits.

## 8. Safe optimization checklist

Before optimizing, record the current contract and call sites. Preserve response shape, ordering, null behaviour, transaction semantics, authorization, and side effects. Low-risk improvements include preventing invalid empty `IN ()` SQL, removing duplicate parent rows from existence-only joins, adding missing `readOnly` markers to pure queries, and replacing measurable repeated allocations. Do not silently change units (kilometres/metres), default filters, replica routing, cache keys, or error HTTP statuses.

## 9. Verification

From `goroute`, run:

```text
./mvnw.cmd -q -DskipTests compile
./mvnw.cmd -q test
```

If integration tests require PostgreSQL or external credentials, report that limitation and still run compilation plus isolated tests. For mapper edits, inspect XML validity, parameter names, generated SQL paths, empty-list behaviour, and result-map columns.

## 10. Known backlog

Many legacy mappers still use `SELECT *`; migrate them when touched. Some controllers extend `BaseService`, which couples presentation to request state. Large services such as trip/expense/place should be split by use case over time. `BaseService` servlet concerns should move to an application request-context port. Existing TODOs indicate unfinished behaviour and must not be “fixed” without a product decision. Replica lag requires explicit read-after-write tests.

Every future change should follow this guide or update it with an explicit architectural decision.
