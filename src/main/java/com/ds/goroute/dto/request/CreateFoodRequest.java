package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

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
}
