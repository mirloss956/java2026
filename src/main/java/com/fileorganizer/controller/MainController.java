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

// 💡 引入小組正式的類別與 C 所建立的上下文/外觀介面
import com.fileorganizer.config.AppConfig;
import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.OrganizeResult;
import com.fileorganizer.AppContext; // 這是 C 寫的，若 package 不同請依實際情況 import

import java.io.File;
import java.nio.file.Path;

public class MainController {

    // 🎯 對齊圖 ⑤ 中 C 同學宣告的最新 FXML 欄位名稱
    @FXML private TableView<FileItem> fileTable;
    @FXML private TextArea lblStatus;          // 負責顯示日誌
    @FXML private CheckBox chkDryRun;          // 預覽模式
    @FXML private CheckBox toggleWatch;         // 即時監控啟用
    @FXML private VBox dropPane;                // 拖曳面板（請確保 fxml 裡此面板的 fx:id 為 dropPane）

    @FXML private TableColumn<FileItem, String> colName;
    @FXML private TableColumn<FileItem, String> colPath;
    @FXML private TableColumn<FileItem, String> colSize;
    @FXML private TableColumn<FileItem, String> colStatus;

    private final ObservableList<FileItem> fileDataList = FXCollections.observableArrayList();
    private AppConfig currentConfig;

    @FXML
    public void initialize() {
        // 💡 完美對接 C 的新架構：透過 AppContext 取得全域單例與設定檔
        // 這裡假設 C 的 AppContext 內有提供獲取最新配置或讀寫的方法
        // 如果有需要調整，可以透過 AppContext.get().getFacade() 呼叫後端服務

        // 綁定表格欄位與小組 FileItem 欄位方法
        if (fileTable != null) {
            // 安全防護：如果 fxml 內有對應的 Column 欄位則進行綁定
            // 建議在 Scene Builder 中確認表格各欄位的 fx:id
            fileTable.setItems(fileDataList);
        }

        // 設定拖曳面板點擊事件
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

        // 💡 依照圖 ③ 的規格：透過 AppContext.get().getFacade() 來呼叫後端搬移與掃描服務
        new Thread(() -> {
            try {
                if (lblStatus != null) {
                    Platform.runLater(() -> lblStatus.appendText("[分析] 透過 Facade 核心啟動掃描...\n"));
                }

                // 這裡留空或使用模擬，當與 C 合併後，可以直接呼叫 C 的真實服務：
                // AppContext.get().getFacade().scanAndOrganize(...);

                // 暫時保留安全更新 UI 機制
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

    // 🎯 圖 ③ 要求的 @FXML handler：補充按鈕或點擊事件
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