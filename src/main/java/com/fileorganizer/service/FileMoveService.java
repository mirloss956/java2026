package com.fileorganizer.service;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.OrganizeResult;
import java.nio.file.Path;
import java.util.List;

/**
 * 負責人：B
 * 根據 FileItem 上已設好的 destinationPath 執行搬移。
 * C（RuleEngine）在呼叫此 Service 前，必須先幫每個 FileItem 設好 destinationPath。
 */
public interface FileMoveService {

    /**
     * 執行搬移，回傳本次操作的結果。
     * @param items 已由 RuleEngine 設好 destinationPath 的清單
     * @param dryRun true = 只預覽，不實際搬移
     */
    OrganizeResult move(List<FileItem> items, boolean dryRun);

    /**
     * 復原上一次的整理操作（Undo）
     */
    boolean undoLast();
}
