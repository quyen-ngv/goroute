package com.ds.goroute.mapper;

import com.ds.goroute.entity.BookPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface BookPageMapper {
    int insert(BookPage page);

    BookPage selectById(@Param("id") UUID id);

    List<BookPage> selectByBookId(@Param("bookId") UUID bookId);

    int updateSlots(@Param("id") UUID id, @Param("slots") String slots);

    int markLayoutEdited(@Param("id") UUID id);

    int resetLayoutMode(@Param("id") UUID id);

    int deleteByBookId(@Param("bookId") UUID bookId);
}
