package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AddCartItemRequest;
import com.ds.goroute.dto.response.CartItemResponse;
import com.ds.goroute.dto.response.CartResponse;
import com.ds.goroute.dto.response.MarketplaceQuoteResponse;
import com.ds.goroute.entity.*;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.MarketplaceCartRepository;
import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.service.HotelMarketplaceService;
import com.ds.goroute.service.MarketplaceCartService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The guest's cart.
 *
 * <p>Adding is validated against the same rules that would book the line, so a guest can never
 * park something that is not sellable. Reading re-quotes every line, because the calendar moves
 * underneath a cart that sits for a day.
 *
 * <p>There is deliberately no "check out the whole cart" command. Each line belongs to a
 * different partner and takes its own inventory under its own hold; one transaction spanning
 * them would either lock several partners' calendars together or leave half a cart booked on a
 * mid-way failure. The client places each line through the ordinary booking endpoint — which
 * already carries idempotency, pricing and notifications — and removes it on success.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceCartServiceImpl implements MarketplaceCartService {

    /** A cart is a shortlist, not a wishlist: past this the read cost stops being trivial. */
    private static final int MAX_ITEMS = 30;
    private static final String HOTEL = "HOTEL";
    private static final String ACTIVITY = "ACTIVITY";

    private final MarketplaceCartRepository repository;
    private final HotelMarketplaceRepository hotelRepository;
    private final ActivityCommerceRepository activityRepository;
    private final HotelMarketplaceService hotelService;
    private final ActivityCommerceService activityService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CartResponse addItem(UUID userId, AddCartItemRequest request) {
        String itemType = normalizedType(request.getItemType());
        MarketplaceCartItem item = HOTEL.equals(itemType) ? hotelItem(userId, request) : activityItem(userId, request);

        // Only a genuinely new line counts against the cap; re-adding an existing selection
        // updates it in place and must not be refused because the cart happens to be full.
        boolean isNew = repository.findItemBySelection(userId, item.getSelectionHash()).isEmpty();
        if (isNew && repository.countItemsByUser(userId) >= MAX_ITEMS) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST,
                    "Your cart is full (" + MAX_ITEMS + " items). Book or remove something first.");
        }

        MarketplaceQuoteResponse quote = quote(item);
        if (!quote.isAvailable()) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, quote.getUnavailableReason());
        }
        repository.upsertItem(item);
        return getCart(userId);
    }

    @Override
    public CartResponse getCart(UUID userId) {
        List<CartItemResponse> items = repository.findItemsByUser(userId).stream().map(this::describe).toList();
        int unavailable = (int) items.stream().filter(item -> !item.isAvailable()).count();

        // Mixed currencies have no honest sum, so the client falls back to per-line prices.
        Set<String> currencies = items.stream().filter(CartItemResponse::isAvailable)
                .map(CartItemResponse::getCurrency).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        String currency = currencies.size() == 1 ? currencies.iterator().next() : null;
        BigDecimal total = currency == null ? null : items.stream()
                .filter(CartItemResponse::isAvailable)
                .map(CartItemResponse::getTotalAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder().items(items).itemCount(items.size())
                .unavailableCount(unavailable).currency(currency).estimatedTotal(total).build();
    }

    @Override
    public long countItems(UUID userId) {
        return repository.countItemsByUser(userId);
    }

    @Override
    @Transactional
    public CartResponse removeItem(UUID userId, UUID itemId) {
        // Removing something already gone is the outcome the caller wanted, not an error:
        // a double tap on the delete button must not surface a 404.
        repository.deleteItem(userId, itemId);
        return getCart(userId);
    }

    @Override
    @Transactional
    public CartResponse clear(UUID userId) {
        repository.deleteItemsByUser(userId);
        return getCart(userId);
    }

    // --- building ---------------------------------------------------------------------------

    private String normalizedType(String value) {
        String itemType = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!HOTEL.equals(itemType) && !ACTIVITY.equals(itemType)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "itemType must be HOTEL or ACTIVITY");
        }
        return itemType;
    }

    private MarketplaceCartItem hotelItem(UUID userId, AddCartItemRequest request) {
        require(request.getHotelId() != null && request.getRoomTypeId() != null && request.getRatePlanId() != null,
                "hotelId, roomTypeId and ratePlanId are required for a hotel item");
        require(request.getCheckInDate() != null && request.getCheckOutDate() != null
                && request.getCheckOutDate().isAfter(request.getCheckInDate()), "Invalid check-in/check-out dates");
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        int adults = request.getAdults() == null ? 1 : request.getAdults();
        int children = request.getChildren() == null ? 0 : request.getChildren();
        LocalDateTime now = LocalDateTime.now();
        return MarketplaceCartItem.builder()
                .id(UUID.randomUUID()).userId(userId).itemType(HOTEL)
                .selectionHash(hash(HOTEL, request.getHotelId(), request.getRoomTypeId(), request.getRatePlanId(),
                        request.getCheckInDate(), request.getCheckOutDate(), quantity, adults, children))
                .hotelId(request.getHotelId()).roomTypeId(request.getRoomTypeId()).ratePlanId(request.getRatePlanId())
                .checkInDate(request.getCheckInDate()).checkOutDate(request.getCheckOutDate())
                .quantity(quantity).adults(adults).children(children)
                .specialRequests(blankToNull(request.getSpecialRequests()))
                .createdAt(now).updatedAt(now).build();
    }

    private MarketplaceCartItem activityItem(UUID userId, AddCartItemRequest request) {
        require(request.getActivityId() != null && request.getPackageId() != null && request.getSlotId() != null,
                "activityId, packageId and slotId are required for an activity item");
        Map<String, Integer> units = normalizedUnits(request.getUnitQuantities(), request.getQuantity());
        LocalDateTime now = LocalDateTime.now();
        return MarketplaceCartItem.builder()
                .id(UUID.randomUUID()).userId(userId).itemType(ACTIVITY)
                .selectionHash(hash(ACTIVITY, request.getActivityId(), request.getPackageId(), request.getSlotId(), units))
                .activityId(request.getActivityId()).packageId(request.getPackageId()).slotId(request.getSlotId())
                .unitQuantities(writeJson(units))
                .specialRequests(blankToNull(request.getSpecialRequests()))
                .createdAt(now).updatedAt(now).build();
    }

    /**
     * Unit codes are upper-cased and zero lines dropped so that two spellings of the same
     * selection hash the same and collapse onto one cart line. A package without units is
     * stored as the GENERAL pseudo-unit, which is how the order endpoint reads a plain quantity.
     */
    private Map<String, Integer> normalizedUnits(Map<String, Integer> requested, Integer quantity) {
        Map<String, Integer> units = new TreeMap<>();
        if (requested != null) {
            requested.forEach((code, value) -> {
                if (code == null || value == null || value <= 0) return;
                units.merge(code.trim().toUpperCase(Locale.ROOT), value, Integer::sum);
            });
        }
        if (units.isEmpty()) units.put("GENERAL", quantity == null || quantity < 1 ? 1 : quantity);
        return units;
    }

    // --- reading ----------------------------------------------------------------------------

    private MarketplaceQuoteResponse quote(MarketplaceCartItem item) {
        if (HOTEL.equals(item.getItemType())) {
            return hotelService.quoteStay(item.getHotelId(), item.getRoomTypeId(), item.getRatePlanId(),
                    item.getCheckInDate(), item.getCheckOutDate(),
                    item.getQuantity(), item.getAdults(), item.getChildren());
        }
        Map<String, Integer> units = readUnits(item);
        Integer general = units.get("GENERAL");
        return activityService.quoteActivity(item.getActivityId(), item.getPackageId(), item.getSlotId(),
                general != null ? Map.of() : units, general);
    }

    private CartItemResponse describe(MarketplaceCartItem item) {
        MarketplaceQuoteResponse quote = quote(item);
        CartItemResponse.CartItemResponseBuilder builder = CartItemResponse.builder()
                .id(item.getId()).itemType(item.getItemType())
                .specialRequests(item.getSpecialRequests()).createdAt(item.getCreatedAt())
                .available(quote.isAvailable()).unavailableReason(quote.getUnavailableReason())
                .currency(quote.getCurrency()).totalAmount(quote.getTotalAmount());

        if (HOTEL.equals(item.getItemType())) {
            HotelProfile hotel = hotelRepository.findPublicHotel(item.getHotelId()).orElse(null);
            RoomType room = hotelRepository.findRoomType(item.getRoomTypeId()).orElse(null);
            RatePlan rate = hotelRepository.findRatePlan(item.getRatePlanId()).orElse(null);
            return builder
                    .title(hotel == null ? null : hotel.getPlaceTitle())
                    .subtitle(joinNonBlank(room == null ? null : room.getName(), rate == null ? null : rate.getName()))
                    .thumbnailUrl(hotel == null ? null : hotel.getPlaceThumbnail())
                    .productId(item.getHotelId())
                    .hotelId(item.getHotelId()).roomTypeId(item.getRoomTypeId()).ratePlanId(item.getRatePlanId())
                    .checkInDate(item.getCheckInDate()).checkOutDate(item.getCheckOutDate())
                    .quantity(item.getQuantity()).adults(item.getAdults()).children(item.getChildren())
                    .build();
        }

        MarketplaceActivityProduct activity = activityRepository.findPublicProduct(item.getActivityId()).orElse(null);
        ActivityPackage pack = activityRepository.findPackage(item.getPackageId()).orElse(null);
        ActivitySlot slot = activityRepository.findSlot(item.getSlotId()).orElse(null);
        Map<String, Integer> units = readUnits(item);
        return builder
                .title(activity == null ? null : activity.getTitle())
                .subtitle(pack == null ? null : pack.getName())
                .thumbnailUrl(activity == null ? null : activity.getThumbnail())
                .productId(item.getActivityId())
                .activityId(item.getActivityId()).packageId(item.getPackageId()).slotId(item.getSlotId())
                .slotStartsAt(slot == null ? null : slot.getStartsAt())
                .unitQuantities(units)
                .quantity(units.values().stream().mapToInt(Integer::intValue).sum())
                .build();
    }

    // --- helpers ----------------------------------------------------------------------------

    /**
     * Identity of a selection. Built from the fields that make two lines the same booking, so
     * re-adding a stay the guest already parked updates that line instead of duplicating it.
     */
    private String hash(Object... parts) {
        String raw = Arrays.stream(parts).map(String::valueOf).reduce("", (a, b) -> a + "|" + b);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private Map<String, Integer> readUnits(MarketplaceCartItem item) {
        if (item.getUnitQuantities() == null || item.getUnitQuantities().isBlank()) return Map.of();
        try {
            return objectMapper.readValue(item.getUnitQuantities(), new TypeReference<Map<String, Integer>>() {});
        } catch (Exception exception) {
            log.warn("Unreadable cart unit quantities on item {}", item.getId(), exception);
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Invalid unit quantities");
        }
    }

    private String joinNonBlank(String... values) {
        String joined = String.join(" · ", Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList());
        return joined.isBlank() ? null : joined;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(ErrorConstant.BAD_REQUEST, message);
    }
}
