package com.fileorganizer.controller;

import com.fileorganizer.config.AppConfig;
import com.fileorganizer.controller.DuplicateActionDialog.DuplicateAction;
import com.fileorganizer.model.FileItem;
import com.fileorganizer.service.impl.AppContext;
import com.fileorganizer.service.impl.OrganizerFacade;
import com.fileorganizer.util.FileSizeUtil;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
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
 * 修改：補上 btnScan @FXML 欄位與 handleScan() 方法，修復掃描按鈕消失問題。
 */
public class MainController {

    @FXML private TableView<FileItem>           fileTable;
    @FXML private TextArea                       lblStatus;
    @FXML private CheckBox                       chkDryRun;
    @FXML private CheckBox                       toggleWatch;
    @FXML private VBox                           dropPane;
    @FXML private Spinner<Integer>               spinnerDepth;
    @FXML private Button                         btnBatchRename;
    @FXML private Button                         btnScan;        // ← 補上：對應 FXML fx:id="btnScan"

    @FXML private TableColumn<FileItem, String> colName;
    @FXML private TableColumn<FileItem, String> colPath;
    @FXML private TableColumn<FileItem, String> colSize;
    @FXML private TableColumn<FileItem, String> colStatus;
    @FXML private TableColumn<FileItem, String> colDest;

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

            // 沒有掃描任何檔案時，「批次重新命名」按鈕灰掉
            if (btnBatchRename != null) {
                btnBatchRename.disableProperty().bind(
                    Bindings.isEmpty(facade.getFileItems())
                );
            }
        }

        // ── 掃描按鈕：沒有選資料夾時灰掉 ────────────────────────────────
        if (btnScan != null) {
            // currentDirectory 是 private 欄位，用 facade.getFileItems() 做間接判斷無意義；
            // 改為在 triggerScan 後解除 disable，初始狀態允許點擊（點後會提示選資料夾）
            // 保持 enable，因為 handleScan 內部會做 null 檢查
        }

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

        // ── 拖曳區點擊 ───────────────────────────────────────────────────
        if (dropPane != null)
            dropPane.setOnMouseClicked(event -> openDirectoryChooser());

        if (lblStatus != null)
            lblStatus.appendText("[系統] 就緒。請拖曳資料夾或點擊選擇資料夾。\n");
    }

    // ── 事件：掃描（補上，對應 FXML onAction="#handleScan"）──────────────

    @FXML
    void handleScan(ActionEvent event) {
        if (currentDirectory == null) {
            // 尚未選資料夾，改為開啟資料夾選擇器
            openDirectoryChooser();
            return;
        }
        // 已有資料夾時，直接重新掃描（允許重複掃描以更新清單）
        triggerScan(currentDirectory);
    }

    // ── 事件：批次重新命名 ────────────────────────────────────────────────

    @FXML
    void handleBatchRename(ActionEvent event) {
        OrganizerFacade facade = AppContext.get().getFacade();
        List<FileItem> items   = facade.getFileItems();

        if (items.isEmpty()) {
            if (lblStatus != null)
                lblStatus.appendText("[資訊] 請先掃描資料夾再執行批次重新命名。\n");
            return;
        }

        try {
            URL fxml = getClass().getResource("/fxml/batch_rename.fxml");
            FXMLLoader loader = new FXMLLoader(fxml);
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                getClass().getResource("/style.css").toExternalForm());

            BatchRenameController ctrl = loader.getController();
            ctrl.setItems(items);

            Stage stage = new Stage();
            stage.setTitle("批次重新命名");
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(dropPane.getScene().getWindow());
            stage.setScene(scene);
            stage.showAndWait();

        } catch (IOException e) {
            if (lblStatus != null)
                lblStatus.appendText("[錯誤] 無法開啟批次重新命名視窗：" + e.getMessage() + "\n");
        }
    }

    // ── 事件：開啟磁碟分析 ────────────────────────────────────────────────

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

    // ── 事件：拖曳 ───────────────────────────────────────────────────────

    @FXML
    void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles())
            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
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

    // ── 事件：開始整理 ───────────────────────────────────────────────────

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

    // ── 事件：復原 ───────────────────────────────────────────────────────

    @FXML
    void handleUndo(ActionEvent event) {
        AppContext.get().getFacade().undoAsync(null);
    }

    // ── 私有工具 ─────────────────────────────────────────────────────────

    private void openDirectoryChooser() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("選擇要整理的資料夾");
        File selectedDir = chooser.showDialog(dropPane.getScene().getWindow());
        if (selectedDir != null) triggerScan(selectedDir.toPath());
    }

    private void triggerScan(Path directory) {
        OrganizerFacade facade = AppContext.get().getFacade();
        AppConfig config       = AppContext.get().getConfig();

        if (spinnerDepth != null) config.setScanDepth(spinnerDepth.getValue());

        boolean wasWatching = facade.isWatching();
        if (wasWatching) facade.stopWatch();

        currentDirectory = directory;

        facade.scanAsync(directory, count -> {
            if (wasWatching || (toggleWatch != null && toggleWatch.isSelected())) {
                facade.startWatch(directory);
                if (toggleWatch != null && !toggleWatch.isSelected())
                    toggleWatch.setSelected(true);
            }
        });
    }
}
