package com.fileorganizer.service;

import com.fileorganizer.model.FileItem;
import java.nio.file.Path;
import java.util.List;

/**
 * 負責人：B
 * 掃描指定資料夾，回傳 FileItem 清單（不做任何搬移）。
 */
public interface FileScanService {

    /**
     * 掃描資料夾（不含子資料夾）
     */
    List<FileItem> scan(Path directory);

    /**
     * 掃描資料夾（含子資料夾，depth 層）
     */
    List<FileItem> scanRecursive(Path directory, int depth);
}
