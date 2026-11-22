package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.MusicTrackResponse;
import com.ds.goroute.entity.MusicTrack;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.MusicTrackRepository;
import com.ds.goroute.service.MusicTrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MusicTrackServiceImpl implements MusicTrackService {

    /** The catalogue is curated by hand; this only keeps the read bounded. */
    private static final int MAX_CATALOGUE_SIZE = 500;

    private final MusicTrackRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<MusicTrackResponse> listActive() {
        return repository.findActive(MAX_CATALOGUE_SIZE).stream().map(MusicTrackServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MusicTrackResponse> findActiveByIds(Collection<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        return repository.findActiveByIds(distinct).stream()
                .map(MusicTrackServiceImpl::toResponse)
                .collect(Collectors.toMap(MusicTrackResponse::getId, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActive(UUID trackId) {
        if (repository.findActiveByIds(List.of(trackId)).isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Music track not found");
        }
    }

    private static MusicTrackResponse toResponse(MusicTrack track) {
        return MusicTrackResponse.builder()
                .id(track.getId())
                .title(track.getTitle())
                .artist(track.getArtist())
                .audioUrl(track.getAudioUrl())
                .sourceUrl(track.getSourceUrl())
                .license(track.getLicense())
                .licenseUrl(track.getLicense() == null ? null : track.getLicense().getUrl())
                .attribution(track.getAttribution())
                .mood(track.getMood())
                .durationSeconds(track.getDurationSeconds())
                .build();
    }
}
