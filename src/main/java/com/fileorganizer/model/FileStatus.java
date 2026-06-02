package com.fileorganizer.model;

/**
 * 檔案在整理流程中的狀態。
 * A（UI）依此狀態決定顯示顏色與圖示。
 */
public enum FileStatus {
    PENDING,    // 等待處理
    MOVED,      // 已成功搬移
    SKIPPED,    // 跳過（重複或規則不符）
    DUPLICATE,  // 偵測為重複檔案
    FAILED      // 搬移失敗
}
