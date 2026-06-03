package com.fileorganizer.rule;

import com.fileorganizer.model.FileItem;

/**
 * 分類規則介面。
 * C 實作預設規則，使用者自訂規則也實作此介面。
 * B 和 A 不需要知道規則細節，只需呼叫 RuleEngine.apply()。
 */
public interface Rule {

    /** 規則名稱（顯示在 UI 設定面板） */
    String getName();

    /** 此規則是否適用於這個檔案 */
    boolean matches(FileItem item);

    /** 若 matches，回傳此檔案應放入的子目錄名稱（相對於目標根目錄） */
    String getTargetSubfolder(FileItem item);

    /** 規則優先序（數字越小越優先） */
    int getPriority();
}
