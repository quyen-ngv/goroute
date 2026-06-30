package com.ds.goroute.dto.request;

import com.ds.goroute.dto.response.PlaceImagesDto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateFoodRequest {
    @NotBlank
    private String nameVi;
    @NotBlank
    private String nameEn;
    private String nameJa;
    private String nameKo;
    @NotBlank
    private String description;
    @NotBlank
    private String category;
    private String imageUrl;
    private List<PlaceImagesDto> introductionImages;
}
