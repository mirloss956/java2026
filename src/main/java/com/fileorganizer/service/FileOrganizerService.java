package com.fileorganizer.service;

import com.fileorganizer.config.AppConfig;
import com.fileorganizer.model.OrganizeResult;
import java.util.function.Consumer;

/**
 * 負責人：B (實作核心) / A (定義與呼叫)
 * 這是 UI 層 (A) 與後端核心服務 (B) 對接的總介面。
 */
public interface FileOrganizerService {
    
    /**
     * 執行檔案掃描與整理
     * @param config 傳入目前的 AppConfig 設定物件
     * @param logConsumer 接收即時文字日誌的管道 (用於 UI 的 TextArea)
     * @param resultConsumer 接收最終處理結果的管道 (用於 UI 的 TableView)
     */
    void scanAndOrganize(AppConfig config, Consumer<String> logConsumer, Consumer<OrganizeResult> resultConsumer);
    
    /**
     * 復原上一次的操作
     */
    void undoLastAction(Consumer<String> logConsumer);
}