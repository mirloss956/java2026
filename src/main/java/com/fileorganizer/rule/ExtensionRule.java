package com.fileorganizer.rule;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.FileCategory;
import java.util.Set;

/**
 * 預設規則：依副檔名分類。
 * C 提供此預設實作，使用者也可在 UI 新增自訂的 ExtensionRule。
 *
 * 文件類別細分（子資料夾）：
 *   文件/PDF      — pdf
 *   文件/Word     — doc, docx
 *   文件/簡報     — ppt, pptx
 *   文件/試算表   — xls, xlsx, csv, ods
 *   文件/純文字   — txt, md, log
 *   文件/其他文件 — 其餘未歸類文件副檔名
 */
public class ExtensionRule implements Rule {

    private static final Set<String> IMAGE_EXT   = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "svg");

    private static final Set<String> VIDEO_EXT   = Set.of(
            "mp4", "mov", "avi", "mkv", "wmv", "flv", "webm");

    private static final Set<String> AUDIO_EXT   = Set.of(
            "mp3", "wav", "aac", "flac", "ogg", "m4a", "wma");

    private static final Set<String> ARCHIVE_EXT = Set.of(
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz");

    private static final Set<String> CODE_EXT    = Set.of(
            "java", "py", "js", "ts", "html", "css",
            "json", "xml", "yml", "yaml", "sh", "bat");

    // ── 文件子分類 ────────────────────────────────────────────────────────────

    private static final Set<String> DOC_PDF       = Set.of("pdf");
    private static final Set<String> DOC_WORD      = Set.of("doc", "docx");
    private static final Set<String> DOC_SLIDE     = Set.of("ppt", "pptx");
    private static final Set<String> DOC_SHEET     = Set.of("xls", "xlsx", "csv", "ods");
    private static final Set<String> DOC_PLAINTEXT = Set.of("txt", "md", "log");

    /** 所有「文件」副檔名的聯集，用於 matches() 判斷 */
    private static final Set<String> ALL_DOC_EXT;
    static {
        var all = new java.util.HashSet<String>();
        all.addAll(DOC_PDF);
        all.addAll(DOC_WORD);
        all.addAll(DOC_SLIDE);
        all.addAll(DOC_SHEET);
        all.addAll(DOC_PLAINTEXT);
        ALL_DOC_EXT = Set.copyOf(all);
    }

    // ── Rule 實作 ─────────────────────────────────────────────────────────────

    @Override
    public String getName() { return "副檔名分類（預設）"; }

    @Override
    public boolean matches(FileItem item) {
        return !item.getExtension().isEmpty();
    }

    @Override
    public String getTargetSubfolder(FileItem item) {
        String ext = item.getExtension();

        if (IMAGE_EXT.contains(ext))   return FileCategory.IMAGE.getDisplayName();
        if (VIDEO_EXT.contains(ext))   return FileCategory.VIDEO.getDisplayName();
        if (AUDIO_EXT.contains(ext))   return FileCategory.AUDIO.getDisplayName();
        if (ARCHIVE_EXT.contains(ext)) return FileCategory.ARCHIVE.getDisplayName();
        if (CODE_EXT.contains(ext))    return FileCategory.CODE.getDisplayName();

        // 文件類：回傳「文件/<子資料夾>」
        if (ALL_DOC_EXT.contains(ext)) return docSubfolder(ext);

        return FileCategory.OTHER.getDisplayName();
    }

    @Override
    public int getPriority() { return 100; }  // 低優先，讓使用者規則先跑

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private static String docSubfolder(String ext) {
        String base = FileCategory.DOCUMENT.getDisplayName(); // "文件"
        if (DOC_PDF.contains(ext))       return base + "/PDF";
        if (DOC_WORD.contains(ext))      return base + "/Word";
        if (DOC_SLIDE.contains(ext))     return base + "/簡報";
        if (DOC_SHEET.contains(ext))     return base + "/試算表";
        if (DOC_PLAINTEXT.contains(ext)) return base + "/純文字";
        return base + "/其他文件";
    }
}
