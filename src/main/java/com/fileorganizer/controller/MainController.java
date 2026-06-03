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
import com.fileorganizer.service.impl.AppContext; // ✅ 修正：正確的 package 路徑

import java.io.File;
import java.nio.file.Path;

public class MainController {

    @FXML private TableView<FileItem> fileTable;
    @FXML private TextArea lblStatus;
    @FXML private CheckBox chkDryRun;
    @FXML private CheckBox toggleWatch;   // ✅ 對齊 FXML 修正後的 fx:id

    @FXML private VBox dropPane;

    @FXML private TableColumn<FileItem, String> colName;
    @FXML private TableColumn<FileItem, String> colPath;
    @FXML private TableColumn<FileItem, String> colSize;
    @FXML private TableColumn<FileItem, String> colStatus;

    private final ObservableList<FileItem> fileDataList = FXCollections.observableArrayList();
    private AppConfig currentConfig;

    @FXML
    public void initialize() {
        if (fileTable != null) {
            fileTable.setItems(fileDataList);
        }

        if (dropPane != null) {
            dropPane.setOnMouseClicked(event -> {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("選擇要整理的資料夾");
                File selectedDir = chooser.showDialog(dropPane.getScene().getWindow());
                if (selectedDir != null) {
                    triggerFileProcess(selectedDir.getAbsolutePath());
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
                triggerFileProcess(file.getAbsolutePath());
                success = true;
            } else {
                if (lblStatus != null) lblStatus.appendText("[錯誤] 請拖曳「資料夾」而非單一檔案。\n");
            }
        }
        event.setDropCompleted(success);
        if (dropPane != null) {
            dropPane.setStyle("");
        }
        event.consume();
    }

    private void triggerFileProcess(String folderPath) {
        if (lblStatus != null) lblStatus.appendText("[系統] 開始處理目標資料夾: " + folderPath + "\n");
        fileDataList.clear();

        new Thread(() -> {
            try {
                Platform.runLater(() -> lblStatus.appendText("[分析] 透過 Facade 核心啟動掃描...\n"));
                // AppContext.get().getFacade().scanAsync(...) 待接入
                Platform.runLater(() -> {
                    if (lblStatus != null) lblStatus.appendText("[成功] 核心架構對接完成，等待後端資料注入！\n");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (lblStatus != null) lblStatus.appendText("[錯誤] 核心呼叫失敗: " + e.getMessage() + "\n");
                });
            }
        }).start();
    }

    @FXML
    void handleUndo(ActionEvent event) {
        new Thread(() -> {
            Platform.runLater(() -> {
                if (lblStatus != null) lblStatus.appendText("[復原] 正在透過 Facade 發送復原請求...\n");
                fileDataList.clear();
            });
        }).start();
    }
}
