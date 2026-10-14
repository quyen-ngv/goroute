# Review backend và kế hoạch cập nhật — 2026-10-14

Tài liệu này **kế thừa** [`BACKEND_REFACTOR_PLAN_2026-09.md`](BACKEND_REFACTOR_PLAN_2026-09.md) (2026-09-18).
Bản 09 tập trung vào hiệu năng, cache và dọn API; các mục hiệu năng của nó vẫn còn hiệu lực và được gom
vào Phase 5 ở đây. Bản này thêm bốn góc bản 09 chưa đi sâu: **cách chia module/feature, khả năng mở rộng,
coding convention, design pattern**, cộng với đối chiếu stack với best practice hiện tại.

Phạm vi: `goroute/src/main/java/com/ds/goroute` — 1 832 file Java, ~138k dòng (tăng 51% kể từ 2026-09-18).
Mọi số liệu đo trên `HEAD` của nhánh `feat/home-map-meetup-chat` ngày 14/10/2026.

Các lỗi thật (không phải nợ kỹ thuật) tìm thấy trong lần review này đã ghi vào
[`.agents/docs/ISSUES.md`](../.agents/docs/ISSUES.md): **A22–A26, B69**, và sửa lại mục **C2b**.

---

## 1. Kết luận

| Tiêu chí | 2026-09 | Bây giờ | Một câu |
|---|---|---|---|
| Clean code | Khá | Khá | Phần code mới viết sạch hơn hẳn, nhưng 31 file "nén" vẫn còn và 14 file trong số đó còn dài thêm khi bị sửa. |
| SOLID | Kém | Kém | God class lớn thêm (`TripServiceImpl` 2 594 dòng / 27 phụ thuộc); 94/94 interface service chỉ có một impl. |
| Chia module | — | **Trung bình** | Đang có hai kiến trúc song song: lõi chia theo tầng, 4 feature mới chia theo feature. Hướng đi đúng nhưng không có gì cưỡng chế ranh giới. |
| Dễ mở rộng | Khá | **Trung bình** | Thêm feature như meetup phải sửa ~10 file dùng chung; 5 chỗ `switch` theo loại nội dung phải sửa mỗi lần thêm feature. |
| Convention | — | **Trung bình** | Quy tắc viết rất tốt (`AGENTS.md`), nhưng **không có công cụ nào cưỡng chế**: không formatter, không lint, không ArchUnit, không CI. |
| Test | Kém | Trung bình | Từ 392 lên 1 570 test, nhưng 0 test chạy SQL thật, 0 test HTTP/phân quyền. |
| Stack | — | **Rủi ro** | Spring Boot 3.4 đã hết hỗ trợ OSS từ 31/12/2025, 3.5 hết từ 30/06/2026. |

**Ba phát hiện quan trọng nhất:**

1. **Quy tắc có, cưỡng chế không.** `AGENTS.md` cấm code nén, cấm controller gọi mapper, cấm raw `ResponseEntity`,
   yêu cầu `@Valid`… — tất cả đều đang bị vi phạm và vi phạm *tăng* theo thời gian (§3.2). Không sửa gốc này thì
   mọi đợt refactor sẽ bị bào mòn lại. Đây là việc phải làm trước tiên, rẻ nhất, và có lợi nhất.
2. **Hướng package-by-feature là đúng, nhưng mới làm nửa vời.** `quest/`, `meetup/`, `localevent/`,
   `partneronboarding/` có cấu trúc tốt hơn lõi, nhưng enum, config key, error code, job, mapper XML của chúng
   vẫn nằm ở package dùng chung; có 5 vòng phụ thuộc feature ↔ lõi; 222/223 class là `public` (§3.1).
3. **Điểm mở rộng là các `switch` đóng.** Kiểm duyệt, chia sẻ vào chat, notification payload, tool của Pengoo,
   map provider — mỗi feature mới phải sửa từng chỗ. Đổi sang "hook interface + registry bean" (mẫu
   `PlaceLookupProviders` đã có sẵn trong repo) là thay đổi có đòn bẩy lớn nhất về khả năng mở rộng (§4).

**Những điều tốt cần giữ nguyên khi refactor:**

- 0 lần dùng `${}` trong 94 file mapper XML; sort động qua `<choose>` allowlist.
- `CommonExceptionHandler` trả đúng mã 4xx/5xx; `BusinessException` + `ErrorConstant` dùng nhất quán (891 điểm).
- `@CurrentUser` + `CurrentUserArgumentResolver`; không có `SecurityContextHolder` rò ra ngoài `config/`.
- Chặn SSRF (`RemoteFileUrlPolicy`), kiểm magic bytes khi upload, BCrypt 12, CORS có kiểu.
- `BusinessConfigKey` + `BusinessConfigService` là mẫu config nghiệp vụ tốt.
- `PlaceLookupProvider` + `PlaceLookupProviders` (EnumMap registry), `AiClient` + `@ConditionalOnProperty`.
- `QuestStatus.canTransitionTo(target, Actor)` + `QuestLifecycleRules` — mẫu state machine tốt nhất trong repo.
- Code mới đã đưa I/O bên ngoài ra khỏi transaction (`ReceiptScanService`).

---

## 2. Tiến độ plan 2026-09

Không ô nào trong plan 09 được đánh dấu, file không đổi kể từ khi viết. Đối chiếu với code:

| Trạng thái | Mục |
|---|---|
| **Xong** | B1 cache `authPrincipal` (`config/cache/AuthSubjectCache`, chưa có projection hẹp), B5 khoá `publicTripSearch` (`PublicTripRankingCache`), D4, D5 (trang admin tĩnh đã gỡ, bỏ permit `/*.html`) |
| **Một phần** | A5 (2/3 lỗi client), B3 (1/6 cache), C1 (1/6 điểm N+1: feed trip công khai), C3 (1/3 index), D3, G2 (15 test parse XML), G3 (assert bằng Mockito, chưa đếm SQL thật) |
| **Chưa làm** | A1 tỉ giá, A2 I/O trong transaction, A3 `file.getBytes()`, A4 đọc cả bảng, B2 `adminPermissions`, B4, B6, C2, C4, D1, D2, E1–E7, F, G1 |
| **Xấu đi** | E3 god service, E4 interface một impl (77→94), E6 đường notification song song (11→16 service gọi thẳng), E7 renderer (1 038→1 243 dòng), F (`@Scheduled` 15→30, phần lớn chưa có khoá) |

