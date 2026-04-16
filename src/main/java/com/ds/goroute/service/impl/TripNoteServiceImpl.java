package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateTripNoteRequest;
import com.ds.goroute.dto.response.TripNoteResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.TripNote;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripNoteRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.TripNoteService;
import com.ds.goroute.type.MemberStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripNoteServiceImpl implements TripNoteService {
    
    private final TripNoteRepository tripNoteRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TripNoteResponse> getTripNotes(UUID tripId, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);
        
        List<TripNote> notes = tripNoteRepository.findByTripId(tripId);
        
        return notes.stream()
                .map(this::toTripNoteResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TripNoteResponse createTripNote(UUID tripId, CreateTripNoteRequest request, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);
        
        TripNote note = TripNote.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .userId(userId)
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();
        
        tripNoteRepository.insert(note);
        log.info("Trip note created: {} for trip: {}", note.getId(), tripId);
        
        return toTripNoteResponse(note);
    }

    @Override
    @Transactional
    public void deleteTripNote(UUID tripId, UUID noteId, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);
        
        TripNote note = tripNoteRepository.findById(noteId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Note not found"));
        
        // Only note owner can delete
        if (!note.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN, "You can only delete your own notes");
        }
        
        tripNoteRepository.softDelete(noteId);
        log.info("Trip note deleted: {}", noteId);
    }
    
    private void verifyTripMember(UUID tripId, UUID userId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        
        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.FORBIDDEN, "You are not a member of this trip"));
        
        if (member.getStatus() != MemberStatus.ACCEPTED) {
            throw new BusinessException(ErrorConstant.FORBIDDEN, "You are not a member of this trip");
        }
    }
    
    private TripNoteResponse toTripNoteResponse(TripNote note) {
        User user = userRepository.findById(note.getUserId()).orElse(null);
        
        return TripNoteResponse.builder()
                .id(note.getId())
                .tripId(note.getTripId())
                .user(UserResponse.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .content(note.getContent())
                .createdAt(note.getCreatedAt())
                .build();
    }
}
