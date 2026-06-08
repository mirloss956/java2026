package com.fileorganizer.controller;

import com.fileorganizer.config.AppConfig;
import com.fileorganizer.controller.DuplicateActionDialog.DuplicateAction;
import com.fileorganizer.model.FileItem;
import com.fileorganizer.service.impl.AppContext;
import com.fileorganizer.service.impl.OrganizerFacade;
import com.fileorganizer.util.FileSizeUtil;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 負責人：A（UI 互動）
 *
 * 操作流程：
 *   1. 拖曳資料夾（或點擊選擇）→ 只記錄路徑，提示使用者調整掃描深度
 *   2. 調整 Spinner 掃描深度
 *   3. 按「掃描」鍵 → 才開始實際掃描，並顯示 loading 動畫
 */
public class MainController {

    @FXML private TableView<FileItem>            fileTable;
    @FXML private TextArea                        lblStatus;
    @FXML private CheckBox                        chkDryRun;
    @FXML private CheckBox                        toggleWatch;
    @FXML private VBox                            dropPane;
    @FXML private Label                           lblDropHint;
    @FXML private HBox                            scanningIndicator;   // ← loading HBox
    @FXML private Label                           lblScanningHint;     // ← loading 文字
    @FXML private Spinner<Integer>                spinnerDepth;
    @FXML private Button                          btnScan;
    @FXML private Button                          btnBatchRename;

    @FXML private TableColumn<FileItem, String>  colName;
    @FXML private TableColumn<FileItem, String>  colPath;
    @FXML private TableColumn<FileItem, String>  colSize;
    @FXML private TableColumn<FileItem, String>  colStatus;
    @FXML private TableColumn<FileItem, String>  colDest;

    /** 已選定的資料夾，僅在按下掃描鍵後才真正掃描 */
    private Path currentDirectory;

    @FXML
    public void initialize() {
        OrganizerFacade facade = AppContext.get().getFacade();
        AppConfig config       = AppContext.get().getConfig();

        // ── TableView ─────────────────────────────────────────────────────
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

            if (btnBatchRename != null)
                btnBatchRename.disableProperty().bind(
                    Bindings.isEmpty(facade.getFileItems()));
        }

        // ── 掃描按鈕：初始灰掉，選到資料夾後才啟用 ──────────────────────
        if (btnScan != null)
            btnScan.setDisable(true);

        // ── loading 動畫：初始隱藏 ────────────────────────────────────────
        setScanningIndicator(false, null);