Kết luận: plan 09 xếp đúng ưu tiên nhưng không có cơ chế ngăn nợ mới. Plan này đặt hàng rào (Phase 1)
**trước** mọi refactor.

---

## 3. Phát hiện

### 3.1 Chia module/feature

**Hai kiến trúc cùng tồn tại.**

- Lõi chia theo tầng: `controller/` (109), `service/` (129 file ở gốc, chủ yếu interface) + `service/impl/` (96 impl phẳng),
  `dto/` (447), `entity/` (155), `mapper/` (92), `repository/` + `impl/` (106), `type/` (161 enum), `utils/` (35).
  Các package con `service/track`, `service/checkin`, `service/marketplace` chỉ chứa *helper*; service chính vẫn ở gốc và `impl/`.
  Chỉ `service/assistant` và `service/notification` là trọn vẹn.
- 4 feature mới chia theo feature: `domain/ dto/ persistence/ service/ web/`.

**4 feature mới không nhất quán với nhau.**

| | quest | meetup | localevent | partneronboarding |
|---|---|---|---|---|
| Controller | `web/` | `web/` | `web/` (kèm `AdminApiKeyController` — việc của nền tảng) | ở gốc package |
| Service | class cụ thể (1 cặp interface/impl) | interface+Impl | class cụ thể | interface+Impl |
| Enum | 13 cái ở `type/` chung | 11 ở `type/` | 8 ở `type/` | trong `domain/` ✔ |
| Job | ở `job/` chung | — | `localevent/job/` ✔ | — |
| DTO admin | `QuestAdminListItem` | `AdminMeetupRow` | `AdminLocalEventRow` | — |

**Ranh giới không được cưỡng chế.** 222/223 class trong feature là `public`, không có ArchUnit hay Spring Modulith.
Hệ quả cụ thể: `ContentModerationServiceImpl` đọc thẳng `MeetupRepository`; `ChatShareValidator` đọc thẳng
`LocalEventRepository`; `UserCheckinServiceImpl:121` gọi `quest.service.QuestPlayService` bằng tên đầy đủ
(grep import không bắt được).

**5 vòng phụ thuộc feature ↔ lõi:** quest ↔ checkin, meetup ↔ moderation, meetup ↔ chat marketplace,
localevent ↔ chat marketplace, localevent ↔ `config/filter/AdminApiKeyAuthenticationFilter`.

**Shared kernel ngầm và đặt sai chỗ.**

- Dùng bởi cả 4 feature: `BusinessConfigKey`, `BusinessConfigService`, `BetaAccessService`, `BusinessException`,
  `ErrorConstant`, `PageResponse`, `BaseResponse`, `@CurrentUser`.
- Đặt sai chỗ: codec JSONB chung nằm trong `service/marketplace/MarketplaceJson`; chat chung mang tên `Marketplace*`
  nhưng meetup và localevent dùng như chat chung; xác thực API key của nền tảng nằm trong `localevent`;
  `utils/` chứa logic nghiệp vụ (`WeatherAdvisoryEvaluator`, `PassportTextResolver`, `PlaceAiFacts`…);
  `PolylineCodec` có hai bản (`utils/` và `service/track/`); event chia giữa `event/` và `service/notification/event`.

**Điểm nghẽn merge.** `BusinessConfigKey` có 199 hằng (71 `QUEST_`, 28 `TRACKER_`), `ErrorConstant` 108 mã
(39 riêng feature), `SecurityConfig` 40 `requestMatchers`, `DataSourceConfig` liệt kê tay từng package mapper
(`@MapperScan`) và chỉ nạp `classpath:mapper/*.xml` phẳng.

**Độ khó tách từng context ra feature package** (ước lượng theo phụ thuộc chiều vào):

| Context | Phụ thuộc chiều vào | Độ khó |
|---|---|---|
| tracker | ~6 | Dễ nhất — làm đầu tiên |
| checkin/passport | ~11 | Dễ, và phá được vòng với quest |
| assistant | ~1 (lá) | Dễ di chuyển, nhưng phụ thuộc ra ngoài rất nhiều |
| notification | ~67 | Trung bình — chiều phụ thuộc đang ngược (notification biết về trip) |
| marketplace | ~90 | Trung bình-khó — phải tách chat chung ra trước |
| trip | ~130 | Khó — làm sau cùng |
| places | ~144 | Khó — làm sau cùng |
| moderation | ~133 | Không di chuyển: thành port trong kernel |

### 3.2 Coding convention và clean code

