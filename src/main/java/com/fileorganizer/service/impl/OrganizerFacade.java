package com.fileorganizer.service.impl;

import com.fileorganizer.model.*;
import com.fileorganizer.rule.RuleEngine;
import com.fileorganizer.service.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.*;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 負責人：C
 *
 * 整合橋接層。A（UI Controller）只需要跟這個類別說話，
 * 不需要知道 B 的任何實作細節。
 *
 * 職責：
 *  1. 把 A 的「掃描 + 整理」指令，按正確順序串接 B 的各個 service
 *  2. 在背景執行緒跑耗時操作，結果切回 JavaFX UI 執行緒
 *  3. 暴露 ObservableList / Property 讓 A 做資料綁定
 */
public class OrganizerFacade {

    // --- 注入的 Service（B 實作，C 持有）---
    private final FileScanService scanService;
    private final FileMoveService moveService;
    private final DuplicateDetectService duplicateService;
    private final LogService logService;
    private final WatchService watchService;
    private final RuleEngine ruleEngine;

    // --- A 可以直接 bind 的狀態 ---
    private final ObservableList<FileItem> fileItems = FXCollections.observableArrayList();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("就緒");

    public OrganizerFacade(
            FileScanService scanService,
            FileMoveService moveService,
            DuplicateDetectService duplicateService,
            LogService logService,
            WatchService watchService,
            RuleEngine ruleEngine) {
        this.scanService = scanService;
        this.moveService = moveService;
        this.duplicateService = duplicateService;
        this.logService = logService;
        this.watchService = watchService;
        this.ruleEngine = ruleEngine;
    }

    // =========================================================
    // 掃描（A 拖曳或選擇資料夾後呼叫）
    // =========================================================

    /**
     * 掃描資料夾，結果更新到 fileItems（ObservableList）。
     * 在背景執行，完成後自動切回 UI 執行緒。
     * @param onDone 完成後的回呼（可用來更新 UI 狀態列）
     */
    public void scanAsync(Path directory, Consumer<Integer> onDone) {
        setBusy(true, "掃描中：" + directory.getFileName());

        Thread.ofVirtual().start(() -> {
            try {
                List<FileItem> result = scanService.scan(directory);

                // 若啟用重複偵測
                duplicateService.detectDuplicates(result);

                // 套用規則，預先計算 destinationPath
                ruleEngine.applyAll(result);

                Platform.runLater(() -> {
                    fileItems.setAll(result);
                    setBusy(false, "掃描完成，共 " + result.size() + " 個檔案");
                    if (onDone != null) onDone.accept(result.size());
                });
            } catch (Exception e) {
                Platform.runLater(() -> setBusy(false, "掃描失敗：" + e.getMessage()));
            }
        });
    }

    // =========================================================
    // 整理（A 按下「開始整理」按鈕後呼叫）
    // =========================================================

    /**
     * @param dryRun true = 預覽模式（不實際搬移）
     * @param onDone 完成後的回呼，傳入 OrganizeResult 供 A 顯示統計
     */
    public void organizeAsync(boolean dryRun, Consumer<OrganizeResult> onDone) {
        if (fileItems.isEmpty()) {
            setStatus("請先掃描資料夾");
            return;
        }
        setBusy(true, dryRun ? "預覽中..." : "整理中...");

        List<FileItem> snapshot = List.copyOf(fileItems);

        Thread.ofVirtual().start(() -> {
            try {
                OrganizeResult result = moveService.move(snapshot, dryRun);

                if (!dryRun) {
                    logService.save(result);
                }

                Platform.runLater(() -> {
                    fileItems.setAll(result.getItems()); // 更新 status 顯示
                    setBusy(false, buildSummary(result, dryRun));
                    if (onDone != null) onDone.accept(result);
                });
            } catch (Exception e) {
                Platform.runLater(() -> setBusy(false, "整理失敗：" + e.getMessage()));
            }
        });
    }

    // =========================================================
    // Undo
    // =========================================================

    public void undoAsync(Runnable onDone) {
        setBusy(true, "復原中...");
        Thread.ofVirtual().start(() -> {
            boolean ok = moveService.undoLast();
            Platform.runLater(() -> {
                setBusy(false, ok ? "已復原上次操作" : "無法復原（無紀錄）");
                if (onDone != null) onDone.run();
            });
        });
    }

    // =========================================================
    // WatchService 控制（A 的監控開關）
    // =========================================================

    public void startWatch(Path directory) {
        watchService.setOnNewFileDetected(newFile -> {
            // 已在 UI 執行緒（FolderWatchServiceImpl 內有 Platform.runLater）
            setStatus("偵測到新檔案：" + newFile.getFileName());
            scanAsync(directory, null); // 自動重新掃描
        });
        watchService.startWatch(directory);
        setStatus("即時監控已啟動");
    }

    public void stopWatch() {
        watchService.stopWatch();
        setStatus("即時監控已停止");
    }

    public boolean isWatching() {
        return watchService.isWatching();
    }

    // =========================================================
    // 日誌查詢（A 的日誌頁面用）
    // =========================================================

    public List<OrganizeResult> getRecentLogs(int limit) {
        return logService.getRecent(limit);
    }

    public void clearLogs() {
        logService.clearAll();
        setStatus("日誌已清除");
    }

    // =========================================================
    // JavaFX 可綁定的屬性（A 直接 bind）
    // =========================================================

    /** A 把 TableView 的 items 設成這個 */
    public ObservableList<FileItem> getFileItems() { return fileItems; }

    /** A 的進度轉圈圈：spinner.visibleProperty().bind(facade.busyProperty()) */
    public BooleanProperty busyProperty() { return busy; }

    /** A 的狀態列：label.textProperty().bind(facade.statusMessageProperty()) */
    public StringProperty statusMessageProperty() { return statusMessage; }

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
        String prefix = dryRun ? "[預覽] " : "";
        return String.format("%s已處理 %d 個檔案 — 搬移 %d、略過 %d、重複 %d、失敗 %d",
                prefix, r.getTotalCount(), r.getMovedCount(),
                r.getSkippedCount(), r.getDuplicateCount(), r.getFailedCount());
    }
}
