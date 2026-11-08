package com.ds.goroute.mapper;

import com.ds.goroute.entity.TripNoteTodoAssignee;
import com.ds.goroute.entity.TripNoteTodoItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface TripNoteTodoMapper {

    TripNoteTodoItem selectItemById(@Param("id") UUID id);

    List<TripNoteTodoItem> selectItemsByNoteIds(@Param("noteIds") Collection<UUID> noteIds);

    int countItemsByNoteId(@Param("noteId") UUID noteId);

    Integer selectMaxSortOrder(@Param("noteId") UUID noteId);

    int insertItem(TripNoteTodoItem item);

    int updateItem(TripNoteTodoItem item);

    int softDeleteItem(@Param("id") UUID id);

    int softDeleteItemsByNoteId(@Param("noteId") UUID noteId);

    List<TripNoteTodoAssignee> selectAssigneesByItemIds(@Param("itemIds") Collection<UUID> itemIds);

    int insertAssignees(@Param("itemId") UUID itemId, @Param("userIds") Collection<UUID> userIds);

    int deleteAssigneesByItemId(@Param("itemId") UUID itemId);
}
