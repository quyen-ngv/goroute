# Đánh giá backend và kế hoạch refactor — 2026-09-18

Tài liệu này thay thế [`BACKEND_REFACTOR_PLAN.md`](BACKEND_REFACTOR_PLAN.md) (bản 2026-08-23).
Bản cũ vẫn đúng ở phần Phase 0 nhưng đã lệch ở nhiều ô checkbox; phần đối chiếu nằm ở §7.

Phụ lục chi tiết, kèm bằng chứng `file:line`:

| Phụ lục | Nội dung |
|---|---|
| [`docs/audit-2026-09/01-architecture.md`](docs/audit-2026-09/01-architecture.md) | 16 finding kiến trúc, bảng 15 class lớn nhất, đối chiếu plan cũ |
| [`docs/audit-2026-09/02-performance-cache.md`](docs/audit-2026-09/02-performance-cache.md) | 28 finding hiệu năng, kiểm kê cache, bảng kế hoạch cache |
| [`docs/audit-2026-09/03-api-cleanup.md`](docs/audit-2026-09/03-api-cleanup.md) | 66 endpoint không có consumer, chia 3 mức an toàn |
| [`docs/audit-2026-09/endpoint-usage.tsv`](docs/audit-2026-09/endpoint-usage.tsv) | Toàn bộ 594 endpoint và consumer của từng cái |

---

## 1. Kết luận đánh giá

| Tiêu chí | Điểm | Một câu |
|---|---|---|
| Clean code | Khá | Controller mỏng thật, comment giải thích *tại sao*, nhưng 32 file bị nén thành dòng khổng lồ và 29 method dài quá 80 dòng. |
| SOLID | Kém | `TripServiceImpl` 2256 dòng với 25 collaborator, `HotelMarketplaceServiceImpl` 54 method phục vụ cả public lẫn partner lẫn admin; 77 interface service đều chỉ có đúng một implementation nên là nghi thức chứ không phải đảo phụ thuộc. |
| Clean architecture | Kém | Tên lớp có nhưng không mang nghĩa: `repository/impl` là 93,5% chuyển tiếp cơ học, 31/81 mapper không có repository, 44 service gọi thẳng mapper, cả 132 entity là túi `@Data` không hành vi. |
| Dễ bảo trì | Khá | Xử lý lỗi, config có kiểu và resolver người dùng đã tập trung và có test, nhưng chỉ 2/92 controller và 7/81 mapper có test. |
| Dễ mở rộng | Khá | Thêm một feature thì máy móc và ít rủi ro; thêm một mối quan tâm xuyên suốt thì đắt, vì không có domain model và có ba đường gửi notification song song. |
| Hiệu năng | Kém | Đúng ở quy mô hiện tại, không sống nổi khi lớn: mỗi request đã đăng nhập tốn 3 truy vấn trước khi vào controller, 42% câu select danh sách không có `LIMIT`, 22 điểm N+1 đã xác nhận. |
| Cache | Kém | Đúng thư viện, gắn sai chỗ: có 9 cache Caffeine nhưng không cái nào nằm trên đường nóng nhất. |

Ba điều tốt cần giữ, không được refactor hỏng:

- Không có một chỗ nào dùng `${}` trong toàn bộ 75 file mapper XML. Sắp xếp động đi qua `<choose>` với danh sách trắng.
- `CommonExceptionHandler` trả đúng mã 4xx/5xx, không còn đường nào trả HTTP 200 kèm lỗi.
- Mọi statement mapper đều có timeout, `RestTemplate` là bean dùng chung có timeout, push notification đã dời ra `afterCommit`.

---

## 2. Trả lời trực tiếp câu hỏi về cache