        // ── 掃描深度 Spinner ──────────────────────────────────────────────
        if (spinnerDepth != null) {
            SpinnerValueFactory<Integer> vf =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, config.getScanDepth());
            spinnerDepth.setValueFactory(vf);
            spinnerDepth.focusedProperty().addListener((obs, was, is) -> {
                if (!is) spinnerDepth.increment(0);
            });
        }

        // ── 狀態列 ───────────────────────────────────────────────────────
        if (lblStatus != null) {
            facade.statusMessageProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isBlank())
                    lblStatus.appendText(newVal + "\n");
            });
        }

        // ── 即時監控 CheckBox ─────────────────────────────────────────────
        if (toggleWatch != null) {
            toggleWatch.setSelected(config.isWatchEnabled());
            toggleWatch.selectedProperty().addListener((obs, was, is) -> {
                if (is) {
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

        // ── 拖曳區點擊：開啟資料夾選擇器（不立即掃描）──────────────────
        if (dropPane != null)
            dropPane.setOnMouseClicked(e -> openDirectoryChooser());

        if (lblStatus != null)
            lblStatus.appendText("[系統] 就緒。請拖曳資料夾或點擊選擇資料夾。\n");
    }

    // =========================================================
    // 事件：拖曳
    // =========================================================

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
                // ★ 只記錄路徑，不掃描
                setSelectedDirectory(file.toPath());
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

    // =========================================================
    // 事件：掃描（使用者按下按鈕才執行）
    // =========================================================

    @FXML
    void handleScan(ActionEvent event) {
        if (currentDirectory == null) {
            if (lblStatus != null)
                lblStatus.appendText("[提示] 請先拖曳或選擇一個資料夾。\n");
            return;
        }
        executeScan(currentDirectory);
    }

    // =========================================================
    // 事件：開始整理
    // =========================================================

    @FXML
    void handleOrganize(ActionEvent event) {
        if (currentDirectory == null) {
            if (lblStatus != null)
                lblStatus.appendText("[錯誤] 請先選擇或拖曳一個資料夾再整理。\n");
            return;
        }
        boolean dryRun = chkDryRun != null && chkDryRun.isSelected();

        Optional<DuplicateAction> actionOpt = DuplicateActionDialog.show();
        if (actionOpt.isEmpty()) {
            if (lblStatus != null)
                lblStatus.appendText("[資訊] 已取消整理。\n");
            return;
        }

        AppContext.get().getFacade().organizeAsync(dryRun, actionOpt.get(), result -> {});
    }

    // =========================================================
    // 事件：復原
    // =========================================================

    @FXML
    void handleUndo(ActionEvent event) {
        AppContext.get().getFacade().undoAsync(null);
    }

    // =========================================================
    // 事件：批次重新命名
    // =========================================================

    @FXML
    void handleBatchRename(ActionEvent event) {
        OrganizerFacade facade = AppContext.get().getFacade();
        List<FileItem> items   = facade.getFileItems();

        if (items.isEmpty()) {
            if (lblStatus != null)
                lblStatus.appendText("[錯誤] 請先掃描資料夾，才能使用批次重新命名。\n");
            return;
        }

        try {
            URL fxmlUrl = getClass().getResource("/fxml/batch_rename.fxml");
            if (fxmlUrl == null)
                throw new IOException("找不到 batch_rename.fxml");

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            VBox root = loader.load();

            BatchRenameController ctrl = loader.getController();
            ctrl.setItems(List.copyOf(items));

            Stage dialog = new Stage();
            dialog.setTitle("批次重新命名");
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(dropPane.getScene().getWindow());
            dialog.setScene(new Scene(root));
            dialog.setResizable(true);
            dialog.showAndWait();

            fileTable.refresh();
            if (lblStatus != null)
                lblStatus.appendText("[批次重新命名] 對話框已關閉。\n");

        } catch (IOException e) {
            if (lblStatus != null)
                lblStatus.appendText("[錯誤] 無法開啟批次重新命名：" + e.getMessage() + "\n");
        }
    }

    // =========================================================
    // 事件：磁碟分析
    // =========================================================

    @FXML
    void onOpenDiskDashboard(ActionEvent event) {
        try {
            URL fxml = getClass().getResource("/fxml/DiskDashboard.fxml");
            FXMLLoader loader = new FXMLLoader(fxml);
            Scene scene = new Scene(loader.load());

            Stage stage = new Stage();
            stage.setTitle("磁碟分析");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(dropPane.getScene().getWindow());
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            if (lblStatus != null)
                lblStatus.appendText("[錯誤] 無法開啟磁碟分析視窗：" + e.getMessage() + "\n");
        }
    }

    // =========================================================
    // 私有工具
    // =========================================================

    /** 開啟資料夾選擇器，選好後只記錄路徑，不掃描。 */
    private void openDirectoryChooser() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("選擇要整理的資料夾");
        File selectedDir = chooser.showDialog(dropPane.getScene().getWindow());
        if (selectedDir != null)
            setSelectedDirectory(selectedDir.toPath());
    }

    /**
     * 記錄選定的資料夾路徑，更新 UI 提示，啟用掃描按鈕。
     * 不執行掃描。
     */
    private void setSelectedDirectory(Path directory) {
        currentDirectory = directory;

        if (lblDropHint != null)
            lblDropHint.setText("📂 已選擇：" + directory.toAbsolutePath()
                + "　← 確認掃描深度後，按「🔍 掃描」開始");

        if (btnScan != null)
            btnScan.setDisable(false);

        if (lblStatus != null)
            lblStatus.appendText("[系統] 已選擇資料夾：" + directory.toAbsolutePath()
                + "　請確認掃描深度後按「掃描」。\n");
    }

    /**
     * 實際執行掃描（只有按下掃描鍵才呼叫）。
     * 掃描開始時顯示 loading，完成後隱藏。
     */
    private void executeScan(Path directory) {
        OrganizerFacade facade = AppContext.get().getFacade();
        AppConfig config       = AppContext.get().getConfig();

        if (spinnerDepth != null) config.setScanDepth(spinnerDepth.getValue());

        boolean wasWatching = facade.isWatching();
        if (wasWatching) facade.stopWatch();

        // ── 掃描開始：顯示 loading、禁用按鈕 ────────────────────────────
        if (btnScan != null) btnScan.setDisable(true);
        setScanningIndicator(true, "掃描中（深度 " + config.getScanDepth() + " 層），請稍候...");

        facade.scanAsync(directory, count -> {
            // ── 掃描結束：回 UI 執行緒收起 loading、恢復按鈕 ────────────
            Platform.runLater(() -> {
                setScanningIndicator(false, null);
                if (btnScan != null) btnScan.setDisable(false);
            });

            if (wasWatching || (toggleWatch != null && toggleWatch.isSelected())) {
                facade.startWatch(directory);
                if (toggleWatch != null && !toggleWatch.isSelected())
                    Platform.runLater(() -> toggleWatch.setSelected(true));
            }
        });
    }

    /**
     * 控制拖曳區的 loading 動畫顯示／隱藏。
     *
     * @param show    true = 顯示，false = 隱藏
     * @param message 顯示時的文字；null 則維持 FXML 預設文字
     */
    private void setScanningIndicator(boolean show, String message) {
        if (scanningIndicator != null) {
            scanningIndicator.setVisible(show);
            scanningIndicator.setManaged(show);
        }
        if (lblDropHint != null)
            lblDropHint.setVisible(!show);   // 掃描中隱藏原提示，避免兩行擠在一起

        if (show && message != null && lblScanningHint != null)
            lblScanningHint.setText(message);
    }
}
