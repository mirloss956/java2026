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

public class FileMoveServiceImpl implements FileMoveService {

    private final List<MoveHistory> lastMoveHistory = new ArrayList<>();

    private static class MoveHistory {
        final Path originalSource;
        final Path movedDestination;

        MoveHistory(Path originalSource, Path movedDestination) {
            this.originalSource = originalSource;
            this.movedDestination = movedDestination;
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
            Path src = item.getSourcePath();
            Path dest = item.getDestinationPath();

            if (dest == null) {
                item.setStatus(FileStatus.FAILED);
                continue;
            }

            if (dryRun) {
                // 修正：狀態從 SUCCESS 改為 MOVED
                item.setStatus(FileStatus.MOVED);
            } else {
                try {
                    Path destFolder = dest.getParent();
                    if (destFolder != null && !Files.exists(destFolder)) {
                        Files.createDirectories(destFolder);
                    }

                    Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    lastMoveHistory.add(new MoveHistory(src, dest));
                    
                    // 修正：狀態從 SUCCESS 改為 MOVED
                    item.setStatus(FileStatus.MOVED);

                } catch (IOException e) {
                    System.err.println("檔案搬移失敗: " + src + " -> " + e.getMessage());
                    item.setStatus(FileStatus.FAILED);
                }
            }
        }

        // 修正：配合 C 的規格，將處理完的清單與當前時間丟進建構子，讓 OrganizeResult 自己去算成功/失敗數
        return new OrganizeResult(items, LocalDateTime.now());
    }

    @Override
    public boolean undoLast() {
        if (lastMoveHistory.isEmpty()) {
            return false;
        }

        boolean allSuccess = true;
        for (int i = lastMoveHistory.size() - 1; i >= 0; i--) {
            MoveHistory history = lastMoveHistory.get(i);
            try {
                Path originalFolder = history.originalSource.getParent();
                if (originalFolder != null && !Files.exists(originalFolder)) {
                    Files.createDirectories(originalFolder);
                }
                Files.move(history.movedDestination, history.originalSource, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("復原檔案失敗: " + history.movedDestination + " -> " + e.getMessage());
                allSuccess = false;
            }
        }

        lastMoveHistory.clear();
        return allSuccess;
    }
}