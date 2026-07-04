package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.BookPage;
import com.ds.goroute.entity.BookPageSlot;
import com.ds.goroute.entity.BookSkeleton;
import com.ds.goroute.entity.BookTemplate;
import com.ds.goroute.entity.TripBook;
import com.ds.goroute.mapper.BookPageMapper;
import com.ds.goroute.mapper.BookPageSlotMapper;
import com.ds.goroute.mapper.BookSkeletonMapper;
import com.ds.goroute.mapper.BookTemplateMapper;
import com.ds.goroute.mapper.TripBookMapper;
import com.ds.goroute.repository.TripBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TripBookRepositoryImpl implements TripBookRepository {
    private final BookTemplateMapper bookTemplateMapper;
    private final BookSkeletonMapper bookSkeletonMapper;
    private final TripBookMapper tripBookMapper;
    private final BookPageMapper bookPageMapper;
    private final BookPageSlotMapper bookPageSlotMapper;

    @Override
    public Optional<BookTemplate> findTemplate(String templateType, String refId) {
        if (refId == null || refId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(bookTemplateMapper.selectByTypeAndRefId(templateType, refId));
    }

    @Override
    public void insertSkeleton(BookSkeleton skeleton) {
        bookSkeletonMapper.insert(skeleton);
    }

    @Override
    public void updateSkeleton(BookSkeleton skeleton) {
        bookSkeletonMapper.update(skeleton);
    }

    @Override
    public Optional<BookSkeleton> findSkeletonById(UUID skeletonId) {
        return Optional.ofNullable(bookSkeletonMapper.selectById(skeletonId));
    }

    @Override
    public Optional<BookSkeleton> findActiveSkeleton(String skeletonKey) {
        return Optional.ofNullable(bookSkeletonMapper.selectActiveByKey(skeletonKey));
    }

    @Override
    public Optional<BookSkeleton> findSkeleton(String skeletonKey, Integer version) {
        return Optional.ofNullable(bookSkeletonMapper.selectByKeyAndVersion(skeletonKey, version));
    }

    @Override
    public List<BookSkeleton> findAllSkeletons() {
        return bookSkeletonMapper.selectAll();
    }

    @Override
    public void insertBook(TripBook book) {
        tripBookMapper.insert(book);
    }

    @Override
    public Optional<TripBook> findBookById(UUID bookId) {
        return Optional.ofNullable(tripBookMapper.selectById(bookId));
    }

    @Override
    public Optional<TripBook> findBookByTripId(UUID tripId) {
        return Optional.ofNullable(tripBookMapper.selectByTripId(tripId));
    }

    @Override
    public void updateBookStatus(UUID bookId, String status) {
        tripBookMapper.updateStatus(bookId, status);
    }

    @Override
    public void insertPage(BookPage page) {
        bookPageMapper.insert(page);
    }

    @Override
    public Optional<BookPage> findPageById(UUID pageId) {
        return Optional.ofNullable(bookPageMapper.selectById(pageId));
    }

    @Override
    public List<BookPage> findPagesByBookId(UUID bookId) {
        return bookPageMapper.selectByBookId(bookId);
    }

    @Override
    public void updatePageSlots(UUID pageId, String slots) {
        bookPageMapper.updateSlots(pageId, slots);
    }

    @Override
    public void markPageLayoutEdited(UUID pageId) {
        bookPageMapper.markLayoutEdited(pageId);
    }

    @Override
    public void resetPageLayoutMode(UUID pageId) {
        bookPageMapper.resetLayoutMode(pageId);
    }

    @Override
    public void deletePagesByBookId(UUID bookId) {
        bookPageMapper.deleteByBookId(bookId);
    }

    @Override
    public void insertPageSlot(BookPageSlot slot) {
        bookPageSlotMapper.insert(slot);
    }

    @Override
    public Optional<BookPageSlot> findPageSlot(UUID pageId, String slotId) {
        return Optional.ofNullable(bookPageSlotMapper.selectByPageIdAndSlotId(pageId, slotId));
    }

    @Override
    public List<BookPageSlot> findPageSlots(UUID pageId) {
        return bookPageSlotMapper.selectByPageId(pageId);
    }

    @Override
    public void updatePageSlot(BookPageSlot slot) {
        bookPageSlotMapper.update(slot);
    }

    @Override
    public void deletePageSlots(UUID pageId) {
        bookPageSlotMapper.deleteByPageId(pageId);
    }

    @Override
    public void deleteSlotsByBookId(UUID bookId) {
        bookPageSlotMapper.deleteByBookId(bookId);
    }
}
