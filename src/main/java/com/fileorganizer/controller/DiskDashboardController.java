package com.fileorganizer.controller;

import com.fileorganizer.model.DiskStats;
import com.fileorganizer.model.DiskStats.CategoryStats;
import com.fileorganizer.model.FolderStats;
import com.fileorganizer.service.impl.AppContext;
import com.fileorganizer.service.impl.OrganizerFacade;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class DiskDashboardController {

    @FXML private TextField                        pathField;
    @FXML private Button                           scanBtn;
    @FXML private ProgressBar                      progressBar;
    @FXML private Label                            statusLabel;
    @FXML private PieChart                         pieChart;
    @FXML private VBox                             legendBox;
    @FXML private HBox                             statCards;
    @FXML private TableView<FolderStats>           folderTable;
    @FXML private TableColumn<FolderStats, String> folderNameCol;
    @FXML private TableColumn<FolderStats, String> folderSizeCol;
    @FXML private TableColumn<FolderStats, String> folderCountCol;

    private final OrganizerFacade facade = AppContext.get().getFacade();
    private Path selectedPath;

    @FXML
    public void initialize() {
        folderNameCol.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().name()));

        folderCountCol.setCellValueFactory(c ->
            new SimpleStringProperty(
                String.format("%,d", c.getValue().fileCount())));

        folderSizeCol.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().formattedSize()));

        folderSizeCol.setCellFactory(col -> new TableCell<>() {
            private final ProgressBar bar   = new ProgressBar(0);
            private final Label       label = new Label();
            private final StackPane   cell  = new StackPane(bar, label);

            {
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.setStyle("-fx-accent: #378ADD;");
                StackPane.setAlignment(label, javafx.geometry.Pos.CENTER_LEFT);
                label.setStyle("-fx-padding: 0 0 0 6; -fx-text-fill: white;");
            }

            @Override
            protected void updateItem(String size, boolean empty) {
                super.updateItem(size, empty);
                if (empty || getTableRow() == null
                        || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                FolderStats row = getTableRow().getItem();
                double max = folderTable.getItems().isEmpty()
                    ? 1 : folderTable.getItems().get(0).bytes();
                bar.setProgress(max == 0 ? 0 : row.bytes() / max);
                label.setText(size);
                setGraphic(cell);
            }
        });

        pieChart.setLegendVisible(false);
        pieChart.setLabelsVisible(false);
        progressBar.setVisible(false);
        statusLabel.setText("請選擇資料夾後按「開始分析」");
    }

    @FXML
    private void onChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("選擇要分析的資料夾");
        File dir = chooser.showDialog(pathField.getScene().getWindow());
        if (dir != null) {
            selectedPath = dir.toPath();
            pathField.setText(selectedPath.toString());
        }
    }

    @FXML
    private void onScan() {
        if (selectedPath == null) {
            statusLabel.setText("請先選擇資料夾");
            return;
        }

        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        scanBtn.setDisable(true);
        statusLabel.setText("掃描中...");
        pieChart.getData().clear();
        legendBox.getChildren().clear();
        statCards.getChildren().clear();
        folderTable.getItems().clear();

        var statsFuture = facade.analyzeDisk(selectedPath,
            count -> Platform.runLater(() ->
                statusLabel.setText("已掃描 " + count + " 個檔案...")));

        var folderFuture = facade.buildFolderTree(selectedPath);

        statsFuture.thenAccept(stats ->
            Platform.runLater(() -> renderStats(stats)));

        folderFuture.thenAccept(tree ->
            Platform.runLater(() -> renderFolderTable(tree)));

        statsFuture.thenCombine(folderFuture, (s, f) -> null)
            .thenRun(() -> Platform.runLater(() -> {
                progressBar.setVisible(false);
                scanBtn.setDisable(false);
                statusLabel.setText("分析完成");
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    statusLabel.setText("掃描失敗：" + ex.getMessage());
                    progressBar.setVisible(false);
                    scanBtn.setDisable(false);
                });
                return null;
            });
    }

    private void renderStats(DiskStats stats) {
        statCards.getChildren().addAll(
            makeStatCard("總大小",   stats.formattedTotal()),
            makeStatCard("檔案數量", String.format("%,d 個", stats.fileCount())),
            makeStatCard("類別數",   stats.byCategory().size() + " 種")
        );

        for (CategoryStats cat : stats.byCategory())
            pieChart.getData().add(new PieChart.Data(cat.name(), cat.bytes()));

        Platform.runLater(() -> {
            List<CategoryStats> cats = stats.byCategory();
            var slices = pieChart.getData();
            for (int i = 0; i < slices.size() && i < cats.size(); i++) {
                CategoryStats cat = cats.get(i);
                slices.get(i).getNode()
                    .setStyle("-fx-pie-color: " + cat.hexColor() + ";");
                slices.get(i).getNode().setOnMouseClicked(e ->
                    statusLabel.setText(
                        cat.name() + "：" + cat.formattedSize()
                        + "（" + "%.1f".formatted(cat.percent(stats.totalBytes()))
                        + "%，共 " + cat.count() + " 個檔案）"));
            }
            buildLegend(stats);
        });
    }

    private void buildLegend(DiskStats stats) {
        legendBox.getChildren().clear();
        for (CategoryStats cat : stats.byCategory()) {
            HBox row = new HBox(8);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Rectangle dot = new Rectangle(12, 12, Color.web(cat.hexColor()));
            dot.setArcWidth(3);
            dot.setArcHeight(3);

            Label name = new Label(cat.name());
            name.setMinWidth(52);
            name.setStyle("-fx-font-weight: bold;");

            Label detail = new Label(
                cat.formattedSize()
                + "  (" + "%.1f".formatted(cat.percent(stats.totalBytes())) + "%)");
            detail.setStyle("-fx-text-fill: #888780;");

            row.getChildren().addAll(dot, name, detail);
            legendBox.getChildren().add(row);
        }
    }

    private void renderFolderTable(FolderStats tree) {
        List<FolderStats> top10 = tree.children().stream().limit(10).toList();
        folderTable.setItems(FXCollections.observableArrayList(top10));
    }

    private VBox makeStatCard(String label, String value) {
        VBox card = new VBox(4);
        card.setStyle("""
            -fx-border-color: #e0e0e0;
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            -fx-padding: 12 20 12 20;
            -fx-min-width: 130;
            """);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 12; -fx-text-fill: #888780;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 20; -fx-font-weight: bold;");
        card.getChildren().addAll(lbl, val);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }
}