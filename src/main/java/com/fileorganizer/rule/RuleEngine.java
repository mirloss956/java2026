package com.fileorganizer.rule;

import com.fileorganizer.model.FileItem;
import java.nio.file.Path;
import java.util.List;

/**
 * 負責人：C
 * 依優先序套用 Rule，決定每個 FileItem 的 destinationPath。
 */
public class RuleEngine {

    private final List<Rule> rules;
    private final Path targetRoot;  // 整理後的根目錄（使用者在 UI 選的）

    public RuleEngine(List<Rule> rules, Path targetRoot) {
        // 依優先序排列
        this.rules = rules.stream()
                .sorted(java.util.Comparator.comparingInt(Rule::getPriority))
                .toList();
        this.targetRoot = targetRoot;
    }

    /**
     * 對單一 FileItem 套用規則，設好 destinationPath。
     * 若無任何規則匹配，放入 "其他" 資料夾。
     */
    public void apply(FileItem item) {
        for (Rule rule : rules) {
            if (rule.matches(item)) {
                String subfolder = rule.getTargetSubfolder(item);
                item.setDestinationPath(
                    targetRoot.resolve(subfolder).resolve(item.getFileName())
                );
                return;
            }
        }
        // 預設：放到「其他」
        item.setDestinationPath(
            targetRoot.resolve("其他").resolve(item.getFileName())
        );
    }

    /**
     * 對整批清單套用規則。B 呼叫 FileMoveService.move() 前先呼叫此方法。
     */
    public void applyAll(List<FileItem> items) {
        items.forEach(this::apply);
    }

    public List<Rule> getRules() { return List.copyOf(rules); }
    public Path getTargetRoot() { return targetRoot; }
}
