package com.ds.goroute.service;

import com.ds.goroute.dto.request.UpsertGuideProfileRequest;
import com.ds.goroute.dto.request.UpsertGuideServiceRequest;
import com.ds.goroute.dto.response.GuideProfileResponse;
import com.ds.goroute.dto.response.GuideServiceResponse;
import com.ds.goroute.entity.GuideAvailability;
import com.ds.goroute.type.GuideProfileStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Guide profiles, their services and the public directory (GUIDE-01 to GUIDE-04).
 *
 * <p>The rule that shapes everything here: only an approved guide can publish. This is a
 * service where people meet strangers in places they do not know, so "list now, verify
 * later" is not on offer.
 */
public interface GuideDirectoryService {

    // --- the guide's own profile ------------------------------------------------------

    GuideProfileResponse createOrUpdateProfile(UUID userId, UpsertGuideProfileRequest request);

    /** Sends the application for review. Only a draft or a rejected one can be sent. */
    GuideProfileResponse submitForVerification(UUID userId);

    GuideProfileResponse myProfile(UUID userId);

    /** Stores an identity document with a disposal date already set. */
    void attachIdentityDocument(UUID userId, String documentType, String fileUrl);

    // --- services ---------------------------------------------------------------------

    GuideServiceResponse createService(UUID userId, UpsertGuideServiceRequest request);

    GuideServiceResponse updateService(UUID userId, UUID serviceId, UpsertGuideServiceRequest request);

    List<GuideServiceResponse> myServices(UUID userId);

    // --- availability -----------------------------------------------------------------

    List<GuideAvailability> availability(UUID userId, LocalDate from, LocalDate to);

    /** Blocking a day never cancels a booking that is already agreed. */
    void setAvailability(UUID userId, LocalDate date, boolean blocked, Integer maxGuests, String note);

    // --- public directory -------------------------------------------------------------

    List<GuideServiceResponse> search(String provinceCode, String language, BigDecimal maxPrice,
                                      LocalDate date, int page, int size);

    long countSearch(String provinceCode, String language, BigDecimal maxPrice, LocalDate date);

    /** The public view: no contact details, no internal notes. */
    GuideProfileResponse publicProfile(UUID guideId);

    List<GuideServiceResponse> publicServices(UUID guideId);

    GuideServiceResponse publicService(UUID serviceId);

    // --- operator ---------------------------------------------------------------------

    List<GuideProfileResponse> verificationQueue(GuideProfileStatus status, int page, int size);

    long countVerificationQueue(GuideProfileStatus status);

    GuideProfileResponse decide(UUID operatorId, UUID guideId, GuideProfileStatus status,
                                String decisionNote, String informationRequested);

    List<java.util.Map<String, Object>> identityDocuments(UUID guideId);
}
