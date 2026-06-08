package com.fileorganizer.controller;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.RenamePreviewItem;
import com.fileorganizer.model.RenamePreviewItem.State;
import com.fileorganizer.model.RenameRule;
import com.fileorganizer.model.RenameRule.CaseType;
import com.fileorganizer.model.RenameRule.Mode;
import com.fileorganizer.service.BatchRenameService;
import com.fileorganizer.service.impl.BatchRenameServiceImpl;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.List;

/**
 * 負責人：A
 * 批次重新命名對話框 Controller。
 * 透過 setItems() 由 MainController 注入要改名的清單。
 */
public class BatchRenameController {

    // ── FXML 注入 ──────────────────────────────────────────────────────────
    @FXML private TabPane tabPane;

    // ── Regex Tab ──
    @FXML private TextField regexFindField;
    @FXML private TextField regexReplaceField;
    @FXML private CheckBox  regexIgnoreCase;

    // ── Insert Tab ──
    @FXML private TextField      insertTextField;
    @FXML private RadioButton    insertAtStart;
    @FXML private RadioButton    insertBeforeExt;
    @FXML private RadioButton    insertAtPos;
    @FXML private Spinner<Integer> insertPosSpinner;

    // ── Case Tab ──
    @FXML private RadioButton caseLower;
    @FXML private RadioButton caseUpper;
    @FXML private RadioButton caseTitle;

    // ── Serialize Tab ──
    @FXML private TextField        serializePrefixField;
    @FXML private TextField        serializeSuffixField;
    @FXML private Spinner<Integer> serializeStartSpinner;
    @FXML private Spinner<Integer> serializePaddingSpinner;

    // ── Preview Table ──
    @FXML private TableView<RenamePreviewItem>            previewTable;
    @FXML private TableColumn<RenamePreviewItem, String>  colOriginal;
    @FXML private TableColumn<RenamePreviewItem, String>  colNew;
    @FXML private TableColumn<RenamePreviewItem, String>  colState;

    // ── Bottom Bar ──
    @FXML private Label  lblPreviewCount;
    @FXML private Label  lblStatusMsg;
    @FXML private Button btnExecute;
    @FXML private Button btnUndo;

    // ── 狀態 ───────────────────────────────────────────────────────────────
    private final BatchRenameService renameService = new BatchRenameServiceImpl();
    private List<FileItem> sourceItems;
    private final ObservableList<RenamePreviewItem> previewItems = FXCollections.observableArrayList();

    // ── 模式映射（TabPane 索引 → RenameRule.Mode）─────────────────────────
    private static final Mode[] TAB_MODES = {
        Mode.REGEX, Mode.INSERT, Mode.CASE, Mode.SERIALIZE
    };

    // =========================================================
    // initialize
    // =========================================================