**Đã có cache chưa?** Có, nhưng mỏng. `config/cache/LocalCacheConfig.java` khai báo 5 `CacheManager`
Caffeine với 9 tên cache: `businessConfig`, `userQuotaOverrides` (300 giây, **không giới hạn kích thước**),
`placeSearch`, `activityBookingSearch`, `activityBookingByPlaceSearch`, `publicTripSearch` (2 phút, 2 000 entry),
`foodsByCity` (1 giờ), `exchangeRates` (6 giờ), `weatherSnapshot` (30 phút, 5 000 entry).
Có 43 điểm annotation, trong đó 10 điểm đang bị comment.

**Local cache trước, đúng như anh muốn?** Đúng, và hiện tại đó là lựa chọn *duy nhất*.
Redis không phải bị tắt mà là **không tồn tại**: `spring-boot-starter-data-redis` và
`redisson-spring-boot-starter` đều bị comment khỏi `pom.xml:122-128, 144-150`, còn
`service/redis/RedisService.java` chỉ ghi log rồi trả null. Cờ `app.cache.redis.enable: false`
và cả khối `cache-expirations` dưới nó là config chết, không dòng Java nào đọc.

**Thiếu gì?** Cache chưa chạm vào đường nóng nhất trong toàn ứng dụng. Chi tiết ở §3.

**Hệ quả kèm theo:** không Redis nghĩa là không có khoá phân tán, không có relay pub/sub.
Backend hiện chỉ chạy được **một instance**. Chi tiết ở §6.

---

## 3. Kế hoạch cache — L1 Caffeine, xếp theo lợi ích chia công sức

Tất cả đều là bean `CacheManager` mới đặt tên riêng trong `LocalCacheConfig`.
Tuyệt đối không gắn vào manager `@Primary` vì nó không giới hạn kích thước.

| # | Đối tượng | Tên cache | Khoá | TTL | Xoá cache khi | Lợi ích |
|---|---|---|---|---|---|---|
| 1 | `JwtAuthenticationFilter.authenticate()` — `findById` + `hasAnyRole` + `isPartnerUser` | `authPrincipal`, max 50 000 | `userId` | 60 giây | Sửa/xoá mềm user, đổi mật khẩu, cấp/thu vai trò, đổi trạng thái thành viên tổ chức, khoá tài khoản | Bỏ 3 truy vấn trên **mọi** request đã đăng nhập. Thắng lớn nhất trong toàn bộ báo cáo |
| 2 | `AdminAuthorization.can()` — join 3 bảng, bị gọi từ 223 điểm `@PreAuthorize` | `adminPermissions`, max 10 000 | `userId`, lưu cả `Set<String>` rồi so khớp `resource:action` trong bộ nhớ | 300 giây | Mọi thay đổi gán vai trò hoặc quyền | Một trang admin có thể đang bắn cả chục join quyền |
| 3 | `GET /v1/api/public/configs` | `publicAppConfig`, max 8 | `"all"` | 300 giây | Thêm tên cache vào 3 `@CacheEvict` sẵn có ở `AppConfigServiceImpl.java:65, 89, 115` | Endpoint mở app, đang là một N+1 |
| 4 | `GET /v1/api/places/{id}` và `/google/{placeId}` | `placeDetail`, max 20 000 | `id` + ngôn ngữ | 120 giây | 4 điểm đã evict `placeSearch` + evict theo id khi `updatePlace` | Read công khai nhiều traffic nhất |
| 5 | `GET /v1/api/location-images` | `locationImages`, max 16 | `includeWeather` + ngôn ngữ | 300 giây | Create/update/delete location image | Màn hình Home; bỏ một lần đọc cả bảng cộng fan-out `CompletableFuture` |
| 6 | Passport catalog | `passportCatalog`, max 64 | method + locale | 1 giờ | Mọi lệnh ghi từ `AdminPassportCatalogController` | Dữ liệu tham chiếu gần như tĩnh |
| 7 | Listing marketplace công khai | `marketplacePublicSearch`, max 2 000 | bộ lọc + page + size + tiền tệ + ngôn ngữ | 60 giây | Partner publish/unpublish/đổi giá | **Chỉ cache listing.** Không bao giờ cache `/availability`, `/rates`, `/slots`, giỏ hàng, hold |
| 8 | Bảng từ điển: `admin_roles`, `admin_permissions`, `subscription_plans`, `passport_stamp_rules` | `dictionary`, max 256 | bảng + locale | 1 giờ | Admin ghi vào bảng tương ứng | Nhỏ nhưng miễn phí |

