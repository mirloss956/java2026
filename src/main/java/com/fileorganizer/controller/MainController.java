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
import com.fileorganizer.config.ConfigLoader;
import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.OrganizeResult;
import com.fileorganizer.service.FileOrganizerService;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    @FXML private CheckBox chkExtension;
    @FXML private CheckBox chkDuplicate;
    @FXML private Button btnUndo;
    @FXML private VBox dropPane;
    @FXML private Label lblDropHint;
    @FXML private TextArea txtLog;
    @FXML private TableView<FileItem> tblFiles;
    @FXML private TableColumn<FileItem, String> colName;
    @FXML private TableColumn<FileItem, String> colPath;
    @FXML private TableColumn<FileItem, String> colSize;
    @FXML private TableColumn<FileItem, String> colStatus;

    private final ObservableList<FileItem> fileDataList = FXCollections.observableArrayList();
    private FileOrganizerService fileService; 
    private final ConfigLoader configLoader = new ConfigLoader();
    private AppConfig currentConfig;

    @FXML
    public void initialize() {
        currentConfig = configLoader.load();
        
        chkDuplicate.setSelected(currentConfig.isDetectDuplicates());
        if ("extension".equals(currentConfig.getActiveRuleMode())) {
            chkExtension.setSelected(true);
        }

        colName.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getFileName()));
        colPath.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getSourcePath().toString()));
        colSize.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getSizeBytes() + " Bytes"));
        colStatus.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getStatus().toString()));
        
        tblFiles.setItems(fileDataList);

        dropPane.setOnMouseClicked(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("選擇要整理的資料夾");
            File selectedDir = chooser.showDialog(dropPane.getScene().getWindow());
            if (selectedDir != null) {
                triggerFileProcess(selectedDir.getAbsolutePath());
            }
        });

        setupMockService();
    }

    @FXML
    void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.ANY);
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
                triggerFileProcess(file.getAbsolutePath());
                success = true;
            } else {
                txtLog.appendText("[錯誤] 請拖曳「資料夾」而非單一檔案。\n");
            }
        }
        event.setDropCompleted(success);
        dropPane.setStyle(""); 
        event.consume();
    }

    private void triggerFileProcess(String folderPath) {
        txtLog.appendText("[系統] 開始處理目標資料夾: " + folderPath + "\n");
        fileDataList.clear();

        currentConfig.setSourceDirectory(Path.of(folderPath));
        currentConfig.setDetectDuplicates(chkDuplicate.isSelected());
        currentConfig.setActiveRuleMode(chkExtension.isSelected() ? "extension" : "custom");
        
        configLoader.save(currentConfig);

        new Thread(() -> {
            fileService.scanAndOrganize(currentConfig,
                log -> Platform.runLater(() -> txtLog.appendText(log + "\n")),
                result -> Platform.runLater(() -> {
                    fileDataList.addAll(result.getItems());
                    txtLog.appendText(String.format("[摘要] 成功搬移: %d, 重複跳過: %d, 失敗: %d\n", 
                        result.getMovedCount(), result.getDuplicateCount(), result.getFailedCount()));
                })
            );
        }).start();
    }

    @FXML
    void handleUndo(ActionEvent event) {
        new Thread(() -> {
            fileService.undoLastAction(log -> Platform.runLater(() -> {
                txtLog.appendText(log + "\n");
                fileDataList.clear();
            }));
        }).start();
    }

    private void setupMockService() {
        this.fileService = new FileOrganizerService() {
            @Override
            public void scanAndOrganize(AppConfig config, java.util.function.Consumer<String> logConsumer, java.util.function.Consumer<OrganizeResult> resultConsumer) {
                try {
                    String pathStr = config.getSourceDirectory().toString();
                    logConsumer.accept("[分析] 正在掃描目錄：" + pathStr);
                    Thread.sleep(500);
                    logConsumer.accept("[分析] 套用規則模式：" + config.getActiveRuleMode());
                    Thread.sleep(400);

                    List<FileItem> mockItems = new ArrayList<>();
                    
                    FileItem item1 = new FileItem(Path.of(pathStr, "final_report.docx"), 45120, LocalDateTime.now());
                    item1.setStatus(com.fileorganizer.model.FileStatus.MOVED);
                    mockItems.add(item1);

                    FileItem item2 = new FileItem(Path.of(pathStr, "banner.png"), 1580000, LocalDateTime.now());
                    item2.setStatus(com.fileorganizer.model.FileStatus.MOVED);
                    mockItems.add(item2);

                    if (config.isDetectDuplicates()) {
                        FileItem item3 = new FileItem(Path.of(pathStr, "backup_copy.zip"), 9500000, LocalDateTime.now());
                        item3.setStatus(com.fileorganizer.model.FileStatus.DUPLICATE);
                        mockItems.add(item3);
                        logConsumer.accept("[提示] 偵測到重複壓縮檔，依規則標記為 DUPLICATE。");
                    }

                    logConsumer.accept("[成功] 處理完畢，已產生對接報告。");
                    
                    OrganizeResult result = new OrganizeResult(mockItems, LocalDateTime.now());
                    resultConsumer.accept(result);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void undoLastAction(java.util.function.Consumer<String> logConsumer) {
                logConsumer.accept("[復原] 模擬復原成功：已將變更檔案全數歸位。");
            }
        };
    }
}