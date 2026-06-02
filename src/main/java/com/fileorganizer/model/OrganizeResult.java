package com.fileorganizer.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次整理操作的完整結果。
 * B（Service）產生，A（UI）顯示，C（DB）寫入日誌。
 */
public class OrganizeResult {

    private final List<FileItem> items;
    private final LocalDateTime executedAt;
    private final int movedCount;
    private final int skippedCount;
    private final int failedCount;
    private final int duplicateCount;

    public OrganizeResult(List<FileItem> items, LocalDateTime executedAt) {
        this.items = List.copyOf(items);
        this.executedAt = executedAt;
        this.movedCount     = (int) items.stream().filter(f -> f.getStatus() == FileStatus.MOVED).count();
        this.skippedCount   = (int) items.stream().filter(f -> f.getStatus() == FileStatus.SKIPPED).count();
        this.failedCount    = (int) items.stream().filter(f -> f.getStatus() == FileStatus.FAILED).count();
        this.duplicateCount = (int) items.stream().filter(f -> f.getStatus() == FileStatus.DUPLICATE).count();
    }

    public List<FileItem> getItems() { return items; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public int getMovedCount() { return movedCount; }
    public int getSkippedCount() { return skippedCount; }
    public int getFailedCount() { return failedCount; }
    public int getDuplicateCount() { return duplicateCount; }
    public int getTotalCount() { return items.size(); }

    @Override
    public String toString() {
        return String.format("OrganizeResult{total=%d, moved=%d, skipped=%d, dup=%d, failed=%d}",
                getTotalCount(), movedCount, skippedCount, duplicateCount, failedCount);
    }
}
