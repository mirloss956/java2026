package com.fileorganizer.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import com.fileorganizer.config.AppConfig;
import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.OrganizeResult;
import com.fileorganizer.service.impl.AppContext;          // ✅ 修正：正確 package
import com.fileorganizer.service.impl.OrganizerFacade;    // ✅ 補充：讓 facade 型別明確

import java.io.File;
import java.nio.file.Path;

public class MainController {

    @FXML private TableView<FileItem> fileTable;
    @FXML private TextArea lblStatus;
    @FXML private CheckBox chkDryRun;
    @FXML private CheckBox toggleWatch;
    @FXML private VBox dropPane;

    @FXML private TableColumn<FileItem, String> colName;
    @FXML private TableColumn<FileItem, String> colPath;
    @FXML private TableColumn<FileItem, String> colSize;
    @FXML private TableColumn<FileItem, String> colStatus;

    private final ObservableList<FileItem> fileDataList = FXCollections.observableArrayList();
    private AppConfig currentConfig;

    @FXML
    public void initialize() {
        OrganizerFacade facade = AppContext.get().getFacade();
        currentConfig = AppContext.get().getConfig();

        // 直接 bind facade 的 ObservableList，不再用本地 fileDataList
        if (fileTable != null) {
            fileTable.setItems(facade.getFileItems());
        }

        // 狀態列 bind
        if (lblStatus != null) {
            facade.statusMessageProperty().addListener(
                (obs, oldVal, newVal) -> Platform.runLater(() -> lblStatus.appendText(newVal + "\n"))
            );
        }

        // 預覽模式 CheckBox 預設值對齊 config
        if (chkDryRun != null) {
            chkDryRun.setSelected(currentConfig.isDryRunDefault());
        }

        // 監控開關 CheckBox 預設值對齊 config
        if (toggleWatch != null) {
            toggleWatch.setSelected(currentConfig.isWatchEnabled());
            toggleWatch.selectedProperty().addListener((obs, oldVal, isOn) -> {
                if (isOn) {
                    Path src = currentConfig.getSourceDirectory();
                    if (src != null) facade.startWatch(src);
                } else {
                    facade.stopWatch();
                }
            });
        }

        // 拖曳面板點擊 → 開啟資料夾選擇器
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
            // result 已在 UI 執行緒，需要時可再做額外操作
        });
    }

    @FXML
    void handleUndo(ActionEvent event) {
        AppContext.get().getFacade().undoAsync(null);
    }

    // ── 私有工具 ─────────────────────────────────────────────────────────────

    private void triggerScan(Path directory) {
        AppContext.get().getFacade().scanAsync(directory, count -> {
            // count 已在 UI 執行緒，狀態列由 statusMessageProperty listener 自動更新
        });
    }
}