| Vấn đề | Số liệu | Ví dụ | Mức |
|---|---|---|---|
| Code nén (nhiều lệnh/dòng, import một dòng) — `AGENTS.md` cấm | 31 file có dòng > 300 ký tự | `ActivityCommerceServiceImpl` dòng dài nhất 2 564; `PartnerMarketplaceReviewController` cả file 2 dòng | Cao |
| Không có công cụ cưỡng chế | 0 Spotless/Checkstyle/PMD/Error Prone/ArchUnit/JaCoCo/CI, 0 `.editorconfig` | `pom.xml` chỉ có compiler, spring-boot, mybatis-generator | Cao |
| Raw `ResponseEntity` — `AGENTS.md` cấm | 101 method / 16 file; 18 `BaseResponse<Map<String,Object>>` | `AdminPlaceController:51`, `AdminFoodController`, `ReviewController` | Cao |
| `@RequestBody` thiếu `@Valid` | 21/256; 3 body là `Map` | `QuestPlayController:122`, `PlaceController:60` | Trung bình |
| Phân trang không thống nhất | `page/size`, `limit`, `cursor`, `afterSequence`; default size 20/30/50/100; chỉ 43 tham số có `@Max`; clamp tay ở 52 chỗ | `HotelMarketplaceServiceImpl:827` cap 200 (quy tắc là 100) | Trung bình |
| Lấy user hiện tại | `@CurrentUser` 306 lần, nhưng 19 controller nhận `Authentication`, 17 helper riêng, 15 `UUID.fromString(authentication.getName())` | `AdminMarketplaceChatController` ×4 | Trung bình |
| Controller gọi mapper — `AGENTS.md` cấm | 4 controller, 21 lời gọi | `AdminManagementController:27`, `InternalAiCallController:28` (mới) | Trung bình |
| Giá trị đóng lưu bằng String | 86 trường status/type/role String trong entity vs 30 enum; 33 literal | `AiTripGenerationService:405` `"FAILED"` | Trung bình |
| Thời gian | `LocalDateTime.now()` 385 lần, 16 file dùng `Clock`; `"Asia/Ho_Chi_Minh"` 13 lần dưới 7 tên hằng; `ZoneId.systemDefault()` 4 lần; container không đặt `TZ` | `HotelMarketplaceServiceImpl:499` | Trung bình-Cao |
| Ba thư viện JSON | Jackson 154 file, Gson 3, json-simple 1 (`CorsFilter`); `new ObjectMapper()` 2 lần bỏ qua cấu hình Spring | `JsonUtils:20`, `PlaceAiFacts:24` | Trung bình |
| `catch (Exception)` | 280 điểm / 93 file; 11 trong controller (cấm) | `PlaceServiceImpl` 23, `NotificationHelper` 22 | Trung bình |
| Null | 489 `return null`, 148 mapper `find*` trả null, 0 `@Nullable` — không có hợp đồng null | `PlaceServiceImpl` 22 | Trung bình |
| Magic number nghiệp vụ | 10 điểm mạnh nhất, trong đó 1 là bug thật | `ReviewServiceImpl:288` (ISSUES A24), `ReferralService:32` 3 ngày, `ReviewScoringService:174-188` trọng số | Trung bình |
| Chuỗi tiếng Việt cho người dùng trong Java | 371 literal / 42 file | `WeatherAdvisoryEvaluator` 46, `AdminNotificationServiceImpl:139` | Trung bình |
| Notification tiêu đề tiếng Anh hard-code, bỏ qua renderer/i18n | ~18 service | `HotelMarketplaceServiceImpl:836` "Guest cancelled a booking" | Trung bình |
| Email user log ở INFO | ~12 điểm | `AuthServiceImpl:103,123,153,198` | Trung bình |
| Đặt tên DTO | `*Dto`, `*DTO`, `*Query`, `*Input`, danh từ trần; 13 file trong `dto/response` không có hậu tố Response | `JoinInQueueRequestDTO`, `PlaceMenuDto` | Thấp |
| Enum rải rác | `type/` 161, `enums/` 2, `constant/CitySlug`, feature `domain/` | — | Thấp |
| Động từ method | `get*` 153, `find*` 14, `load*` 0, `transition*` 0 (quy tắc yêu cầu load/find/transition/validate); mapper dùng cả `find` (392) và `select` (136) | — | Thấp |
| Rác | 3 file Java 0 byte (`AuthProviderTypeHandler`, `TripStatusTypeHandler`, `PlaceGroupMapper`), `GoogleMapsServiceImpl` 87/237 dòng là code comment, `RedisService` stub vẫn được gọi, `docker-file` cũ, artifact tên `ticketmaster` | — | Thấp |

Ghi chú về quy ước cá nhân (`mybatis-sql-standard`): các quy tắc giả định MyBatis-Plus (`LambdaQueryWrapper`,
`TenantLineInnerInterceptor`, 8 cột audit, `tenant_id`) **không áp dụng** — dự án dùng MyBatis thuần, không multi-tenant.

### 3.3 Design pattern và SOLID

**Notification — 7 đường ghi song song** vào `NotificationService`: pipeline sự kiện trip (`NotificationHelper` →
`NotificationDispatcher` `@Async` trên executor mặc định chứ không phải `notificationExecutor` → 8 handler → strategy),
gọi thẳng `createNotification(...)` từ ~18 service, `SocialNotificationService`, `ConversationNotifier`,
`MeetupNotifier`, `TripItineraryNotificationService`, admin broadcast. Strategy chỉ là danh nghĩa: handler inject
strategy cụ thể, `CustomRecipientStrategy` không ai dùng. Renderer 1 243 dòng, 168 `case`, chuỗi 4 ngôn ngữ nằm trong Java.

**Spring event dùng nửa vời.** 9 `@TransactionalEventListener(AFTER_COMMIT)` tốt, nhưng song song có 22 khối
`TransactionSynchronizationManager.registerSynchronization` viết tay ở 19 file. Repository publish event
(`ActivityRepositoryImpl:190`, `TripRepositoryImpl:63`, `UserCheckinRepositoryImpl:284`) và inject service
(`PlaceReviewRepositoryImpl:26` → `StorageService`) — rò tầng.

**Provider ngoài.** `PlaceLookupProvider` là strategy thật. Nhưng search/geocode/route đi đường khác:
`MapProviderResolver` trả enum chứ không trả adapter, `MapSearchService` và `RouteService` rẽ nhánh
`== MapProvider.GOONG`; hai enum `MapProvider` / `PlaceLookupProviderType` map tay; `GoongController` gọi thẳng
`GoongClient`. Ba AI client lặp nguyên khối config/request factory/logging; DeepSeek và OpenAI cùng wire format.
Chỉ `GoongClient` có retry; không có circuit breaker. 71 `@Value` trong 28 file (chủ yếu client bên thứ ba),
trái quy tắc `@ConfigurationProperties`. HTTP client: `RestClient` 12 file, `RestTemplate` 11 file.

**State machine — ba kiểu khác nhau.**

| Domain | Luật nằm ở |
|---|---|
| Quest, Meetup | Enum `canTransitionTo(target, Actor)` — tốt, nhưng mỗi cái tự định nghĩa `Actor` |
| Booking marketplace | Enum `canTransitionTo` + `canBeSetByPartner/Admin` tách rời; code chuyển trạng thái lặp ở hotel (`HotelMarketplaceServiceImpl:719`) và activity (`ActivityCommerceServiceImpl:319`) |
| Thành viên trip | if/else trong `TripServiceImpl:626-797` |
| `TripStatus` | **Không có luật** — `TripStatus.valueOf(request.getStatus())` (ISSUES A25) |
| Chia chi phí | Boolean `isSettled` |

**God class.** `TripServiceImpl` 2 594 dòng / 78 method / 27 phụ thuộc; `PlaceServiceImpl` 1 582; `QuestPlayService`
1 343 (mới); `SocialLocationJobServiceImpl` 1 259; `TrackServiceImpl` 1 228 (mới); `AssistantToolService` 1 035 (mới, 17 phụ thuộc);
`UserCheckinServiceImpl` 995 (21 phụ thuộc).

