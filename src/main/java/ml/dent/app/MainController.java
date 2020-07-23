package ml.dent.app;

import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import ml.dent.net.ControllerNetworkClient;
import ml.dent.net.SimpleNetworkClient;
import ml.dent.util.UIUtil;
import ml.dent.video.VideoClient;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class MainController {
    private final ControllerNetworkClient networkClient;
    private final VideoClient             videoClient;

    private final Stage window;

    private StatusHandler statusHandler;

    public MainController(Stage window) {
        this.window = window;
        networkClient = new ControllerNetworkClient("bounceserver.tk", 1111);
        videoClient = new VideoClient("bounceserver.tk", 1111);
        networkClient.setName("Network Client");
        videoClient.setName("Video Client");

        window.setOnCloseRequest(event -> {
            window.close();
            event.consume();
            try {
                Future<?> future = networkClient.disconnect();
                future.awaitUninterruptibly();
                videoClient.stopVideo();
                while (videoClient.isPlaying())
                    ;
                future = videoClient.disconnect();
                future.awaitUninterruptibly();
            } finally {
                System.exit(0);
            }
        });
    }

    @FXML private boolean enableSSL;
    @FXML private boolean enableProxy;

    @FXML private MenuItem closeConnection;

    @FXML private VBox millControlsContainer;

    @FXML private Slider speedControl;
    @FXML private Label  speedDisplay;

    @FXML private ImageView  videoView;
    @FXML private HBox       imageContainer;
    @FXML private AnchorPane displayPanel;

    @FXML private Label leftStatus;
    @FXML private Label rightStatus;

    @FXML private VBox     eventLogPopup;
    @FXML private TextArea eventLog;
    @FXML private Button   closeEventLog;
    @FXML private HBox     statusBar;

    @FXML
    public void initialize() {
        /* GUI BINDINGS */
        closeConnection.disableProperty().bind(networkClient.connectionActiveProperty().not());
        networkClient.connectionActiveProperty().addListener(listener -> {
            if (!networkClient.isConnectionActive() && networkClient.isUnexpectedClose()) {
                handleDisconnect(networkClient);
            }
        });
        videoClient.connectionActiveProperty().addListener(listener -> {
            if (!videoClient.isConnectionActive() && videoClient.isUnexpectedClose()) {
                handleDisconnect(videoClient);
            }
        });
        speedControl.valueProperty().addListener((obs, oldVal, newVal) -> {
            speedControl.setValue(newVal.intValue());
            if (newVal.intValue() == newVal.doubleValue()) {
                networkClient.setSpeed(newVal.intValue());
            }
        });
        speedDisplay.textProperty().bind(speedControl.valueProperty().asString());

        bindVideoTo(imageContainer);
        imageContainer.prefWidthProperty().bind(displayPanel.widthProperty());
        imageContainer.prefHeightProperty().bind(displayPanel.heightProperty());

        millControlsContainer.disableProperty().bind(networkClient.connectionActiveProperty().not());

        ProgressIndicator loadingGraphic = new ProgressIndicator();
        loadingGraphic.prefHeightProperty().bind(statusBar.heightProperty().subtract(5));
        rightStatus.setGraphicTextGap(0);
        statusHandler = new StatusHandler(leftStatus, rightStatus, loadingGraphic);
        statusHandler.setupEventLog(eventLogPopup, eventLog, closeEventLog);
        /* GUI BINDINGS */
    }

    @FXML
    protected void connectNetworkClient() {
        connectClient(networkClient);
    }

    @FXML
    protected void disconnectNetworkClient() {
        disconnectClient(networkClient);
    }

    @FXML
    protected void jogXPlus() {
        jogMill("X", 1);
    }

    @FXML
    protected void jogXMinus() {
        jogMill("X", -1);
    }

    @FXML
    protected void jogYPlus() {
        jogMill("Y", 1);
    }

    @FXML
    protected void jogYMinus() {
        jogMill("Y", -1);
    }

    @FXML
    protected void jogZPlus() {
        jogMill("Z", 1);
    }

    @FXML
    protected void jogZMinus() {
        jogMill("Z", -1);
    }

    @FXML
    protected void jogAPlus() {
        jogMill("A", 1);
    }

    @FXML
    protected void jogAMinus() {
        jogMill("A", -1);
    }

    @FXML
    protected void stopMill() {
        networkClient.stopMill();
    }

    @FXML
    protected void startVideo() {
        connectClient(videoClient);
        videoClient.startVideo(videoView);
    }

    @FXML
    protected void stopVideo() {
        videoClient.stopVideo();
        disconnectClient(videoClient);
    }

    @FXML
    protected void displayAbout() {

    }

    private Stage videoPopout;

    @FXML
    protected void popoutVideo() {
        if (videoPopout == null) {
            videoPopout = new Stage();
        }
        if (videoPopout.isShowing()) {
            // send close request to popout window ensuring cleanup code gets run
            videoPopout.fireEvent(new WindowEvent(videoPopout, WindowEvent.WINDOW_CLOSE_REQUEST));
        } else {
            AnchorPane root = new AnchorPane();
            HBox popoutImageContainer = new HBox();
            popoutImageContainer.setAlignment(Pos.CENTER);
            popoutImageContainer.prefWidthProperty().bind(root.widthProperty());
            popoutImageContainer.prefHeightProperty().bind(root.heightProperty());
            popoutImageContainer.getChildren().add(videoView);
            root.getChildren().add(popoutImageContainer);

            bindVideoTo(popoutImageContainer);

            videoPopout.setOnCloseRequest(event -> {
                event.consume();
                bindVideoTo(imageContainer);
                imageContainer.getChildren().add(videoView);
                videoPopout.close();
            });

            imageContainer.getChildren().clear();

            videoPopout.setScene(new Scene(root));
            videoPopout.show();
        }
    }

    @FXML
    protected void openSettingsWindow() throws IOException {
        Stage settingsWindow = new Stage();
        SettingsController settingsController = new SettingsController(networkClient, videoClient, settingsWindow);
        FXMLLoader settingsLoader = new FXMLLoader(getClass().getResource("/Settings.fxml"));
        settingsLoader.setController(settingsController);
        Parent root = settingsLoader.load();
        settingsWindow.setTitle("Settings");
        settingsWindow.setScene(new Scene(root));
        settingsWindow.initOwner(window);
        settingsController.switchToNetworkSettingsView();
        settingsWindow.show();
    }

    private void connectClient(SimpleNetworkClient client) {
        if (client.isConnectionActive()) {
            UIUtil.showError("Error", "Connection already active", "Error", window);
            return;
        }
//        new Thread(() -> {
//            AtomicReference<ChannelFuture> cf = new AtomicReference<>();
//            UIUtil.showAlert(Alert.AlertType.INFORMATION, "Operation in progress", "Attempting to connect to server", "Please wait...", true, () -> {
//                // connect can sometimes block while trying to resolve the hostname, so we do a null check
//                // will only happen with very slow connections (ping >= 1000ms)
//                if (cf.get() == null)
//                    return false;
//                if (client.proxyEnabled())
//                    return cf.get().isDone() && client.proxyConnectionAttempted();
//                return cf.get().isDone();
//            }, window);
//            cf.set(client.connect());
//            cf.get().awaitUninterruptibly(); // wait for connect to finish
//            if (cf.get().isSuccess()) {
//                UIUtil.showAlert(Alert.AlertType.INFORMATION, "Operation completed successfully", "Successfully connected to server", "Success", false, null, window);
//            } else {
//                UIUtil.showError("Operation failed", client.getCloseReason(), "Failed to connect to server", window);
//            }
//        }).start();
        BooleanProperty isDone = new SimpleBooleanProperty(false);
        BooleanProperty onError = new SimpleBooleanProperty(false);
        StringProperty completionText = new SimpleStringProperty();
        statusHandler.offerOperation("Connecting " + client.getName().toLowerCase() + "to the server", completionText, isDone, onError);
        new Thread(() -> {
            ChannelFuture cf = client.connect();
            cf.awaitUninterruptibly();
            if (cf.isSuccess()) {
                completionText.set(client.getName() + " successfully connected to the server");
                isDone.set(true);
            } else {
                completionText.set(client.getName() + " failed to connect to the server");
                onError.set(true);
                isDone.set(true);
            }
        }).start();
    }

    private void disconnectClient(SimpleNetworkClient client) {
        client.disconnect();
    }

    /**
     * Checks to see if the given network client disconnected unexpectedly, if so it tries to automatically reconnect
     */
    private void handleDisconnect(SimpleNetworkClient client) {
        if (client.isUnexpectedClose()) {
            UIUtil.runOnJFXThread(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Network client lost connection to server, trying to reconnect. Press cancel to stop trying", ButtonType.CANCEL);
                alert.setTitle("Lost connection");
                alert.setHeaderText("Trying to reconnect in ");
                alert.initOwner(window);
                new Thread(() -> {
                    while (!alert.isShowing()) {
                        // Ensure alert is showing before trying reconnect
                    }
                    ChannelFuture cf;
                    int tries = 1;
                    while (alert.isShowing()) { // Cancel reconnect attempt if user closes alert window
                        if (client.isConnectionActive()) {
                            // make sure not already connected before retrying
                            break;
                        }
                        // wait for async connect method to finish
                        cf = client.connect().awaitUninterruptibly();
                        if (cf.isSuccess()) {
                            // reconnect successful
                            break;
                        }
                        // Countdown animation until next attempt
                        AtomicInteger time = new AtomicInteger(tries);
                        Label timerLabel = new Label(time.toString());
                        Timeline timeline = new Timeline();
                        timeline.setCycleCount(Timeline.INDEFINITE);
                        timeline.getKeyFrames().add(
                                new KeyFrame(Duration.seconds(1), event -> {
                                    time.decrementAndGet();
                                    timerLabel.setText(time.toString());
                                    if (time.get() <= 0)
                                        timeline.stop();
                                }));
                        UIUtil.runOnJFXThread(() -> alert.setGraphic(timerLabel));
                        timeline.playFromStart();
                        try {
                            // wait for twice the amount of time before retrying reconnect attempt
                            Thread.sleep(tries * 1000);
                        } catch (InterruptedException ignored) {
                        }
                        tries *= 2;
                    }
                    UIUtil.runOnJFXThread(alert::close);
                }).start();
                alert.showAndWait();
            });
        }
    }

    private String currentAxis;

    /**
     * @param axis      A string containing either "X", "Y", "Z", or "A"
     * @param direction either 1 or -1 indicating the direction to move the mill
     */
    private void jogMill(String axis, int direction) {
        if (!axis.equals(currentAxis)) {
            currentAxis = axis;
            networkClient.setAxis(axis);
        }
        networkClient.jogMill(direction);
    }

    /**
     * setSpeed will round given speeds to the nearest whole number
     */
    private void setSpeed(double value) {
        networkClient.setSpeed((int) Math.round(value));
    }

    private void bindVideoTo(Pane pane) {
        videoView.fitHeightProperty().unbind();
        videoView.fitHeightProperty().unbind();
        videoView.fitWidthProperty().bind(pane.widthProperty());
        videoView.fitHeightProperty().bind(pane.heightProperty());
    }
}
