package com.ds.goroute.mapper;

import com.ds.goroute.entity.MusicTrack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface MusicTrackMapper {

    /** The active catalogue in picker order, at most {@code limit} rows. */
    List<MusicTrack> findActive(@Param("limit") int limit);

    /** The active tracks among these ids; a track switched off drops out of every post. */
    List<MusicTrack> findActiveByIds(@Param("ids") Collection<UUID> ids);
}