**Interface một implementation.** 94/94 interface service chỉ có một impl; test dùng Mockito, không có fake viết tay.
Interface có giá trị thật: `NotificationEventHandler` (8), `PlaceLookupProvider` (3), `AiClient` (3), `ImageModerationProvider` (2), `ListingMaterializer` (2).

**Repository.** 474/783 method trong `repository/impl` là chuyển tiếp một dòng; 38/89 mapper không có repository;
56 service, 4 controller import thẳng `mapper`. Giá trị thật chỉ ở: bọc `Optional`, chia lô ID 500, ghép nhiều mapper.

**Trùng lặp.** Kiểm tra thành viên trip viết lại ~8 lần dù đã có `TripAccessGuard`; khung Việt Nam (bounding box) định nghĩa 3 lần;
"tên đầy đủ, không thì username" viết ở 9 class; clamp phân trang 52 chỗ.

**Concurrency.** `AiTripSseService`: `new SseEmitter(0L)` không timeout, callback xoá emitter nhưng không xoá key của job
nên map phình mãi; heartbeat gửi blocking trên thread scheduler. `CityStoryServiceImpl:341`, `LocationImageServiceImpl:131`
fan-out lên `applicationTaskExecutor` rồi `join()` — đói thread nếu bị gọi từ chính pool đó.

### 3.4 Persistence, transaction, migration

- `SELECT *` 184 lần / 51 file XML; 7 câu đọc cả bảng không `LIMIT` (`PlaceMapper.xml:468` `findAll`…), gọi từ job và endpoint chấm điểm.
- `DataSourceConfig` tự dựng `SqlSessionFactory` và `Flyway` nên **mọi `mybatis.*` và `spring.flyway.*` trong YAML là config chết**.
- JSON lưu `String` + `#{x}::jsonb` (219 lần), không có type handler JSONB.
- **V226 bị sửa sau khi commit** (ISSUES A22) — không có gì chặn việc này.
- Backfill cả bảng `places`/`user_checkins` chạy trong migration lúc khởi động app (V175, V218, V219, V225, V172…);
  `CREATE INDEX` trên `places` không `CONCURRENTLY` (V218, V214, V170, V120). Health check của container chỉ cho 120 s.
- 643 `@Transactional`, chỉ 30% `readOnly`. I/O trong transaction mới phát sinh: `QuestArObjectLibraryService:134` (đọc file GLB nhiều MB từ S3),
  `CityWeatherServiceImpl:43` (gọi Open-Meteo).
- Hikari: pool slave luôn được tạo dù routing đọc đang tắt (5 connection idle phí); không có `leak-detection-threshold`.

### 3.5 Bảo mật

- `CorsFilter` viết lại body và chèn `api_secret` vào body (ISSUES A23).
- Không rate limit cho đăng nhập (ISSUES A26).
- `permitAll` cho mọi method trên `/v1/api/activity-bookings/**`, `/v1/api/foods/**`, `/v1/api/public/**`, `/share/**` —
  endpoint ghi chỉ an toàn nhờ `@PreAuthorize` ở method; quên một annotation là thành public.
- Refresh token lưu plain text, không phát hiện tái sử dụng; thời hạn 30 ngày hard-code ở `AuthServiceImpl:343` trùng YAML.
- IAE trả nguyên message JDK (ISSUES A25).
- `generatorConfig.xml` có credential mẫu bị đóng gói vào jar; `Dockerfile` `ENTRYPOINT sh -c` không `exec`, không `server.shutdown: graceful`.

### 3.6 Test và tooling

- 251 file test, 1 570 test, JUnit 5 + AssertJ (tốt).
- **0** `@WebMvcTest`/MockMvc, **0** test chạy SQL thật (test mapper chỉ parse XML), **0** Testcontainers.
- Chưa có test: chia/tất toán chi phí (`ExpenseServiceImpl`), login/refresh/logout, `AdminAuthorization`, `AiTripQuotaService`, payout partner.
- Không CI, không JaCoCo. `TicketmasterApplicationTests` cần DB thật và `application.yaml` không có trong git (ISSUES B69).

### 3.7 Stack so với hiện trạng (research 14/10/2026)

| Thành phần | Đang dùng | Hiện tại | Ghi chú |
|---|---|---|---|
| Spring Boot | 3.4.13 | 4.1.1 (OSS tới 31/07/2027); 4.0.8 (OSS tới 31/12/2026) | **3.4 hết OSS 31/12/2025, 3.5 hết 30/06/2026** — không còn bản vá CVE miễn phí |
| mybatis-spring-boot-starter | 3.0.3 | 3.0.x (Boot 3.2–3.5), **4.0.0** (Boot 4) | Boot 4 bắt buộc starter 4.0 |
| JWT | jjwt 0.11.5 (API deprecated) | jjwt 0.13.0, hoặc `spring-boot-starter-oauth2-resource-server` + `NimbusJwtDecoder` | Khuyến nghị resource server: bỏ filter JWT tự viết |
| HTTP client | RestTemplate + RestClient | RestClient + `@HttpExchange` | RestTemplate bị `@Deprecated` từ Spring 7.1, xoá ở 8.0 |
| Resilience | Không | Spring 7 `@Retryable`/`@ConcurrencyLimit`; Resilience4j 2.4 cho circuit breaker | |
| Job nhiều instance | Không khoá | ShedLock 7.10 (JDBC, `usingDbTime()`) | Rẻ nhất |
| Kiến trúc module | Không | Spring Modulith 1.4.x (Boot 3) / 2.0–2.1 (Boot 4); ArchUnit 1.5 | `ApplicationModules.verify()` + baseline |
| Lỗi API | Envelope riêng | RFC 9457 `ProblemDetail` | Giữ `BaseResponse` vì app mobile phụ thuộc; chỉ cân nhắc cho API mới/version mới |
| JSON | Jackson + Gson + json-simple | Chỉ Jackson (Boot 4 lên **Jackson 3**, group `tools.jackson`) | Gỡ Gson/json-simple **trước** khi lên Boot 4 |
| Lucene | 9.9.1 | 10.5.2 | Major, cần reindex (đã reindex mỗi lần khởi động) |
| firebase-admin | 9.2.0 | 9.11.0 | Rủi ro thấp |
| AWS SDK BOM | 2.29.0 | 2.55.x | |
| google-maps-services | 2.2.0 | Không phát triển nữa (chỉ API legacy) | Places (New)/Routes qua RestClient |
| Logging | logstash-logback-encoder | Structured logging sẵn trong Boot 3.4+ (`logging.structured.format.console`) | |
| Tracing | Không | Micrometer Observation + OTel bridge | traceId vào log |
| Tooling | Không | Spotless (palantir) ratchet + Error Prone/NullAway (JSpecify) + JaCoCo + ArchUnit + Renovate/Dependabot | |
| Java | 21 | 21 OK; 25 LTS (JEP 491 bỏ pinning `synchronized`) | Virtual thread: chỉ bật khi đã giới hạn đồng thời cho lời gọi AI và pool DB |

