package com.ds.goroute.repository;

import com.ds.goroute.entity.TripNoteTodoAssignee;
import com.ds.goroute.entity.TripNoteTodoItem;
import com.ds.goroute.mapper.TripNoteTodoMapper;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Items of todo-list notes and the people they are assigned to (V271). */
@Repository
@RequiredArgsConstructor
public class TripNoteTodoRepository {

    private static final int IN_CHUNK = 500;

    private final TripNoteTodoMapper mapper;

    public Optional<TripNoteTodoItem> findItemById(UUID id) {
        return Optional.ofNullable(mapper.selectItemById(id));
    }

    /** Not deleted, in list order. */
    public List<TripNoteTodoItem> findItemsByNoteIds(Collection<UUID> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) return List.of();
        List<TripNoteTodoItem> items = new ArrayList<>();
        for (List<UUID> chunk : Lists.partition(List.copyOf(noteIds), IN_CHUNK)) {
            items.addAll(mapper.selectItemsByNoteIds(chunk));
        }
        return items;
    }

    public int countItems(UUID noteId) {
        return mapper.countItemsByNoteId(noteId);
    }

    /** The sort order after the last item, 0 for an empty list. */
    public int nextSortOrder(UUID noteId) {
        Integer max = mapper.selectMaxSortOrder(noteId);
        return max == null ? 0 : max + 1;
    }

    public void insertItem(TripNoteTodoItem item) {
        mapper.insertItem(item);
    }

    public void updateItem(TripNoteTodoItem item) {
        mapper.updateItem(item);
    }

    public void softDeleteItem(UUID id) {
        mapper.softDeleteItem(id);
    }

    public void softDeleteItemsOfNote(UUID noteId) {
        mapper.softDeleteItemsByNoteId(noteId);
    }

    public List<TripNoteTodoAssignee> findAssignees(Collection<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return List.of();
        List<TripNoteTodoAssignee> assignees = new ArrayList<>();
        for (List<UUID> chunk : Lists.partition(List.copyOf(itemIds), IN_CHUNK)) {
            assignees.addAll(mapper.selectAssigneesByItemIds(chunk));
        }
        return assignees;
    }

    /** Replaces the item's assignees with {@code userIds}. */
    public void replaceAssignees(UUID itemId, Collection<UUID> userIds) {
        mapper.deleteAssigneesByItemId(itemId);
        if (userIds != null && !userIds.isEmpty()) {
            mapper.insertAssignees(itemId, userIds);
        }
    }
}