**Không được cache:** số dư ví và sao, giỏ hàng, trạng thái hold, tình trạng phòng còn trống
(biến động từng giây và dính tiền); mọi *quyết định* phân quyền ở mức nhỏ hơn `userId`
(cache tập quyền, còn quyết định thì tính trực tiếp); `mustChangePassword` và trạng thái tài khoản
nếu TTL của `authPrincipal` bị nâng quá 60 giây, vì đó là cổng bảo mật chứ không phải dữ liệu.

**Sửa hai lỗi cache đang có, làm ngay:**

- `ExchangeRateServiceImpl.java:39` không có `unless`, còn `:51` nuốt mọi exception và trả `BigDecimal.ONE`.
  Một lần lỗi upstream là cache "1 USD = 1 VND" trong sáu tiếng, áp lên mọi giá app hiển thị.
  Thêm `unless` hoặc ném lỗi để không ghi cache.
- `publicTripSearch` đang gộp cả `viewerId`, `excludeUserId` và `randomSeed` vào khoá, nên gần như
  không bao giờ trúng trong khi vẫn quay vòng 2 000 entry. Tách phần phụ thuộc người xem ra khỏi
  payload được cache, chỉ đánh khoá theo truy vấn.

---

## 4. Thứ tự ưu tiên

Xếp theo rủi ro production giảm dần, không theo độ sạch của code.

| # | Việc | Vì sao đứng đây | Công sức |
|---|---|---|---|
| 1 | Sửa cache tỉ giá | Đang sai tiền hiển thị bất cứ lúc nào upstream lỗi | 1 giờ |
| 2 | Đưa I/O bên ngoài ra khỏi transaction ghi | 5 service đang giữ connection suốt round trip của bên thứ ba, pool chỉ có 10 | 2 ngày |
| 3 | Cache `authPrincipal` và `adminPermissions` | Bỏ 3-4 truy vấn mỗi request | 2 ngày |
| 4 | Phân trang các endpoint đang nạp cả bảng vào heap | Cách một sự cố OOM đúng một lần tăng traffic | 3 ngày |
| 5 | Diệt N+1 ở 6 chỗ nặng nhất | Feed trip công khai đang bắn ~160 truy vấn cho một trang 20 mục | 1 tuần |
| 6 | Trang share cho collection, và dọn code chết ở app | Link chia sẻ collection đang chết; xem §8 | 1 ngày |
| 7 | Khoá cho 8 job chưa được bảo vệ | Chặn trước, để sau này thêm instance không hỏng dữ liệu | 3 ngày |
| 8 | Cache nhóm 3-8 trong bảng trên | Giảm tải đọc | 3 ngày |
| 9 | Format lại 32 file bị nén | Làm mù mọi công cụ theo dòng, kể cả review diff | 1 ngày |
| 10 | Xoá API không consumer, mức A | Giảm 66 endpoint bề mặt | 1 tuần, sau khi có số liệu traffic |
| 11 | Quyết định số phận lớp repository | 100 file, ~3 500 dòng, không mang lại trừu tượng nào | 2 tuần |
| 12 | Tách god service | Rủi ro cao, lợi ích dài hạn | theo từng đợt |

---

## 5. Các phase

### Phase A — Chặn máu (2 tuần)

Mục tiêu: không còn đường nào làm sập production dưới tải.

- [ ] **A1. Cache tỉ giá.** Thêm `unless` vào `ExchangeRateServiceImpl.java:39` để không ghi cache
      giá trị fallback. Kiểm chứng: test cho trường hợp upstream lỗi, khẳng định lần gọi sau vẫn đi ra ngoài.
