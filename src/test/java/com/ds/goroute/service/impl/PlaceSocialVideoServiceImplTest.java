package com.ds.goroute.service.impl;

import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceSocialVideo;
import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.mapper.PlaceMapper;
import com.ds.goroute.mapper.PlaceSocialVideoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceSocialVideoServiceImplTest {
    private PlaceSocialVideoMapper videoMapper;
    private PlaceMapper placeMapper;
    private PlaceSocialVideoServiceImpl service;

    @BeforeEach
    void setUp() {
        videoMapper = mock(PlaceSocialVideoMapper.class);
        placeMapper = mock(PlaceMapper.class);
        service = new PlaceSocialVideoServiceImpl(videoMapper, placeMapper, new ObjectMapper());
    }

    @Test
    void syncSocialJobPersistsVideoAndPerPlaceRecapForResolvedPlace() {
        UUID jobId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        Place place = Place.builder().id(placeId).build();
        when(placeMapper.findByPlaceId("google-place-1")).thenReturn(place);

        service.syncSocialJob(SocialLocationJob.builder()
                .id(jobId)
                .sourceUrl("https://vt.tiktok.com/source")
                .platform("tiktok")
                .language("vi")
                .resultPayload("""
                        {
                          "metadata": {
                            "webpage_url": "https://www.tiktok.com/@creator/video/123",
                            "title": "Ba quán nên thử",
                            "thumbnailUrl": "https://cdn.example/video.jpg",
                            "uploader": "creator"
                          },
                          "extraction": {
                            "summary": "Video giới thiệu ba quán ăn.",
                            "useful_summary": "Các quán nằm gần nhau.",
                            "general_guidance": ["Nên đi buổi sáng"],
                            "candidates": [{
                              "description": "Video khen món bún cá và nước dùng.",
                              "useful_info": ["Có món bún cá"],
                              "visit_guidance": ["Đi sớm"],
                              "evidence_sources": ["audio", "frame"],
                              "evidence_text": ["bún cá rất ngon"],
                              "confidence": 0.94,
                              "mapSearch": {"candidates": [{"placeId": "google-place-1"}]}
                            }]
                          }
                        }
                        """)
                .build());

        ArgumentCaptor<PlaceSocialVideo> captor = ArgumentCaptor.forClass(PlaceSocialVideo.class);
        verify(videoMapper).upsert(captor.capture());
        PlaceSocialVideo linked = captor.getValue();
        assertThat(linked.getPlaceId()).isEqualTo(placeId);
        assertThat(linked.getSocialJobId()).isEqualTo(jobId);
        assertThat(linked.getCanonicalUrl()).isEqualTo("https://www.tiktok.com/@creator/video/123");
        assertThat(linked.getPlaceRecap()).isEqualTo("Video khen món bún cá và nước dùng.");
        assertThat(linked.getUsefulInfo()).isEqualTo("[\"Có món bún cá\"]");
        assertThat(linked.getVisitGuidance()).isEqualTo("[\"Đi sớm\"]");
        assertThat(linked.getConfidence()).isEqualByComparingTo(new BigDecimal("0.94"));
    }

    @Test
    void syncSocialJobDoesNotCreateLinkUntilPlaceCanBeResolved() {
        service.syncSocialJob(SocialLocationJob.builder()
                .id(UUID.randomUUID())
                .sourceUrl("https://instagram.com/reel/abc")
                .platform("instagram")
                .resultPayload("""
                        {"extraction":{"candidates":[{
                          "description":"A place",
                          "mapSearch":{"candidates":[{"placeId":"missing"}]}
                        }]}}
                        """)
                .build());

        verify(videoMapper, never()).upsert(org.mockito.ArgumentMatchers.any());
    }
}
