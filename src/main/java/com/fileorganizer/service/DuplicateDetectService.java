package com.fileorganizer.service;

import com.fileorganizer.model.FileItem;
import java.util.List;
import java.util.Map;

/**
 * 負責人：B
 * 計算 MD5 checksum，找出重複檔案群組。
 */
public interface DuplicateDetectService {

    /**
     * 對 items 計算 checksum 並標記重複。
     * 重複者的 status 會被設為 DUPLICATE，checksum 會被填入。
     * @return 重複群組 Map（checksum → 同 checksum 的 FileItem 清單）
     */
    Map<String, List<FileItem>> detectDuplicates(List<FileItem> items);
}
