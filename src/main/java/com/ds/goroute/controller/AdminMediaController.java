package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/v1/api/admin/media")
@RequiredArgsConstructor
public class AdminMediaController {
    private final AdminMapper adminMapper; private final StorageService storage;
    @GetMapping public BaseResponse<List<Map<String,Object>>> list(@RequestParam(defaultValue="") String search){return BaseResponse.ofSucceeded(adminMapper.findMedia(search));}
    @PostMapping("/upload") public BaseResponse<Map<String,String>> upload(@RequestParam MultipartFile file,@RequestAttribute UUID userId)throws Exception{String url=storage.uploadBytes(file.getBytes(),"admin-media/"+UUID.randomUUID()+"-"+Objects.requireNonNullElse(file.getOriginalFilename(),"image"),file.getContentType());return save(url,userId,file.getOriginalFilename());}
    @PostMapping("/from-url") public BaseResponse<Map<String,String>> fromUrl(@RequestParam String url,@RequestParam(required=false) String caption,@RequestAttribute UUID userId){return save(storage.uploadFileFromUrl(url,"admin-media/"+UUID.randomUUID()+".jpg"),userId,caption);}
    @DeleteMapping("/{id}") public BaseResponse<Void> delete(@PathVariable UUID id){adminMapper.softDeleteMedia(id);return BaseResponse.ofSucceeded(null);}
    private BaseResponse<Map<String,String>> save(String url,UUID userId,String caption){adminMapper.insertMedia(UUID.randomUUID(),url,caption,userId);return BaseResponse.ofSucceeded(Map.of("url",url));}
}
