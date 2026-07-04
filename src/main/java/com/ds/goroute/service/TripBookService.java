package com.ds.goroute.service;

import com.ds.goroute.dto.request.PatchBookPageSlotRequest;
import com.ds.goroute.dto.request.UpdateBookPageRequest;
import com.ds.goroute.dto.request.UpsertBookSkeletonRequest;
import com.ds.goroute.dto.response.BookPageResponse;
import com.ds.goroute.dto.response.BookSkeletonResponse;
import com.ds.goroute.dto.response.BookSlotResponse;
import com.ds.goroute.dto.response.TripBookResponse;

import java.util.List;
import java.util.UUID;

public interface TripBookService {
    TripBookResponse generateBook(UUID tripId, UUID userId);

    TripBookResponse getBook(UUID tripId, UUID userId);

    BookPageResponse updatePage(UUID pageId, UpdateBookPageRequest request, UUID userId);

    List<BookSkeletonResponse> getSkeletons();

    BookSkeletonResponse upsertSkeleton(UpsertBookSkeletonRequest request);

    BookSlotResponse patchPageSlot(UUID pageId, String slotId, PatchBookPageSlotRequest request, UUID userId);

    BookPageResponse resetPageLayout(UUID pageId, UUID userId);
}
