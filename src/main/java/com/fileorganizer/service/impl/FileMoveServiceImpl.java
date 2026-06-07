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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 負責人：B
 *
 * 修正：undoLast() 完成後，自動刪除整理時建立的空資料夾。
 * 採用由深到淺的順序刪除，避免刪父資料夾時子資料夾還在。
 */
public class FileMoveServiceImpl implements FileMoveService {

    private final List<MoveHistory> lastMoveHistory = new ArrayList<>();

    private static class MoveHistory {
        final Path originalSource;
        final Path movedDestination;

        MoveHistory(Path originalSource, Path movedDestination) {
            this.originalSource   = originalSource;
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

        // 收集整理時建立的所有目標資料夾（用來之後清空資料夾）
        Set<Path> createdDirs = new HashSet<>();
        for (MoveHistory history : lastMoveHistory) {
            Path destParent = history.movedDestination.getParent();
            if (destParent != null) {
                createdDirs.add(destParent);
                // 也收集上一層（例如 圖片/重複檔案 → 也收集 圖片）
                Path grandParent = destParent.getParent();
                if (grandParent != null) {
                    createdDirs.add(grandParent);
                }
            }
        }

        // 反向順序把檔案移回原位
        boolean allSuccess = true;
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

        // 由深到淺刪除空資料夾
        createdDirs.stream()
            .sorted(Comparator.comparingInt(p -> -p.getNameCount())) // 深的先刪
            .forEach(dir -> deleteIfEmpty(dir));

        return allSuccess;
    }

    /**
     * 若資料夾存在且為空，則刪除。
     */
    private void deleteIfEmpty(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) return;
        try (Stream<Path> entries = Files.list(dir)) {
            if (entries.findFirst().isEmpty()) {
                Files.delete(dir);
            }
        } catch (IOException e) {
            System.err.println("刪除空資料夾失敗: " + dir + " -> " + e.getMessage());
        }
    }
}
