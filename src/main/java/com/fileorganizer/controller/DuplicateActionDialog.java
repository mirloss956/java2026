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
        MOVE_ALL,       // 一起整理（兩個都移到分類資料夾）
        KEEP_ONE,       // 只保留一個（刪除多餘的重複檔）
        ISOLATE         // 移到「重複檔案」資料夾
    }

    /**
     * 顯示對話框，回傳使用者選擇的動作。
     * 若使用者按取消則回傳 Optional.empty()。
     */
    public static Optional<DuplicateAction> show() {
        Dialog<DuplicateAction> dialog = new Dialog<>();
        dialog.setTitle("重複檔案處理方式");
        dialog.setHeaderText("偵測到重複檔案，請選擇處理方式：");

        // 按鈕
        ButtonType confirmBtn = new ButtonType("確認並整理", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn  = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmBtn, cancelBtn);

        // 選項
        ToggleGroup group = new ToggleGroup();

        RadioButton rbMoveAll = new RadioButton("一起整理（兩個都移到分類資料夾）");
        rbMoveAll.setToggleGroup(group);
        rbMoveAll.setSelected(true);

        RadioButton rbKeepOne = new RadioButton("只保留一個（自動刪除多餘的重複檔）");
        rbKeepOne.setToggleGroup(group);

        RadioButton rbIsolate = new RadioButton("移到「重複檔案」資料夾（讓我自己決定）");
        rbIsolate.setToggleGroup(group);

        VBox content = new VBox(10, rbMoveAll, rbKeepOne, rbIsolate);
        content.setPadding(new Insets(10, 20, 10, 10));
        dialog.getDialogPane().setContent(content);

        // 結果轉換
        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmBtn) {
                if (rbKeepOne.isSelected()) return DuplicateAction.KEEP_ONE;
                if (rbIsolate.isSelected())  return DuplicateAction.ISOLATE;
                return DuplicateAction.MOVE_ALL;
            }
            return null;
        });

        Optional<DuplicateAction> result = dialog.showAndWait();
        return result;
    }
}
