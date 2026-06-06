package com.fileorganizer.controller;

import com.fileorganizer.config.AppConfig;
import com.fileorganizer.model.FileItem;
import com.fileorganizer.service.impl.AppContext;
import com.fileorganizer.service.impl.OrganizerFacade;
import com.fileorganizer.util.FileSizeUtil;
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
 * 修正項目：
 *  - 新增 spinnerDepth 欄位（對應 FXML 的 fx:id="spinnerDepth"）
 *  - initialize() 從 config.getScanDepth() 初始化 Spinner 預設值
 *  - triggerScan() 掃描前先將 Spinner 值寫回 AppConfig，確保 Facade 讀到最新深度
 */
public class MainController {

    @FXML private TableView<FileItem>           fileTable;
    @FXML private TextArea                       lblStatus;
    @FXML private CheckBox                       chkDryRun;
    @FXML private CheckBox                       toggleWatch;
    @FXML private VBox                           dropPane;
    @FXML private Spinner<Integer>               spinnerDepth;

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
                    cd -> new SimpleStringProperty(
                        FileSizeUtil.humanReadable(cd.getValue().getSizeBytes())));
            if (colStatus != null)
                colStatus.setCellValueFactory(
                    cd -> new SimpleStringProperty(cd.getValue().getStatus().name()));
        }

        // ── 掃描深度 Spinner ──────────────────────────────────────────────────
        if (spinnerDepth != null) {
            // 以 config 儲存的值作為初始值，範圍 1–10
            SpinnerValueFactory<Integer> valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, config.getScanDepth());
            spinnerDepth.setValueFactory(valueFactory);

            // 使用者直接在輸入框打字時，commitValue() 才會觸發；
            // 加上 focusedProperty listener 確保失焦時也能更新
            spinnerDepth.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) {
                    spinnerDepth.increment(0); // 強制 commit 文字框中的值
                }
            });
        }

        // ── 狀態列：追加 Facade 狀態訊息 ─────────────────────────────────────
        if (lblStatus != null) {
            facade.statusMessageProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isBlank()) {
                    lblStatus.appendText(newVal + "\n");
                }
            });
        }

        // ── 即時監控 CheckBox ─────────────────────────────────────────────────
        if (toggleWatch != null) {
            toggleWatch.setSelected(config.isWatchEnabled());

            toggleWatch.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                if (isNowSelected) {
                    if (currentDirectory == null) {
                        if (lblStatus != null)
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
            if (dropPane != null)
                dropPane.setStyle("-fx-background-color: #e8f4fd; -fx-border-color: #3498db;");
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
            // statusMessageProperty listener 會自動更新 lblStatus
        });
    }

    @FXML
    void handleUndo(ActionEvent event) {
        AppContext.get().getFacade().undoAsync(null);
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private void openDirectoryChooser() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("選擇要整理的資料夾");
        File selectedDir = chooser.showDialog(dropPane.getScene().getWindow());
        if (selectedDir != null) {
            triggerScan(selectedDir.toPath());
        }
    }

    /**
     * 掃描前先將 Spinner 值寫入 AppConfig，確保 OrganizerFacade 讀到最新深度。
     * 若使用者換了資料夾且監控正在執行，先停止舊的監控，
     * 掃描完成後若 toggleWatch 仍為勾選，自動對新資料夾啟動監控。
     */
    private void triggerScan(Path directory) {
        OrganizerFacade facade = AppContext.get().getFacade();
        AppConfig config = AppContext.get().getConfig();

        // 將 Spinner 值同步到 AppConfig
        if (spinnerDepth != null) {
            config.setScanDepth(spinnerDepth.getValue());
        }

        // 換資料夾時：若正在監控舊資料夾，先停止
        boolean wasWatching = facade.isWatching();
        if (wasWatching) {
            facade.stopWatch();
        }

        currentDirectory = directory;

        facade.scanAsync(directory, count -> {
            // 掃描完成後，若原本監控中（或 CheckBox 仍勾選），自動對新資料夾啟動監控
            if (wasWatching || (toggleWatch != null && toggleWatch.isSelected())) {
                facade.startWatch(directory);
                if (toggleWatch != null && !toggleWatch.isSelected()) {
                    toggleWatch.setSelected(true);
                }
            }
        });
    }
}
