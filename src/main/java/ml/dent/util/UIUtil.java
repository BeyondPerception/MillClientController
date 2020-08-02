package ml.dent.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressBar;
import javafx.stage.Window;

import java.util.function.Supplier;

public class UIUtil {
    public static void showError(String title, String contextInfo, String headerText, Window parent) {
        Runnable run = () -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, contextInfo, ButtonType.OK);
            alert.setTitle(title);
            if (headerText != null) {
                alert.setHeaderText(headerText);
            }
            alert.initOwner(parent);
            alert.showAndWait();
        };
        runOnJFXThread(run);
    }

    public static void showAlert(Alert.AlertType type, String title, String contextInfo, String headerText, boolean showProgress,
                                 Supplier<Boolean> isDone, Window window) {
        Runnable run = () -> {
            Alert alert = new Alert(type, contextInfo, ButtonType.OK);
            alert.setTitle(title);
            alert.initOwner(window);
            if (headerText != null) {
                alert.setHeaderText(headerText);
            }
            if (showProgress) {
                alert.setGraphic(new ProgressBar());
            }
            alert.show();
            if (isDone != null) {
                new Thread(() -> {
                    while (true) {
                        try {
                            if (isDone.get())
                                break;
                        } catch (Exception ignored) {
                        }
                    }
                    runOnJFXThread(alert::close);
                }).start();
            }
        };
        runOnJFXThread(run);
    }

    public static void runOnJFXThread(Runnable run) {
        if (Platform.isFxApplicationThread()) {
            run.run();
        } else {
            Platform.runLater(run);
        }
    }
}
