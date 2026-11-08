package com.ds.goroute.service;

import com.ds.goroute.config.FileUploadProperties;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Bounds how many image uploads are processed at once.
 *
 * <p>An upload holds its whole image in the heap while it is validated and sent to storage.
 * Without a bound, a burst of check-ins (six photos each, all uploading in parallel) could
 * hold gigabytes on a 1.6 GB container and take the instance down, every other API with it.
 * Past the bound the request waits, then is answered 503 with Retry-After, which the app
 * retries on its own.
 *
 * <p>Both bounds wait rather than refuse at once, within one shared deadline. App versions
 * before 01/11/2026 send every photo of a check-in at the same moment and show a refused
 * one as failed until the author taps retry; for them a short wait is invisible, a refusal
 * is not.
 *
 * <p>Only the processing is bounded: nginx buffers the request body before it reaches the
 * application, so a slow phone does not hold a slot while it sends.
 */
@Component
public class UploadSlots {

    private final Semaphore slots;
    private final int perUserLimit;
    private final long waitNanos;
    private final Map<UUID, UserSlots> slotsByUser = new ConcurrentHashMap<>();

    public UploadSlots(FileUploadProperties properties) {
        this.slots = new Semaphore(properties.getMaxConcurrentUploads(), true);
        this.perUserLimit = properties.getMaxConcurrentUploadsPerUser();
        this.waitNanos = properties.getUploadSlotWait().toNanos();
    }

    /** Runs {@code upload} in a slot, or throws {@link ErrorConstant#UPLOAD_BUSY}. */
    public <T> T run(UUID userId, Supplier<T> upload) {
        long deadline = System.nanoTime() + waitNanos;
        UserSlots userSlots = userId == null ? null : enterUser(userId);
        try {
            if (userSlots != null && !acquire(userSlots.permits, deadline)) {
                throw busy();
            }
            try {
                if (!acquire(slots, deadline)) {
                    throw busy();
                }
                try {
                    return upload.get();
                } finally {
                    slots.release();
                }
            } finally {
                if (userSlots != null) {
                    userSlots.permits.release();
                }
            }
        } finally {
            if (userSlots != null) {
                leaveUser(userId);
            }
        }
    }

    /** The user's permits, kept in the map while any of their uploads is running or waiting. */
    private UserSlots enterUser(UUID userId) {
        return slotsByUser.compute(userId, (id, current) -> {
            UserSlots entry = current == null ? new UserSlots(new Semaphore(perUserLimit, true)) : current;
            entry.users++;
            return entry;
        });
    }

    private void leaveUser(UUID userId) {
        slotsByUser.computeIfPresent(userId, (id, current) -> --current.users <= 0 ? null : current);
    }

    private boolean acquire(Semaphore semaphore, long deadline) {
        try {
            return semaphore.tryAcquire(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private BusinessException busy() {
        return new BusinessException(ErrorConstant.UPLOAD_BUSY, "Upload slots are busy");
    }

    /** {@code users} is only read and written inside the map's compute functions. */
    private static final class UserSlots {
        private final Semaphore permits;
        private int users;

        private UserSlots(Semaphore permits) {
            this.permits = permits;
        }
    }
}
