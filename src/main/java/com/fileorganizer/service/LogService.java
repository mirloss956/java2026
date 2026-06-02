package com.fileorganizer.service;

import com.fileorganizer.model.OrganizeResult;
import java.util.List;

/**
 * 負責人：C
 * 將操作結果寫入 SQLite，並提供查詢介面給 A（UI 日誌面板）。
 */
public interface LogService {

    /** 寫入一筆整理記錄 */
    void save(OrganizeResult result);

    /** 取得最近 N 筆記錄（A 用來顯示日誌列表） */
    List<OrganizeResult> getRecent(int limit);

    /** 清除所有日誌 */
    void clearAll();
}
