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
import javafx.application.Platform;
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

    // ── A 自己實作的進階功能組件（已清理重複宣告，並加入對比按鈕） ─────────────────
    @FXML private TextField txtSearch;         // 搜尋輸入框
    @FXML private Button btnSearch;           // 搜尋按鈕
    @FXML private Button btnCompareImages;    // 🎯 新增的對比圖片按鈕

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

        // ── 綁定全文搜尋輸入框的 Enter 事件 ──────────────────────────────────────
        if (txtSearch != null) {
            txtSearch.setOnAction(event -> handleSearch(null));
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
                getLblStatusAppend("[錯誤] 請先選擇或拖曳一個資料夾。\n");
            return;
        }
        triggerScan(currentDirectory);
    }

    @FXML
    void handleOrganize(ActionEvent event) {
        if (currentDirectory == null) {
            if (lblStatus != null)
                getLblStatusAppend("[錯誤] 請先選擇或拖曳一個資料夾再整理。\n");
            return;
        }

        boolean dryRun = chkDryRun != null && chkDryRun.isSelected();

        Optional<DuplicateAction> actionOpt = DuplicateActionDialog.show();
        if (actionOpt.isEmpty()) {
            if (lblStatus != null)
                getLblStatusAppend("[資訊] 已取消整理。\n");
            return;
        }

        AppContext.get().getFacade().organizeAsync(dryRun, actionOpt.get(), result -> {
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
            setState(AppState.FOLDER_SELECTED);
            if (lblStatus != null)
                getLblStatusAppend("[系統] 請重新掃描以確認復原結果。\n");
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
                getLblStatusAppend("[錯誤] 無法開啟磁碟分析視窗：" + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    // ── 🎯 功能拆分一：純關鍵字內文搜尋 ──────────────────────────────────────────
    @FXML
    void handleSearch(ActionEvent event) {
        if (txtSearch == null) return;

        String keyword = txtSearch.getText().trim();
        if (keyword.isEmpty()) {
            if (lblStatus != null) getLblStatusAppend("[提示] 請輸入關鍵字再進行全文檢索。\n");
            return;
        }

        if (currentDirectory == null) {
            if (lblStatus != null) getLblStatusAppend("[警告] 請先選擇或拖曳一個目標資料夾！\n");
            return;
        }

        File folder = currentDirectory.toFile();
        if (!folder.exists() || !folder.isDirectory()) return;

        if (lblStatus != null) {
            getLblStatusAppend(String.format("\n🔍 [全文檢索] 正在搜尋「%s」內文關鍵字: \"%s\"...\n", folder.getName(), keyword));
        }

        // 清空當前表格，只塞入文字搜尋結果
        fileTable.getItems().clear();

        new Thread(() -> {
            File[] files = folder.listFiles();
            if (files == null) return;

            java.util.List<FileItem> matchResults = new java.util.ArrayList<>();

            for (File file : files) {
                if (file.isDirectory()) continue;
                String filename = file.getName().toLowerCase();

                // 📄 僅處理文字相關檔案 (.txt, .md, .docx, .pdf)
                if (filename.endsWith(".txt") || filename.endsWith(".md") || filename.endsWith(".docx") || filename.endsWith(".pdf")) {
                    String content = com.fileorganizer.util.FileTextExtractor.extractText(file);

                    if (content != null && content.contains(keyword)) {
                        int index = content.indexOf(keyword);
                        int start = Math.max(0, index - 15);
                        int end = Math.min(content.length(), index + keyword.length() + 15);
                        String snippet = content.substring(start, end).replace("\n", " ").trim();

                        Platform.runLater(() -> {
                            if (lblStatus != null) {
                                getLblStatusAppend(String.format("🎯 [內文匹配] 在《%s》內發現關鍵字！\n   👉 \"...%s...\"\n", file.getName(), snippet));
                            }
                        });

                        FileItem matchItem = new FileItem(file.toPath(), file.length(), java.time.LocalDateTime.now());
                        matchItem.setStatus(com.fileorganizer.model.FileStatus.PENDING);
                        matchResults.add(matchItem);
                    }
                }
            }

            Platform.runLater(() -> {
                fileTable.getItems().addAll(matchResults);
                if (lblStatus != null) getLblStatusAppend("[完成] 關鍵字內文檢索結束。\n");
            });

        }).start();
    }

    // ── 🎯 功能拆分二：獨立的圖片感知雜湊（pHash）相似度對比 ─────────────────────────
    @FXML
    void handleCompareImages(ActionEvent event) {
        if (currentDirectory == null) {
            if (lblStatus != null) getLblStatusAppend("[警告] 請先選擇或拖曳一個目標資料夾，才能對比圖片！\n");
            return;
        }

        File folder = currentDirectory.toFile();
        if (!folder.exists() || !folder.isDirectory()) return;

        if (lblStatus != null) {
            getLblStatusAppend(String.format("\n📸 [pHash 圖像分析] 正在計算「%s」目錄內所有圖片的視覺特徵...\n", folder.getName()));
        }

        // 清空表格，專注呈現相似圖片的結果
        fileTable.getItems().clear();

        new Thread(() -> {
            File[] files = folder.listFiles();
            if (files == null) return;

            java.util.List<FileItem> matchResults = new java.util.ArrayList<>();
            java.util.List<File> imageFiles = new java.util.ArrayList<>();

            // 僅收集圖片格式檔案
            for (File file : files) {
                if (file.isDirectory()) continue;
                String filename = file.getName().toLowerCase();
                if (filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png")) {
                    imageFiles.add(file);
                }
            }

            // 開始進行雙重比對
            if (imageFiles.size() > 1) {
                for (int i = 0; i < imageFiles.size(); i++) {
                    for (int j = i + 1; j < imageFiles.size(); j++) {
                        File imgA = imageFiles.get(i);
                        File imgB = imageFiles.get(j);

                        String hashA = com.fileorganizer.util.ImagePHash.getPHash(imgA);
                        String hashB = com.fileorganizer.util.ImagePHash.getPHash(imgB);

                        double similarity = com.fileorganizer.util.ImagePHash.calculateSimilarity(hashA, hashB);

                        // 相似度大於 85% 判定為高度相似圖片
                        if (similarity >= 0.85) {
                            Platform.runLater(() -> {
                                if (lblStatus != null) {
                                    getLblStatusAppend(String.format("⚠️ [圖片相似] 偵測到高度相似圖片！\n   🖼️ 圖片A: %s\n   🖼️ 圖片B: %s\n   📈 pHash 相似度: %.1f%%\n",
                                            imgA.getName(), imgB.getName(), similarity * 100));
                                }
                            });

                            FileItem dupImg = new FileItem(imgB.toPath(), imgB.length(), java.time.LocalDateTime.now());
                            dupImg.setStatus(com.fileorganizer.model.FileStatus.DUPLICATE);
                            if (!matchResults.contains(dupImg)) {
                                matchResults.add(dupImg);
                            }
                        }
                    }
                }
            } else {
                Platform.runLater(() -> {
                    if (lblStatus != null) getLblStatusAppend("[提示] 目錄內圖片數量小於 2 張，無法進行相似度對比。\n");
                });
            }

            // 刷新結果回 UI
            Platform.runLater(() -> {
                fileTable.getItems().addAll(matchResults);
                if (lblStatus != null) getLblStatusAppend("[完成] 相似圖片感知雜湊對比結束。\n");
            });

        }).start();
    }

    private void getLblStatusAppend(String x) {
        lblStatus.appendText(x);
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
                    + " （調整好深度後按「掃描」）");
            lblDropHint.setStyle("-fx-font-size: 14px; -fx-text-fill: #2563eb;");
        }

        if (lblStatus != null)
            getLblStatusAppend("[系統] 已選擇資料夾：" + directory.toAbsolutePath()
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