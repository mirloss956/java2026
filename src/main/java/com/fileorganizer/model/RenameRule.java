package com.fileorganizer.model;

/**
 * 負責人：C / A
 * 一條重新命名規則的設定（對應 BatchRenameController 的 UI 狀態）。
 * BatchRenameServiceImpl 用它決定如何轉換檔名。
 */
public class RenameRule {

    public enum Mode {
        REGEX,       // 正規表達式取代
        INSERT,      // 在指定位置插入文字
        CASE,        // 大小寫轉換
        SERIALIZE    // 加序號（prefix_001.ext）
    }

    // ── 通用 ──────────────────────────────────────────────────────────────
    private Mode   mode          = Mode.REGEX;

    // ── REGEX 模式 ────────────────────────────────────────────────────────
    private String regexFind     = "";   // 要比對的正規表達式
    private String regexReplace  = "";   // 取代字串（支援 $1 群組）
    private boolean regexIgnoreCase = false;

    // ── INSERT 模式 ───────────────────────────────────────────────────────
    private String insertText    = "";
    private int    insertPosition = 0;   // 0 = 開頭，-1 = 副檔名前，正整數 = 第 N 字元後

    // ── CASE 模式 ─────────────────────────────────────────────────────────
    public enum CaseType { UPPER, LOWER, TITLE }
    private CaseType caseType   = CaseType.LOWER;

    // ── SERIALIZE 模式 ────────────────────────────────────────────────────
    private String serializePrefix  = "";
    private String serializeSuffix  = "";
    private int    serializeStart   = 1;
    private int    serializePadding = 3;  // 001, 002 … 的位數

    // ── Getters / Setters ─────────────────────────────────────────────────

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public String getRegexFind() { return regexFind; }
    public void setRegexFind(String regexFind) { this.regexFind = regexFind; }

    public String getRegexReplace() { return regexReplace; }
    public void setRegexReplace(String regexReplace) { this.regexReplace = regexReplace; }

    public boolean isRegexIgnoreCase() { return regexIgnoreCase; }
    public void setRegexIgnoreCase(boolean regexIgnoreCase) { this.regexIgnoreCase = regexIgnoreCase; }

    public String getInsertText() { return insertText; }
    public void setInsertText(String insertText) { this.insertText = insertText; }

    public int getInsertPosition() { return insertPosition; }
    public void setInsertPosition(int insertPosition) { this.insertPosition = insertPosition; }

    public CaseType getCaseType() { return caseType; }
    public void setCaseType(CaseType caseType) { this.caseType = caseType; }

    public String getSerializePrefix() { return serializePrefix; }
    public void setSerializePrefix(String serializePrefix) { this.serializePrefix = serializePrefix; }

    public String getSerializeSuffix() { return serializeSuffix; }
    public void setSerializeSuffix(String serializeSuffix) { this.serializeSuffix = serializeSuffix; }

    public int getSerializeStart() { return serializeStart; }
    public void setSerializeStart(int serializeStart) { this.serializeStart = serializeStart; }

    public int getSerializePadding() { return serializePadding; }
    public void setSerializePadding(int serializePadding) { this.serializePadding = Math.max(1, serializePadding); }
}
