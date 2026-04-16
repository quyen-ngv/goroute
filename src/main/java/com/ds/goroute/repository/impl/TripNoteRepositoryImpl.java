package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.TripNote;
import com.ds.goroute.mapper.TripNoteMapper;
import com.ds.goroute.repository.TripNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TripNoteRepositoryImpl implements TripNoteRepository {
    
    private final TripNoteMapper tripNoteMapper;
    
    @Override
    public void insert(TripNote note) {
        tripNoteMapper.insert(note);
    }
    
    @Override
    public Optional<TripNote> findById(UUID id) {
        return Optional.ofNullable(tripNoteMapper.selectById(id));
    }
    
    @Override
    public List<TripNote> findByTripId(UUID tripId) {
        return tripNoteMapper.selectByTripId(tripId);
    }
    
    @Override
    public void updateById(TripNote note) {
        tripNoteMapper.updateById(note);
    }
    
    @Override
    public void deleteById(UUID id) {
        tripNoteMapper.deleteById(id);
    }
    
    @Override
    public void softDelete(UUID id) {
        tripNoteMapper.softDelete(id);
    }
}