- [ ] **A2. I/O ra khỏi transaction.** Ở `UserServiceImpl.java:182`, `TripMemoryServiceImpl.java:128`,
      `OrganizationVerificationServiceImpl.java:72`, `AuthServiceImpl.java:124`: upload, kiểm duyệt và nén
      ảnh chạy **trước** khi mở transaction; transaction chỉ ghi URL kết quả.
      Mẫu sẵn có trong repo: `NotificationServiceImpl.pushAfterCommit` (`:135-152`).
      Kiểm chứng: test khẳng định không có lệnh gọi client bên ngoài nào nằm trong phạm vi transaction.
- [ ] **A3. Upload không nạp hết vào heap.** `FileUploadServiceImpl.java:271` đang `file.getBytes()`;
      đường batch (`:117-121`) nhân số đó lên tới 10 file song song, xấu nhất khoảng 500 MB mỗi request.
      Stream giống như video đang làm ở `:187`.
- [ ] **A4. Phân trang 4 endpoint nạp cả bảng.** `PlaceMapper.xml:216`, `PlaceReviewMapper.xml:233`,
      `UserMapper.xml:112`, `MediaAssetMapper.xml:173`. Dùng keyset pagination.
      Hai endpoint chấm điểm còn chạy đồng bộ trên request thread: chuyển sang `@Async` trả `202 Accepted` kèm job id.
- [ ] **A5. Sửa 3 lỗi client.** Xem §8.

**Điều kiện hoàn thành:** không còn câu truy vấn nào đọc cả bảng từ đường HTTP; không còn
transaction nào bọc lệnh gọi HTTP hoặc S3; `mvnw test` xanh.

### Phase B — Cache (1 tuần)

- [ ] **B1.** `authPrincipal` theo §3 mục 1. Kèm projection hẹp cho `UserMapper` ở đường auth —
      hiện `SELECT *` đang kéo cả `password_hash` qua dây mỗi request.
- [ ] **B2.** `adminPermissions` theo §3 mục 2.
- [ ] **B3.** Các cache 3-8 trong bảng §3.
- [ ] **B4.** Đặt giới hạn kích thước cho `CacheManager` `@Primary`.
- [ ] **B5.** Sửa khoá `publicTripSearch`.
- [ ] **B6.** Xoá config Redis chết trong `application.yaml:93-101` hoặc khôi phục Redis (§6).
      Để nguyên như hiện tại là bẫy cho người đọc sau.

**Điều kiện hoàn thành:** đo lại bằng `http_server_requests_seconds` và số truy vấn trên một request
mẫu ở endpoint đã đăng nhập; kỳ vọng giảm ít nhất 3 truy vấn mỗi request.

**Cạm bẫy:** mọi cache ở đây đều cần evict tường minh. TTL không phải là chiến lược invalidate cho
dữ liệu phân quyền. Nếu không đảm bảo evict được, bỏ cache đó thay vì nới TTL.

### Phase C — Chống N+1 và index (2 tuần)

- [ ] **C1.** Sáu điểm nặng nhất: feed trip công khai (`TripServiceImpl.java:1945`, ~160 truy vấn/trang),
      danh sách contributions admin (`PlaceContributionServiceImpl.java:890`, 300+ truy vấn),
      activities của trip (`ActivityServiceImpl.java:435`), tổng quan chi phí (`ExpenseServiceImpl.java:230`),
      contributors của place (`PlaceContributionServiceImpl.java:573`, không phân trang),
      đường đăng nhập (`AuthServiceImpl.java:416`, 5 statement mỗi guest membership).
      Cách làm: gom id, select một lần bằng `IN`, dựng `Map` trong bộ nhớ.
      Mẫu sẵn có: `ExpenseServiceImpl.ExpenseBatch` (`:577-624`), `PlaceServiceImpl.java:339`.
