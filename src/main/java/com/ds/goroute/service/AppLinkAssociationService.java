package com.ds.goroute.service;

import com.ds.goroute.dto.response.AndroidAssetLinkResponse;
import com.ds.goroute.dto.response.AppleAppSiteAssociationResponse;

import java.util.List;
import java.util.Optional;

/** The association files that let the app open shared profile links itself. */
public interface AppLinkAssociationService {

    /** Empty while no signing-certificate fingerprint is configured. */
    Optional<List<AndroidAssetLinkResponse>> androidAssetLinks();

    /** Empty while no iOS app id is configured. */
    Optional<AppleAppSiteAssociationResponse> appleAppSiteAssociation();
}
