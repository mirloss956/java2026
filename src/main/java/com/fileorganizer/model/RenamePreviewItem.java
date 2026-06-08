package com.fileorganizer.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * 負責人：C / A
 * 批次重新命名預覽列，對應 batch_rename.fxml 的 TableView 每一列。
 * 儲存「原始檔名 → 新檔名」的對照，以及是否衝突的狀態。
 */
public class RenamePreviewItem {

    public enum State {
        OK,        // 可以重新命名
        CONFLICT,  // 目標檔名已存在
        ERROR,     // 正規表達式套用失敗
        UNCHANGED  // 規則套用後名稱不變
    }

    private final StringProperty originalName = new SimpleStringProperty();
    private final StringProperty newName       = new SimpleStringProperty();
    private final StringProperty stateLabel    = new SimpleStringProperty();

    private State state;

    /** 原始的 FileItem，執行時需要拿它的路徑 */
    private final FileItem fileItem;

    public RenamePreviewItem(FileItem fileItem) {
        this.fileItem = fileItem;
        this.originalName.set(fileItem.getFileName());
        this.newName.set(fileItem.getFileName());
        setState(State.UNCHANGED);
    }

    // ── 屬性存取 ──────────────────────────────────────────────────────────

    public StringProperty originalNameProperty() { return originalName; }
    public StringProperty newNameProperty()       { return newName; }
    public StringProperty stateLabelProperty()    { return stateLabel; }

    public String getOriginalName() { return originalName.get(); }
    public String getNewName()      { return newName.get(); }
    public State  getState()        { return state; }
    public FileItem getFileItem()   { return fileItem; }

    public void setNewName(String name) { newName.set(name); }

    public void setState(State state) {
        this.state = state;
        stateLabel.set(switch (state) {
            case OK        -> "✓ 確認";
            case CONFLICT  -> "⚠ 已存在";
            case ERROR     -> "✗ 錯誤";
            case UNCHANGED -> "— 不變";
        });
    }
}
