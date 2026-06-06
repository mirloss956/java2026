package com.fileorganizer.service.impl;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.FileStatus;
import com.fileorganizer.model.OrganizeResult;
import com.fileorganizer.service.FileMoveService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 負責人：B
 *
 * 修正：dryRun 模式下，只有 PENDING 狀態的檔案才標為 MOVED（預覽）；
 * DUPLICATE / SKIPPED / FAILED 等非 PENDING 狀態應維持原狀，不被覆蓋。
 */
public class FileMoveServiceImpl implements FileMoveService {

    private final List<MoveHistory> lastMoveHistory = new ArrayList<>();

    private static class MoveHistory {
        final Path originalSource;
        final Path movedDestination;

        MoveHistory(Path originalSource, Path movedDestination) {
            this.originalSource    = originalSource;
            this.movedDestination  = movedDestination;
        }
    }

    @Override
    public OrganizeResult move(List<FileItem> items, boolean dryRun) {
        if (items == null) {
            items = new ArrayList<>();
        }

        if (!dryRun) {
            lastMoveHistory.clear();
        }

        for (FileItem item : items) {
            // 非 PENDING 的檔案（DUPLICATE、SKIPPED、FAILED…）一律跳過，
            // 不論 dryRun 與否都不應改變其狀態。
            if (item.getStatus() != FileStatus.PENDING) {
                continue;
            }

            Path src  = item.getSourcePath();
            Path dest = item.getDestinationPath();

            if (dest == null) {
                item.setStatus(FileStatus.FAILED);
                continue;
            }

            if (dryRun) {
                // 預覽模式：只標記「會被搬移」，不實際動檔案
                item.setStatus(FileStatus.MOVED);
            } else {
                try {
                    Path destFolder = dest.getParent();
                    if (destFolder != null && !Files.exists(destFolder)) {
                        Files.createDirectories(destFolder);
                    }
                    Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    lastMoveHistory.add(new MoveHistory(src, dest));
                    item.setStatus(FileStatus.MOVED);
                } catch (IOException e) {
                    System.err.println("檔案搬移失敗: " + src + " -> " + e.getMessage());
                    item.setStatus(FileStatus.FAILED);
                }
            }
        }

        return new OrganizeResult(items, LocalDateTime.now());
    }

    @Override
    public boolean undoLast() {
        if (lastMoveHistory.isEmpty()) {
            return false;
        }

        boolean allSuccess = true;
        // 反向順序復原，避免同資料夾下的檔案互相衝突
        for (int i = lastMoveHistory.size() - 1; i >= 0; i--) {
            MoveHistory history = lastMoveHistory.get(i);
            try {
                Path originalFolder = history.originalSource.getParent();
                if (originalFolder != null && !Files.exists(originalFolder)) {
                    Files.createDirectories(originalFolder);
                }
                Files.move(history.movedDestination, history.originalSource,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("復原檔案失敗: " + history.movedDestination + " -> " + e.getMessage());
                allSuccess = false;
            }
        }

        lastMoveHistory.clear();
        return allSuccess;
    }
}