Nguồn: endoflife.date/spring-boot, Spring Boot 4.0 Migration Guide, spring.io blog (Boot 4.1, Modulith 2.x,
"state of HTTP clients", core resilience), mybatis.org starter docs, ShedLock README, Maven Central metadata.

---

## 4. Kiến trúc đích

### 4.1 Cây package

```
com.ds.goroute
├── platform/                  # shared kernel — không có luật nghiệp vụ
│   ├── web/                   BaseResponse, PageResponse, PageQuery, @CurrentUser, CommonExceptionHandler
│   ├── error/                 BusinessException (+ NotFound/Forbidden/Conflict), ErrorCode (interface)
│   ├── config/                BusinessConfigService, ConfigKey (interface — mỗi feature một enum riêng)
│   ├── security/              SecurityConfig + RouteRules hook, filter, apikey/
│   ├── persistence/           DataSourceConfig (scan **.persistence), JsonbTypeHandler
│   ├── time/                  AppZones, Clock bean
│   ├── media/ ai/ geo/        upload/storage; AiClient + VisionClient + call log; GeoService, PolylineCodec, VietnamBounds
│   ├── moderation/            ModerationPort, @ModeratedText, ReportableContentResolver (hook)
│   ├── notification/          NotificationPublisher, NotificationTemplateProvider (hook), FCM, device
│   └── chat/                  engine hội thoại, ShareableContentResolver (hook)
├── identity/   wallet/
├── places/                    place, food, area, import, search; maps/ (GeocodingPort, RoutingPort: goong, mapbox, google)
├── reviews/  trip/  expense/  aitrip/  checkin/  tracker/  assistant/
├── marketplace/               hotel, activity, booking, partner, finance; onboarding/ (= partneronboarding)
└── quest/  meetup/  localevent/

Mỗi feature:  api/ (interface + DTO công khai cho feature khác) · web/ · application/ · domain/ (entity + enum + luật)
              persistence/ (package-private) · notification/ (template) · config/ (ConfigKey enum) · job/
resources/mapper/<feature>/*.xml   nạp bằng classpath*:mapper/**/*.xml
```

### 4.2 Hook interface thay cho `switch`

Mẫu đã có sẵn: `PlaceLookupProviders` (EnumMap, fail-fast khi trùng). Áp cùng mẫu cho:

| Hook | Thay cho | Feature mới chỉ cần |
|---|---|---|
| `ReportableContentResolver` | `switch` trong `ContentModerationServiceImpl:129-153` | 1 bean |
| `ShareableContentResolver` | `switch` trong `ChatShareValidator:103-110` | 1 bean |
| `NotificationTemplateProvider` + bundle `notifications_{vi,en,hi,th}.properties` | 168 `case` trong renderer, `switch` trong `NotificationPayloadFactory:25-70` | 1 bean + key trong bundle |
| `AssistantTool` (registry) | `AssistantToolService` 1 035 dòng | 1 bean/tool |
| `GeocodingPort`, `RoutingPort` + `FallbackGeocodingPort` (decorator) | rẽ nhánh `MapProvider` trong `MapSearchService`, `RouteService`, `AiTripGenerationService:291` | 1 adapter |
| `RouteRules` | 40 `requestMatchers` trong `SecurityConfig` | khai báo trong feature |
| `ConfigKey` / `ErrorCode` interface | enum 199 hằng / 108 hằng chung | enum riêng trong feature |

### 4.3 Hợp đồng chung

- `LifecycleStatus<S, A>`: `canTransitionTo(S target, A actor)` + `Actor` dùng chung — áp cho `TripStatus`, `MemberStatus`,
  `DraftStatus`, `MarketplaceBookingStatus`. Một `MarketplaceBookingTransitioner` dùng chung cho hotel và activity.
- Một đường notification: `NotificationPublisher.publish(NotificationCommand)` → event → một
  `@TransactionalEventListener(AFTER_COMMIT) @Async("notificationExecutor")` duy nhất → renderer qua bundle.
- `PageQuery.of(page, size, default, max)` + `@Max` server-side, thay 52 chỗ clamp tay.
- Side effect qua domain event + `@TransactionalEventListener`, không qua `registerSynchronization` viết tay,
  không publish từ repository.

### 4.4 Quyết định về tầng repository và interface

**Giữ tầng repository, nhưng chia theo aggregate thay vì một-một với mapper.**

Vấn đề hiện tại không phải là "có repository", mà là repository đang được viết một-một với mapper:
`ExpenseRepository` bọc `ExpenseMapper`, `ExpenseSplitRepository` bọc `ExpenseSplitMapper`, và
474/783 method chỉ là `return xMapper.y(...)`. Vì thế service vẫn phải tự ghép nhiều bảng
(`TripServiceImpl` giữ 16 repository), còn repository không gánh được việc gì.

Quy tắc đề xuất — ba tầng, mỗi tầng một loại việc:

| Tầng | Làm gì | Ví dụ |
|---|---|---|
| **Mapper** | SQL của một bảng/một truy vấn. Không logic | `ExpenseMapper.insert`, `ExpenseSplitMapper.insertBatch` |
| **Repository** (theo aggregate) | Đọc/ghi **một khối dữ liệu đi cùng nhau**, có thể gọi nhiều mapper: ghi cha + con, nạp aggregate, chia lô `IN` 500, bọc `Optional`, evict cache, giữ bất biến lưu trữ (ví dụ "split luôn đi kèm expense"). **Không** có luật nghiệp vụ, không kiểm quyền, không gọi service | `ExpenseRepository.saveWithSplits(expense, splits)`, `TripRepository.loadWithMembers(tripId)` |
| **Service** (use case) | Luật nghiệp vụ, quyền, transaction, gọi nhiều repository của nhiều aggregate, publish event | `ExpenseService.create(...)`: kiểm thành viên → tính chia → `expenseRepository.saveWithSplits` → event |
| **Domain service / guard dùng chung** | Logic nghiệp vụ mà **nhiều service** cùng cần | `TripAccessGuard`, `MarketplaceBookingTransitioner`, `CheckinVerifier` |

