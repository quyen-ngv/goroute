package com.ds.goroute.dto;

import lombok.Data;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class ArchiveStatistics {
    private AtomicInteger totalProcessed = new AtomicInteger(0);
    private AtomicInteger successful = new AtomicInteger(0);
    private AtomicInteger failed = new AtomicInteger(0);
    private AtomicInteger skipped = new AtomicInteger(0);
    
    private AtomicLong originalTotalSize = new AtomicLong(0);
    private AtomicLong compressedTotalSize = new AtomicLong(0);
    
    public void addSuccess(long originalSize, long compressedSize) {
        totalProcessed.incrementAndGet();
        successful.incrementAndGet();
        originalTotalSize.addAndGet(originalSize);
        compressedTotalSize.addAndGet(compressedSize);
    }
    
    public void addFailure() {
        totalProcessed.incrementAndGet();
        failed.incrementAndGet();
    }
    
    public void addSkipped() {
        skipped.incrementAndGet();
    }
    
    public double getSuccessRate() {
        int total = totalProcessed.get();
        return total > 0 ? (successful.get() * 100.0 / total) : 0.0;
    }
    
    public double getCompressionRatio() {
        long original = originalTotalSize.get();
        long compressed = compressedTotalSize.get();
        return original > 0 ? (compressed * 100.0 / original) : 0.0;
    }
    
    public double getSizeSavedMB() {
        return (originalTotalSize.get() - compressedTotalSize.get()) / (1024.0 * 1024.0);
    }
    
    public void logSummary(String entityType) {
        System.out.println(String.format(
            "📊 %s Archive Summary:\n" +
            "   ✅ Successful: %d\n" +
            "   ❌ Failed: %d\n" +
            "   ⏭️ Skipped: %d\n" +
            "   📈 Success Rate: %.1f%%\n" +
            "   🗜️ Compression: %.1f%% of original (%.1f%% saved)\n" +
            "   💾 Space Saved: %.1f MB",
            entityType,
            successful.get(),
            failed.get(), 
            skipped.get(),
            getSuccessRate(),
            getCompressionRatio(),
            100.0 - getCompressionRatio(),
            getSizeSavedMB()
        ));
    }
}