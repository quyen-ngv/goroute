package com.ds.goroute.service.expense;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.WalletPersonResponse;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.type.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who may appear in a trip's ledger, and how each person is shown (epic 05 §2.1).
 *
 * <p>A trip's people are its owner, its ACCEPTED members and its guests ({@code trip_members}
 * rows with {@code is_guest}). Anyone else named as a payer, a share or a side of a settlement
 * is refused — unless they are already on the expense being edited, so a person who left the trip
 * does not make their old expenses uneditable.
 */
@Component
@RequiredArgsConstructor
public class ExpenseParticipants {

    static final String UNKNOWN_USER = "Someone";
    static final String UNKNOWN_GUEST = "Guest";

    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;

    /** The people of one trip. */
    public record TripRoster(UUID tripId, UUID ownerId, Set<UUID> acceptedUserIds,
                             Map<UUID, TripMember> guests) {

        public boolean contains(ParticipantRef participant) {
            if (participant == null) return false;
            if (participant.isGuest()) return guests.containsKey(participant.guestMemberId());
            return participant.userId().equals(ownerId) || acceptedUserIds.contains(participant.userId());
        }

        /**
         * The participant these ids name. Old builds sent a guest's {@code trip_members} id as
         * {@code userId} (plan §1 bug 6); such an id is read as the guest it is.
         *
         * @return null when neither id is set
         * @throws BusinessException when both are set
         */
        public ParticipantRef resolve(UUID userId, UUID guestMemberId) {
            if (userId != null && guestMemberId != null && !userId.equals(guestMemberId)) {
                throw invalid("A person is either a user or a guest, not both");
            }
            if (guestMemberId != null) return ParticipantRef.guest(guestMemberId);
            if (userId == null) return null;
            if (guests.containsKey(userId)) return ParticipantRef.guest(userId);
            return ParticipantRef.user(userId);
        }

        /** The one guest of this trip with this name (trimmed, any case), or null. */
        public ParticipantRef guestNamed(String name) {
            if (name == null || name.isBlank()) return null;
            String wanted = name.trim().toLowerCase(Locale.ROOT);
            List<UUID> matches = guests.values().stream()
                    .filter(guest -> guest.getGuestName() != null
                            && guest.getGuestName().trim().toLowerCase(Locale.ROOT).equals(wanted))
                    .map(TripMember::getId)
                    .toList();
            return matches.size() == 1 ? ParticipantRef.guest(matches.get(0)) : null;
        }

        public String guestName(ParticipantRef participant) {
            if (participant == null || !participant.isGuest()) return null;
            TripMember guest = guests.get(participant.guestMemberId());
            return guest == null ? null : guest.getGuestName();
        }

        /** Every registered user of the trip: owner and accepted members. */
        public Set<UUID> userIds() {
            Set<UUID> ids = new HashSet<>(acceptedUserIds);
            ids.add(ownerId);
            return ids;
        }
    }

    public TripRoster roster(Trip trip) {
        Set<UUID> accepted = new HashSet<>();
        Map<UUID, TripMember> guests = new HashMap<>();
        for (TripMember member : tripMemberRepository.findByTripId(trip.getId())) {
            if (Boolean.TRUE.equals(member.getIsGuest())) {
                guests.put(member.getId(), member);
            } else if (member.getUserId() != null && MemberStatus.ACCEPTED.equals(member.getStatus())) {
                accepted.add(member.getUserId());
            }
        }
        return new TripRoster(trip.getId(), trip.getOwnerId(), accepted, guests);
    }

    /** Display identities for these people, in two queries at most. */
    public Map<ParticipantRef, WalletPersonResponse> describe(Collection<ParticipantRef> participants) {
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> guestIds = new HashSet<>();
        for (ParticipantRef participant : participants) {
            if (participant == null) continue;
            if (participant.isGuest()) guestIds.add(participant.guestMemberId());
            else userIds.add(participant.userId());
        }
        Map<UUID, TripMember> guests = guestIds.isEmpty() ? Map.of()
                : tripMemberRepository.findByIds(guestIds).stream()
                        .collect(Collectors.toMap(TripMember::getId, Function.identity(), (a, b) -> a));
        guests.values().stream().map(TripMember::getUserId).filter(Objects::nonNull).forEach(userIds::add);
        Map<UUID, User> users = userIds.isEmpty() ? Map.of()
                : userRepository.findByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        Map<ParticipantRef, WalletPersonResponse> people = new HashMap<>();
        for (ParticipantRef participant : participants) {
            if (participant == null || people.containsKey(participant)) continue;
            if (!participant.isGuest()) {
                people.put(participant, user(participant.userId(), users.get(participant.userId())));
                continue;
            }
            TripMember guest = guests.get(participant.guestMemberId());
            User linked = guest == null || guest.getUserId() == null ? null : users.get(guest.getUserId());
            people.put(participant, WalletPersonResponse.builder()
                    .userId(null)
                    .guestMemberId(participant.guestMemberId())
                    .displayName(linked != null ? displayName(linked)
                            : guest != null && guest.getGuestName() != null && !guest.getGuestName().isBlank()
                            ? guest.getGuestName() : UNKNOWN_GUEST)
                    .avatarUrl(linked == null ? null : linked.getAvatarUrl())
                    .isGuest(true)
                    .build());
        }
        return people;
    }

    /** The identity of one person from a {@link #describe} result, never null. */
    public static WalletPersonResponse person(ParticipantRef participant, Map<ParticipantRef, WalletPersonResponse> people) {
        if (participant == null) return null;
        WalletPersonResponse known = people.get(participant);
        if (known != null) return known;
        return participant.isGuest()
                ? WalletPersonResponse.builder().guestMemberId(participant.guestMemberId())
                        .displayName(UNKNOWN_GUEST).isGuest(true).build()
                : WalletPersonResponse.builder().userId(participant.userId())
                        .displayName(UNKNOWN_USER).isGuest(false).build();
    }

    static WalletPersonResponse user(UUID userId, User user) {
        return WalletPersonResponse.builder()
                .userId(userId)
                .displayName(displayName(user))
                .avatarUrl(user == null ? null : user.getAvatarUrl())
                .isGuest(false)
                .build();
    }

    public static String displayName(User user) {
        if (user == null) return UNKNOWN_USER;
        if (user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
        return user.getUsername() == null || user.getUsername().isBlank() ? UNKNOWN_USER : user.getUsername();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }
}