Trả lời câu "nghiệp vụ chung cần gọi nhiều mapper thì sao":

- Nếu "chung" nghĩa là **cùng một khối dữ liệu** (expense + splits, trip + members + destinations) → một method trên
  repository của aggregate đó gọi nhiều mapper. Đây chính là chỗ repository có giá trị nhất.
- Nếu "chung" là **luật nghiệp vụ** dùng ở nhiều use case (kiểm quyền thành viên, chuyển trạng thái booking) →
  domain service/guard, nó gọi repository. Không đặt luật vào repository.
- Nếu là **một use case** chạm nhiều aggregate (tạo trip từ lịch AI: trip + activity + member) → service, gọi nhiều repository
  trong một transaction.

Áp dụng:

- Service **chỉ phụ thuộc repository**, không import `mapper` (chặn bằng ArchUnit ở 1.3, baseline cho 56 service hiện tại).
- Repository một-một với mapper cũ không cần viết lại ngay; khi chạm vào thì gộp về theo aggregate
  (`ExpenseSplitRepository` nhập vào `ExpenseRepository`…). 38 mapper chưa có repository: thêm khi service cần dùng.
- Repository là class cụ thể, **không cần interface + Impl** (một implementation, test mock trực tiếp class được).
- Đặt trong `persistence/` của feature, cùng mapper; mapper để package-private khi Modulith cho phép.

**Interface service.** Không thêm interface một implementation cho code mới; interface chỉ dùng khi có ≥ 2 impl hoặc là
port/hook. Không xoá hàng loạt interface cũ — bỏ khi chạm vào service đó.

---

## 5. Kế hoạch

Thứ tự: sửa lỗi thật → dựng hàng rào → chuẩn hoá kernel → mở điểm mở rộng → di chuyển feature → nâng stack.
Hiệu năng (Phase 5) chạy song song khi có người rảnh. Mỗi phase là nhiều PR nhỏ; không trộn format với logic.

### Phase 0 — Sửa lỗi thật (3–4 ngày)

- [ ] **0.1** V226: kiểm checksum trên mọi DB trước lần deploy tới (ISSUES A22).
- [ ] **0.2** `CorsFilter`: bỏ parse/ghi lại body, request id qua header + MDC, không chép `X-API-SECRET`; đổi tên `RequestContextFilter`; gỡ json-simple (A23).
- [ ] **0.3** `ReviewServiceImpl.isVerifiedLocation` đọc `CHECKIN_*`; xoá ternary chết (A24).
- [ ] **0.4** IAE → thông báo chung + log; helper parse enum từ request ném `BusinessException(4000001)` (A25).
- [ ] **0.5** Rate limit login/refresh/admin login ở nginx + khoá tạm theo tài khoản (A26).
- [ ] **0.6** Tỉ giá: `unless` trên `@Cacheable`, không cache `BigDecimal.ONE` fallback (plan 09 A1, vẫn chưa làm).
- [ ] **0.7** Track `application.yaml` với placeholder, override ở `application-local.yaml` (B69).

**Xong khi:** `mvnw test` xanh; test cho 0.2, 0.3, 0.4, 0.6.

### Phase 1 — Hàng rào (1 tuần) — làm trước mọi refactor

- [ ] **1.1** Spotless (palantir-java-format, cấm wildcard/unused import) với `ratchetFrom origin/main` — chỉ ép trên file bị sửa. `.editorconfig`.
- [ ] **1.2** Một commit **chỉ format** cho 31 file nén (danh sách trong §3.2, nặng nhất `ActivityCommerceServiceImpl`,
      `MarketplaceChatRepositoryImpl`, `ActivityCommerceRepositoryImpl`, `HotelMarketplaceServiceImpl`). Thêm commit này vào `.git-blame-ignore-revs`.
- [ ] **1.3** ArchUnit với baseline vi phạm hiện tại (`FreezingArchRule`) — chỉ chặn vi phạm **mới**:
      controller không phụ thuộc `..mapper..`/`..repository..`/`..thirdparty..`; method controller không trả raw `ResponseEntity`/`Map`;
      `@RequestBody` phải có `@Valid`; cấm import `com.google.gson`, `org.json.simple`; cấm `ZoneId.systemDefault()`,
      `new ObjectMapper()`, `System.out`, `printStackTrace`; repository không phụ thuộc service; feature không đọc `persistence` của feature khác.
- [ ] **1.4** Spring Modulith 1.4.x `ApplicationModules.verify()` chế độ `explicitly-annotated` cho 4 feature mới; đánh dấu
      package tầng cũ là `Type.OPEN` cho tới khi rỗng.
- [ ] **1.5** Test chặn sửa migration đã commit: so checksum file `V*.sql` với `origin/main`.
- [ ] **1.6** CI (GitHub Actions): `mvnw verify` + Spotless check + ArchUnit + JaCoCo report (chưa đặt ngưỡng).
- [ ] **1.7** Nền test tích hợp: Testcontainers 2.x + image `postgis/postgis`, `@ServiceConnection`; một test `@MybatisTest`
      mẫu chạy SQL thật; một `@WebMvcTest` mẫu cho ma trận phân quyền (chưa đăng nhập / sai quyền / khác chủ / hợp lệ).
- [ ] **1.8** Ghi vào `goroute/AGENTS.md`: quyết định §4.4, cấu trúc feature §4.1, "Definition of done" §6. Sửa drift:
      `POINTS/EXPIRY_DAYS` đã `@Deprecated` và không dùng.

**Xong khi:** CI đỏ nếu PR thêm vi phạm mới; số vi phạm baseline chỉ được giảm.

### Phase 2 — Chuẩn hoá kernel và convention (2 tuần)

