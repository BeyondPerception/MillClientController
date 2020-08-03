package ml.dent.app;

import io.netty.channel.ChannelFuture;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainController {
    private final Stage window;

    private ControllerNetworkClient networkClient;
    private VideoClient             videoClient;

    private StatusHandler statusHandler;

    public MainController(Stage window) {
        this.window = window;

        window.setOnCloseRequest(event -> {
            System.out.println("Closing window");
            window.close();
            System.out.println("Consuming event");
            event.consume();
            try {
                System.out.println("Disconnecting network client");
                networkClient.disconnect().awaitUninterruptibly();
                System.out.println("Stopping video");
                videoClient.stopVideo();
                while (videoClient.isPlaying())
                    ;
                System.out.println("Disconnecting video client");
                videoClient.disconnect().awaitUninterruptibly();
            } finally {
                System.out.println("Exiting");
                System.exit(0);
            }
        });
    }

    @FXML private boolean enableSSL;
    @FXML private boolean enableProxy;

    @FXML private MenuItem closeConnection;

    @FXML private SplitPane controlWindow;
    @FXML private VBox      millControlsContainer;

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

    @FXML private Button XPlus;
    @FXML private Button XMinus;
    @FXML private Button YPlus;
    @FXML private Button YMinus;
    @FXML private Button ZPlus;
    @FXML private Button ZMinus;
    @FXML private Button APlus;
    @FXML private Button AMinus;

    @FXML private Label helpMenu;

    private HashMap<KeyCode, Button> keymap;

    @FXML
    public void initialize() {
        // Status Handler needs to be initialized before video and network client
        statusHandler = new StatusHandler(leftStatus, rightStatus, window);

        networkClient = new ControllerNetworkClient("bounceserver.tk", 1111);
        videoClient = new VideoClient("bounceserver.tk", 1111);
        networkClient.setName("Network Client");
        videoClient.setName("Video Client");

        /* GUI BINDINGS */
        ProgressIndicator loadingGraphic = new ProgressIndicator();
        loadingGraphic.prefHeightProperty().bind(statusBar.heightProperty().subtract(5));
        rightStatus.setGraphicTextGap(0);
        statusHandler.setupEventLog(eventLogPopup, eventLog, closeEventLog);
        statusHandler.setLoadingGraphic(loadingGraphic);

        SplitPane.setResizableWithParent(controlWindow, false);
        window.showingProperty().addListener((obv, oldVal, newVal) -> {
            controlWindow.setDividerPositions(.2f);
        });

        // if the network client or the video client is active, allow close connection
        BooleanBinding onePlusConnectionActive = networkClient.connectionActiveProperty().not().and(videoClient.connectionActiveProperty().not());
        closeConnection.disableProperty().bind(onePlusConnectionActive);
        networkClient.connectionActiveProperty().addListener((listener, oldVal, newVal) -> {
            if (!networkClient.isConnectionActive()) {
                if (networkClient.isUnexpectedClose()) {
                    handleDisconnect(networkClient);
                }
                statusHandler.offerStatus("Network client disconnected from server", StatusHandler.INFO);
            } else {
                pingMill();
                networkClient.setSpeed((int) speedControl.getValue());
            }
        });
        videoClient.connectionActiveProperty().addListener((listener, oldVal, newVal) -> {
            if (!videoClient.isConnectionActive()) {
                if (videoClient.isUnexpectedClose()) {
                    handleDisconnect(videoClient);
                }
                statusHandler.offerStatus("Video client disconnected from server", StatusHandler.INFO);
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

        millControlsContainer.disableProperty().bind(networkClient.millAccessProperty().not());
        networkClient.millAccessProperty().addListener((obv, oldVal, newVal) -> {
            if ((newVal)) {
                statusHandler.offerStatus("Connection to mill active", StatusHandler.INFO);
            } else {
                statusHandler.offerStatus("Lost connection to mill", StatusHandler.INFO);
            }
        });

        /* Key Bindings */

        keymap = new HashMap<KeyCode, Button>() {
            {
                put(KeyCode.RIGHT, XPlus);
                put(KeyCode.LEFT, XMinus);
                put(KeyCode.UP, YPlus);
                put(KeyCode.DOWN, YMinus);
                put(KeyCode.PAGE_UP, ZPlus);
                put(KeyCode.PAGE_DOWN, ZMinus);
                put(KeyCode.X, APlus);
                put(KeyCode.Z, AMinus);
            }
        };
        setKeymapOnNode(controlWindow);

        helpMenu.setText("Use arrow keys for X and Y axis\n" +
                "X and Z keys for rotation\n" +
                "Page UP/Down for Z axis\n" +
                "Hold ctrl key for speed up");

        ScheduledExecutorService bitrateTimer = new ScheduledThreadPoolExecutor(1);
        bitrateTimer.scheduleAtFixedRate(() -> {

        }, 0, 1, TimeUnit.SECONDS);



        /* GUI BINDINGS */

        videoClient.startVideo(videoView);

        statusHandler.setVerbosity(StatusHandler.INFO);
    }

    @FXML
    protected void connectNetworkClients() {
        connectClient(networkClient);
        connectClient(videoClient);
    }

    @FXML
    protected void disconnectNetworkClients() {
        if (networkClient.isConnectionActive()) {
            networkClient.disconnect();
        }
        if (videoClient.isConnectionActive()) {
            videoClient.disconnect();
        }
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
            root.prefWidthProperty().bind(videoPopout.widthProperty());
            root.prefHeightProperty().bind(videoPopout.heightProperty());
            HBox popoutContainer = new HBox();
            popoutContainer.setAlignment(Pos.CENTER);
            popoutContainer.prefWidthProperty().bind(root.widthProperty());
            popoutContainer.prefHeightProperty().bind(root.heightProperty());
            popoutContainer.getChildren().add(videoView);
            root.getChildren().add(popoutContainer);

            bindVideoTo(popoutContainer);

            videoPopout.setOnCloseRequest(event -> {
                event.consume();
                bindVideoTo(imageContainer);
                imageContainer.getChildren().add(videoView);
                videoPopout.close();
            });

            imageContainer.getChildren().clear();

            setKeymapOnNode(root);

            videoPopout.setScene(new Scene(root));
            videoPopout.show();
            root.requestFocus();
        }
    }

    private Stage eventLogPopout;

    @FXML
    protected void popoutEventLog() {
        if (eventLogPopout == null) {
            eventLogPopout = new Stage();
        }
        if (eventLogPopout.isShowing()) {
            eventLogPopout.fireEvent(new WindowEvent(eventLogPopout, WindowEvent.WINDOW_CLOSE_REQUEST));
        } else {
            AnchorPane root = new AnchorPane();
            eventLog.prefWidthProperty().bind(root.widthProperty());
            eventLog.prefHeightProperty().bind(root.heightProperty());
            root.getChildren().add(eventLog);

            fireMouseEvent(leftStatus, MouseEvent.MOUSE_CLICKED);

            eventLogPopout.setOnCloseRequest(event -> {
                event.consume();
                eventLogPopout.close();
                eventLogPopup.getChildren().add(eventLog);
                fireMouseEvent(leftStatus, MouseEvent.MOUSE_CLICKED);
            });

            eventLogPopout.setScene(new Scene(root));
            eventLogPopout.show();
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
        settingsWindow.showAndWait();
    }


    @FXML
    protected void openDiagnosticWindow() throws IOException {
        Stage diagnosticsWindow = new Stage();
        DiagnosticController diagnosticController = new DiagnosticController(networkClient, videoClient);
        FXMLLoader diagnosticLoader = new FXMLLoader(getClass().getResource("/DiagnosticWindow.fxml"));
        diagnosticLoader.setController(diagnosticController);
        Parent root = diagnosticLoader.load();
        diagnosticsWindow.setTitle("Diagnostics");
        diagnosticsWindow.setScene(new Scene(root));
        diagnosticsWindow.initOwner(window);
        diagnosticsWindow.show();
    }

    private void connectClient(SimpleNetworkClient client) {
        if (client.isConnectionActive()) {
            statusHandler.offerError("Connection already active", "Error");
            return;
        }
        BooleanProperty isDone = new SimpleBooleanProperty(false);
        BooleanProperty onError = new SimpleBooleanProperty(false);
        StringProperty completionText = new SimpleStringProperty();
        statusHandler.offerOperation("Attempting to connect " + client.getName().toLowerCase() + " to the server", completionText, isDone, onError, StatusHandler.INFO);
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

    /**
     * Checks to see if the given network client disconnected unexpectedly, if so it tries to automatically reconnect
     */
    private void handleDisconnect(SimpleNetworkClient client) {
        if (client.isUnexpectedClose()) {
            BooleanProperty isDone = new SimpleBooleanProperty(false);
            StringProperty completionText = new SimpleStringProperty("Successfully reconnected " + client.getName().toLowerCase() + " to the server");
            statusHandler.offerOperation("Lost connection to server, trying to reconnect", completionText, isDone, StatusHandler.INFO);
            UIUtil.runOnJFXThread(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Network client lost connection to server, trying to reconnect. Press cancel to stop trying", ButtonType.CANCEL);
                alert.setTitle("Lost connection");
                alert.setHeaderText("Trying to reconnect in ");
                alert.initOwner(window);
                new Thread(() -> {
                    while (!alert.isShowing()) {
                        // block
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
                            break;
                        }
                        tries *= 2;
                    }
                    UIUtil.runOnJFXThread(alert::close);
                }).start();
                alert.showAndWait();
            });
            isDone.set(true);
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

    private void pingMill() {
        BooleanProperty isDone = new SimpleBooleanProperty(false);
        BooleanProperty onError = new SimpleBooleanProperty(false);
        StringProperty completionText = new SimpleStringProperty("Received response from mill.");
        statusHandler.offerOperation("Waiting for response from mill", completionText, isDone, onError, StatusHandler.INFO);
        new Thread(() -> {
            try {
                // wait 10 seconds for mill to respond. Arbitrary number, seems like a reasonable amt. of time to wait
                boolean recvPing = networkClient.awaitNextPing(1000 * 10);
                if (!recvPing) {
                    onError.set(true);
                    completionText.set("Did not receive response from mill. Terminating connection");
                    disconnectNetworkClients();
                }
                isDone.set(true);
            } catch (InterruptedException e) {
                e.printStackTrace();
                statusHandler.offerStatus("Error: interrupted while waiting for response from mill", StatusHandler.ERROR);
            }
        }).start();
    }

    private void bindVideoTo(Pane pane) {
        videoView.fitHeightProperty().unbind();
        videoView.fitHeightProperty().unbind();
        videoView.fitWidthProperty().bind(pane.widthProperty());
        videoView.fitHeightProperty().bind(pane.heightProperty());
    }

    private void setKeymapOnNode(Node area) {
        AtomicBoolean isKeyDown = new AtomicBoolean();

        area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!networkClient.isConnectionActive()) {
                return;
            }
            KeyCode kc = event.getCode();
            if (kc == KeyCode.CONTROL) {
                speedControl.setValue(Math.min(25, Math.max(speedControl.getValue() * 2, speedControl.getValue() + 5)));
            } else if (keymap.containsKey(kc) && !isKeyDown.get()) {
                event.consume();
                isKeyDown.set(true);
                fireMouseEvent(keymap.get(kc), MouseEvent.MOUSE_PRESSED);
            }
        });

        area.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (!networkClient.isConnectionActive()) {
                return;
            }
            KeyCode kc = event.getCode();
            if (kc == KeyCode.CONTROL) {
                speedControl.setValue(Math.max(1, Math.min(speedControl.getValue() / 2, speedControl.getValue() - 5)));
            } else if (keymap.containsKey(kc) && isKeyDown.get()) {
                event.consume();
                fireMouseEvent(keymap.get(kc), MouseEvent.MOUSE_RELEASED);
                isKeyDown.set(false);
            }
        });
    }

    private void fireMouseEvent(Node node, EventType<MouseEvent> type) {
        Bounds sceneBounds = node.localToScene(node.getBoundsInLocal());
        Bounds screenBounds = node.localToScreen(node.getBoundsInLocal());
        node.fireEvent(new MouseEvent(type,
                sceneBounds.getMinX(),
                sceneBounds.getMinY(),
                screenBounds.getMinX(),
                screenBounds.getMinY(),
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null));
    }
}
