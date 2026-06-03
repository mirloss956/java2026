package com.fileorganizer.service.impl;

import com.fileorganizer.config.AppConfig;
import com.fileorganizer.model.*;
import com.fileorganizer.rule.RuleEngine;
import com.fileorganizer.service.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.*;

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
 *  - 建構子新增 AppConfig 參數
 *  - scanAsync 依 config.isDetectDuplicates() 決定是否執行重複偵測
 *  - 新增 setRuleEngine()，供 AppContext.updateRuleMode() 執行期熱替換規則引擎
 */
public class OrganizerFacade {

    private final FileScanService scanService;
    private final FileMoveService moveService;
    private final DuplicateDetectService duplicateService;
    private final LogService logService;
    private final WatchService watchService;
    private final AppConfig config;

    /** volatile：updateRuleMode() 可能在任意執行緒呼叫，scanAsync 在虛擬執行緒讀取 */
    private volatile RuleEngine ruleEngine;

    private final ObservableList<FileItem> fileItems = FXCollections.observableArrayList();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("就緒");

    public OrganizerFacade(
            FileScanService scanService,
            FileMoveService moveService,
            DuplicateDetectService duplicateService,
            LogService logService,
            WatchService watchService,
            RuleEngine ruleEngine,
            AppConfig config) {
        this.scanService = scanService;
        this.moveService = moveService;
        this.duplicateService = duplicateService;
        this.logService = logService;
        this.watchService = watchService;
        this.ruleEngine = ruleEngine;
        this.config = config;
    }

    // =========================================================
    // 執行期熱替換規則引擎（⑤）
    // =========================================================

    /**
     * 由 AppContext.updateRuleMode() 呼叫，替換為新規則引擎。
     * 下次 scanAsync 時自動使用新規則。
     */
    public void setRuleEngine(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    // =========================================================
    // 掃描
    // =========================================================

    public void scanAsync(Path directory, Consumer<Integer> onDone) {
        setBusy(true, "掃描中：" + directory.getFileName());

        Thread.ofVirtual().start(() -> {
            try {
                List<FileItem> result = scanService.scan(directory);

                // 依 config 開關決定是否執行重複偵測
                if (config.isDetectDuplicates()) {
                    duplicateService.detectDuplicates(result);
                }

                // 讀取當下的 ruleEngine（volatile 保證可見性）
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
    // 整理
    // =========================================================

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
                    fileItems.setAll(result.getItems());
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
    // WatchService 控制
    // =========================================================

    public void startWatch(Path directory) {
        watchService.setOnNewFileDetected(newFile -> {
            setStatus("偵測到新檔案：" + newFile.getFileName());
            scanAsync(directory, null);
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
    // 日誌查詢
    // =========================================================

    public List<OrganizeResult> getRecentLogs(int limit) {
        return logService.getRecent(limit);
    }

    public void clearLogs() {
        logService.clearAll();
        setStatus("日誌已清除");
    }

    // =========================================================
    // JavaFX 可綁定的屬性
    // =========================================================

    public ObservableList<FileItem> getFileItems()        { return fileItems; }
    public BooleanProperty busyProperty()                 { return busy; }
    public StringProperty statusMessageProperty()         { return statusMessage; }

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
