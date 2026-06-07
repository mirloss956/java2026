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

public class MainController {

    @FXML private TableView<FileItem>            fileTable;
    @FXML private TextArea                        lblStatus;
    @FXML private CheckBox                        chkDryRun;
    @FXML private CheckBox                        toggleWatch;
    @FXML private VBox                            dropPane;
    @FXML private Spinner<Integer>                spinnerDepth;

    @FXML private TableColumn<FileItem, String>  colName;
    @FXML private TableColumn<FileItem, String>  colPath;
    @FXML private TableColumn<FileItem, String>  colSize;
    @FXML private TableColumn<FileItem, String>  colStatus;
    @FXML private TableColumn<FileItem, String>  colDest;

    private Path currentDirectory;

    @FXML
    public void initialize() {
        OrganizerFacade facade = AppContext.get().getFacade();
        AppConfig config = AppContext.get().getConfig();

        // ── TableView ──────────────────────────────────────────────────────────
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

            // 顯示「目標資料夾/檔名」，例如：圖片/photo.jpg 或 2025/06/report.pdf
            if (colDest != null)
                colDest.setCellValueFactory(cd -> {
                    Path dest = cd.getValue().getDestinationPath();
                    if (dest == null) return new SimpleStringProperty("—");
                    int count = dest.getNameCount();
                    String display = count >= 2
                        ? dest.getName(count - 2) + "/" + dest.getName(count - 1)
                        : dest.getFileName().toString();
                    return new SimpleStringProperty(display);
                });
        }

        // ── 掃描深度 Spinner ────────────────────────────────────────────────────
        if (spinnerDepth != null) {
            SpinnerValueFactory<Integer> valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, config.getScanDepth());
            spinnerDepth.setValueFactory(valueFactory);
            spinnerDepth.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) spinnerDepth.increment(0);
            });
        }

        // ── 狀態列 ──────────────────────────────────────────────────────────────
        if (lblStatus != null) {
            facade.statusMessageProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isBlank()) {
                    lblStatus.appendText(newVal + "\n");
                }
            });
        }

        // ── 即時監控 CheckBox ───────────────────────────────────────────────────
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

        // ── 拖曳區點擊 ──────────────────────────────────────────────────────────
        if (dropPane != null) {
            dropPane.setOnMouseClicked(event -> openDirectoryChooser());
        }

        if (lblStatus != null) {
            lblStatus.appendText("[系統] 就緒。請拖曳資料夾或點擊選擇資料夾。\n");
        }
    }

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
        AppContext.get().getFacade().organizeAsync(dryRun, result -> {});
    }

    @FXML
    void handleUndo(ActionEvent event) {
        AppContext.get().getFacade().undoAsync(null);
    }

    private void openDirectoryChooser() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("選擇要整理的資料夾");
        File selectedDir = chooser.showDialog(dropPane.getScene().getWindow());
        if (selectedDir != null) triggerScan(selectedDir.toPath());
    }

    private void triggerScan(Path directory) {
        OrganizerFacade facade = AppContext.get().getFacade();
        AppConfig config = AppContext.get().getConfig();

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
