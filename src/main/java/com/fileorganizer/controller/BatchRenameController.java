package com.fileorganizer.controller;

import com.fileorganizer.model.FileItem;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 批次重新命名對話框 Controller。
 * 負責人：A
 *
 * 支援四種模式：
 *  1. 純文字取代
 *  2. 正規表達式取代
 *  3. 序號附加（前綴 / 後綴）
 *  4. 大小寫轉換
 *
 * 流程：
 *  MainController 傳入選取的 FileItem 清單 → 使用者設定規則 → 即時預覽 → 確認執行
 */
public class BatchRenameController {

    // ── FXML 元件 ──────────────────────────────────────────────────────────────

    @FXML private TabPane               tabPane;

    // Tab 1：文字取代
    @FXML private TextField             txtFind;
    @FXML private TextField             txtReplace;
    @FXML private CheckBox              chkRegex;
    @FXML private CheckBox              chkCaseSensitive;

    // Tab 2：序號
    @FXML private TextField             txtPrefix;
    @FXML private TextField             txtSuffix;
    @FXML private Spinner<Integer>      spinStart;
    @FXML private Spinner<Integer>      spinStep;
    @FXML private Spinner<Integer>      spinPadding;
    @FXML private CheckBox              chkKeepOriginal;

    // Tab 3：大小寫
    @FXML private ToggleGroup           caseGroup;
    @FXML private RadioButton          radUppercase;
    @FXML private RadioButton          radLowercase;
    @FXML private RadioButton          radTitleCase;
    @FXML private RadioButton          radKeepCase;

    // 預覽表格
    @FXML private TableView<RenameRow>           previewTable;
    @FXML private TableColumn<RenameRow, String> colOriginal;
    @FXML private TableColumn<RenameRow, String> colPreview;
    @FXML private TableColumn<RenameRow, String> colStatus;

    // 底部
    @FXML private Label                 lblSummary;
    @FXML private Button                btnApply;
    @FXML private Button                btnCancel;

    // ── 狀態 ───────────────────────────────────────────────────────────────────

    private List<FileItem>                  sourceItems;
    private final ObservableList<RenameRow> rows = FXCollections.observableArrayList();

    /** 一列預覽資料 */
    public static class RenameRow {
        final FileItem item;
        final SimpleStringProperty original = new SimpleStringProperty();
        final SimpleStringProperty preview  = new SimpleStringProperty();
        final SimpleStringProperty status   = new SimpleStringProperty();

        RenameRow(FileItem item) {
            this.item = item;
            this.original.set(item.getFileName());
            this.preview.set(item.getFileName());
            this.status.set("待確認");
        }
    }

    // ── 初始化 ─────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // 表格欄位綁定
        colOriginal.setCellValueFactory(c -> c.getValue().original);
        colPreview .setCellValueFactory(c -> c.getValue().preview);
        colStatus  .setCellValueFactory(c -> c.getValue().status);

