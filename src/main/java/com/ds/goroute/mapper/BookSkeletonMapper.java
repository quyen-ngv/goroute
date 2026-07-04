package com.ds.goroute.mapper;

import com.ds.goroute.entity.BookSkeleton;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface BookSkeletonMapper {
    int insert(BookSkeleton skeleton);

    int update(BookSkeleton skeleton);

    BookSkeleton selectById(@Param("id") UUID id);

    BookSkeleton selectActiveByKey(@Param("skeletonKey") String skeletonKey);

    BookSkeleton selectByKeyAndVersion(@Param("skeletonKey") String skeletonKey, @Param("version") Integer version);

    List<BookSkeleton> selectAll();

    List<BookSkeleton> selectByKeysAndVersions(@Param("keys") List<String> keys);
}
