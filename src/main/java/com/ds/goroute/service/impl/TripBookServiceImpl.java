package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.PatchBookPageSlotRequest;
import com.ds.goroute.dto.request.UpdateBookPageRequest;
import com.ds.goroute.dto.request.UpsertBookSkeletonRequest;
import com.ds.goroute.dto.response.BookPageResponse;
import com.ds.goroute.dto.response.BookSkeletonResponse;
import com.ds.goroute.dto.response.BookSlotDefResponse;
import com.ds.goroute.dto.response.BookSlotResponse;
import com.ds.goroute.dto.response.TripBookResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.BookPage;
import com.ds.goroute.entity.BookPageSlot;
import com.ds.goroute.entity.BookSkeleton;
import com.ds.goroute.entity.BookTemplate;
import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripBook;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.TripNote;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.TripBookRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripNoteRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.TripBookService;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.utils.CitySlugResolver;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripBookServiceImpl implements TripBookService {
    private static final String DEFAULT_BACKGROUND = "asset://templates/default/bg_cream.png";
    private static final DateTimeFormatter COVER_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter PAGE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final ActivityRepository activityRepository;
    private final ExpenseRepository expenseRepository;
    private final TripNoteRepository tripNoteRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final TripBookRepository tripBookRepository;

    @Override
    @Transactional
    public TripBookResponse generateBook(UUID tripId, UUID userId) {
        Trip trip = getTripAndEnsureMember(tripId, userId);
        TripBook book = tripBookRepository.findBookByTripId(tripId).orElseGet(() -> createBook(tripId));
        tripBookRepository.deleteSlotsByBookId(book.getId());
        tripBookRepository.deletePagesByBookId(book.getId());
        tripBookRepository.updateBookStatus(book.getId(), "draft");
        book.setStatus("draft");

        List<Activity> activities = activityRepository.findByTripId(tripId).stream()
                .sorted(activityComparator())
                .toList();
        List<MediaAsset> memories = mediaAssetRepository.findByTripId(tripId).stream()
                .sorted(Comparator.comparing(MediaAsset::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<UUID, List<MediaAsset>> memoriesByActivity = memories.stream()
                .filter(memory -> memory.getActivityId() != null)
                .collect(Collectors.groupingBy(MediaAsset::getActivityId));
        Map<UUID, List<TripNote>> notesByActivity = tripNoteRepository.findByTripId(tripId).stream()
                .filter(note -> note.getActivityId() != null)
                .collect(Collectors.groupingBy(TripNote::getActivityId));
        Map<UUID, BigDecimal> expensesByActivity = expenseRepository.findByTripId(tripId).stream()
                .filter(expense -> expense.getActivityId() != null)
                .collect(Collectors.groupingBy(
                        Expense::getActivityId,
                        Collectors.reducing(BigDecimal.ZERO, this::expenseAmount, BigDecimal::add)
                ));

        int pageOrder = 1;
        insertPage(book.getId(), null, "cover", pageOrder++, "cover_page", null, buildCoverSlots(trip, activities));

        for (Activity activity : activities) {
            BookTemplate template = resolveTemplate(activity, trip).orElse(null);
            List<MediaAsset> activityMemories = memoriesByActivity.getOrDefault(activity.getId(), List.of());
            String noteText = resolveNoteText(activity, notesByActivity.getOrDefault(activity.getId(), List.of()));
            String skeletonId = chooseSkeleton(activityMemories.size(), noteText);
            insertPage(
                    book.getId(),
                    activity.getId(),
                    "place",
                    pageOrder++,
                    skeletonId,
                    template != null ? template.getId() : null,
                    buildActivitySlots(activity, activityMemories, noteText, template, expensesByActivity.get(activity.getId()))
            );
        }

        insertPage(book.getId(), null, "summary", pageOrder, "summary_page", null, buildSummarySlots(trip, activities, memories));
        return toResponse(book);
    }

    @Override
    @Transactional(readOnly = true)
    public TripBookResponse getBook(UUID tripId, UUID userId) {
        getTripAndEnsureMember(tripId, userId);
        TripBook book = tripBookRepository.findBookByTripId(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Travel book not found"));
        return toResponse(book);
    }

    @Override
    @Transactional
    public BookPageResponse updatePage(UUID pageId, UpdateBookPageRequest request, UUID userId) {
        BookPage page = tripBookRepository.findPageById(pageId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Book page not found"));
        TripBook book = tripBookRepository.findBookById(page.getBookId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Travel book not found"));
        getTripAndEnsureMember(book.getTripId(), userId);

        List<BookSlotResponse> currentSlots = parseSlots(page.getSlots());
        Map<String, BookSlotResponse> updatedBySlotId = request.getSlots().stream()
                .filter(slot -> slot.getSlotId() != null && !slot.getSlotId().isBlank())
                .collect(Collectors.toMap(BookSlotResponse::getSlotId, slot -> slot, (first, second) -> second, LinkedHashMap::new));

        List<BookSlotResponse> merged = new ArrayList<>();
        for (BookSlotResponse current : currentSlots) {
            BookSlotResponse replacement = updatedBySlotId.remove(current.getSlotId());
            merged.add(replacement != null ? replacement : current);
        }
        merged.addAll(updatedBySlotId.values());

        upsertPageSlots(pageId, merged);
        updateLegacySlots(pageId);
        page.setSlots(JsonUtils.toJson(merged));
        return toPageResponse(page);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookSkeletonResponse> getSkeletons() {
        return tripBookRepository.findAllSkeletons().stream()
                .map(this::toSkeletonResponse)
                .toList();
    }

    @Override
    @Transactional
    public BookSkeletonResponse upsertSkeleton(UpsertBookSkeletonRequest request) {
        validateSlotDefs(request.getSlotDefs());
        Integer version = request.getVersion() != null ? request.getVersion() : 1;
        BookSkeleton existing = tripBookRepository.findSkeleton(request.getSkeletonKey(), version).orElse(null);
        BookSkeleton skeleton = BookSkeleton.builder()
                .id(existing != null ? existing.getId() : UUID.randomUUID())
                .skeletonKey(request.getSkeletonKey())
                .version(version)
                .name(request.getName())
                .pageType(request.getPageType())
                .photoCount(request.getPhotoCount())
                .canvasWidth(request.getCanvasWidth())
                .canvasHeight(request.getCanvasHeight())
                .slotDefs(JsonUtils.toJson(request.getSlotDefs()))
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();
        if (existing == null) {
            tripBookRepository.insertSkeleton(skeleton);
        } else {
            tripBookRepository.updateSkeleton(skeleton);
        }
        return toSkeletonResponse(skeleton);
    }

    @Override
    @Transactional
    public BookSlotResponse patchPageSlot(UUID pageId, String slotId, PatchBookPageSlotRequest request, UUID userId) {
        BookPage page = tripBookRepository.findPageById(pageId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Book page not found"));
        TripBook book = tripBookRepository.findBookById(page.getBookId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Travel book not found"));
        getTripAndEnsureMember(book.getTripId(), userId);

        BookPageSlot slot = tripBookRepository.findPageSlot(pageId, slotId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Book page slot not found"));
        if (Boolean.TRUE.equals(slot.getLocked())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "This slot is locked");
        }
        if (request.getValue() != null) slot.setValue(valueToString(request.getValue()));
        if (request.getVisible() != null) slot.setVisible(request.getVisible());
        if (request.getFrameStyle() != null) slot.setFrameStyle(request.getFrameStyle());
        if (request.getCropRect() != null) slot.setCropRect(JsonUtils.toJson(request.getCropRect()));
        if (request.getTransform() != null) slot.setTransform(JsonUtils.toJson(request.getTransform()));
        if (request.getStyle() != null) slot.setStyle(JsonUtils.toJson(request.getStyle()));
        if (request.getLocked() != null) slot.setLocked(request.getLocked());
        tripBookRepository.updatePageSlot(slot);
        tripBookRepository.markPageLayoutEdited(pageId);
        updateLegacySlots(pageId);
        return toSlotResponse(slot);
    }

    @Override
    @Transactional
    public BookPageResponse resetPageLayout(UUID pageId, UUID userId) {
        BookPage page = tripBookRepository.findPageById(pageId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Book page not found"));
        TripBook book = tripBookRepository.findBookById(page.getBookId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Travel book not found"));
        getTripAndEnsureMember(book.getTripId(), userId);

        for (BookPageSlot slot : tripBookRepository.findPageSlots(pageId)) {
            slot.setTransform(null);
            tripBookRepository.updatePageSlot(slot);
        }
        tripBookRepository.resetPageLayoutMode(pageId);
        updateLegacySlots(pageId);
        page.setLayoutMode("skeleton");
        return toPageResponse(page);
    }

    private TripBook createBook(UUID tripId) {
        TripBook book = TripBook.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .status("draft")
                .build();
        tripBookRepository.insertBook(book);
        return book;
    }

    private void insertPage(UUID bookId, UUID activityId, String pageType, int pageOrder, String skeletonKey,
                            UUID templateId, List<BookSlotResponse> slots) {
        BookSkeleton skeleton = resolveSkeletonOrThrow(skeletonKey);
        BookPage page = BookPage.builder()
                .id(UUID.randomUUID())
                .bookId(bookId)
                .activityId(activityId)
                .pageType(pageType)
                .pageOrder(pageOrder)
                .skeletonId(skeleton.getSkeletonKey())
                .skeletonKey(skeleton.getSkeletonKey())
                .skeletonVersion(skeleton.getVersion())
                .templateId(templateId)
                .slots(JsonUtils.toJson(slots))
                .layoutMode("skeleton")
                .build();
        tripBookRepository.insertPage(page);
        upsertPageSlots(page.getId(), slots);
    }

    private List<BookSlotResponse> buildCoverSlots(Trip trip, List<Activity> activities) {
        String dates = formatDate(trip.getStartDate(), COVER_DATE_FORMAT) + " -> " + formatDate(trip.getEndDate(), COVER_DATE_FORMAT);
        return List.of(
                slot("background", "background", trip.getCoverImageUrl(), true),
                slot("cover_photo", "photo", trip.getCoverImageUrl(), trip.getCoverImageUrl() != null && !trip.getCoverImageUrl().isBlank(), "polaroid"),
                slot("trip_title", "text", trip.getName(), true),
                slot("trip_dates", "text", dates, true),
                slot("trip_desc", "text", truncate(trip.getDescription(), 120), trip.getDescription() != null && !trip.getDescription().isBlank()),
                slot("member_count", "stat", null, false),
                slot("cities_list", "text", trip.getDestination(), trip.getDestination() != null && !trip.getDestination().isBlank()),
                slot("total_places", "stat", activities.size(), true)
        );
    }

    private List<BookSlotResponse> buildActivitySlots(Activity activity, List<MediaAsset> memories, String noteText,
                                                      BookTemplate template, BigDecimal totalExpense) {
        List<BookSlotResponse> slots = new ArrayList<>();
        slots.add(slot("background", "background", pickBackground(template), true));

        List<String> frameStyles = frameStyles(template);
        int maxPhotoSlots = Math.max(4, memories.size());
        for (int i = 0; i < maxPhotoSlots; i++) {
            MediaAsset memory = i < memories.size() ? memories.get(i) : null;
            slots.add(slot("photo_" + (i + 1), "photo", memory != null ? memory.getUrl() : null,
                    memory != null, pick(frameStyles)));
        }

        slots.add(slot("place_name", "text", activity.getName(), true));
        slots.add(slot("visit_date", "text", visitDate(activity), true));
        slots.add(slot("note_text", "text", truncate(noteText, 150), noteText != null && !noteText.isBlank()));
        slots.add(slot("caption_1", "text", memories.isEmpty() ? null : memories.get(0).getCaption(),
                !memories.isEmpty() && memories.get(0).getCaption() != null && !memories.get(0).getCaption().isBlank()));
        slots.add(slot("rating", "stat", activity.getRating(), activity.getRating() != null));
        slots.add(slot("total_expense", "stat", totalExpense, totalExpense != null && totalExpense.compareTo(BigDecimal.ZERO) > 0));

        List<String> stickers = stickerUrls(template);
        slots.add(slot("sticker_1", "sticker", stickers.isEmpty() ? null : pick(stickers), !stickers.isEmpty()));
        slots.add(slot("sticker_2", "sticker", stickers.size() < 2 ? null : pick(stickers), stickers.size() >= 2));
        return slots;
    }

    private List<BookSlotResponse> buildSummarySlots(Trip trip, List<Activity> activities, List<MediaAsset> memories) {
        BigDecimal totalExpense = expenseRepository.findByTripId(trip.getId()).stream()
                .map(this::expenseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalDays = (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        int totalCities = CitySlugResolver.resolveFromDestination(trip.getDestination()).isPresent() ? 1 : 0;

        return List.of(
                slot("background", "background", DEFAULT_BACKGROUND, true),
                slot("total_days", "stat", Math.max(totalDays, 1), true),
                slot("total_places", "stat", activities.size(), true),
                slot("total_cities", "stat", totalCities, true),
                slot("total_expense", "stat", totalExpense, true),
                slot("highlight_photo", "photo", memories.isEmpty() ? trip.getCoverImageUrl() : memories.get(0).getUrl(),
                        !memories.isEmpty() || trip.getCoverImageUrl() != null, "polaroid"),
                slot("trip_title", "text", trip.getName(), true)
        );
    }

    private Optional<BookTemplate> resolveTemplate(Activity activity, Trip trip) {
        Optional<BookTemplate> placeTemplate = tripBookRepository.findTemplate("place", activity.getPlaceId());
        if (placeTemplate.isPresent()) {
            return placeTemplate;
        }
        String cityRef = CitySlugResolver.resolveFromDestination(activity.getAddress())
                .or(() -> CitySlugResolver.resolveFromDestination(trip.getDestination()))
                .map(city -> city.getSlug())
                .orElse(null);
        return tripBookRepository.findTemplate("city", cityRef);
    }

    private String chooseSkeleton(int photoCount, String noteText) {
        boolean textHeavy = noteText != null && noteText.length() > 100 && photoCount <= 1;
        if (textHeavy) {
            return "text_heavy";
        }
        return switch (photoCount) {
            case 0 -> "no_photo";
            case 1 -> "single_photo";
            case 2 -> "two_photo";
            case 3 -> "three_photo";
            default -> "four_photo";
        };
    }

    private Comparator<Activity> activityComparator() {
        return Comparator
                .comparing(Activity::getDayNumber, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Activity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Activity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Trip getTripAndEnsureMember(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        if (trip.getOwnerId().equals(userId)) {
            return trip;
        }
        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId).orElse(null);
        if (member == null || member.getStatus() != MemberStatus.ACCEPTED) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You are not a trip member");
        }
        return trip;
    }

    private TripBookResponse toResponse(TripBook book) {
        List<BookPageResponse> pages = tripBookRepository.findPagesByBookId(book.getId()).stream()
                .map(this::toPageResponse)
                .toList();
        List<BookSkeletonResponse> skeletons = pages.stream()
                .map(page -> resolveSkeleton(page.getSkeletonKey(), page.getSkeletonVersion()))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(
                        skeleton -> skeleton.getSkeletonKey() + ":" + skeleton.getVersion(),
                        skeleton -> skeleton,
                        (first, second) -> first,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .map(this::toSkeletonResponse)
                .toList();
        return TripBookResponse.builder()
                .bookId(book.getId())
                .tripId(book.getTripId())
                .status(book.getStatus())
                .pages(pages)
                .skeletons(skeletons)
                .build();
    }

    private BookPageResponse toPageResponse(BookPage page) {
        List<BookPageSlot> pageSlots = tripBookRepository.findPageSlots(page.getId());
        List<BookSlotResponse> slots = pageSlots.isEmpty()
                ? parseSlots(page.getSlots())
                : pageSlots.stream().map(this::toSlotResponse).toList();
        return BookPageResponse.builder()
                .pageId(page.getId())
                .pageType(page.getPageType())
                .pageOrder(page.getPageOrder())
                .activityId(page.getActivityId())
                .skeletonId(page.getSkeletonId())
                .skeletonKey(page.getSkeletonKey() != null ? page.getSkeletonKey() : page.getSkeletonId())
                .skeletonVersion(page.getSkeletonVersion())
                .templateId(page.getTemplateId())
                .layoutMode(page.getLayoutMode())
                .slots(slots)
                .build();
    }

    private BookSkeletonResponse toSkeletonResponse(BookSkeleton skeleton) {
        return BookSkeletonResponse.builder()
                .id(skeleton.getId())
                .skeletonKey(skeleton.getSkeletonKey())
                .version(skeleton.getVersion())
                .name(skeleton.getName())
                .pageType(skeleton.getPageType())
                .photoCount(skeleton.getPhotoCount())
                .canvasWidth(skeleton.getCanvasWidth())
                .canvasHeight(skeleton.getCanvasHeight())
                .isActive(skeleton.getIsActive())
                .slotDefs(parseSlotDefs(skeleton.getSlotDefs()))
                .build();
    }

    private BookSlotResponse toSlotResponse(BookPageSlot slot) {
        return BookSlotResponse.builder()
                .slotId(slot.getSlotId())
                .type(slot.getType())
                .value(slot.getValue())
                .visible(slot.getVisible())
                .frameStyle(slot.getFrameStyle())
                .cropRect(parseMap(slot.getCropRect()))
                .transform(parseMap(slot.getTransform()))
                .style(parseMap(slot.getStyle()))
                .locked(slot.getLocked())
                .build();
    }

    private List<BookSlotDefResponse> parseSlotDefs(String slotDefsJson) {
        if (slotDefsJson == null || slotDefsJson.isBlank()) {
            return List.of();
        }
        List<BookSlotDefResponse> slotDefs = JsonUtils.fromJson(slotDefsJson, new TypeReference<List<BookSlotDefResponse>>() {});
        return slotDefs != null ? slotDefs : List.of();
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
    }

    private List<BookSlotResponse> parseSlots(String slotsJson) {
        if (slotsJson == null || slotsJson.isBlank()) {
            return List.of();
        }
        List<BookSlotResponse> slots = JsonUtils.fromJson(slotsJson, new TypeReference<List<BookSlotResponse>>() {});
        return slots != null ? slots : List.of();
    }

    private void upsertPageSlots(UUID pageId, List<BookSlotResponse> slots) {
        for (BookSlotResponse slotResponse : slots) {
            BookPageSlot existing = tripBookRepository.findPageSlot(pageId, slotResponse.getSlotId()).orElse(null);
            BookPageSlot slot = toPageSlot(pageId, slotResponse, existing != null ? existing.getId() : UUID.randomUUID());
            if (existing == null) {
                tripBookRepository.insertPageSlot(slot);
            } else {
                tripBookRepository.updatePageSlot(slot);
            }
        }
    }

    private BookPageSlot toPageSlot(UUID pageId, BookSlotResponse slot, UUID id) {
        return BookPageSlot.builder()
                .id(id)
                .pageId(pageId)
                .slotId(slot.getSlotId())
                .type(slot.getType())
                .value(valueToString(slot.getValue()))
                .visible(slot.getVisible() == null || slot.getVisible())
                .frameStyle(slot.getFrameStyle())
                .cropRect(slot.getCropRect() != null ? JsonUtils.toJson(slot.getCropRect()) : null)
                .transform(slot.getTransform() != null ? JsonUtils.toJson(slot.getTransform()) : null)
                .style(slot.getStyle() != null ? JsonUtils.toJson(slot.getStyle()) : null)
                .locked(slot.getLocked() != null && slot.getLocked())
                .build();
    }

    private void updateLegacySlots(UUID pageId) {
        List<BookSlotResponse> slots = tripBookRepository.findPageSlots(pageId).stream()
                .map(this::toSlotResponse)
                .toList();
        tripBookRepository.updatePageSlots(pageId, JsonUtils.toJson(slots));
    }

    private String valueToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return value.toString();
    }

    private BookSkeleton resolveSkeletonOrThrow(String skeletonKey) {
        return tripBookRepository.findActiveSkeleton(skeletonKey)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Book skeleton not found: " + skeletonKey));
    }

    private Optional<BookSkeleton> resolveSkeleton(String skeletonKey, Integer version) {
        if (skeletonKey == null || skeletonKey.isBlank()) {
            return Optional.empty();
        }
        if (version != null) {
            Optional<BookSkeleton> versioned = tripBookRepository.findSkeleton(skeletonKey, version);
            if (versioned.isPresent()) {
                return versioned;
            }
        }
        return tripBookRepository.findActiveSkeleton(skeletonKey);
    }

    private void validateSlotDefs(List<BookSlotDefResponse> slotDefs) {
        Set<String> seen = new HashSet<>();
        for (BookSlotDefResponse slotDef : slotDefs) {
            if (slotDef.getSlotId() == null || slotDef.getSlotId().isBlank()) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "slotId is required");
            }
            if (!seen.add(slotDef.getSlotId())) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Duplicate slotId: " + slotDef.getSlotId());
            }
            if (slotDef.getType() == null || slotDef.getType().isBlank()) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Slot type is required: " + slotDef.getSlotId());
            }
            if (slotDef.getW() == null || slotDef.getW() <= 0 || slotDef.getH() == null || slotDef.getH() <= 0) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Slot size must be positive: " + slotDef.getSlotId());
            }
        }
    }

    private BookSlotResponse slot(String slotId, String type, Object value, boolean visible) {
        return slot(slotId, type, value, visible, null);
    }

    private BookSlotResponse slot(String slotId, String type, Object value, boolean visible, String frameStyle) {
        return BookSlotResponse.builder()
                .slotId(slotId)
                .type(type)
                .value(value)
                .visible(visible)
                .frameStyle(frameStyle)
                .build();
    }

    private String pickBackground(BookTemplate template) {
        List<String> backgrounds = jsonStringList(template != null ? template.getBackgrounds() : null);
        return backgrounds.isEmpty() ? DEFAULT_BACKGROUND : pick(backgrounds);
    }

    private List<String> frameStyles(BookTemplate template) {
        List<String> styles = jsonStringList(template != null ? template.getFrameStyles() : null);
        return styles.isEmpty() ? List.of("polaroid") : styles;
    }

    private List<String> stickerUrls(BookTemplate template) {
        if (template == null || template.getStickers() == null || template.getStickers().isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> stickers = JsonUtils.fromJson(template.getStickers(), new TypeReference<List<Map<String, Object>>>() {});
        if (stickers == null) {
            return List.of();
        }
        return stickers.stream()
                .map(sticker -> sticker.get("url"))
                .filter(value -> value != null && !value.toString().isBlank())
                .map(Object::toString)
                .toList();
    }

    private List<String> jsonStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> values = JsonUtils.fromJson(json, new TypeReference<List<String>>() {});
        return values != null ? values : List.of();
    }

    private String pick(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private String resolveNoteText(Activity activity, List<TripNote> activityNotes) {
        if (activity.getNotes() != null && !activity.getNotes().isBlank()) {
            return activity.getNotes();
        }
        if (!activityNotes.isEmpty() && activityNotes.get(0).getContent() != null) {
            return activityNotes.get(0).getContent();
        }
        return activity.getDescription();
    }

    private String visitDate(Activity activity) {
        if (activity.getStartingPointDate() != null) {
            return activity.getStartingPointDate().format(PAGE_DATE_FORMAT);
        }
        if (activity.getDayNumber() != null) {
            return "Day " + activity.getDayNumber();
        }
        return "";
    }

    private String formatDate(LocalDate date, DateTimeFormatter formatter) {
        return date != null ? date.format(formatter) : "";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)).trim() + "...";
    }

    private BigDecimal expenseAmount(Expense expense) {
        if (expense.getAmountInTripCurrency() != null) {
            return expense.getAmountInTripCurrency();
        }
        return expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO;
    }
}
