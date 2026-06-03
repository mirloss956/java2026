package com.fileorganizer;

import com.fileorganizer.service.impl.AppContext;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * 應用程式進入點。
 * 負責人：C（啟動流程）、A（FXML 和 CSS 路徑）
 */
public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // C：初始化 AppContext（組裝所有 Service）
        AppContext.init();

        // A：負責建立 main.fxml 和 main.css
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Scene scene = new Scene(loader.load(), 960, 640);
        scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());

        stage.setTitle("File Organizer");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        // 應用程式關閉時停止監控
        AppContext.get().getFacade().stopWatch();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
