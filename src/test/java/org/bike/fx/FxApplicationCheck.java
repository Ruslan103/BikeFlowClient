package org.bike.fx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/** Exercises the production application startup, including the shell and stored-profile screen. */
public final class FxApplicationCheck {
    private static volatile Throwable failure;
    public static void main(String[] args) {
        Application.launch(Probe.class, args);
        if (failure != null) throw new AssertionError("Application startup failed", failure);
    }
    public static final class Probe extends Application {
        private FxApplication application;
        @Override public void start(Stage stage) {
            try {
                application=new FxApplication();application.start(stage);
                if(!stage.isShowing() || !(stage.getScene().getRoot() instanceof BorderPane))throw new AssertionError("Main window not shown");
                if(stage.getScene().getRoot().lookup("#content")==null)throw new AssertionError("Navigation host missing");
                if(stage.getScene().getRoot().getStylesheets().isEmpty())throw new AssertionError("FXML stylesheet missing");
                System.out.println("Production JavaFX startup passed");
            }catch(Throwable error){failure=error;}
            Platform.runLater(()->{stage.hide();Platform.exit();});
        }
        @Override public void stop(){if(application!=null)application.stop();}
    }
}
