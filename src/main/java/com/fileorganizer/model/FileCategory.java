package com.fileorganizer.model;

/**
 * 檔案類別，決定整理到哪個子資料夾。
 * C（RuleEngine）輸出此值，B（FileMoveService）用它決定目標目錄。
 */
public enum FileCategory {
    IMAGE("圖片"),
    VIDEO("影片"),
    AUDIO("音樂"),
    DOCUMENT("文件"),
    ARCHIVE("壓縮檔"),
    CODE("程式碼"),
    OTHER("其他");

    private final String displayName;

    FileCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
