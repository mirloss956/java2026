package com.fileorganizer.service.impl;

import com.fileorganizer.model.FileItem;
import com.fileorganizer.model.RenamePreviewItem;
import com.fileorganizer.model.RenamePreviewItem.State;
import com.fileorganizer.model.RenameRule;
import com.fileorganizer.service.BatchRenameService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 負責人：C
 * BatchRenameService 實作。
 * 四種模式的轉換邏輯全部在 applyRule() 內，Controller 不接觸任何字串處理。
 */
public class BatchRenameServiceImpl implements BatchRenameService {

    /** 上一次 execute() 的歷史，供 undo() 使用 */
    private final List<RenameHistory> lastHistory = new ArrayList<>();

    private record RenameHistory(Path from, Path to) {}

    // =========================================================
    // preview
    // =========================================================

    @Override
    public List<RenamePreviewItem> preview(List<FileItem> items, RenameRule rule) {
        List<RenamePreviewItem> result = new ArrayList<>();

        // 用來偵測批次內部的重複（不同 item 被改成同一個名字）
        Set<String> seenNames = new HashSet<>();

        int counter = rule.getSerializeStart(); // 序號計數器

        for (FileItem item : items) {
            RenamePreviewItem preview = new RenamePreviewItem(item);
            try {
                String newName = applyRule(item.getFileName(), rule, counter);

                if (rule.getMode() == RenameRule.Mode.SERIALIZE) {
                    counter++;
                }

                if (newName.equals(item.getFileName())) {
                    preview.setNewName(newName);
                    preview.setState(State.UNCHANGED);
                } else if (seenNames.contains(newName.toLowerCase())) {
                    // 批次內重複
                    preview.setNewName(newName);
                    preview.setState(State.CONFLICT);
                } else {
                    // 檢查磁碟上是否已存在同名檔案
                    Path parent = item.getSourcePath().getParent();
                    Path target = (parent != null) ? parent.resolve(newName) : Path.of(newName);

                    if (Files.exists(target)) {
                        preview.setNewName(newName);
                        preview.setState(State.CONFLICT);
                    } else {
                        preview.setNewName(newName);
                        preview.setState(State.OK);
                        seenNames.add(newName.toLowerCase());
                    }
                }
            } catch (PatternSyntaxException e) {
                preview.setNewName("（正規表達式錯誤）");
                preview.setState(State.ERROR);
            } catch (Exception e) {
                preview.setNewName("（套用失敗：" + e.getMessage() + "）");
                preview.setState(State.ERROR);
            }
            result.add(preview);
        }
        return result;
    }

    // =========================================================
    // execute
    // =========================================================

    @Override
    public int execute(List<RenamePreviewItem> previews) {
        lastHistory.clear();
        int successCount = 0;

        for (RenamePreviewItem preview : previews) {
            if (preview.getState() != State.OK) continue;

            Path src    = preview.getFileItem().getSourcePath();
            Path parent = src.getParent();
            Path dest   = (parent != null) ? parent.resolve(preview.getNewName()) : Path.of(preview.getNewName());

            try {
                Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE);
                lastHistory.add(new RenameHistory(src, dest));
                preview.getFileItem().setStatus(com.fileorganizer.model.FileStatus.MOVED);
                successCount++;
            } catch (IOException e) {
                // ATOMIC_MOVE 不支援跨磁碟時退 fallback
                try {
                    Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    lastHistory.add(new RenameHistory(src, dest));
                    preview.getFileItem().setStatus(com.fileorganizer.model.FileStatus.MOVED);
                    successCount++;
                } catch (IOException ex) {
                    System.err.println("重新命名失敗：" + src + " → " + dest + "，原因：" + ex.getMessage());
                    preview.setState(State.ERROR);
                }
            }
        }
        return successCount;
    }

    // =========================================================
    // undo
    // =========================================================

    @Override
    public int undo() {
        if (lastHistory.isEmpty()) return 0;

        int successCount = 0;
        // 反向復原，避免同資料夾名稱衝突
        for (int i = lastHistory.size() - 1; i >= 0; i--) {
            RenameHistory h = lastHistory.get(i);
            try {
                Files.move(h.to(), h.from(), StandardCopyOption.REPLACE_EXISTING);
                successCount++;
            } catch (IOException e) {
                System.err.println("復原重新命名失敗：" + h.to() + " → " + h.from() + "，原因：" + e.getMessage());
            }
        }
        lastHistory.clear();
        return successCount;
    }

    // =========================================================
    // 核心：applyRule — 把一個 RenameRule 套到一個檔名上
    // =========================================================

    private String applyRule(String filename, RenameRule rule, int counter) {
        return switch (rule.getMode()) {
            case REGEX    -> applyRegex(filename, rule);
            case INSERT   -> applyInsert(filename, rule);
            case CASE     -> applyCase(filename, rule);
            case SERIALIZE -> applySerialize(filename, rule, counter);
        };
    }

    // ── Regex 模式 ────────────────────────────────────────────────────────

    private String applyRegex(String filename, RenameRule rule) {
        if (rule.getRegexFind().isBlank()) return filename;

        int flags = rule.isRegexIgnoreCase() ? Pattern.CASE_INSENSITIVE : 0;
        Pattern p = Pattern.compile(rule.getRegexFind(), flags); // 若語法錯誤會拋 PatternSyntaxException
        Matcher m = p.matcher(filename);
        return m.replaceAll(rule.getRegexReplace());
    }

    // ── Insert 模式 ───────────────────────────────────────────────────────

    private String applyInsert(String filename, RenameRule rule) {
        String text = rule.getInsertText();
        if (text.isEmpty()) return filename;

        int pos = rule.getInsertPosition();

        if (pos == -1) {
            // 在副檔名之前插入
            int dot = filename.lastIndexOf('.');
            if (dot < 0) return filename + text;
            return filename.substring(0, dot) + text + filename.substring(dot);
        }

        // 一般位置插入（超出邊界自動截斷）
        int safePos = Math.min(Math.max(pos, 0), filename.length());
        return filename.substring(0, safePos) + text + filename.substring(safePos);
    }

    // ── Case 模式 ─────────────────────────────────────────────────────────

    private String applyCase(String filename, RenameRule rule) {
        // 只轉換主檔名，不動副檔名
        int dot = filename.lastIndexOf('.');
        String base = (dot >= 0) ? filename.substring(0, dot) : filename;
        String ext  = (dot >= 0) ? filename.substring(dot)    : "";

        String converted = switch (rule.getCaseType()) {
            case UPPER -> base.toUpperCase();
            case LOWER -> base.toLowerCase();
            case TITLE -> toTitleCase(base);
        };
        return converted + ext;
    }

    private String toTitleCase(String s) {
        if (s.isBlank()) return s;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c) || c == '_' || c == '-') {
                sb.append(c);
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    // ── Serialize 模式 ────────────────────────────────────────────────────

    private String applySerialize(String filename, RenameRule rule, int counter) {
        int dot = filename.lastIndexOf('.');
        String ext = (dot >= 0) ? filename.substring(dot) : "";

        String paddedNum = String.format(
            "%0" + rule.getSerializePadding() + "d",
            counter
        );
        return rule.getSerializePrefix() + paddedNum + rule.getSerializeSuffix() + ext;
    }
}