- [ ] **2.1** Tạo `platform/` và dời các class kernel (§4.1) — thuần di chuyển, không đổi logic.
- [ ] **2.2** `PageQuery` + `@Max` server-side; thay 52 chỗ clamp; thống nhất default size và cap 100.
- [ ] **2.3** `AppZones.VIETNAM` + bean `Clock`; đặt `TZ`/`user.timezone` trong Dockerfile; bỏ `ZoneId.systemDefault()`.
- [ ] **2.4** Chỉ Jackson: thay Gson ở 3 service, `JsonUtils` và `PlaceAiFacts` dùng `ObjectMapper` của Spring; `JsonbTypeHandler` dùng Jackson; maven-enforcer cấm Gson/json-simple.
- [ ] **2.5** `@CurrentUser` thay 17 helper và 15 `UUID.fromString(...)`.
- [ ] **2.6** 4 controller gọi mapper → qua service; 101 raw `ResponseEntity` + 18 `BaseResponse<Map>` → DTO có kiểu (theo từng controller); 21 `@Valid`; 3 body `Map` → DTO.
- [ ] **2.7** `@Value` → `@ConfigurationProperties` cho client bên thứ ba (AI, Goong, OpenAI vision, Open-Meteo, exchange rate URL).
- [ ] **2.8** Exception có kiểu: `NotFoundException`, `ForbiddenException`, `ConflictException` (kế thừa `BusinessException`);
      bỏ `catch (Exception)` trong controller; log lỗi kèm throwable.
- [ ] **2.9** Hợp đồng null: JSpecify `@NullMarked` cho package mới + `platform/`; repository trả `Optional` cho `find*` một bản ghi.
- [ ] **2.10** Dọn: 3 file 0 byte, code comment trong `GoogleMapsServiceImpl`, stub `RedisService` và lời gọi của nó, config Redis chết, `docker-file`, đổi `ticketmaster` → `goroute`; che email trong log INFO.
- [ ] **2.11** Gom enum feature về `domain/` của feature; `type/` chỉ giữ enum dùng chung.

### Phase 3 — Mở điểm mở rộng (2–3 tuần)

- [ ] **3.1** `ReportableContentResolver` + `ShareableContentResolver` — phá 3 vòng (meetup↔moderation, meetup/localevent↔chat).
- [ ] **3.2** Quest ↔ checkin: `UserCheckinServiceImpl` publish `CheckinCreatedEvent` (đã có), quest lắng nghe thay vì bị gọi.
- [ ] **3.3** API key của nền tảng: dời `AdminApiKeyService`/controller ra `platform/security/apikey` — phá vòng localevent↔config.
- [ ] **3.4** Một đường notification (§4.3); `NotificationTemplateProvider` + bundle; tiêu đề hard-code tiếng Anh đi qua renderer;
      xoá `CustomRecipientStrategy`; dispatcher chạy trên `notificationExecutor`. Làm theo nhóm sender, có test trước.
- [ ] **3.5** `GeocodingPort`/`RoutingPort`, `MapProviderResolver` trả adapter, gộp `MapProvider` với `PlaceLookupProviderType`, `GoongController` qua service, một `VietnamBounds`.
- [ ] **3.6** AI: `AiProviderProperties`, `OpenAiCompatibleChatClient` cho DeepSeek + OpenAI, interceptor inject qua constructor, retry có giới hạn cho 429/5xx.
- [ ] **3.7** `LifecycleStatus` + `Actor` chung; luật cho `TripStatus` và `MemberStatus`; `MarketplaceBookingTransitioner`.
- [ ] **3.8** 22 `registerSynchronization` viết tay → domain event; dời publish event ra khỏi repository; `StorageService` ra khỏi repository review.
- [ ] **3.9** `AssistantTool` registry, tách `AssistantToolService` theo tool.
- [ ] **3.10** `AiTripSseService`: emitter có timeout, xoá key job khi rỗng, heartbeat không blocking thread scheduler.

**Xong khi:** thêm một loại nội dung có thể báo cáo/chia sẻ/notify không phải sửa file dùng chung nào ngoài migration.

### Phase 4 — Di chuyển sang feature package (theo đợt, mỗi context một PR)

Thứ tự theo §3.1, mỗi bước có test trước khi chuyển, không đổi hành vi trong cùng PR:

1. `tracker` — ít phụ thuộc chiều vào nhất, chứng minh mẫu.
2. `checkin/passport` — kèm tách `UserCheckinServiceImpl` (command / feed query / like; hệ quả qua `CheckinConsequencesListener`).
3. `notification` → `platform/notification`; event trip dời về `trip/`.
4. `marketplace` — tách chat chung ra `platform/chat` trước; `partneronboarding` → `marketplace/onboarding`; tách
   `HotelMarketplaceServiceImpl`/`ActivityCommerceServiceImpl` theo actor (public / partner / admin).
5. `trip` — tách `TripServiceImpl`: `TripCommandService`, `TripMembershipService`, `PublicTripQueryService`, `TripSearchBiasService`, `TripContentCountReconciler`.
6. `places` — tách `PlaceServiceImpl`: `PlaceImportService`, `PlaceAdminService`, `PlaceSearchService`; review về `PlaceReviewService`.

Mỗi context chuyển xong thì bật kiểm tra Modulith cho nó (bỏ khỏi `Type.OPEN`) và đưa mapper XML vào `mapper/<feature>/`.

### Phase 5 — Hiệu năng và vận hành (song song, kế thừa plan 09)

Các mục chưa làm của plan 09, giữ nguyên nội dung và ưu tiên ở đó: A2, A3, A4, B2, B3, B4, C1 (5 điểm còn lại), C2, C3, C4, D1–D3.
Thêm:

- [ ] **5.1** I/O trong transaction mới: `QuestArObjectLibraryService:134`, `CityWeatherServiceImpl:43`.
- [ ] **5.2** Backfill sau này không chạy trong migration khởi động: job batch riêng; index trên `places`/`user_checkins` luôn `CONCURRENTLY`.
- [ ] **5.3** Bỏ pool slave khi routing đọc tắt; `leak-detection-threshold`; `server.shutdown: graceful`; `exec` trong `ENTRYPOINT`.
- [ ] **5.4** `permitAll` theo method: thu hẹp `activity-bookings/**`, `foods/**`, `public/**`, `share/**` về GET + đúng route ghi cần mở.
- [ ] **5.5** Refresh token: lưu hash, phát hiện tái sử dụng (thu hồi cả họ token), thời hạn đọc từ `JwtProperties`.
- [ ] **5.6** Structured logging gốc của Boot + Micrometer tracing (traceId trong log); timer theo provider/model cho lời gọi AI.

### Phase 6 — Nâng stack (2–3 tuần, sau Phase 1–2)

Điều kiện trước: Phase 1 xong (có CI + test tích hợp), Phase 2.4 xong (chỉ còn Jackson).