        // 預覽欄顏色：衝突顯示紅色
        colPreview.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(value);
                RenameRow row = getTableView().getItems().get(getIndex());
                if ("衝突".equals(row.status.get())) {
                    setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                } else if (value.equals(row.original.get())) {
                    setStyle("-fx-text-fill: #9ca3af;");
                } else {
                    setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                }
            }
        });

        previewTable.setItems(rows);

        // Spinner 初始化
        spinStart  .setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 99999, 1));
        spinStep   .setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100,   1));
        spinPadding.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 8,     3));

        // 任何輸入變動都即時更新預覽
        txtFind.textProperty()         .addListener((o, ov, nv) -> refreshPreview());
        txtReplace.textProperty()      .addListener((o, ov, nv) -> refreshPreview());
        chkRegex.selectedProperty()    .addListener((o, ov, nv) -> refreshPreview());
        chkCaseSensitive.selectedProperty().addListener((o, ov, nv) -> refreshPreview());

        txtPrefix.textProperty()       .addListener((o, ov, nv) -> refreshPreview());
        txtSuffix.textProperty()       .addListener((o, ov, nv) -> refreshPreview());
        spinStart.valueProperty()      .addListener((o, ov, nv) -> refreshPreview());
        spinStep.valueProperty()       .addListener((o, ov, nv) -> refreshPreview());
        spinPadding.valueProperty()    .addListener((o, ov, nv) -> refreshPreview());
        chkKeepOriginal.selectedProperty().addListener((o, ov, nv) -> refreshPreview());

        caseGroup.selectedToggleProperty().addListener((o, ov, nv) -> refreshPreview());

        tabPane.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> refreshPreview());
    }

    /**
     * 由 MainController 呼叫，傳入要重新命名的檔案清單。
     */
    public void setItems(List<FileItem> items) {
        this.sourceItems = new ArrayList<>(items);
        rows.clear();
        for (FileItem item : items) {
            rows.add(new RenameRow(item));
        }
        refreshPreview();
    }

    // ── 預覽邏輯 ───────────────────────────────────────────────────────────────

    private void refreshPreview() {
        if (rows.isEmpty()) return;

        int activeTab = tabPane.getSelectionModel().getSelectedIndex();

        switch (activeTab) {
            case 0 -> applyReplacePreview();
            case 1 -> applySequencePreview();
            case 2 -> applyCasePreview();
        }

        detectConflicts();
        updateSummary();
    }

    /** Tab 0：文字 / 正規表達式取代 */
    private void applyReplacePreview() {
        String find    = txtFind.getText();
        String replace = txtReplace.getText();
        boolean isRegex = chkRegex.isSelected();
        boolean caseSensitive = chkCaseSensitive.isSelected();

        for (RenameRow row : rows) {
            String original = row.original.get();
            if (find == null || find.isEmpty()) {
                row.preview.set(original);
                row.status.set("未變更");
                continue;
            }
            try {
                String newName;
                if (isRegex) {
                    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                    newName = Pattern.compile(find, flags)
                                     .matcher(original)
                                     .replaceAll(replace == null ? "" : replace);
                } else {
                    if (caseSensitive) {
                        newName = original.replace(find, replace == null ? "" : replace);
                    } else {
                        newName = original.replaceAll("(?i)" + Pattern.quote(find),
                                                      replace == null ? "" : replace);
                    }
                }
                row.preview.set(newName.isEmpty() ? original : newName);
                row.status.set(newName.equals(original) ? "未變更" : "待確認");
            } catch (PatternSyntaxException e) {
                row.preview.set(original);
                row.status.set("⚠ 正規式錯誤");
            }
        }
    }

    /** Tab 1：序號附加 */
    private void applySequencePreview() {
        String prefix      = txtPrefix.getText() == null ? "" : txtPrefix.getText();
        String suffix      = txtSuffix.getText() == null ? "" : txtSuffix.getText();
        int    start       = spinStart.getValue();
        int    step        = spinStep.getValue();
        int    padding     = spinPadding.getValue();
        boolean keepOrig   = chkKeepOriginal.isSelected();

        int counter = start;
        for (RenameRow row : rows) {
            String original  = row.original.get();
            String ext       = "";
            String baseName  = original;

            int dot = original.lastIndexOf('.');
            if (dot > 0) {
                ext      = original.substring(dot);        // 含點，如 ".jpg"
                baseName = original.substring(0, dot);
            }

            String num    = String.format("%0" + padding + "d", counter);
            String middle = keepOrig ? baseName : "";
            String newName = prefix + middle + num + suffix + ext;

            row.preview.set(newName);
            row.status.set(newName.equals(original) ? "未變更" : "待確認");
            counter += step;
        }
    }

    /** Tab 2：大小寫轉換 */
    private void applyCasePreview() {
        for (RenameRow row : rows) {
            String original = row.original.get();
            int    dot      = original.lastIndexOf('.');
            String base     = dot > 0 ? original.substring(0, dot) : original;
            String ext      = dot > 0 ? original.substring(dot)     : "";

            String newBase;
            if (radUppercase.isSelected()) {
                newBase = base.toUpperCase();
            } else if (radLowercase.isSelected()) {
                newBase = base.toLowerCase();
            } else if (radTitleCase.isSelected()) {
                newBase = toTitleCase(base);
            } else {
                newBase = base;
            }

            String newName = newBase + ext;
            row.preview.set(newName);
            row.status.set(newName.equals(original) ? "未變更" : "待確認");
        }
    }

    /** 偵測檔名衝突（同目錄下有重複的預覽名稱） */
    private void detectConflicts() {
        // 先重設所有非錯誤的狀態
        for (RenameRow row : rows) {
            if ("⚠ 正規式錯誤".equals(row.status.get())) continue;
            row.status.set(row.preview.get().equals(row.original.get()) ? "未變更" : "待確認");
        }

        // 找出重複的預覽名稱（在同一個資料夾中）
        java.util.Map<String, List<RenameRow>> grouped = new java.util.HashMap<>();
        for (RenameRow row : rows) {
            Path parent = row.item.getSourcePath().getParent();
            String key  = (parent != null ? parent.toString() : "") + "/" + row.preview.get();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        for (var entry : grouped.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (RenameRow r : entry.getValue()) {
                    r.status.set("衝突");
                }
            }
        }

        // 偵測與磁碟上現有檔案衝突（排除自己）
        for (RenameRow row : rows) {
            if ("衝突".equals(row.status.get())) continue;
            if (row.preview.get().equals(row.original.get())) continue;
            Path parent = row.item.getSourcePath().getParent();
            if (parent != null) {
                Path target = parent.resolve(row.preview.get());
                if (Files.exists(target)) {
                    row.status.set("衝突");
                }
            }
        }
    }

    private void updateSummary() {
        long willRename  = rows.stream().filter(r -> "待確認".equals(r.status.get())).count();
        long conflicts   = rows.stream().filter(r -> "衝突"  .equals(r.status.get())).count();
        long unchanged   = rows.stream().filter(r -> "未變更".equals(r.status.get())).count();

        lblSummary.setText(String.format(
            "共 %d 個檔案｜%d 個將重新命名｜%d 個衝突（無法執行）｜%d 個未變更",
            rows.size(), willRename, conflicts, unchanged));

        // 有衝突時停用「確認執行」
        btnApply.setDisable(conflicts > 0 || willRename == 0);
    }

    // ── 執行 ───────────────────────────────────────────────────────────────────

    @FXML
    private void handleApply() {
        List<String> errors = new ArrayList<>();
        int renamed = 0;

        for (RenameRow row : rows) {
            if (!"待確認".equals(row.status.get())) continue;

            Path source = row.item.getSourcePath();
            Path target = source.getParent().resolve(row.preview.get());

            try {
                Files.move(source, target);
                row.status.set("✓ 完成");
                renamed++;
            } catch (IOException e) {
                row.status.set("✗ 失敗");
                errors.add(row.original.get() + " → " + e.getMessage());
            }
        }

        previewTable.refresh();
        btnApply.setDisable(true);

        if (errors.isEmpty()) {
            lblSummary.setText("✅ 完成！共重新命名 " + renamed + " 個檔案。");
        } else {
            lblSummary.setText("⚠ 完成 " + renamed + " 個，" + errors.size() + " 個失敗。");
        }
    }

    @FXML
    private void handleCancel() {
        ((Stage) btnCancel.getScene().getWindow()).close();
    }

    // ── 工具 ───────────────────────────────────────────────────────────────────

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] words = s.split("(?<=\\s)|(?=\\s)");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isBlank()) {
                sb.append(w);
            } else {
                sb.append(Character.toUpperCase(w.charAt(0)));
                sb.append(w.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
