package com.ds.goroute.mapper;

import com.ds.goroute.entity.SeatGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface SeatGroupMapper {
    int deleteByPrimaryKey(@Param("id") UUID id);

    int insert(SeatGroup row);

    SeatGroup selectByPrimaryKey(@Param("id") UUID id);

    List<SeatGroup> selectAll();

    List<SeatGroup> selectByEventId(@Param("eventId") UUID eventId);

    int updateByPrimaryKey(SeatGroup row);

    SeatGroup selectByIdAndEventId(@Param("id") UUID id, @Param("eventId") UUID eventId);
}