package com.fileorganizer.controller;

import com.fileorganizer.config.AppConfig;
import com.fileorganizer.controller.DuplicateActionDialog.DuplicateAction;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

public class MainController {

    @FXML private TableView<FileItem>            fileTable;
    @FXML private TextArea                        lblStatus;
    @FXML private CheckBox                        chkDryRun;
    @FXML private CheckBox                        toggleWatch;
    @FXML private VBox                            dropPane;
    @FXML private Spinner<Integer>                spinnerDepth;
    @FXML private Label                           lblDropHint;
    @FXML private HBox                            scanningIndicator;
    @FXML private Label                           lblScanningHint;

    @FXML private Button                          btnScan;
    @FXML private Button                          btnOrganize;
    @FXML private Button                          btnUndo;

    @FXML private TableColumn<FileItem, String>  colName;
    @FXML private TableColumn<FileItem, String>  colPath;
    @FXML private TableColumn<FileItem, String>  colSize;
    @FXML private TableColumn<FileItem, String>  colStatus;
    @FXML private TableColumn<FileItem, String>  colDest;

    private Path currentDirectory;

    /** 目前的操作狀態，控制按鈕 enable/disable */
    private enum AppState {
        NO_FOLDER,      // 尚未選資料夾
        FOLDER_SELECTED,// 已選資料夾，未掃描
        SCANNED,        // 掃描完成，可以整理
        ORGANIZED,      // 整理完成，可以復原
        BUSY            // 處理中，所有按鈕 disable
    }

    private AppState appState = AppState.NO_FOLDER;

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

        // ── busy 連動 loading 指示器 ────────────────────────────────────────────
        facade.busyProperty().addListener((obs, wasBusy, isNowBusy) -> {
            if (scanningIndicator != null) {
                scanningIndicator.setVisible(isNowBusy);
                scanningIndicator.setManaged(isNowBusy);
            }
            if (lblDropHint != null && lblScanningHint != null) {
                if (isNowBusy) {
                    lblDropHint.setVisible(false);
                    lblDropHint.setManaged(false);
                    String msg = facade.statusMessageProperty().get();
                    lblScanningHint.setText(msg != null ? msg : "處理中，請稍候...");
                } else {
                    lblDropHint.setVisible(true);
                    lblDropHint.setManaged(true);
                }
            }
            if (isNowBusy) setState(AppState.BUSY);
        });

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

        // 初始狀態
        setState(AppState.NO_FOLDER);
    }

    // ── 拖曳事件 ──────────────────────────────────────────────────────────────

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
                selectDirectory(file.toPath());
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

    // ── 按鈕事件 ──────────────────────────────────────────────────────────────

    @FXML
    void handleScan(ActionEvent event) {
        if (currentDirectory == null) {
            if (lblStatus != null)
                lblStatus.appendText("[錯誤] 請先選擇或拖曳一個資料夾。\n");
            return;
        }
        triggerScan(currentDirectory);
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

        AppContext.get().getFacade().organizeAsync(dryRun, actionOpt.get(), result -> {
            // 預覽模式完成後回到 SCANNED，讓使用者可以繼續按整理
            // 真正整理完成後進入 ORGANIZED，需要重新掃描才能再整理
            if (dryRun) {
                setState(AppState.SCANNED);
            } else {
                setState(AppState.ORGANIZED);
            }
        });
    }

    @FXML
    void handleUndo(ActionEvent event) {
        AppContext.get().getFacade().undoAsync(() -> {
            // 復原完成後回到 FOLDER_SELECTED，強制重新掃描
            setState(AppState.FOLDER_SELECTED);
            if (lblStatus != null)
                lblStatus.appendText("[系統] 請重新掃描以確認復原結果。\n");
        });
    }

    @FXML
    void onOpenDiskDashboard(ActionEvent event) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/fxml/DiskDashboard.fxml"));
            javafx.scene.Parent root = loader.load();

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("磁碟空間分析");
            stage.setScene(new javafx.scene.Scene(root, 900, 620));
            stage.initOwner(dropPane.getScene().getWindow());
            stage.show();

        } catch (java.io.IOException e) {
            if (lblStatus != null)
                lblStatus.appendText("[錯誤] 無法開啟磁碟分析視窗：" + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private void openDirectoryChooser() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("選擇要整理的資料夾");
        File selectedDir = chooser.showDialog(dropPane.getScene().getWindow());
        if (selectedDir != null) selectDirectory(selectedDir.toPath());
    }

    private void selectDirectory(Path directory) {
        currentDirectory = directory;

        if (lblDropHint != null) {
            lblDropHint.setText("📂 已選擇：" + directory.toAbsolutePath()
                + "　（調整好深度後按「掃描」）");
            lblDropHint.setStyle("-fx-font-size: 14px; -fx-text-fill: #2563eb;");
        }

        if (lblStatus != null)
            lblStatus.appendText("[系統] 已選擇資料夾：" + directory.toAbsolutePath()
                + "，請調整掃描深度後按「掃描」。\n");

        setState(AppState.FOLDER_SELECTED);
    }

    private void triggerScan(Path directory) {
        OrganizerFacade facade = AppContext.get().getFacade();
        AppConfig config = AppContext.get().getConfig();

        if (spinnerDepth != null) config.setScanDepth(spinnerDepth.getValue());

        boolean wasWatching = facade.isWatching();
        if (wasWatching) facade.stopWatch();

        facade.scanAsync(directory, count -> {
            setState(AppState.SCANNED);
            if (wasWatching || (toggleWatch != null && toggleWatch.isSelected())) {
                facade.startWatch(directory);
                if (toggleWatch != null && !toggleWatch.isSelected())
                    toggleWatch.setSelected(true);
            }
        });
    }

    /**
     * 依狀態控制按鈕 enable/disable。
     */
    private void setState(AppState state) {
        appState = state;

        boolean isBusy = (state == AppState.BUSY);

        if (btnScan != null)
            btnScan.setDisable(isBusy || currentDirectory == null);

        if (btnOrganize != null)
            btnOrganize.setDisable(isBusy || state != AppState.SCANNED);

        if (btnUndo != null)
            btnUndo.setDisable(isBusy || state != AppState.ORGANIZED);
    }
}
