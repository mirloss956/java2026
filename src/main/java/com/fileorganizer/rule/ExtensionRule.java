package com.fileorganizer.rule;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.FileCategory;
import java.util.Set;

/**
 * 預設規則：依副檔名分類。
 * C 提供此預設實作，使用者也可在 UI 新增自訂的 ExtensionRule。
 */
public class ExtensionRule implements Rule {

    private static final Set<String> IMAGE_EXT   = Set.of("jpg","jpeg","png","gif","bmp","webp","heic","svg");
    private static final Set<String> VIDEO_EXT   = Set.of("mp4","mov","avi","mkv","wmv","flv","webm");
    private static final Set<String> AUDIO_EXT   = Set.of("mp3","wav","aac","flac","ogg","m4a","wma");
    private static final Set<String> DOC_EXT     = Set.of("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","md","csv");
    private static final Set<String> ARCHIVE_EXT = Set.of("zip","rar","7z","tar","gz","bz2","xz");
    private static final Set<String> CODE_EXT    = Set.of("java","py","js","ts","html","css","json","xml","yml","yaml","sh","bat");

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
        if (DOC_EXT.contains(ext))     return FileCategory.DOCUMENT.getDisplayName();
        if (ARCHIVE_EXT.contains(ext)) return FileCategory.ARCHIVE.getDisplayName();
        if (CODE_EXT.contains(ext))    return FileCategory.CODE.getDisplayName();
        return FileCategory.OTHER.getDisplayName();
    }

    @Override
    public int getPriority() { return 100; }  // 低優先，讓使用者規則先跑
}
