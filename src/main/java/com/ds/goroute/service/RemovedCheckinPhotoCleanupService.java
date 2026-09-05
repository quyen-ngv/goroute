package com.ds.goroute.service;

import com.ds.goroute.repository.UserCheckinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes photo rows left behind by historic soft-deleted check-ins.
 *
 * <p>The object cleanup is deliberately requested before deleting the rows: it needs the
 * URLs to determine which managed objects are safe to remove. It defers the physical
 * storage delete until this transaction commits, while a linked surviving review retains
 * any files it still displays.
 */
@Service
@RequiredArgsConstructor
public class RemovedCheckinPhotoCleanupService {

    private final UserCheckinRepository checkinRepository;
    private final ImageStorageCleanupService imageStorageCleanupService;

    @Transactional
    public int purgeRemovedCheckinPhotos(int limit) {
        int safeLimit = Math.max(1, limit);
        int purgedCheckins = 0;
        for (var checkinId : checkinRepository.findRemovedCheckinIdsWithPhotos(safeLimit)) {
            imageStorageCleanupService.deleteImagesForEntityRecord("USER_CHECKIN_PHOTO", checkinId);
            checkinRepository.deletePhotos(checkinId);
            purgedCheckins++;
        }
        return purgedCheckins;
    }
}
