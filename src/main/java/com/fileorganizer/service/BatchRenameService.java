package com.fileorganizer.service;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.RenamePreviewItem;
import com.fileorganizer.model.RenameRule;

import java.util.List;

/**
 * 負責人：C
 * 提供批次重新命名的預覽計算與實際執行。
 * Controller 只呼叫此介面，不直接處理正規表達式或 IO。
 */
public interface BatchRenameService {

    /**
     * 根據規則計算每個 FileItem 的新檔名，回傳預覽清單。
     * 不做任何實際 IO；純粹計算。
     *
     * @param items 要重新命名的檔案清單
     * @param rule  套用的規則
     * @return 每個 item 的預覽結果（含衝突偵測）
     */
    List<RenamePreviewItem> preview(List<FileItem> items, RenameRule rule);

    /**
     * 執行重新命名（只對 state == OK 的項目實際改名）。
     *
     * @param previews 已由 preview() 計算好的清單
     * @return 成功改名的數量
     */
    int execute(List<RenamePreviewItem> previews);

    /**
     * 復原上一次 execute() 的操作（交換 original ↔ new）。
     *
     * @return 成功復原的數量
     */
    int undo();
}
