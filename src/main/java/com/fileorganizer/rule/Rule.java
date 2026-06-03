package com.fileorganizer.rule;

import com.fileorganizer.model.FileItem;

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