- [ ] **C2.** 16 điểm N+1 còn lại.
- [ ] **C3.** Ba index còn thiếu: `user_reviews(place_id, created_at DESC)`,
      `place_import_jobs(status, created_at DESC)`, và index trigram cho các ô tìm kiếm đang dùng `ILIKE '%…%'`.
- [ ] **C4.** Partition các danh sách `IN` trong `<foreach>` ở phía Java.

**Lưu ý:** quy ước tránh JOIN của dự án là đúng, vấn đề chỉ là chưa áp dụng. Không đổi quy ước.

### Phase D — Dọn API (1 tuần code, nhưng phải chờ dữ liệu traffic)

Chi tiết ở [`docs/audit-2026-09/03-api-cleanup.md`](docs/audit-2026-09/03-api-cleanup.md).

- [ ] **D1.** Bật cổng kiểm chứng: Prometheus đã sẵn (`application.yaml:186-203`), metric
      `http_server_requests_seconds_count` có nhãn `uri` là template route. Truy vấn cho từng route
      ứng viên trong một chu kỳ phát hành cộng một tháng.
- [ ] **D2.** Xoá nhóm A sau khi số liệu về 0. Trọng tâm: `ImageMigrationController` 7 route
      cùng `ImageMigrationJob` (job này không `@Scheduled`, các route là đường chạy duy nhất của nó),
      và các cặp route trùng giữa `AdminPlaceController` với `PlaceController`.
- [ ] **D3.** Chốt quyết định sản phẩm cho nhóm B, rồi hoặc nối vào client hoặc xoá.
      Hai việc nên nối chứ đừng xoá: `DELETE /v1/api/ai-trip-generations/{jobId}` là đường huỷ job AI
      **duy nhất** mà app chưa dùng; `GET /v1/api/notifications/unread-count` rẻ hơn hẳn cách app
      đang tải toàn bộ danh sách để đếm.
- [ ] **D4.** Không động vào nhóm C. Feed iCal có consumer là lịch bên ngoài, 11 route `internal`
      đều đang sống qua callback URL do chính backend cấp.
- [ ] **D5.** Quyết định số phận bộ trang admin HTML tĩnh trong `resources/static`.
      Nó đang giữ sống 6 endpoint mà không client hiện đại nào gọi, và `SecurityConfig` đang
      `permitAll` cho `/*.html`, `/*.css`, `/*.js`.

**Quy tắc an toàn:** không bao giờ xoá endpoint trong cùng đợt phát hành với thay đổi client.
Gỡ lời gọi ở client, phát hành, chờ người dùng cập nhật, rồi mới xoá route.
App cũ vẫn nằm trên máy người dùng; grep source không thay được số liệu traffic.

### Phase E — Kiến trúc (theo đợt, 4-6 tuần)

- [ ] **E1. Format lại 32 file bị nén.** Vi phạm trực tiếp `AGENTS.md:42-43`.
      Nặng nhất: `ActivityCommerceServiceImpl.java` 69 411 byte trong 372 dòng, dòng dài nhất 2 564 ký tự;
      `HotelMarketplaceServiceImpl.java` dòng dài nhất 1 636 ký tự; `PartnerPlaceController.java` 3 dòng.
      Làm riêng một commit chỉ format, để diff sau này đọc được.
- [ ] **E2. Chốt luật cho lớp repository.** Hiện `repository/impl` có 516/552 method chỉ chuyển tiếp
      một dòng tới mapper, 31/81 mapper không có repository, và 44 service gọi thẳng mapper.
      Hai lựa chọn, phải chọn một:
      - **(a) Bỏ lớp repository chuyển tiếp** (rẻ hơn, khớp thực tế): service phụ thuộc thẳng vào
        `@Mapper`; chỉ giữ repository cho khoảng 10 aggregate thật sự có hành vi
        (`UserReview`, `PlaceReview`, `RefreshToken`, `AiTrip`, `Place` — các chỗ có guard danh sách `IN` rỗng,
        side effect media asset, băm token, hoặc xử lý propagation).
      - **(b) Giữ và bắt buộc**: sinh 31 repository còn thiếu, cấm import `com.ds.goroute.mapper.*`
        ngoài `repository/` bằng test ArchUnit.

      Khuyến nghị (a). Dù chọn gì, ghi luật vào `AGENTS.md` ngay trong cùng thay đổi.
