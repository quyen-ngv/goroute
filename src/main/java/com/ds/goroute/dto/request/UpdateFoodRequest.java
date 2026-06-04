package com.ds.goroute.dto.request;

import lombok.Data;

@Data
public class UpdateFoodRequest {
    private String nameVi;
    private String nameEn;
    private String nameJa;
    private String nameKo;
    private String description;
    private String category;
    private String imageUrl;
}
