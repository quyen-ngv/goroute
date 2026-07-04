package com.ds.goroute.repository;

import com.ds.goroute.entity.BookPage;
import com.ds.goroute.entity.BookPageSlot;
import com.ds.goroute.entity.BookSkeleton;
import com.ds.goroute.entity.BookTemplate;
import com.ds.goroute.entity.TripBook;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripBookRepository {
    Optional<BookTemplate> findTemplate(String templateType, String refId);

    void insertSkeleton(BookSkeleton skeleton);

    void updateSkeleton(BookSkeleton skeleton);

    Optional<BookSkeleton> findSkeletonById(UUID skeletonId);

    Optional<BookSkeleton> findActiveSkeleton(String skeletonKey);

    Optional<BookSkeleton> findSkeleton(String skeletonKey, Integer version);

    List<BookSkeleton> findAllSkeletons();

    void insertBook(TripBook book);

    Optional<TripBook> findBookById(UUID bookId);

    Optional<TripBook> findBookByTripId(UUID tripId);

    void updateBookStatus(UUID bookId, String status);

    void insertPage(BookPage page);

    Optional<BookPage> findPageById(UUID pageId);

    List<BookPage> findPagesByBookId(UUID bookId);

    void updatePageSlots(UUID pageId, String slots);

    void markPageLayoutEdited(UUID pageId);

    void resetPageLayoutMode(UUID pageId);

    void deletePagesByBookId(UUID bookId);

    void insertPageSlot(BookPageSlot slot);

    Optional<BookPageSlot> findPageSlot(UUID pageId, String slotId);

    List<BookPageSlot> findPageSlots(UUID pageId);

    void updatePageSlot(BookPageSlot slot);

    void deletePageSlots(UUID pageId);

    void deleteSlotsByBookId(UUID bookId);
}