- [ ] **E3. Tách 3 god service theo đối tượng phục vụ.**
      `HotelMarketplaceServiceImpl` (54 method) và `ActivityCommerceServiceImpl` (46 method) đang
      gộp public, partner và admin trong một class; tách theo ba đối tượng đó.
      `TripServiceImpl` (2 256 dòng, 25 collaborator) tách theo use case: CRUD trip, thành viên và lời mời,
      liên kết guest, tìm kiếm công khai, clone.
      Làm từng service một, mỗi lần một pull request, có test trước khi tách.
- [ ] **E4. Bỏ bớt interface một implementation.** 77 interface service đều chỉ có đúng một impl.
      Không cần xoá hết; bỏ ở những chỗ interface không phục vụ test hay ranh giới nào.
- [ ] **E5. Quy tắc trạng thái về đúng chỗ.** Không làm DDD toàn phần trên nền MyBatis.
      Chỉ nhắm ba aggregate có máy trạng thái thật: booking marketplace, thành viên trip, chia chi phí.
      Đưa guard chuyển trạng thái lên entity hoặc một class `*Policy` bên cạnh.
      Mẫu sẵn có: `type/MarketplacePaymentStatus.java:21`.
- [ ] **E6. Gộp ba đường notification.** 22 event, 9 handler, 6 strategy nhưng chỉ có đúng hai điểm
      dispatch, cả hai nằm trong `NotificationHelper.java:53,59`, trong khi 11 service bỏ qua pipeline
      và gọi thẳng `NotificationService`. Chọn một đường, cấm đường còn lại bằng test.
- [ ] **E7.** Đưa `NotificationTemplateRenderer` (1 039 dòng, 88 KB, 8 ngôn ngữ × 68 loại, 31 `switch`,
      147 `case`) sang `ResourceBundle`.

### Phase F — Sẵn sàng chạy nhiều instance (quyết định hạ tầng)

Đây là quyết định kiến trúc, không phải refactor. Phải chốt trước khi tăng traffic.

Hiện tại backend **chỉ chạy được một instance**:

- 8 trong 13 job `@Scheduled` sẽ chạy trùng nếu có instance thứ hai:
  `PartnerQualityJob`, `WeatherCacheRefreshJob` (nhân đôi chi phí với hạn mức 10k/ngày của Open-Meteo),
  `RemovedCheckinPhotoCleanupJob` (xoá S3 trùng), `PassportBackfillJob`, `MarketplaceHoldExpiryJob`,
  `LocationAreaBackfillJob`, `AiTripJobWatchdogJob`, `PartnerStatementJob`.
  5 job còn lại đã được bảo vệ đúng cách bằng unique-index claim, tức là đội đã biết mẫu này.
- `EditLockServiceImpl.java:21` giữ khoá sửa trong `Map` cục bộ, chính comment ở `:20` ghi "replace Redis".
- `WebSocketConfig.java:46` dùng `enableSimpleBroker` không relay; `AiTripSseService.java:17` giữ
  emitter trong `ConcurrentHashMap` cục bộ. Callback của worker AI rơi vào instance khác với
  trình duyệt đang mở stream là mất tin.
- `PlaceSearchIndexServiceImpl.java:115` mở `FSDirectory` cho cả vòng đời tiến trình. Hai instance
  cùng đường dẫn sẽ tranh `write.lock`, khác đường dẫn thì âm thầm lệch dữ liệu.

