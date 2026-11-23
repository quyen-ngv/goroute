package com.ds.goroute.repository;

import com.ds.goroute.entity.LocationImage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationImageRepository {
    Optional<LocationImage> findBestMatch(String searchTerm);
    List<LocationImage> findAll();

    /** All areas for list screens, without {@code description} and {@code normalizedAddress}. */
    List<LocationImage> findAllForList();
    Optional<LocationImage> findById(UUID id);

    /** The tourist area a point falls in (ward link first, then nearest covering area); empty outside every area. */
    Optional<UUID> findAreaIdForPoint(java.math.BigDecimal latitude, java.math.BigDecimal longitude);

    /** Only {@code id} and {@code fullAddress} are filled. */
    List<LocationImage> findNamesByIds(java.util.Collection<UUID> ids);
    void insert(LocationImage locationImage);
    void update(LocationImage locationImage);
    void deleteById(UUID id);

    List<String> findWardCodes(UUID locationImageId);

    /** Every link at once, keyed by area, for list screens. */
    java.util.Map<UUID, List<String>> findAllWardCodes();

    void replaceWardCodes(UUID locationImageId, java.util.Collection<String> wardCodes);

    /** The COUNTRY row of an ISO alpha-2 code. */
    Optional<UUID> findCountryId(String countryCode);

    /** Every region name translation: area id, then locale code, then name. */
    java.util.Map<UUID, java.util.Map<String, String>> findAllNameTranslations();

    /** Replaces an area's translated names with {@code namesByLocale}. */
    void replaceNameTranslations(UUID locationImageId, java.util.Map<String, String> namesByLocale);
}
