package org.bike.fx;

import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public final class FxApplication extends Application {
    private FxContext context;

    @Override
    public void start(Stage stage) {
        context = new FxContext();
        FxView.Loaded<org.bike.fx.shell.ShellController> view = FxView.load("Shell");
        stage.setTitle("BikeFlow");
        stage.getIcons().add(new Image(FxApplication.class.getResourceAsStream("/app-icon.png")));
        stage.setScene(FxView.scene(view.root()));
        stage.setWidth(1440);
        stage.setHeight(960);
        stage.setMinWidth(1080);
        stage.setMinHeight(720);
        view.controller().bind(context);
        stage.setOnCloseRequest(
                event -> {
                    if (!view.controller().canClose()) event.consume();
                });
        stage.show();
    }

    @Override
    public void stop() {
        if (context != null) context.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