Hai hướng, chọn một:

- **(a) Khôi phục Redis.** Mở lại hai dependency trong `pom.xml`, cài `RLock` cho 8 job,
  đổi `EditLockService` sang Redis, thêm STOMP relay, cân nhắc L2 cache.
- **(b) Chấp nhận một instance.** Ghi rõ ràng vào tài liệu vận hành như một ràng buộc có chủ đích,
  thêm kiểm tra khi khởi động để từ chối chạy nếu phát hiện instance thứ hai, và xoá phần
  scaffolding Redis chết.

Ngay cả khi chọn (b), vẫn nên bảo vệ 8 job bằng advisory lock của PostgreSQL hoặc mẫu unique-index
claim mà 5 job kia đã dùng. Chi phí thấp, và nó xoá hẳn một loại sự cố khỏi tương lai.

### Phase G — Test (song song, liên tục)

Hiện có 98 class test, 392 test cho 1 312 file main. Lỗ hổng theo tầng:

- **2 trong 92 controller** có test, trên tổng số 594 endpoint. Không có `@WebMvcTest` nào.
  Ma trận phân quyền mà bản audit 2026-08 xếp là rủi ro số một vẫn chưa được kiểm chứng.
- **7 trong 81 mapper** có test.

- [ ] **G1.** Test phân quyền cho mọi endpoint mutation: chưa đăng nhập, sai quyền, khác chủ sở hữu, hợp lệ.
      Ưu tiên nhóm `ADMIN` (229 endpoint) và `PARTNER` (86 endpoint).
- [ ] **G2.** Test contract mapper XML cho mọi SQL động bị sửa trong các phase trên.
- [ ] **G3.** Test đếm số truy vấn cho các đường vừa sửa N+1, để chống tái phát.

---

## 6. Rủi ro và cách giảm

| Rủi ro | Giảm bằng |
|---|---|
| Cache `authPrincipal` giữ lại vai trò hoặc lệnh khoá đã cũ | TTL 60 giây, cộng evict tường minh ở mọi điểm ghi vai trò và trạng thái tài khoản. Nếu không đảm bảo được evict thì không cache. |
| Xoá nhầm endpoint vẫn có app cũ gọi | Số liệu Prometheus trong một chu kỳ phát hành cộng một tháng, không dựa vào grep source. |
| Tách god service làm hồi quy | Viết test trước khi tách, mỗi service một pull request, không tách kèm sửa hành vi. |
| Format lại 32 file làm rối lịch sử git | Commit riêng chỉ có format, không kèm thay đổi logic nào. |
| Bỏ lớp repository làm vỡ diện rộng | Làm theo từng aggregate, không làm một lần. 44 service đã gọi thẳng mapper nên hướng đi đã có tiền lệ trong chính repo. |

---

## 7. Đối chiếu với plan 2026-08-23

Phase 0 hoàn thành thật và kiểm chứng được. Hai ô đang đánh dấu chưa xong thì thực ra đã xong,
tài liệu bị cũ:

- Resolver người dùng đã đăng nhập: `@CurrentUser` đã có, có test, dùng 179 lần, chỉ còn 13 chỗ sót
  dùng `UUID.fromString(authentication.getName())` trong 8 file.
- Sáu nhóm `@ConfigurationProperties` có kiểu ở Phase 2 đều đã tồn tại.

Ngược lại, một ô bị đánh giá nhẹ hơn thực tế: mục "format lại controller bị nén" thực ra là
**32 file trải trên sáu package**, không phải chuyện format vài controller.

Bốn mệnh đề trong tiêu chí hoàn thành của plan cũ đều chưa đạt: còn 3 controller gọi mapper
(`AdminManagementController.java:26-27`, `AdminMediaController.java:39`, `AdminPlanController.java:16`,
tổng 22 lệnh gọi mapper), còn generic thô trong `BaseResponse`, còn 8 controller bị nén,
còn 11 `catch (Exception)` — trong đó 9 nằm trong `ImageMigrationController` và sẽ biến mất khi xoá class đó.

