package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.MusicTrackResponse;
import com.ds.goroute.service.MusicTrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The background-music catalogue the check-in composer picks from (V282). */
@RestController
@RequestMapping("/v1/api/public/music-tracks")
@RequiredArgsConstructor
public class PublicMusicController extends BaseController {

    private final MusicTrackService musicTrackService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<MusicTrackResponse>>> list() {
        return ResponseEntity.ok(ofSucceeded(musicTrackService.listActive()));
    }
}
