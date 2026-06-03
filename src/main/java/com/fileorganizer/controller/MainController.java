package com.fileorganizer.controller;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.FileStatus;
import com.fileorganizer.service.impl.AppContext;
import com.fileorganizer.service.impl.OrganizerFacade;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;

/**
 * 負責人：A（@FXML 互動邏輯）
 *
 * 注意事項：
 *  - 透過 AppContext.get().getFacade() 取得 facade（package: com.fileorganizer.service.impl）
 *  - 所有耗時操作均由 OrganizerFacade 內部的虛擬執行緒處理，A 只需呼叫方法
 *  - UI 狀態（busy、statusMessage、fileItems）直接 bind，不需手動刷新
 */
public class MainController {

    // --- FXML 對應元件（需與 main.fxml 的 fx:id 一致）---
    @FXML private TableView<FileItem>              fileTable;
    @FXML private TableColumn<FileItem, String>    colName;
    @FXML private TableColumn<FileItem, String>    colPath;
    @FXML private TableColumn<FileItem, String>    colSize;
    @FXML private TableColumn<FileItem, String>    colStatus;
    @FXML private TextArea                         lblStatus;      // 日誌輸出區
    @FXML private ProgressIndicator                spinner;        // 忙碌轉圈（bind 到 busyProperty）
    @FXML private CheckBox                         chkDryRun;      // 預覽模式
    @FXML private CheckBox                         chkDetectDup;   // 偵測重複檔案
    @FXML private VBox                             dropPane;       // 拖曳面板

    private OrganizerFacade facade;
    private Path currentDirectory;

    @FXML
    public void initialize() {
        facade = AppContext.get().getFacade();

        // --- 狀態綁定 ---
        // lblStatus 是 TextArea，監聽 statusMessage 並 append（非直接 bind，避免覆蓋歷史訊息）
        facade.statusMessageProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                lblStatus.appendText(newVal + "\n");
            }
        });

        if (spinner != null) {
            spinner.visibleProperty().bind(facade.busyProperty());
        }

        // --- TableView 欄位設定 ---
        colName.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        colPath.setCellValueFactory(new PropertyValueFactory<>("sourcePath"));
        colSize.setCellValueFactory(new PropertyValueFactory<>("sizeBytes"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // 狀態欄位顏色（A 可自行擴充 cell factory）
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    setStyle(switch (status) {
                        case "MOVED"     -> "-fx-text-fill: #27ae60;";
                        case "FAILED"    -> "-fx-text-fill: #e74c3c;";
                        case "DUPLICATE" -> "-fx-text-fill: #e67e22;";
                        case "SKIPPED"   -> "-fx-text-fill: #7f8c8d;";
                        default          -> "";
                    });
                }
            }
        });

        fileTable.setItems(facade.getFileItems());

        // --- 拖曳資料夾 ---
        if (dropPane != null) {
            dropPane.setOnDragOver(this::handleDragOver);
            dropPane.setOnDragDropped(this::handleDragDropped);
            dropPane.setOnMouseClicked(e -> handleChooseFolder());
        }
    }

    // --- 拖曳事件 ---

    @FXML
    void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
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
            File dropped = event.getDragboard().getFiles().get(0);
            if (dropped.isDirectory()) {
                currentDirectory = dropped.toPath();
                facade.scanAsync(currentDirectory, count ->
                        lblStatus.appendText("[掃描完成] 共 " + count + " 個檔案\n"));
                success = true;
            } else {
                lblStatus.appendText("[錯誤] 請拖曳「資料夾」而非單一檔案。\n");
            }
        }
        event.setDropCompleted(success);
        if (dropPane != null) dropPane.setStyle("");
        event.consume();
    }

    // --- 按鈕事件（在 main.fxml 用 onAction="#handleXxx" 對應）---

    @FXML
    private void handleChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("選擇要整理的資料夾");
        File dir = chooser.showDialog(fileTable.getScene().getWindow());
        if (dir != null) {
            currentDirectory = dir.toPath();
            facade.scanAsync(currentDirectory, count ->
                    lblStatus.appendText("[掃描完成] 共 " + count + " 個檔案\n"));
        }
    }

    @FXML
    private void handleOrganize() {
        boolean dryRun = chkDryRun != null && chkDryRun.isSelected();
        facade.organizeAsync(dryRun, result ->
                lblStatus.appendText("[整理完成] " + result + "\n"));
    }

    @FXML
    private void handleUndo() {
        facade.undoAsync(() -> lblStatus.appendText("[復原完成]\n"));
    }
}
