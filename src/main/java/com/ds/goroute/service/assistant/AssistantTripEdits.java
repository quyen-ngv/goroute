package com.ds.goroute.service.assistant;

import com.ds.goroute.dto.request.AssistantReplyRequest;
import com.ds.goroute.dto.request.CreateActivityRequest;
import com.ds.goroute.dto.request.CreateExpenseRequest;
import com.ds.goroute.dto.request.CreateTripNoteRequest;
import com.ds.goroute.dto.request.ExpenseSplitRequest;
import com.ds.goroute.dto.request.InviteMemberRequest;
import com.ds.goroute.dto.request.UpdateActivityRequest;
import com.ds.goroute.dto.request.UpdateExpenseRequest;
import com.ds.goroute.dto.request.UpdateTripRequest;
import com.ds.goroute.dto.response.ActivityResponse;
import com.ds.goroute.dto.response.ExpenseResponse;
import com.ds.goroute.dto.response.ExpenseSplitResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ActivityService;
import com.ds.goroute.service.ExpenseService;
import com.ds.goroute.service.TextModerationService;
import com.ds.goroute.service.TripNoteService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.type.AssistantProposalAction;
import com.ds.goroute.type.ExpenseCategory;
import com.ds.goroute.type.MemberRole;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.type.TransportMode;
import com.ds.goroute.type.TripVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The proposals that edit a trip's information rather than its days: the trip's own fields, an
 * activity's fields, an activity that is not a catalogue place, expenses, notes and guests. They
 * usually come from something a member pasted or photographed (a receipt, a ticket, a note).
 *
 * <p>Building reads every value the worker sent, keeps only the fields the action takes, checks
 * each one, drops values equal to what the trip already has, and stores the result twice in the
 * payload: {@code changes} (what applying writes) and {@code lines} (field, before, after, for the
 * card). Applying replays {@code changes} through the normal services as the person who taps.
 */
@Component
@RequiredArgsConstructor
class AssistantTripEdits {

    static final int MAX_TEXT = 2000;
    static final int MAX_NAME = 255;
    static final int MAX_ADDRESS = 500;
    static final int MAX_EXPENSE_DESCRIPTION = 500;
    static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");
    private static final Pattern CATEGORY = Pattern.compile("[a-z_]{2,40}");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private final ActivityRepository activities;
    private final TripMemberRepository members;
    private final UserRepository users;
    private final ActivityService activityService;
    private final ExpenseService expenseService;
    private final TripService tripService;
    private final TripNoteService noteService;
    private final TextModerationService moderation;

    /** The person a payer or a split names: a registered user or a guest member. */
    record Person(UUID userId, UUID guestMemberId, String name) {
        Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            if (userId != null) out.put("userId", userId.toString());
            if (guestMemberId != null) out.put("guestMemberId", guestMemberId.toString());
            out.put("name", name);
            return out;
        }

