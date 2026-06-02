package com.fileorganizer.service.impl;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.FileStatus;
import com.fileorganizer.model.OrganizeResult;
import com.fileorganizer.service.FileMoveService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 負責人：B
 * 實作 FileMoveService 介面
 */
public class FileMoveServiceImpl implements FileMoveService {

    // 用來記錄上一次成功搬移的歷史紀錄，供 undoLast() 使用
    private final List<MoveHistory> lastMoveHistory = new ArrayList<>();

    // 內部類別：記錄每一次的搬移軌跡
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
        OrganizeResult result = new OrganizeResult(); // 建立結果物件
        
        if (items == null || items.isEmpty()) {
            return result;
        }

        // 如果不是預覽模式，在開始前清空上一次的歷史紀錄
        if (!dryRun) {
            lastMoveHistory.clear();
        }

        for (FileItem item : items) {
            Path src = item.getSourcePath();
            Path dest = item.getDestinationPath();

            // 防禦性檢查：如果沒有目標路徑，跳過不處理
            if (dest == null) {
                item.setStatus(FileStatus.FAILED);
                result.incrementFailed(); // 假設 OrganizeResult 有這個方法
                continue;
            }

            if (dryRun) {
                // 預覽模式：只改狀態，不實際搬檔案
                item.setStatus(FileStatus.SUCCESS);
                result.incrementSuccess();
            } else {
                // 實際搬移模式
                try {
                    // 1. 確保目標資料夾存在，若不存在就自動建立
                    Path destFolder = dest.getParent();
                    if (destFolder != null && !Files.exists(destFolder)) {
                        Files.createDirectories(destFolder);
                    }

                    // 2. 執行搬移 (如果目標已有同名檔案，選擇覆蓋。你也可以根據需求調整)
                    Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);

                    // 3. 記錄歷史紀錄，供未來復原使用
                    lastMoveHistory.add(new MoveHistory(src, dest));

                    // 4. 更新物件狀態
                    item.setStatus(FileStatus.SUCCESS);
                    result.incrementSuccess();

                } catch (IOException e) {
                    System.err.println("檔案搬移失敗: " + src + " -> " + e.getMessage());
                    item.setStatus(FileStatus.FAILED);
                    result.incrementFailed();
                }
            }
        }
        return result;
    }

    @Override
    public boolean undoLast() {
        // 如果沒有上一次的搬移紀錄，無法復原
        if (lastMoveHistory.isEmpty()) {
            System.out.println("沒有可以復原的操作紀錄。");
            return false;
        }

        boolean allSuccess = true;
        
        // 從最後搬移的檔案開始反向搬回去（倒序處理比較安全）
        for (int i = lastMoveHistory.size() - 1; i >= 0; i--) {
            MoveHistory history = lastMoveHistory.get(i);
            try {
                // 確保原本的父資料夾存在
                Path originalFolder = history.originalSource.getParent();
                if (originalFolder != null && !Files.exists(originalFolder)) {
                    Files.createDirectories(originalFolder);
                }

                // 將檔案反向搬回
                Files.move(history.movedDestination, history.originalSource, StandardCopyOption.REPLACE_EXISTING);
                
            } catch (IOException e) {
                System.err.println("復原檔案失敗: " + history.movedDestination + " -> " + e.getMessage());
                allSuccess = false;
            }
        }

        // 復原完成後，清空紀錄，避免重複 undo
        lastMoveHistory.clear();
        return allSuccess;
    }
}