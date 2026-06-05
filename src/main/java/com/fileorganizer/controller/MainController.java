package com.fileorganizer.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import com.fileorganizer.config.AppConfig;
import com.fileorganizer.model.FileItem;
import com.fileorganizer.service.impl.AppContext;
import com.fileorganizer.service.impl.OrganizerFacade;

import java.io.File;
import java.nio.file.Path;

public class MainController {

    @FXML private TableView<FileItem>            fileTable;
    @FXML private TextArea                        lblStatus;
    @FXML private CheckBox                        chkDryRun;
    @FXML private CheckBox                        toggleWatch;
    @FXML private VBox                            dropPane;

    @FXML private TableColumn<FileItem, String>  colName;
    @FXML private TableColumn<FileItem, String>  colPath;
    @FXML private TableColumn<FileItem, String>  colSize;
    @FXML private TableColumn<FileItem, String>  colStatus;

    private AppConfig currentConfig;

    @FXML
    public void initialize() {
        OrganizerFacade facade = AppContext.get().getFacade();
        currentConfig = AppContext.get().getConfig();

        // ── TableView ────────────────────────────────────────────────────────
        if (fileTable != null) {
            fileTable.setItems(facade.getFileItems());

            if (colName   != null) colName  .setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getFileName()));
            if (colPath   != null) colPath  .setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getSourcePath().toString()));
            if (colSize   != null) colSize  .setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(formatSize(cd.getValue().getSizeBytes())));
            if (colStatus != null) colStatus.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getStatus().name()));
        }

        // ── 狀態列 bind ───────────────────────────────────────────────────────
        if (lblStatus != null) {
            facade.statusMessageProperty().addListener(
                (obs, oldVal, newVal) -> Platform.runLater(() -> lblStatus.appendText(newVal + "\n"))
            );
        }

        // ── 預覽模式 CheckBox ─────────────────────────────────────────────────
        if (chkDryRun != null) {
            chkDryRun.setSelected(currentConfig.isDryRunDefault());
        }

        // ── 監控開關 CheckBox ─────────────────────────────────────────────────
        if (toggleWatch != null) {
            toggleWatch.setSelected(currentConfig.isWatchEnabled());
            toggleWatch.selectedProperty().addListener((obs, oldVal, isOn) -> {
                if (isOn) {
                    Path src = currentConfig.getSourceDirectory();
                    if (src != null) {
                        facade.startWatch(src);
                    } else {
                        if (lblStatus != null) lblStatus.appendText("[警告] 尚未設定來源資料夾，無法啟動監控。\n");
                        toggleWatch.setSelected(false);
                    }
                } else {
                    facade.stopWatch();
                }
            });
        }

        // ── 拖曳面板點擊 → 開啟資料夾選擇器 ──────────────────────────────────
        if (dropPane != null) {
            dropPane.setOnMouseClicked(event -> {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("選擇要整理的資料夾");
                File selectedDir = chooser.showDialog(dropPane.getScene().getWindow());
                if (selectedDir != null) {
                    triggerScan(selectedDir.toPath());
                }
            });
        }
    }

    // ── FXML handlers ────────────────────────────────────────────────────────

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
                if (lblStatus != null) lblStatus.appendText("[錯誤] 請拖曳「資料夾」而非單一檔案。\n");
            }
        }
        event.setDropCompleted(success);
        if (dropPane != null) dropPane.setStyle("");
        event.consume();
    }

    @FXML
    void handleOrganize(ActionEvent event) {
        boolean dryRun = chkDryRun != null && chkDryRun.isSelected();
        AppContext.get().getFacade().organizeAsync(dryRun, result -> {
            // result 已在 UI 執行緒；狀態列由 statusMessageProperty listener 自動更新
        });
    }

    @FXML
    void handleUndo(ActionEvent event) {
        AppContext.get().getFacade().undoAsync(null);
    }

    // ── 私有工具 ─────────────────────────────────────────────────────────────

    private void triggerScan(Path directory) {
        AppContext.get().getFacade().scanAsync(directory, count -> {
            // count 已在 UI 執行緒；狀態列由 statusMessageProperty listener 自動更新
        });
    }

    /** 將 byte 數格式化為人類可讀字串，例如 1.2 MB */
    private static String formatSize(long bytes) {
        if (bytes < 1024)                    return bytes + " B";
        if (bytes < 1024 * 1024)             return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)      return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
