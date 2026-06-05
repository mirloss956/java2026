package com.fileorganizer;

import com.fileorganizer.service.impl.AppContext;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * 應用程式進入點。
 * 負責人：C（啟動流程）、A（FXML 和 CSS 路徑）
 *
 * 資源路徑約定（A 請對齊這裡）：
 *   FXML → src/main/resources/fxml/main.fxml
 *   CSS  → src/main/resources/style.css
 */
public class App extends Application {

    private static final String FXML_PATH = "/fxml/main.fxml";
    private static final String CSS_PATH  = "/style.css";

    @Override
    public void start(Stage stage) throws IOException {
        AppContext.init();

        URL fxmlUrl = getClass().getResource(FXML_PATH);
        if (fxmlUrl == null) {
            throw new IOException("找不到 FXML，請確認檔案存在於：src/main/resources" + FXML_PATH);
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Scene scene = new Scene(loader.load(), 960, 640);

        URL cssUrl = getClass().getResource(CSS_PATH);
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("警告：找不到 CSS，請確認檔案存在於：src/main/resources" + CSS_PATH);
        }

        stage.setTitle("File Organizer");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        // shutdown() 會等待 WatchService 執行緒確實結束，確保 JVM 乾淨退出
        AppContext.get().getFacade().shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