        static Person from(Object raw) {
            if (!(raw instanceof Map<?, ?> map)) return null;
            UUID user = AssistantProposalService.uuid(map.get("userId"));
            UUID guest = AssistantProposalService.uuid(map.get("guestMemberId"));
            return user == null && guest == null ? null
                    : new Person(user, guest, map.get("name") == null ? "" : map.get("name").toString());
        }
    }

    // ------------------------------------------------------------------ build

    /**
     * Fills {@code payload} for one of the trip-information actions; false when the proposal is
     * not valid (or changes nothing) and must be dropped.
     *
     * @param lastDay the last day an activity may be added to, counting an ADD_DAYS of the same answer
     */
    boolean build(AssistantProposalAction action, Trip trip, UUID askerId, int lastDay,
                  AssistantReplyRequest.ProposalDraft draft, Map<String, Object> payload) {
        Map<String, Object> in = draft.getChanges() == null ? Map.of() : draft.getChanges();
        Map<String, Object> changes = new LinkedHashMap<>();
        List<Map<String, Object>> lines = new ArrayList<>();
        boolean valid = switch (action) {
            case EDIT_TRIP -> buildTrip(trip, in, changes, lines);
            case EDIT_ACTIVITY -> buildActivityEdit(trip, draft, in, changes, lines, payload);
            case NEW_ACTIVITY -> buildNewActivity(trip, draft, lastDay, in, changes, lines, payload);
            case ADD_EXPENSE -> buildNewExpense(trip, askerId, draft, in, changes, lines, payload);
            case EDIT_EXPENSE -> buildExpenseEdit(trip, askerId, draft, in, changes, lines, payload);
            case REMOVE_EXPENSE -> buildExpenseRemoval(trip, askerId, draft, lines, payload);
            case ADD_NOTE -> buildNote(trip, draft, in, changes, lines, payload);
            case ADD_GUEST -> buildGuest(trip, in, changes, lines);
            default -> false;
        };
        if (!valid) return false;
        payload.put("changes", changes);
        payload.put("lines", lines);
        return true;
    }

    private boolean buildTrip(Trip trip, Map<String, Object> in, Map<String, Object> changes,
                              List<Map<String, Object>> lines) {
        String currency = trip.getCurrency() == null ? "VND" : trip.getCurrency();
        putText(in, "name", MAX_NAME, trip.getName(), ModeratedContentType.TRIP, ModerationVisibility.PUBLIC,
                changes, lines);
        putText(in, "description", MAX_TEXT, trip.getDescription(), ModeratedContentType.TRIP,
                ModerationVisibility.PUBLIC, changes, lines);
        String newCurrency = currency(in.get("currency"));
        if (newCurrency != null && !newCurrency.equals(currency)) {
            changes.put("currency", newCurrency);
            lines.add(line("currency", currency, newCurrency, null));
        }
        BigDecimal budget = amount(in.get("budget"), true);
        if (budget != null && (trip.getBudget() == null || budget.compareTo(trip.getBudget()) != 0)) {
            changes.put("budget", budget.toPlainString());
            lines.add(line("budget", plain(trip.getBudget()), budget.toPlainString(),
                    newCurrency != null ? newCurrency : currency));
        }
        TripVisibility visibility = enumValue(TripVisibility.class, in.get("visibility"));
        String oldVisibility = trip.getVisibility() == null ? null : trip.getVisibility().toString();
        if (visibility != null && !visibility.name().equals(oldVisibility)) {
            changes.put("visibility", visibility.name());
            lines.add(line("visibility", oldVisibility, visibility.name(), null));
        }
        putText(in, "startingPointName", MAX_NAME, trip.getStartingPointName(), ModeratedContentType.TRIP,
                ModerationVisibility.GROUP, changes, lines);
        putText(in, "startingPointAddress", MAX_ADDRESS, trip.getStartingPointAddress(), ModeratedContentType.TRIP,
                ModerationVisibility.GROUP, changes, lines);
        LocalDateTime start = dateTime(in.get("startingPointTime"));
        if (start != null && !start.equals(trip.getStartingPointTime())) {
            changes.put("startingPointTime", start.toString());
            lines.add(line("startingPointTime",
                    trip.getStartingPointTime() == null ? null : trip.getStartingPointTime().toString(),
                    start.toString(), null));
        }
        return !changes.isEmpty();
    }

    private boolean buildActivityEdit(Trip trip, AssistantReplyRequest.ProposalDraft draft, Map<String, Object> in,
                                      Map<String, Object> changes, List<Map<String, Object>> lines,
                                      Map<String, Object> payload) {
        Activity activity = activityOfTrip(trip.getId(), AssistantProposalService.uuid(draft.getActivityId()));
        if (activity == null) return false;
        activityFields(in, activity, changes, lines);
        putTimes(draft, activity.getStartTime(), activity.getEndTime(), changes, lines);
        if (changes.isEmpty()) return false;
        payload.put("activityId", activity.getId().toString());
        payload.put("activityName", activity.getName());
        payload.put("dayNumber", activity.getDayNumber());
        return true;
    }

    private boolean buildNewActivity(Trip trip, AssistantReplyRequest.ProposalDraft draft, int lastDay,
                                     Map<String, Object> in, Map<String, Object> changes,
                                     List<Map<String, Object>> lines, Map<String, Object> payload) {
        Integer day = draft.getDayNumber();
        if (day == null || day < 1 || day > lastDay) return false;
        activityFields(in, null, changes, lines);
        if (!changes.containsKey("name")) return false;
        putTimes(draft, null, null, changes, lines);
        changes.putIfAbsent("category", changes.containsKey("transportMode") ? "transport" : "attraction");
        payload.put("dayNumber", day);
        payload.put("activityName", changes.get("name"));
        payload.put("category", changes.get("category"));
        return true;
    }

    /** The editable fields of an activity; {@code current} null for a new one. */
    private void activityFields(Map<String, Object> in, Activity current, Map<String, Object> changes,
                                List<Map<String, Object>> lines) {
        putText(in, "name", MAX_NAME, current == null ? null : current.getName(), ModeratedContentType.ACTIVITY,
                ModerationVisibility.GROUP, changes, lines);
        putText(in, "address", MAX_ADDRESS, current == null ? null : current.getAddress(),
                ModeratedContentType.ACTIVITY, ModerationVisibility.GROUP, changes, lines);
        putText(in, "endAddress", MAX_ADDRESS, current == null ? null : current.getEndAddress(),
                ModeratedContentType.ACTIVITY, ModerationVisibility.GROUP, changes, lines);
        putText(in, "notes", MAX_TEXT, current == null ? null : current.getNotes(), ModeratedContentType.ACTIVITY,
                ModerationVisibility.GROUP, changes, lines);
        putText(in, "description", MAX_TEXT, current == null ? null : current.getDescription(),
                ModeratedContentType.ACTIVITY, ModerationVisibility.GROUP, changes, lines);
        String category = in.get("category") == null ? null : in.get("category").toString().trim().toLowerCase(Locale.ROOT);
        if (category != null && CATEGORY.matcher(category).matches()
                && (current == null || !category.equals(current.getCategory()))) {
            changes.put("category", category);
            lines.add(line("category", current == null ? null : current.getCategory(), category, null));
        }
        TransportMode mode = enumValue(TransportMode.class, in.get("transportMode"));
        String oldMode = current == null ? null : current.getTransportMode() == null ? null : current.getTransportMode().toString();
        if (mode != null && !mode.name().equals(oldMode)) {
            changes.put("transportMode", mode.name());
            lines.add(line("transportMode", oldMode, mode.name(), null));
        }
        String oldCurrency = current == null ? null : current.getCostCurrency();
        String costCurrency = currency(in.get("costCurrency"));
        BigDecimal cost = amount(in.get("cost"), true);
        if (cost != null && (current == null || current.getEstimatedCost() == null
                || cost.compareTo(current.getEstimatedCost()) != 0
                || (costCurrency != null && !costCurrency.equals(oldCurrency)))) {
            changes.put("cost", cost.toPlainString());
            if (costCurrency != null) changes.put("costCurrency", costCurrency);
            lines.add(line("cost", current == null ? null : plain(current.getEstimatedCost()), cost.toPlainString(),
                    costCurrency != null ? costCurrency : oldCurrency));
        }
        Boolean stay = bool(in.get("stay"));
        boolean wasStay = current != null && Boolean.TRUE.equals(current.getIsAccommodation());
        if (stay != null && stay != wasStay) {
            changes.put("stay", stay);
            lines.add(line("stay", current == null ? null : String.valueOf(wasStay), String.valueOf(stay), null));
        }
    }

    private static void putTimes(AssistantReplyRequest.ProposalDraft draft, LocalTime oldStart, LocalTime oldEnd,
                                 Map<String, Object> changes, List<Map<String, Object>> lines) {
        LocalTime start = time(draft.getStartTime());
        LocalTime end = time(draft.getEndTime());
        LocalTime effectiveStart = start != null ? start : oldStart;
        if (end != null && (effectiveStart == null || !end.isAfter(effectiveStart))) end = null;
        if (start != null && !start.equals(oldStart)) {
            changes.put("startTime", start.toString());
            lines.add(line("startTime", oldStart == null ? null : oldStart.toString(), start.toString(), null));
        }
        if (end != null && !end.equals(oldEnd)) {
            changes.put("endTime", end.toString());
            lines.add(line("endTime", oldEnd == null ? null : oldEnd.toString(), end.toString(), null));
        }
    }

    private boolean buildNewExpense(Trip trip, UUID askerId, AssistantReplyRequest.ProposalDraft draft,
                                    Map<String, Object> in, Map<String, Object> changes,
                                    List<Map<String, Object>> lines, Map<String, Object> payload) {
        BigDecimal amount = amount(in.get("amount"), false);
        if (amount == null) return false;
        String currency = Optional.ofNullable(currency(in.get("currency")))
                .orElse(trip.getCurrency() == null ? "VND" : trip.getCurrency());
        amount = amount.setScale(minorDigits(currency), RoundingMode.HALF_UP);
        if (amount.signum() <= 0) return false;
        Map<String, Person> people = people(trip);
        Person payer = in.containsKey("paidBy") ? person(people, in.get("paidBy")) : people.get(askerId.toString());
        if (payer == null) return false;
        List<Person> split = splitPeople(people, in.get("splitWith"));
        if (split.isEmpty()) return false;
        ExpenseCategory category = Optional.ofNullable(enumValue(ExpenseCategory.class, in.get("category")))
                .orElse(ExpenseCategory.OTHER);
        changes.put("amount", amount.toPlainString());
        changes.put("currency", currency);
        changes.put("category", category.name());
        lines.add(line("amount", null, amount.toPlainString(), currency));
        lines.add(line("expenseCategory", null, category.name(), null));
        String description = checkedText(in.get("description"), MAX_EXPENSE_DESCRIPTION,
                ModeratedContentType.EXPENSE, ModerationVisibility.GROUP);
        if (description != null) {
            changes.put("description", description);
            lines.add(line("description", null, description, null));
        }
        LocalDate date = expenseDate(in.get("date"));
        if (in.get("date") != null && date == null) return false;
        if (date != null) {
            changes.put("date", date.toString());
            lines.add(line("date", null, date.toString(), null));
        }
        Activity activity = activityOfTrip(trip.getId(), AssistantProposalService.uuid(draft.getActivityId()));
        if (activity != null) {
            changes.put("activityId", activity.getId().toString());
            payload.put("activityId", activity.getId().toString());
            payload.put("activityName", activity.getName());
        }
        changes.put("payer", payer.toMap());
        changes.put("split", split.stream().map(Person::toMap).toList());
        lines.add(line("paidBy", null, payer.name(), null));
        lines.add(line("splitWith", null, String.join(", ", split.stream().map(Person::name).toList()), null));
        return true;
    }

    private boolean buildExpenseEdit(Trip trip, UUID askerId, AssistantReplyRequest.ProposalDraft draft,
                                     Map<String, Object> in, Map<String, Object> changes,
                                     List<Map<String, Object>> lines, Map<String, Object> payload) {
        ExpenseResponse expense = expenseOfTrip(trip.getId(), AssistantProposalService.uuid(draft.getExpenseId()), askerId);
        if (expense == null) return false;
        String currency = expense.getCurrency() == null ? "VND" : expense.getCurrency();
        BigDecimal amount = amount(in.get("amount"), false);
        if (amount != null) amount = amount.setScale(minorDigits(currency), RoundingMode.HALF_UP);
        if (amount != null && amount.signum() > 0 && amount.compareTo(expense.getAmount()) != 0) {
            changes.put("amount", amount.toPlainString());
            lines.add(line("amount", plain(expense.getAmount()), amount.toPlainString(), currency));
        }
        ExpenseCategory category = enumValue(ExpenseCategory.class, in.get("category"));
        if (category != null && !category.name().equals(expense.getCategory())) {
            changes.put("category", category.name());
            lines.add(line("expenseCategory", expense.getCategory(), category.name(), null));
        }
        String description = checkedText(in.get("description"), MAX_EXPENSE_DESCRIPTION,
                ModeratedContentType.EXPENSE, ModerationVisibility.GROUP);
        if (description != null && !description.equals(expense.getDescription())) {
            changes.put("description", description);
            lines.add(line("description", expense.getDescription(), description, null));
        }
        LocalDate date = expenseDate(in.get("date"));
        LocalDate oldDate = expense.getExpenseDate() == null ? null : expense.getExpenseDate().toLocalDate();
        if (date != null && !date.equals(oldDate)) {
            changes.put("date", date.toString());
            lines.add(line("date", oldDate == null ? null : oldDate.toString(), date.toString(), null));
        }
        if (in.containsKey("paidBy")) {
            Map<String, Person> people = people(trip);
            Person payer = person(people, in.get("paidBy"));
            Person oldPayer = payerOf(expense, people);
            if (payer != null && !payer.equals(oldPayer)) {
                changes.put("payer", payer.toMap());
                lines.add(line("paidBy", oldPayer == null ? null : oldPayer.name(), payer.name(), null));
            }
        }
        if (changes.isEmpty()) return false;
        putExpense(payload, expense);
        return true;
    }

    private boolean buildExpenseRemoval(Trip trip, UUID askerId, AssistantReplyRequest.ProposalDraft draft,
                                        List<Map<String, Object>> lines, Map<String, Object> payload) {
        ExpenseResponse expense = expenseOfTrip(trip.getId(), AssistantProposalService.uuid(draft.getExpenseId()), askerId);
        if (expense == null) return false;
        putExpense(payload, expense);
        lines.add(line("amount", plain(expense.getAmount()), null, expense.getCurrency()));
        return true;
    }

    private boolean buildNote(Trip trip, AssistantReplyRequest.ProposalDraft draft, Map<String, Object> in,
                              Map<String, Object> changes, List<Map<String, Object>> lines,
                              Map<String, Object> payload) {
        String content = checkedText(in.get("content"), MAX_TEXT, ModeratedContentType.TRIP_NOTE,
                ModerationVisibility.GROUP);
        if (content == null) return false;
        changes.put("content", content);
        lines.add(line("note", null, content, null));
        Activity activity = activityOfTrip(trip.getId(), AssistantProposalService.uuid(draft.getActivityId()));
        if (activity != null) {
            changes.put("activityId", activity.getId().toString());
            payload.put("activityId", activity.getId().toString());
            payload.put("activityName", activity.getName());
            payload.put("dayNumber", activity.getDayNumber());
        }
        return true;
    }

    private boolean buildGuest(Trip trip, Map<String, Object> in, Map<String, Object> changes,
                               List<Map<String, Object>> lines) {
        String name = checkedText(in.get("guestName"), MAX_NAME, ModeratedContentType.TRIP, ModerationVisibility.GROUP);
        if (name == null) return false;
        boolean taken = people(trip).values().stream()
                .anyMatch(person -> person.name() != null && person.name().equalsIgnoreCase(name));
        if (taken) return false;
        changes.put("guestName", name);
        lines.add(line("guestName", null, name, null));
        return true;
    }

    // ------------------------------------------------------------------ apply

    /**
     * Writes the proposal through the normal services as {@code viewerId}; returns the activity
     * it created or changed, if any. A service's BusinessException rolls the apply back.
     */
    UUID apply(AssistantProposalAction action, UUID tripId, Map<String, Object> payload, UUID viewerId) {
        Map<String, Object> changes = map(payload.get("changes"));
        UUID activityId = AssistantProposalService.uuid(payload.get("activityId"));
        return switch (action) {
            case EDIT_TRIP -> {
                tripService.updateTrip(tripId, UpdateTripRequest.builder()
                        .name(text(changes.get("name")))
                        .description(text(changes.get("description")))
                        .currency(text(changes.get("currency")))
                        .budget(decimal(changes.get("budget")))
                        .visibility(text(changes.get("visibility")))
                        .startingPointName(text(changes.get("startingPointName")))
                        .startingPointAddress(text(changes.get("startingPointAddress")))
                        .startingPointTime(dateTime(changes.get("startingPointTime")))
                        .build(), viewerId);
                yield null;
            }
            case EDIT_ACTIVITY -> {
                activityService.updateActivity(tripId, activityId, UpdateActivityRequest.builder()
                        .name(text(changes.get("name")))
                        .address(text(changes.get("address")))
                        .endAddress(text(changes.get("endAddress")))
                        .notes(text(changes.get("notes")))
                        .description(text(changes.get("description")))
                        .category(text(changes.get("category")))
                        .transportMode(text(changes.get("transportMode")))
                        .estimatedCost(decimal(changes.get("cost")))
                        .costCurrency(text(changes.get("costCurrency")))
                        .isAccommodation(bool(changes.get("stay")))
                        .startTime(time(changes.get("startTime")))
                        .endTime(time(changes.get("endTime")))
                        .build(), viewerId);
                yield activityId;
            }
            case NEW_ACTIVITY -> {
                Integer day = payload.get("dayNumber") instanceof Number n ? n.intValue() : null;
                ActivityResponse created = activityService.createActivity(tripId, CreateActivityRequest.builder()
                        .name(text(changes.get("name")))
                        .dayNumber(day)
                        .address(text(changes.get("address")))
                        .endAddress(text(changes.get("endAddress")))
                        .notes(text(changes.get("notes")))
                        .description(text(changes.get("description")))
                        .category(text(changes.get("category")))
                        .transportMode(text(changes.get("transportMode")))
                        .estimatedCost(decimal(changes.get("cost")))
                        .costCurrency(text(changes.get("costCurrency")))
                        .isAccommodation(Boolean.TRUE.equals(bool(changes.get("stay"))) ? Boolean.TRUE : null)
                        .startTime(time(changes.get("startTime")))
                        .endTime(time(changes.get("endTime")))
                        .build(), viewerId);
                yield created.getId();
            }
            case ADD_EXPENSE -> {
                addExpense(tripId, changes, viewerId);
                yield null;
            }
            case EDIT_EXPENSE -> {
                editExpense(tripId, AssistantProposalService.uuid(payload.get("expenseId")), changes, viewerId);
                yield null;
            }
            case REMOVE_EXPENSE -> {
                expenseService.deleteExpense(tripId, AssistantProposalService.uuid(payload.get("expenseId")), viewerId);
                yield null;
            }
            case ADD_NOTE -> {
                noteService.createTripNote(tripId, CreateTripNoteRequest.builder()
                        .content(text(changes.get("content")))
                        .activityId(text(changes.get("activityId")))
                        .isShared(true)
                        .build(), viewerId);
                yield activityId;
            }
            case ADD_GUEST -> {
                tripService.inviteMember(tripId, InviteMemberRequest.builder()
                        .isGuest(true)
                        .guestName(text(changes.get("guestName")))
                        .role(MemberRole.VIEWER.name())
                        .build(), viewerId);
                yield null;
            }
            default -> throw new IllegalArgumentException("Not a trip edit: " + action);
        };
    }

    /** Why an expense proposal can no longer be applied, or null. */
    String staleExpense(UUID tripId, Map<String, Object> payload, UUID viewerId) {
        UUID expenseId = AssistantProposalService.uuid(payload.get("expenseId"));
        return expenseOfTrip(tripId, expenseId, viewerId) == null ? "Expense no longer exists" : null;
    }

    private void addExpense(UUID tripId, Map<String, Object> changes, UUID viewerId) {
        String currency = text(changes.get("currency"));
        BigDecimal amount = decimal(changes.get("amount"));
        Person payer = Person.from(changes.get("payer"));
        List<Person> split = new ArrayList<>();
        if (changes.get("split") instanceof List<?> list) list.forEach(raw -> {
            Person person = Person.from(raw);
            if (person != null) split.add(person);
        });
        List<BigDecimal> shares = equalShares(amount, split.size(), minorDigits(currency));
        List<ExpenseSplitRequest> splits = new ArrayList<>();
        for (int i = 0; i < split.size(); i++) {
            Person person = split.get(i);
            splits.add(ExpenseSplitRequest.builder()
                    .userId(person.userId())
                    .guestMemberId(person.guestMemberId())
                    .guestName(person.guestMemberId() == null ? null : person.name())
                    .amount(shares.get(i))
                    .build());
        }
        LocalDate date = date(changes.get("date"));
        expenseService.createExpense(tripId, CreateExpenseRequest.builder()
                .amount(amount)
                .currency(currency)
                .category(ExpenseCategory.valueOf(text(changes.get("category"))))
                .description(text(changes.get("description")))
                .expenseDate(date == null ? null : date.atTime(12, 0))
                .activityId(AssistantProposalService.uuid(changes.get("activityId")))
                .paidBy(payer == null ? null : payer.userId())
                .paidByGuestMemberId(payer == null ? null : payer.guestMemberId())
                .splits(splits)
                .build(), viewerId);
    }

    /**
     * A new amount keeps the split's proportions: each share is scaled, rounded down to the
     * currency's minor unit, and the remainder goes to the last share, so the splits still add up.
     */
    private void editExpense(UUID tripId, UUID expenseId, Map<String, Object> changes, UUID viewerId) {
        ExpenseResponse expense = expenseOfTrip(tripId, expenseId, viewerId);
        if (expense == null) throw new IllegalStateException("Expense no longer exists");
        BigDecimal amount = decimal(changes.get("amount"));
        List<ExpenseSplitRequest> splits = null;
        if (amount != null && expense.getSplits() != null && !expense.getSplits().isEmpty()
                && expense.getAmount() != null && expense.getAmount().signum() > 0) {
            splits = scaledSplits(expense, amount, people(tripId));
        }
        Person payer = Person.from(changes.get("payer"));
        LocalDate date = date(changes.get("date"));
        String category = text(changes.get("category"));
        expenseService.updateExpense(tripId, expenseId, UpdateExpenseRequest.builder()
                .amount(amount)
                .category(category == null ? null : ExpenseCategory.valueOf(category))
                .description(text(changes.get("description")))
                .expenseDate(date == null ? null : date.atTime(12, 0))
                .splits(splits)
                .paidById(payer == null ? null : payer.userId())
                .paidByGuestMemberId(payer == null ? null : payer.guestMemberId())
                .build(), viewerId);
    }

    private static List<ExpenseSplitRequest> scaledSplits(ExpenseResponse expense, BigDecimal amount,
                                                          Map<String, Person> people) {
        int digits = minorDigits(expense.getCurrency());
        List<ExpenseSplitResponse> old = expense.getSplits();
        List<ExpenseSplitRequest> out = new ArrayList<>();
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < old.size(); i++) {
            ExpenseSplitResponse split = old.get(i);
            BigDecimal share = i == old.size() - 1 ? amount.subtract(given)
                    : split.getAmount().multiply(amount).divide(expense.getAmount(), digits, RoundingMode.DOWN);
            given = given.add(share);
            UUID guest = split.getGuestMemberId();
            Person person = guest == null ? null : people.get(guest.toString());
            out.add(ExpenseSplitRequest.builder()
                    .userId(guest == null && split.getUser() != null ? split.getUser().getId() : null)
                    .guestMemberId(guest)
                    .guestName(guest == null ? null : person == null ? "Guest" : person.name())
                    .amount(share)
                    .build());
        }
        return out;
    }

    static List<BigDecimal> equalShares(BigDecimal amount, int count, int digits) {
        List<BigDecimal> shares = new ArrayList<>();
        if (count <= 0) return shares;
        BigDecimal each = amount.divide(BigDecimal.valueOf(count), digits, RoundingMode.DOWN);
        for (int i = 0; i < count - 1; i++) shares.add(each);
        shares.add(amount.subtract(each.multiply(BigDecimal.valueOf(count - 1L))));
        return shares;
    }

    // ------------------------------------------------------------------ people

    /**
     * Everyone on the trip a payer or a split may name, by the id the trip snapshot shows: a
     * registered member by user id, a guest by member id. A registered member's member id
     * resolves too, in case the model copies that one.
     */
    Map<String, Person> people(Trip trip) {
        return people(trip.getId(), trip.getOwnerId());
    }

    private Map<String, Person> people(UUID tripId) {
        return people(tripId, null);
    }

    private Map<String, Person> people(UUID tripId, UUID ownerId) {
        List<TripMember> accepted = members.findByTripId(tripId).stream()
                .filter(m -> m.getStatus() == null || m.getStatus() == MemberStatus.ACCEPTED)
                .toList();
        List<UUID> userIds = new ArrayList<>();
        if (ownerId != null) userIds.add(ownerId);
        accepted.stream().map(TripMember::getUserId).filter(Objects::nonNull).forEach(userIds::add);
        Map<UUID, String> names = new LinkedHashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : users.findByIds(userIds)) {
                names.put(user.getId(), user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName() : user.getUsername());
            }
        }
        Map<String, Person> out = new LinkedHashMap<>();
        if (ownerId != null && names.containsKey(ownerId)) {
            out.put(ownerId.toString(), new Person(ownerId, null, names.get(ownerId)));
        }
        for (TripMember member : accepted) {
            if (member.getUserId() != null) {
                Person person = new Person(member.getUserId(), null,
                        names.getOrDefault(member.getUserId(), ""));
                out.putIfAbsent(member.getUserId().toString(), person);
                out.putIfAbsent(member.getId().toString(), person);
            } else if (member.getGuestName() != null && !member.getGuestName().isBlank()) {
                out.put(member.getId().toString(), new Person(null, member.getId(), member.getGuestName()));
            }
        }
        return out;
    }

    private static Person person(Map<String, Person> people, Object rawId) {
        return rawId == null ? null : people.get(rawId.toString().trim().toLowerCase(Locale.ROOT));
    }

    /** The named people, once each; everyone on the trip when none are named. */
    private static List<Person> splitPeople(Map<String, Person> people, Object raw) {
        List<Person> out = new ArrayList<>();
        if (raw instanceof List<?> ids && !ids.isEmpty()) {
            for (Object id : ids) {
                Person person = person(people, id);
                if (person != null && !out.contains(person)) out.add(person);
            }
            return out;
        }
        for (Person person : people.values()) if (!out.contains(person)) out.add(person);
        return out;
    }

    private static Person payerOf(ExpenseResponse expense, Map<String, Person> people) {
        if (expense.getPaidByGuestMemberId() != null) return people.get(expense.getPaidByGuestMemberId().toString());
        return expense.getPaidBy() == null || expense.getPaidBy().getId() == null ? null
                : people.get(expense.getPaidBy().getId().toString());
    }

    // ------------------------------------------------------------------ helpers

    private ExpenseResponse expenseOfTrip(UUID tripId, UUID expenseId, UUID userId) {
        if (expenseId == null) return null;
        return expenseService.getExpenses(tripId, null, userId).stream()
                .filter(expense -> expenseId.equals(expense.getId()))
                .findFirst().orElse(null);
    }

    private static void putExpense(Map<String, Object> payload, ExpenseResponse expense) {
        payload.put("expenseId", expense.getId().toString());
        String description = expense.getDescription();
        payload.put("expenseName", description == null || description.isBlank() ? expense.getCategory() : description);
    }

    private Activity activityOfTrip(UUID tripId, UUID activityId) {
        if (activityId == null) return null;
        return activities.findById(activityId).filter(a -> tripId.equals(a.getTripId())).orElse(null);
    }

    /** A free-text field: trimmed, cut, moderated, and kept only when it differs from {@code current}. */
    private void putText(Map<String, Object> in, String field, int max, String current,
                         ModeratedContentType type, ModerationVisibility visibility,
                         Map<String, Object> changes, List<Map<String, Object>> lines) {
        String value = checkedText(in.get(field), max, type, visibility);
        if (value == null || value.equals(current == null ? null : current.trim())) return;
        changes.put(field, value);
        lines.add(line(field, current, value, null));
    }

    private String checkedText(Object raw, int max, ModeratedContentType type, ModerationVisibility visibility) {
        if (raw == null) return null;
        String value = raw.toString().trim();
        if (value.isEmpty()) return null;
        if (value.length() > max) value = value.substring(0, max);
        return moderation.evaluate(type, "assistant.proposal", visibility, value, AssistantIdentity.BOT_USER_ID).blocks()
                ? null : value;
    }

    static Map<String, Object> line(String field, String from, String to, String currency) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("field", field);
        if (from != null && !from.isBlank()) line.put("from", from.length() > 300 ? from.substring(0, 300) + "…" : from);
        if (to != null) line.put("to", to.length() > 300 ? to.substring(0, 300) + "…" : to);
        if (currency != null) line.put("currency", currency);
        return line;
    }

    static int minorDigits(String currency) {
        try {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return digits < 0 ? 2 : digits;
        } catch (RuntimeException e) {
            return 2;
        }
    }

    private static String currency(Object raw) {
        if (raw == null) return null;
        String code = raw.toString().trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY.matcher(code).matches()) return null;
        try {
            Currency.getInstance(code);
            return code;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A number from the model: plain, or with thousands separators ("500.000", "1,250,000"). */
    static BigDecimal amount(Object raw, boolean zeroAllowed) {
        BigDecimal value = null;
        if (raw instanceof Number number) {
            value = new BigDecimal(number.toString());
        } else if (raw != null) {
            String text = raw.toString().trim().replace(" ", "");
            if (text.matches("\\d{1,3}([.,]\\d{3})+")) text = text.replaceAll("[.,]", "");
            text = text.replace(",", ".");
            try {
                value = new BigDecimal(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (value == null || value.signum() < 0 || (!zeroAllowed && value.signum() == 0)
                || value.compareTo(MAX_AMOUNT) > 0) {
            return null;
        }
        return value.stripTrailingZeros().scale() < 0 ? value.setScale(0) : value.stripTrailingZeros();
    }

    /** An expense's day: not more than a day ahead (the expense service's own limit), not ten years back. */
    private static LocalDate expenseDate(Object raw) {
        LocalDate date = date(raw);
        if (date == null) return null;
        LocalDate today = LocalDate.now();
        return date.isAfter(today) || date.isBefore(today.minusYears(10)) ? null : date;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object raw) {
        if (raw == null) return null;
        try {
            return Enum.valueOf(type, raw.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Boolean bool(Object raw) {
        if (raw instanceof Boolean value) return value;
        if (raw == null) return null;
        String text = raw.toString().trim().toLowerCase(Locale.ROOT);
        return "true".equals(text) ? Boolean.TRUE : "false".equals(text) ? Boolean.FALSE : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalTime time(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            return LocalTime.parse(value.toString().trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDate date(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        String text = value.toString().trim();
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDateTime dateTime(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        String text = value.toString().trim().replace(' ', 'T');
        try {
            return LocalDateTime.parse(text.length() == 16 ? text + ":00" : text);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
