package com.ds.goroute.partneronboarding.domain;

import com.ds.goroute.service.ImageUploadRequest;

/**
 * What a draft is building towards.
 *
 * <p>The three kinds mirror the three things a partner can sell: a place to stay, an
 * experience run on a schedule, and a service booked by the hour. Everything that differs
 * between them at the API boundary — which permission guards the draft, which resource the
 * organization scope is checked against, where photos are stored — is declared here, so the
 * service never branches on the kind to answer those questions.
 *
 * <p>How a draft turns into marketplace rows is deliberately <em>not</em> here: that is one
 * {@code ListingMaterializer} per kind, selected by {@link #name()}.
 */
public enum ListingKind {

    /** A property let by the night: place, room type, rate plan, calendar. */
    STAY("HOTEL", "HOTEL_READ", "HOTEL_WRITE", ImageUploadRequest.ImageEntryPoint.PARTNER_HOTEL),

    /** A guided experience sold per guest on scheduled departures. */
    EXPERIENCE("ACTIVITY", "ACTIVITY_READ", "ACTIVITY_WRITE", ImageUploadRequest.ImageEntryPoint.PARTNER_ACTIVITY),

    /** A service booked inside business hours; several offerings under one listing. */
    SERVICE("ACTIVITY", "ACTIVITY_READ", "ACTIVITY_WRITE", ImageUploadRequest.ImageEntryPoint.PARTNER_ACTIVITY);

    private final String resourceType;
    private final String readPermission;
    private final String writePermission;
    private final String imageEntryPoint;

    ListingKind(String resourceType, String readPermission, String writePermission, String imageEntryPoint) {
        this.resourceType = resourceType;
        this.readPermission = readPermission;
        this.writePermission = writePermission;
        this.imageEntryPoint = imageEntryPoint;
    }

    /** {@code HOTEL} or {@code ACTIVITY}, as {@code organization_member_scopes} spells it. */
    public String resourceType() {
        return resourceType;
    }

    public String readPermission() {
        return readPermission;
    }

    public String writePermission() {
        return writePermission;
    }

    /** The named door in {@code FileUploadService} that photos for this kind go through. */
    public String imageEntryPoint() {
        return imageEntryPoint;
    }
}