---

## 8. Lỗi client phát hiện khi đối chiếu API

| Mức | Nơi | Vấn đề |
|---|---|---|
| **Đã sửa** | `goroute-admin` | `PlaceDetailView.tsx:55` và `:101` ghép `/v1/api/...` lên base URL vốn đã kết thúc bằng `/v1/api` (`api.ts:1`), thành `/v1/api/v1/api/...`. Thư viện ảnh review và nút upload ở màn chi tiết Place hỏng. Đã bỏ tiền tố thừa; dòng `:108` trong cùng file vốn đã viết đúng. Đã quét lại toàn bộ `goroute-admin/src`, không còn chỗ nào. |
| Link chết | `goroute_fe` | `ApiConfig.buildCollectionShareUrl` (`api_config.dart:19`) sinh `/share/collections/{slug}`, backend chỉ phục vụ `/share/{tripId}`. Link collection copy ra không mở được trên trình duyệt. |
| Code chết | `goroute_fe` | `settleExpense` (`expense_remote_datasource.dart:261`) trỏ tới route không tồn tại; không UI nào gọi. Tương đương ở backend là `PATCH .../splits/{splitId}/mark-paid`. Xoá method này. |
| Code chết | `goroute_fe` | Năm hằng số endpoint khai báo mà không dùng: `/v1/api/admin/foods`, `/v1/api/places/nearby`, `/v1/api/places/route`, `/v1/api/places/distance-matrix`, `/v1/api/users/me/settings`. |
| Tài liệu sai | `.agents/docs/ISSUES.md` C2b | Ghi rằng `AdminGuideController` đã bị xoá. Nó vẫn tồn tại và 3 trong 4 route đang được admin console dùng. |

**Một cảnh báo giả đã rút lại.** Bản đầu của tài liệu này liệt kê "sửa bình luận trong app gọi
`PUT /v1/api/content-comments/{id}` mà backend không có". Sai. Endpoint đó **có tồn tại**
(`ContentCommentController.java:67`), chỉ là nó viết annotation ở dạng đầy đủ
`@org.springframework.web.bind.annotation.PutMapping` nên trình quét endpoint bỏ sót.
Đã quét lại toàn bộ `src/main/java`: đây là **chỗ duy nhất** dùng dạng đầy đủ, và không có
`@RequestMapping(method = ...)` nào ở mức method. Tổng số endpoint đúng là **594**, không phải 593.

Rút ra cho lần sau: quét route bằng regex phải bắt cả dạng full-qualified, và mọi lời gọi client
không khớp route phải mở file controller xác nhận trước khi gọi đó là lỗi. Nên thống nhất viết
`@PutMapping` như 594 endpoint còn lại để công cụ không bị mù.

---

## 9. Kiểm chứng

Trạng thái nền tại thời điểm audit:

- `./mvnw -o -q compile -DskipTests` chạy sạch, mã thoát 0.
- 594 endpoint trên 91 controller: 593 annotation dạng ngắn (`@GetMapping`…) cộng 1 dạng đầy đủ
  ở `ContentCommentController.java:67`. Không có `@RequestMapping(method = ...)` ở mức method.
- 160 migration Flyway, tới `V168`.
- 98 class test, 392 test.

Sau mỗi phase, tối thiểu phải chạy:

```
./mvnw -o test
```

và với các thay đổi chạm SQL, thêm test contract mapper XML theo đúng yêu cầu của `AGENTS.md`.
Cập nhật `.agents/docs/` theo `.agents/docs/MAINTENANCE.md` trong cùng thay đổi khi động tới
endpoint, luật truy cập, enum, `BusinessConfigKey`, mã lỗi, migration, job theo lịch,
tích hợp bên ngoài, hoặc bất kỳ ngưỡng và chuyển trạng thái nghiệp vụ nào.
