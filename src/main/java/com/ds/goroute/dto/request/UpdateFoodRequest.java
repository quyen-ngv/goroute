package com.ds.goroute.dto.request;

import com.ds.goroute.dto.response.PlaceImagesDto;
import lombok.Data;

import java.util.List;

@Data
public class UpdateFoodRequest {
    private String nameVi;
    private String nameEn;
    private String nameJa;
    private String nameKo;
    private String description;
    private String category;
    private String imageUrl;
    private List<PlaceImagesDto> introductionImages;
}