    @FXML
    public void initialize() {
        // TableView 資料繫結
        previewTable.setItems(previewItems);

        colOriginal.setCellValueFactory(cd -> cd.getValue().originalNameProperty());
        colNew.setCellValueFactory(cd -> cd.getValue().newNameProperty());
        colState.setCellValueFactory(cd -> cd.getValue().stateLabelProperty());

        // 狀態欄顏色：依 State 上色
        colState.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTextFill(Color.BLACK);
                    return;
                }
                setText(item);
                RenamePreviewItem row = getTableRow().getItem();
                if (row == null) return;
                setTextFill(switch (row.getState()) {
                    case OK        -> Color.web("#15803d");  // green-700
                    case CONFLICT  -> Color.web("#b45309");  // amber-700
                    case ERROR     -> Color.web("#b91c1c");  // red-700
                    case UNCHANGED -> Color.web("#6b7280");  // gray-500
                });
            }
        });

        // 新檔名欄：若狀態為 ERROR / CONFLICT 用斜體紅色
        colNew.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                RenamePreviewItem row = getTableRow().getItem();
                if (row == null) { setStyle(""); return; }
                setStyle(switch (row.getState()) {
                    case ERROR, CONFLICT -> "-fx-text-fill: #b91c1c; -fx-font-style: italic;";
                    case UNCHANGED -> "-fx-text-fill: #9ca3af;";
                    default -> "";
                });
            }
        });
    }

    // =========================================================
    // 由 MainController 呼叫：注入要改名的清單
    // =========================================================

    public void setItems(List<FileItem> items) {
        this.sourceItems = items;
        setStatus("已載入 " + items.size() + " 個檔案，請設定規則後按「重新預覽」。");
    }

    // =========================================================
    // FXML 事件
    // =========================================================

    @FXML
    void handlePreview(ActionEvent event) {
        if (sourceItems == null || sourceItems.isEmpty()) {
            setStatus("沒有可預覽的檔案。");
            return;
        }

        RenameRule rule = buildRuleFromUI();
        if (rule == null) return; // 驗證失敗，buildRuleFromUI 已顯示提示

        List<RenamePreviewItem> result = renameService.preview(sourceItems, rule);
        previewItems.setAll(result);

        long okCount       = result.stream().filter(r -> r.getState() == State.OK).count();
        long conflictCount = result.stream().filter(r -> r.getState() == State.CONFLICT).count();
        long errorCount    = result.stream().filter(r -> r.getState() == State.ERROR).count();
        long unchangedCount = result.stream().filter(r -> r.getState() == State.UNCHANGED).count();

        lblPreviewCount.setText(
            "共 " + result.size() + " 個　✓ " + okCount +
            "　⚠ " + conflictCount + "　✗ " + errorCount +
            "　— " + unchangedCount
        );

        btnExecute.setDisable(okCount == 0);
        setStatus("預覽完成。可執行改名的檔案：" + okCount + " 個。");
    }

    @FXML
    void handleExecute(ActionEvent event) {
        if (previewItems.isEmpty()) return;

        int count = renameService.execute(previewItems.stream().toList());

        btnUndo.setDisable(count == 0);
        btnExecute.setDisable(true);
        setStatus("已成功重新命名 " + count + " 個檔案。");

        // 重新整理預覽表（執行後 UNCHANGED/CONFLICT 留著，OK 變灰）
        previewItems.forEach(p -> {
            if (p.getState() == State.OK) p.setState(State.UNCHANGED);
        });
        previewTable.refresh();
    }

    @FXML
    void handleUndo(ActionEvent event) {
        int count = renameService.undo();
        btnUndo.setDisable(true);
        setStatus("已復原 " + count + " 個重新命名操作。");
        previewItems.clear();
        lblPreviewCount.setText("");
    }

    @FXML
    void handleClose(ActionEvent event) {
        getStage().close();
    }

    // =========================================================
    // 從 UI 組裝 RenameRule
    // =========================================================

    private RenameRule buildRuleFromUI() {
        int tabIdx = tabPane.getSelectionModel().getSelectedIndex();
        Mode mode  = (tabIdx >= 0 && tabIdx < TAB_MODES.length)
                     ? TAB_MODES[tabIdx]
                     : Mode.REGEX;

        RenameRule rule = new RenameRule();
        rule.setMode(mode);

        switch (mode) {
            case REGEX -> {
                rule.setRegexFind(regexFindField.getText());
                rule.setRegexReplace(regexReplaceField.getText());
                rule.setRegexIgnoreCase(regexIgnoreCase.isSelected());
            }
            case INSERT -> {
                rule.setInsertText(insertTextField.getText());
                if (insertAtStart.isSelected()) {
                    rule.setInsertPosition(0);
                } else if (insertBeforeExt.isSelected()) {
                    rule.setInsertPosition(-1);
                } else {
                    rule.setInsertPosition(insertPosSpinner.getValue());
                }
            }
            case CASE -> {
                if (caseLower.isSelected())      rule.setCaseType(CaseType.LOWER);
                else if (caseUpper.isSelected()) rule.setCaseType(CaseType.UPPER);
                else                             rule.setCaseType(CaseType.TITLE);
            }
            case SERIALIZE -> {
                rule.setSerializePrefix(serializePrefixField.getText());
                rule.setSerializeSuffix(serializeSuffixField.getText());
                rule.setSerializeStart(serializeStartSpinner.getValue());
                rule.setSerializePadding(serializePaddingSpinner.getValue());
            }
        }
        return rule;
    }

    // =========================================================
    // 工具
    // =========================================================

    private void setStatus(String msg) {
        if (lblStatusMsg != null) lblStatusMsg.setText(msg);
    }

    private Stage getStage() {
        return (Stage) tabPane.getScene().getWindow();
    }
}
