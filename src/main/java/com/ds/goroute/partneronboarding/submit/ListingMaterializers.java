package com.ds.goroute.partneronboarding.submit;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.partneronboarding.domain.ListingKind;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the materializer for a kind.
 *
 * <p>Spring hands in every implementation it finds, so registering one is declaring it a
 * bean. A missing kind fails at startup rather than at the end of a partner's sign-up.
 */
@Component
public class ListingMaterializers {

    private final Map<ListingKind, ListingMaterializer> byKind = new EnumMap<>(ListingKind.class);

    public ListingMaterializers(List<ListingMaterializer> materializers) {
        for (ListingMaterializer materializer : materializers) {
            ListingMaterializer previous = byKind.put(materializer.kind(), materializer);
            if (previous != null) {
                throw new IllegalStateException("Two materializers registered for " + materializer.kind());
            }
        }
        for (ListingKind kind : ListingKind.values()) {
            if (!byKind.containsKey(kind)) {
                throw new IllegalStateException("No materializer registered for " + kind);
            }
        }
    }

    public ListingMaterializer of(ListingKind kind) {
        ListingMaterializer materializer = byKind.get(kind);
        if (materializer == null) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Unsupported listing kind: " + kind);
        }
        return materializer;
    }
}
