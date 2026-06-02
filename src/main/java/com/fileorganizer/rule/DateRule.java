package com.fileorganizer.rule;

import com.fileorganizer.model.FileItem;

/**
 * 依「修改年份/月份」整理，例如放到 2025/06/。
 * 優先序比 ExtensionRule 高，可疊加（先依日期分資料夾，再用副檔名細分）。
 */
public class DateRule implements Rule {

    @Override
    public String getName() { return "依日期分類"; }

    @Override
    public boolean matches(FileItem item) {
        return item.getLastModified() != null;
    }

    @Override
    public String getTargetSubfolder(FileItem item) {
        int year  = item.getLastModified().getYear();
        int month = item.getLastModified().getMonthValue();
        return String.format("%d/%02d", year, month);
    }

    @Override
    public int getPriority() { return 50; }
}
