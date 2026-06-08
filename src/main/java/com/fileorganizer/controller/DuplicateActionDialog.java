package com.fileorganizer.controller;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * 按下「開始整理」後跳出的重複檔案處理選項對話框。
 * 重複檔案定義：MD5 相同的檔案，整組（兩個以上）一起處理。
 */
public class DuplicateActionDialog {

    public enum DuplicateAction {
        /** 重複的整組移到對應分類下的「重複檔案」子資料夾，例如 圖片/重複檔案/ */
        ISOLATE_IN_CATEGORY,
        /** 重複的整組統一移到根目錄下的「重複檔案」資料夾 */
        ISOLATE_ALL,
        /** 刪除所有重複檔（整組都刪） */
        DELETE_ALL
    }

    public static Optional<DuplicateAction> show() {
        Dialog<DuplicateAction> dialog = new Dialog<>();
        dialog.setTitle("重複檔案處理方式");
        dialog.setHeaderText("偵測到重複檔案（MD5 相同），請選擇處理方式：\n兩個（或以上）內容相同的檔案將一起被處理。");

        ButtonType confirmBtn = new ButtonType("確認並整理", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn  = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmBtn, cancelBtn);

        ToggleGroup group = new ToggleGroup();

        RadioButton rbIsolateInCategory = new RadioButton(
            "移到對應分類下的「重複檔案」子資料夾（例如 圖片/重複檔案/）");
        rbIsolateInCategory.setToggleGroup(group);
        rbIsolateInCategory.setSelected(true);

        RadioButton rbIsolateAll = new RadioButton(
            "統一移到根目錄下的「重複檔案」資料夾");
        rbIsolateAll.setToggleGroup(group);

        RadioButton rbDeleteAll = new RadioButton(
            "全部刪除（整組重複檔案都刪，請謹慎使用）");
        rbDeleteAll.setToggleGroup(group);

        VBox content = new VBox(12, rbIsolateInCategory, rbIsolateAll, rbDeleteAll);
        content.setPadding(new Insets(10, 20, 10, 10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(520);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmBtn) {
                if (rbIsolateAll.isSelected())  return DuplicateAction.ISOLATE_ALL;
                if (rbDeleteAll.isSelected())   return DuplicateAction.DELETE_ALL;
                return DuplicateAction.ISOLATE_IN_CATEGORY;
            }
            return null;
        });

        return dialog.showAndWait();
    }
}
