package com.fileorganizer.controller;

import com.fileorganizer.config.AppConfig;
import com.fileorganizer.model.FileItem;
import com.fileorganizer.service.impl.AppContext;
import com.fileorganizer.service.impl.OrganizerFacade;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;

/**
 * 負責人：A（UI 互動）
 *
 * 修正項目（dev/C review）：
 *  - import 路徑改為 com.fileorganizer.service.impl.AppContext（正確 package）
 *  - 補上 currentDirectory 欄位，供 toggleWatch 使用
 *  - 補上 lblStatus 與 statusMessageProperty 的 listener binding
 *  - 補上 toggleWatch CheckBox 的 ChangeListener（startWatch / stopWatch）
 */
public class MainController {

    @FXML private TableView<FileItem>           fileTable;
    @FXML private TextArea                       lblStatus;
    @FXML private CheckBox                       chkDryRun;
    @FXML private CheckBox                       toggleWatch;
    @FXML private VBox                           dropPane;

    @FXML private TableColumn<FileItem, String> colName;
    @FXML private TableColumn<FileItem, String> colPath;
    @FXML private TableColumn<FileItem, String> colSize;
    @FXML private TableColumn<FileItem, String> colStatus;

    /** 使用者最近一次選擇（或拖曳）的資料夾，供 toggleWatch 使用 */
    private Path currentDirectory;

    @FXML
    public void initialize() {
        OrganizerFacade facade = AppContext.get().getFacade();
        AppConfig config = AppContext.get().getConfig();

        // ── TableView ────────────────────────────────────────────────────────
        if (fileTable != null) {
            fileTable.setItems(facade.getFileItems());

            if (colName != null)
                colName.setCellValueFactory(
                    cd -> new SimpleStringProperty(cd.getValue().getFileName()));
            if (colPath != null)
                colPath.setCellValueFactory(
                    cd -> new SimpleStringProperty(cd.getValue().getSourcePath().toString()));
            if (colSize != null)
                colSize.setCellValueFactory(
                    cd -> new SimpleStringProperty(formatSize(cd.getValue().getSizeBytes())));
            if (colStatus != null)
                colStatus.setCellValueFactory(
                    cd -> new SimpleStringProperty(cd.getValue().getStatus().name()));
        }

        // ── 狀態列：綁定 Facade 的 statusMessageProperty ─────────────────────
        // 用 listener 而非直接 bind，以便保留 appendText 的彈性
        if (lblStatus != null) {
            facade.statusMessageProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isBlank()) {
                    lblStatus.appendText(newVal + "\n");
                }
            });
        }

        // ── 即時監控 CheckBox ─────────────────────────────────────────────────
        if (toggleWatch != null) {
            // 根據 config 預設值同步 UI 狀態（不觸發 listener）
            toggleWatch.setSelected(config.isWatchEnabled());

            toggleWatch.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                if (isNowSelected) {
                    if (currentDirectory == null) {
                        lblStatus.appendText("[錯誤] 請先選擇或拖曳一個資料夾再啟動監控。\n");
                        toggleWatch.setSelected(false);
                        return;
                    }
                    facade.startWatch(currentDirectory);
                } else {
                    facade.stopWatch();
                }
            });
        }

        // ── 拖曳區點擊選擇資料夾 ──────────────────────────────────────────────
        if (dropPane != null) {
            dropPane.setOnMouseClicked(event -> openDirectoryChooser());
        }

        // ── 初始化提示 ────────────────────────────────────────────────────────
        if (lblStatus != null) {
            lblStatus.appendText("[系統] 就緒。請拖曳資料夾或點擊選擇資料夾。\n");
        }
    }

    // ── FXML 事件處理 ─────────────────────────────────────────────────────────

    @FXML
    void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.ANY);
            if (dropPane != null) {
                dropPane.setStyle("-fx-background-color: #e8f4fd; -fx-border-color: #3498db;");
            }
        }
        event.consume();
    }

    @FXML
    void handleDragDropped(DragEvent event) {
        boolean success = false;
        if (event.getDragboard().hasFiles()) {
            File file = event.getDragboard().getFiles().get(0);
            if (file.isDirectory()) {
                triggerScan(file.toPath());
                success = true;
            } else {
                if (lblStatus != null)
                    lblStatus.appendText("[錯誤] 請拖曳「資料夾」而非單一檔案。\n");
            }
        }
        event.setDropCompleted(success);
        if (dropPane != null) dropPane.setStyle("");
        event.consume();
    }

    @FXML
    void handleOrganize(ActionEvent event) {
        if (currentDirectory == null) {
            if (lblStatus != null)
                lblStatus.appendText("[錯誤] 請先選擇或拖曳一個資料夾再整理。\n");
            return;
        }
        boolean dryRun = chkDryRun != null && chkDryRun.isSelected();
        AppContext.get().getFacade().organizeAsync(dryRun, result -> {
            // 結果已在 UI 執行緒；statusMessageProperty listener 會自動更新 lblStatus
        });
    }

    @FXML
    void handleUndo(ActionEvent event) {
        AppContext.get().getFacade().undoAsync(null);
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    /** 開啟目錄選擇對話框 */
    private void openDirectoryChooser() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("選擇要整理的資料夾");
        File selectedDir = chooser.showDialog(dropPane.getScene().getWindow());
        if (selectedDir != null) {
            triggerScan(selectedDir.toPath());
        }
    }

    /**
     * 儲存選擇的資料夾並啟動掃描。
     * currentDirectory 儲存後，toggleWatch 才能正確使用。
     */
    private void triggerScan(Path directory) {
        currentDirectory = directory;           // ← 儲存供 toggleWatch 使用
        AppContext.get().getFacade().scanAsync(directory, count -> {
            // count 已在 UI 執行緒；statusMessageProperty listener 會自動更新 lblStatus
        });
    }

    /** 將 byte 數格式化為人類可讀字串，例如 1.2 MB */
    private static String formatSize(long bytes) {
        if (bytes < 1024)               return bytes + " B";
        if (bytes < 1024 * 1024)        return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
