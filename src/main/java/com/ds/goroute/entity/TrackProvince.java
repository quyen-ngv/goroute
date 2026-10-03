package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A province a recorded route passed through: its code and display name. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackProvince {
    private String code;
    private String name;
}
