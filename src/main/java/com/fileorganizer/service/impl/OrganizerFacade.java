package com.fileorganizer.service.impl;

import com.fileorganizer.config.AppConfig;
import com.fileorganizer.controller.DuplicateActionDialog.DuplicateAction;
import com.fileorganizer.model.*;
import com.fileorganizer.rule.RuleEngine;
import com.fileorganizer.service.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * 負責人：C
 *
 * 整合橋接層。A（UI Controller）只需要跟這個類別說話，
 * 不需要知道 B 的任何實作細節。
 *
 * 修正：
 *  - targetRoot 改為掃描的資料夾本身（原地整理）
 *  - organizeAsync 新增 DuplicateAction 參數，支援三種重複檔案處理方式
 */
public class OrganizerFacade {

    private final FileScanService        scanService;
    private final FileMoveService        moveService;
    private final DuplicateDetectService duplicateService;
    private final LogService             logService;
    private final WatchService           watchService;
    private final AppConfig              config;

    private volatile RuleEngine ruleEngine;

    private final ObservableList<FileItem> fileItems     = FXCollections.observableArrayList();
    private final BooleanProperty          busy          = new SimpleBooleanProperty(false);
    private final StringProperty           statusMessage = new SimpleStringProperty("就緒");

    /** 記住最近一次掃描的資料夾，供重複檔案 ISOLATE 模式使用 */
    private volatile Path lastScannedDirectory;

    public OrganizerFacade(
            FileScanService scanService,
            FileMoveService moveService,
            DuplicateDetectService duplicateService,
            LogService logService,
            WatchService watchService,
            RuleEngine ruleEngine,
            AppConfig config) {
        this.scanService      = scanService;
        this.moveService      = moveService;
        this.duplicateService = duplicateService;
        this.logService       = logService;
        this.watchService     = watchService;
        this.ruleEngine       = ruleEngine;
        this.config           = config;
    }

    // =========================================================
    // 執行期熱替換規則引擎
    // =========================================================

