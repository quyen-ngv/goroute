package com.ds.goroute.mapper;

import com.ds.goroute.entity.BookPageSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface BookPageSlotMapper {
    int insert(BookPageSlot slot);

    BookPageSlot selectByPageIdAndSlotId(@Param("pageId") UUID pageId, @Param("slotId") String slotId);

    List<BookPageSlot> selectByPageId(@Param("pageId") UUID pageId);

    int update(BookPageSlot slot);

    int deleteByPageId(@Param("pageId") UUID pageId);

    int deleteByBookId(@Param("bookId") UUID bookId);
}
