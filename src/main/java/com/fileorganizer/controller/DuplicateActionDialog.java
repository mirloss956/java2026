package com.fileorganizer.controller;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * 按下「開始整理」後跳出的重複檔案處理選項對話框。
 */
public class DuplicateActionDialog {

    public enum DuplicateAction {
        /** 重複的移到對應分類資料夾下的「重複檔案」子資料夾，例如 圖片/重複檔案/ */
        ISOLATE_IN_CATEGORY,
        /** 只保留一個，刪除多餘的重複檔 */
        KEEP_ONE,
        /** 所有重複檔統一移到根目錄下的「重複檔案」資料夾 */
        ISOLATE_ALL
    }

    public static Optional<DuplicateAction> show() {
        Dialog<DuplicateAction> dialog = new Dialog<>();
        dialog.setTitle("重複檔案處理方式");
        dialog.setHeaderText("偵測到重複檔案，請選擇處理方式：");

        ButtonType confirmBtn = new ButtonType("確認並整理", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn  = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmBtn, cancelBtn);

        ToggleGroup group = new ToggleGroup();

        RadioButton rbIsolateInCategory = new RadioButton(
            "一起整理（重複的移到對應分類下的「重複檔案」子資料夾，例如 圖片/重複檔案/）");
        rbIsolateInCategory.setToggleGroup(group);
        rbIsolateInCategory.setSelected(true);

        RadioButton rbKeepOne = new RadioButton(
            "只保留一個（自動刪除多餘的重複檔）");
        rbKeepOne.setToggleGroup(group);

        RadioButton rbIsolateAll = new RadioButton(
            "統一隔離（所有重複檔移到根目錄下的「重複檔案」資料夾）");
        rbIsolateAll.setToggleGroup(group);

        VBox content = new VBox(12, rbIsolateInCategory, rbKeepOne, rbIsolateAll);
        content.setPadding(new Insets(10, 20, 10, 10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(520);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmBtn) {
                if (rbKeepOne.isSelected())    return DuplicateAction.KEEP_ONE;
                if (rbIsolateAll.isSelected()) return DuplicateAction.ISOLATE_ALL;
                return DuplicateAction.ISOLATE_IN_CATEGORY;
            }
            return null;
        });

        return dialog.showAndWait();
    }
}