    public void setRuleEngine(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    // =========================================================
    // 掃描
    // =========================================================

    public void scanAsync(Path directory, Consumer<Integer> onDone) {
        int depth = config.getScanDepth();
        lastScannedDirectory = directory;
        setBusy(true, "[系統] 掃描中（深度 " + depth + " 層）：" + directory.getFileName());

        Thread.ofVirtual().start(() -> {
            try {
                List<FileItem> result = scanService.scanRecursive(directory, depth);

                if (config.isDetectDuplicates()) {
                    duplicateService.detectDuplicates(result);
                }

                // 以掃描的資料夾本身為 targetRoot，實現原地整理
                RuleEngine localEngine = new RuleEngine(ruleEngine.getRules(), directory);
                localEngine.applyAll(result);

                Platform.runLater(() -> {
                    fileItems.setAll(result);
                    setBusy(false, "[系統] 掃描完成（深度 " + depth + " 層），共 " + result.size() + " 個檔案");
                    if (onDone != null) onDone.accept(result.size());
                });
            } catch (Exception e) {
                Platform.runLater(() -> setBusy(false, "[錯誤] 掃描失敗：" + e.getMessage()));
            }
        });
    }

    // =========================================================
    // 整理
    // =========================================================

    /**
     * @param dryRun          true = 預覽模式
     * @param duplicateAction 重複檔案處理方式
     * @param onDone          完成後回呼
     */
    public void organizeAsync(boolean dryRun, DuplicateAction duplicateAction,
                              Consumer<OrganizeResult> onDone) {
        if (fileItems.isEmpty()) {
            setStatus("[錯誤] 請先掃描資料夾");
            return;
        }
        setBusy(true, dryRun ? "[系統] 預覽中，計算整理結果..." : "[系統] 整理中，搬移檔案...");

        List<FileItem> snapshot = List.copyOf(fileItems);
        Path targetDir = lastScannedDirectory;

        Thread.ofVirtual().start(() -> {
            try {
                // 依重複處理模式調整檔案狀態
                applyDuplicateAction(snapshot, duplicateAction, targetDir, dryRun);

                OrganizeResult result = moveService.move(snapshot, dryRun);

                if (!dryRun) {
                    logService.save(result);
                }

                Platform.runLater(() -> {
                    fileItems.setAll(result.getItems());
                    setBusy(false, buildSummary(result, dryRun));
                    if (onDone != null) onDone.accept(result);
                });
            } catch (Exception e) {
                Platform.runLater(() -> setBusy(false, "[錯誤] 整理失敗：" + e.getMessage()));
            }
        });
    }

    /**
     * 依使用者選擇的模式，調整重複檔案的 destinationPath 或 status。
     */
    private void applyDuplicateAction(List<FileItem> items, DuplicateAction action,
                                      Path targetDir, boolean dryRun) {
        for (FileItem item : items) {
            if (item.getStatus() != FileStatus.DUPLICATE) continue;

            switch (action) {
                case MOVE_ALL -> {
                    // 重複檔案也照正常規則移動：把狀態改回 PENDING，
                    // destinationPath 已由 RuleEngine 設好，直接沿用
                    item.setStatus(FileStatus.PENDING);
                }
                case KEEP_ONE -> {
                    // 刪除多餘的重複檔（dryRun 時只標記，不實際刪）
                    if (!dryRun) {
                        try {
                            Files.deleteIfExists(item.getSourcePath());
                        } catch (IOException e) {
                            System.err.println("[錯誤] 刪除重複檔失敗：" + item.getFileName()
                                    + " -> " + e.getMessage());
                        }
                    }
                    item.setStatus(FileStatus.SKIPPED);
                }
                case ISOLATE -> {
                    // 移到「重複檔案」子資料夾
                    if (targetDir != null) {
                        Path isolateDest = targetDir
                                .resolve("重複檔案")
                                .resolve(item.getFileName());
                        item.setDestinationPath(isolateDest);
                        item.setStatus(FileStatus.PENDING);
                    }
                }
            }
        }
    }

    // =========================================================
    // Undo
    // =========================================================

    public void undoAsync(Runnable onDone) {
        setBusy(true, "[系統] 復原上一次操作中...");
        Thread.ofVirtual().start(() -> {
            boolean ok = moveService.undoLast();
            Platform.runLater(() -> {
                setBusy(false, ok
                    ? "[系統] 復原完成，所有檔案已移回原位"
                    : "[資訊] 沒有可復原的操作（尚未執行整理，或已復原過）");
                if (onDone != null) onDone.run();
            });
        });
    }

    // =========================================================
    // WatchService
    // =========================================================

    public void startWatch(Path directory) {
        watchService.setOnNewFileDetected(newFile -> {
            setStatus("[監控] 偵測到新檔案：" + newFile.getFileName() + "，重新掃描中...");
            scanAsync(directory, null);
        });
        watchService.startWatch(directory);
        setStatus("[系統] 即時監控已啟動：" + directory.getFileName());
    }

    public void stopWatch() {
        watchService.stopWatch();
        setStatus("[系統] 即時監控已停止");
    }

    public void shutdown() {
        if (watchService instanceof FolderWatchServiceImpl fws) {
            fws.shutdown();
        } else {
            watchService.stopWatch();
        }
    }

    public boolean isWatching() {
        return watchService.isWatching();
    }

    // =========================================================
    // 日誌查詢
    // =========================================================

    public List<OrganizeResult> getRecentLogs(int limit) {
        return logService.getRecent(limit);
    }

    public void clearLogs() {
        logService.clearAll();
        setStatus("[系統] 日誌已清除");
    }

    // =========================================================
    // JavaFX 可綁定屬性
    // =========================================================

    public ObservableList<FileItem> getFileItems()  { return fileItems; }
    public BooleanProperty busyProperty()           { return busy; }
    public StringProperty statusMessageProperty()   { return statusMessage; }

    // =========================================================
    // 私有工具
    // =========================================================

    private void setBusy(boolean isBusy, String message) {
        busy.set(isBusy);
        statusMessage.set(message);
    }

    private void setStatus(String message) {
        statusMessage.set(message);
    }

    private String buildSummary(OrganizeResult r, boolean dryRun) {
        String prefix = dryRun ? "[預覽] " : "[完成] ";
        return String.format(
            "%s已處理 %d 個檔案 — 搬移 %d、略過 %d、重複 %d、失敗 %d",
            prefix, r.getTotalCount(), r.getMovedCount(),
            r.getSkippedCount(), r.getDuplicateCount(), r.getFailedCount());
    }
}
