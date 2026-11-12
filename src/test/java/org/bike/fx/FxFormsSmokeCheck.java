package org.bike.fx;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Loads real FXML and CSS on the JavaFX thread, without contacting any server. */
public final class FxFormsSmokeCheck {
    public static void main(String[] args) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(
                () -> {
                    try {
                        for (String name :
                                new String[] {
                                    "Shell",
                                    "Login",
                                    "Profiles",
                                    "Catalog",
                                    "Discounts",
                                    "Settings",
                                    "Clients",
                                    "ClientEditor",
                                    "BicycleEditor",
                                    "WorkOrder"
                                }) {
                            var view = FxView.load(name);
                            var scene = FxView.scene(view.root());
                            view.root().resize(1440, 960);
                            view.root().applyCss();
                            view.root().layout();
                            if (view.controller() == null)
                                throw new AssertionError(name + ": controller missing");
                            System.out.println("FXML loaded: " + name);
                            if (name.equals("WorkOrder")
                                    || name.equals("Clients")
                                    || name.equals("ClientEditor")) {
                                var snapshot = view.root().snapshot(null, null);
                                var image =
                                        new java.awt.image.BufferedImage(
                                                (int) snapshot.getWidth(),
                                                (int) snapshot.getHeight(),
                                                java.awt.image.BufferedImage.TYPE_INT_ARGB);
                                var reader = snapshot.getPixelReader();
                                for (int y = 0; y < image.getHeight(); y++)
                                    for (int x = 0; x < image.getWidth(); x++)
                                        image.setRGB(x, y, reader.getArgb(x, y));
                                javax.imageio.ImageIO.write(
                                        image,
                                        "png",
                                        new java.io.File("target/javafx-" + name + ".png"));
                            }
                        }
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        finished.countDown();
                    }
                });
        finished.await();
        Platform.exit();
        if (failure.get() != null)
            throw new AssertionError("JavaFX form loading failed", failure.get());
    }
}
