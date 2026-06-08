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
 * 修改：新增 handleBatchRename()，開啟批次重新命名對話框。
 * 其餘邏輯不變。
 */
public class MainController {

    @FXML private TableView<FileItem>           fileTable;
    @FXML private TextArea                       lblStatus;
    @FXML private CheckBox                       chkDryRun;
    @FXML private CheckBox                       toggleWatch;
    @FXML private VBox                           dropPane;
    @FXML private Spinner<Integer>               spinnerDepth;
    @FXML private Button                         btnBatchRename;   // 新增按鈕

    @FXML private TableColumn<FileItem, String> colName;
    @FXML private TableColumn<FileItem, String> colPath;
    @FXML private TableColumn<FileItem, String> colSize;
    @FXML private TableColumn<FileItem, String> colStatus;

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

    // ── 事件：批次重新命名（新增）────────────────────────────────────────

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
            if (fxmlUrl == null) {
                throw new IOException("找不到 batch_rename.fxml，請確認檔案位於 src/main/resources/fxml/");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            VBox root = loader.load();

            // 注入目前掃描到的 FileItem 清單
            BatchRenameController controller = loader.getController();
            controller.setItems(List.copyOf(items));

            Stage dialog = new Stage();
            dialog.setTitle("批次重新命名");
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(dropPane.getScene().getWindow());
            dialog.setScene(new Scene(root));
            dialog.setResizable(true);
            dialog.showAndWait();

            // 對話框關閉後，重新整理主視窗 TableView（檔名可能已改變）
            fileTable.refresh();
            if (lblStatus != null)
                lblStatus.appendText("[批次重新命名] 對話框已關閉。\n");

        } catch (IOException e) {
            if (lblStatus != null)
                lblStatus.appendText("[錯誤] 無法開啟批次重新命名：" + e.getMessage() + "\n");
        }
    }

    // ── 其餘事件（不變）──────────────────────────────────────────────────

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

        Optional<DuplicateAction> actionOpt = DuplicateActionDialog.show();
        if (actionOpt.isEmpty()) {
            if (lblStatus != null)
                lblStatus.appendText("[資訊] 已取消整理。\n");
            return;
        }

        AppContext.get().getFacade().organizeAsync(dryRun, actionOpt.get(), result -> {});
    }

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
