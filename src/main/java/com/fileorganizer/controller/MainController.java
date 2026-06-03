package com.fileorganizer.controller;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.FileStatus;
import com.fileorganizer.service.impl.AppContext;
import com.fileorganizer.service.impl.OrganizerFacade;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ResourceBundle;

/**
 * 負責人：A（實作 @FXML 對應的互動邏輯）
 *         C（提供 facade 綁定範例，A 照著接就好）
 *
 * 這個檔案是 C 給 A 的「接線說明書」：
 *  - 所有耗時操作都已在 OrganizerFacade 內部處理，A 只需要呼叫方法
 *  - UI 狀態（busy、statusMessage、fileItems）直接 bind，不用手動刷新
 */
public class MainController implements Initializable {

    // --- A 在 main.fxml 對應的元件 ---
    @FXML private TableView<FileItem>           fileTable;
    @FXML private TableColumn<FileItem, String> colName;
    @FXML private TableColumn<FileItem, String> colExt;
    @FXML private TableColumn<FileItem, String> colDest;
    @FXML private TableColumn<FileItem, FileStatus> colStatus;
    @FXML private Label                         lblStatus;
    @FXML private ProgressIndicator             spinner;
    @FXML private ToggleButton                  toggleWatch;
    @FXML private CheckBox                      chkDryRun;

    private OrganizerFacade facade;
    private Path currentDirectory;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        facade = AppContext.get().getFacade();

        // --- C 提供的綁定範例（A 照著接，不需要改 facade）---
        lblStatus.textProperty().bind(facade.statusMessageProperty());
        spinner.visibleProperty().bind(facade.busyProperty());

        // --- TableView 欄位設定（A 負責調整顯示格式）---
        colName.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        colExt.setCellValueFactory(new PropertyValueFactory<>("extension"));
        colDest.setCellValueFactory(new PropertyValueFactory<>("destinationPath"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        fileTable.setItems(facade.getFileItems());

        // --- 拖曳資料夾進來（A 負責 UI 回饋動畫）---
        fileTable.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        fileTable.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles()) {
                File dropped = db.getFiles().get(0);
                if (dropped.isDirectory()) {
                    currentDirectory = dropped.toPath();
                    facade.scanAsync(currentDirectory, count -> {
                        // A 可在這裡更新其他 UI 元件，例如顯示檔案數 badge
                    });
                }
            }
            e.consume();
        });
    }

    // --- 按鈕事件（A 在 FXML 用 onAction="#handleXxx" 對應）---

    @FXML
    private void handleChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("選擇要整理的資料夾");
        File dir = chooser.showDialog(fileTable.getScene().getWindow());
        if (dir != null) {
            currentDirectory = dir.toPath();
            facade.scanAsync(currentDirectory, null);
        }
    }

    @FXML
    private void handleOrganize() {
        boolean dryRun = chkDryRun.isSelected();
        facade.organizeAsync(dryRun, result -> {
            // A 可在這裡彈出結果 Dialog
            // 例如：showResultDialog(result);
        });
    }

    @FXML
    private void handleUndo() {
        facade.undoAsync(null);
    }

    @FXML
    private void handleToggleWatch() {
        if (toggleWatch.isSelected()) {
            if (currentDirectory == null) {
                toggleWatch.setSelected(false);
                return;
            }
            facade.startWatch(currentDirectory);
        } else {
            facade.stopWatch();
        }
    }
}
