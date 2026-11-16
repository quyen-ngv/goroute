package com.ds.goroute.entity;

import lombok.Data;

import java.util.UUID;

/** A place and the hash of everything the search index reads of it by name. */
@Data
public class PlaceSearchNameHash {
    private UUID id;
    private String nameHash;
}