- [ ] **6.1** Boot 3.4.13 → 3.5.x: sửa mọi deprecation, chạy `spring-boot-properties-migrator`. mybatis starter 3.0.x mới nhất.
- [ ] **6.2** Boot 3.5 → 4.1.x: đổi tên starter (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`), Jackson 3,
      `@MockBean` → `@MockitoBean`, mybatis starter **4.0.0**, Modulith 2.1. Thử OpenRewrite trước.
- [ ] **6.3** JWT: `spring-boot-starter-oauth2-resource-server` + `NimbusJwtDecoder`/`NimbusJwtEncoder` (HS256), bỏ filter JWT tự viết và jjwt.
      Giữ nguyên định dạng token để app không phải đăng nhập lại; test song song trước khi bật.
- [ ] **6.4** `RestTemplate` → `RestClient` (11 file); client khai báo `@HttpExchange` cho worker Python.
- [ ] **6.5** firebase-admin 9.11, AWS BOM 2.55, Lucene 10 (reindex — đã có reindex khi khởi động), google-maps-services → REST Places (New)/Routes.
- [ ] **6.6** Renovate hoặc Dependabot để không tụt version lần nữa.
- [ ] **6.7** Cân nhắc virtual thread chỉ sau khi có `@ConcurrencyLimit` cho lời gọi AI và đo pool Hikari.

### Phase 7 — Một instance (đã chốt 14/10/2026)

**Quyết định:** backend chạy **một instance** có chủ đích (chọn hướng (b) của plan 09 §F). Cache Caffeine cục bộ,
simple STOMP broker, emitter SSE trong bộ nhớ, khoá sửa trong `Map`, 30 job `@Scheduled` không khoá — tất cả đều
đúng trong ràng buộc này, không phải nợ cần trả.

- [ ] **7.1** Ghi ràng buộc vào `.agents/docs/00-system-architecture.md` và `goroute/AGENTS.md`: "một instance; muốn
      thêm instance phải làm 7.3 trước". Nêu rõ các thành phần phụ thuộc vào nó (danh sách ở trên + `PlaceSearchIndexServiceImpl`
      giữ `write.lock` của Lucene, `MapSearchRateLimiter`, `RealtimeSessionRegistry`, `TripRealtimeAccessCache`).
- [ ] **7.2** Xoá scaffolding Redis chết: dependency comment trong `pom.xml`, khối `app.cache.redis` trong YAML, stub `RedisService`
      và lời gọi của nó, `commons-pool2`, script Lua trong `resources/scripts` (trùng với 2.10).
- [ ] **7.3** *(Chỉ khi cần scale ngang — chưa làm)* ShedLock cho job, STOMP broker relay, fan-out SSE qua Postgres
      `LISTEN/NOTIFY`, khoá sửa và rate limiter ra DB/Redis, Lucene thành dịch vụ riêng hoặc chuyển sang Postgres FTS.

Lưu ý khi deploy: `docker compose up -d` dừng container cũ trước khi chạy container mới nên không có hai instance chồng nhau.
Nếu sau này chuyển sang deploy không downtime (blue/green, rolling), hai instance sẽ chạy cùng lúc vài chục giây — lúc đó
cần 7.3 hoặc ít nhất ShedLock cho các job ghi dữ liệu.

---

## 6. Definition of done cho code mới (áp dụng ngay, trước cả Phase 1)

Một feature mới (như meetup) chỉ được merge khi:

1. Nằm trong một package feature theo §4.1; enum, `ConfigKey`, error code, job, mapper XML nằm trong feature.
2. Không đọc `persistence` của feature khác; giao tiếp qua `api/` của feature đó hoặc qua event.
3. Đăng ký vào hook (§4.2) thay vì sửa `switch` dùng chung.
4. Không interface service một impl; service chỉ gọi repository (theo aggregate, §4.4), không gọi mapper.
5. Controller: `ResponseEntity<BaseResponse<Dto>>`, `@Valid`, `PageQuery`, `@CurrentUser`; không gọi mapper/thirdparty.
6. Transaction không bọc I/O bên ngoài; side effect qua `@TransactionalEventListener`.
7. Thời gian qua `Clock` + `AppZones`; chuỗi cho người dùng qua bundle i18n.
8. Có test service, test `@MybatisTest` cho SQL mới, test phân quyền cho endpoint ghi.
9. File đã format (Spotless), không dòng nào > 160 ký tự.
10. Cập nhật `.agents/docs/` theo `MAINTENANCE.md`.

---

## 7. Rủi ro

| Rủi ro | Giảm bằng |
|---|---|
| Di chuyển package làm vỡ diện rộng, xung đột với nhánh đang làm | Mỗi context một PR thuần di chuyển; làm khi ít nhánh mở; IntelliJ "Move" giữ import; không đổi logic cùng PR |
| Commit format làm rối `git blame` | Commit riêng + `.git-blame-ignore-revs` |
| Baseline ArchUnit/Modulith thành "chấp nhận mãi" | Theo dõi số vi phạm trên CI; mỗi phase phải giảm |
| Boot 4 + Jackson 3 đổi hành vi serialize, app mobile vỡ | Phase 1 có test contract JSON cho DTO chính; lên 3.5 trước; gỡ Gson trước |
| Đổi JWT bắt người dùng đăng nhập lại | Giữ thuật toán, secret và claim; chạy decoder mới song song, so kết quả trước khi cắt |
| Gộp notification làm mất hoặc nhân đôi thông báo | Test cho từng loại trước khi chuyển; chuyển theo nhóm sender; giữ idempotency theo event id |
| Sửa migration đã commit lặp lại | Test 1.5 trên CI |

---

## 8. Kiểm chứng

Nền tại thời điểm review: nhánh `feat/home-map-meetup-chat` sạch và đã đồng bộ với `origin`; 230 migration (tới V238);
251 file test / 1 570 test. Danh sách test luôn đỏ do môi trường: xem memory/ghi chú của dự án.

Sau mỗi phase tối thiểu:

```
./mvnw -o test
```

Từ Phase 1 trở đi: `./mvnw verify` (Spotless + ArchUnit + test tích hợp Testcontainers) phải xanh trên CI.
Cập nhật `.agents/docs/` theo `.agents/docs/MAINTENANCE.md` trong cùng thay đổi khi động tới endpoint, luật truy cập,
enum, `BusinessConfigKey`, mã lỗi, migration, job, tích hợp ngoài hoặc chuyển trạng thái nghiệp vụ.
