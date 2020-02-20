package ml.dent.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import ml.dent.net.ControllerNetworkClient;
import ml.dent.video.VideoClient;

public class Main extends Application {

	private String	hostname;
	private int		port;
	private int		internalPort;
	private boolean	enableSSL;
	private boolean	enableProxy;

	private int		speed;
	private boolean	buttonPressed;

	@Override
	public void start(Stage primaryStage) throws Exception {
		hostname = "bounceserver.ml";
		port = 443;
		internalPort = 1111;
		enableSSL = true;
		enableProxy = true;

		ControllerNetworkClient client = new ControllerNetworkClient(hostname, port);
		client.enableSSL(enableSSL);
		client.enableProxy(enableProxy);
		client.setInternalPort(internalPort);

		VideoClient videoClient = new VideoClient(hostname, port);
		videoClient.enableSSL(enableSSL);
		videoClient.enableProxy(enableProxy);
		videoClient.setInternalPort(internalPort);

		TextArea messages = new TextArea();
		messages = new TextArea();
		messages.setEditable(false);
		messages.setWrapText(true);
		GridPane.setConstraints(messages, 0, 2);

		Stage window = primaryStage;

		window.setOnCloseRequest(event -> {
			event.consume();
			window.close();
			client.disconnect();
			while (client.isConnectionActive())
				;
			videoClient.stopVideo();
			videoClient.disconnect();
			while (videoClient.isConnectionActive())
				;
			System.exit(0);
		});

		BorderPane root = new BorderPane();

		/** TOP MENU BAR */
		MenuItem connectionSettings = new MenuItem("Settings...");
		connectionSettings.setOnAction(event -> {
			Stage box = new Stage();

			box.initModality(Modality.APPLICATION_MODAL);
			box.setTitle("Connection Settings");

			GridPane grid = new GridPane();
			grid.setPadding(new Insets(10, 10, 10, 10));
			grid.setVgap(10);
			grid.setHgap(10);

			Label hostLabel = new Label("Server Host:");
			GridPane.setConstraints(hostLabel, 0, 0);

			TextField hostInput = new TextField(this.hostname);
			GridPane.setConstraints(hostInput, 1, 0);

			Label portLabel = new Label("Port:");
			GridPane.setConstraints(portLabel, 0, 1);

			TextField portInput = new TextField(Integer.toString(this.port));
			GridPane.setConstraints(portInput, 1, 1);

			CheckBox enableSsl = new CheckBox();
			GridPane.setConstraints(enableSsl, 1, 2);
			Label sslLabel = new Label("Enable SSL");
			GridPane.setConstraints(sslLabel, 0, 2);
			enableSsl.setOnAction(checkEvent -> {
				if (enableSsl.isSelected()) {
					this.enableSSL = true;
					client.enableSSL(this.enableSSL);
					videoClient.enableSSL(this.enableSSL);
				} else {
					this.enableSSL = false;
					client.enableSSL(this.enableSSL);
					videoClient.enableSSL(this.enableSSL);
				}
			});
			if (this.enableSSL) {
				enableSsl.fire();
			}

			Label internalPortLabel = new Label("Internal Port");
			GridPane.setConstraints(internalPortLabel, 0, 4);

			TextField internalPortInput = new TextField(Integer.toString(this.internalPort));
			GridPane.setConstraints(internalPortInput, 1, 4);
			internalPortInput.setDisable(true);

			CheckBox enableProxy = new CheckBox();
			GridPane.setConstraints(enableProxy, 1, 3);
			Label proxyLabel = new Label("Enable Http(s) Proxy");
			GridPane.setConstraints(proxyLabel, 0, 3);
			enableProxy.setOnAction(checkEvent -> {
				if (enableProxy.isSelected()) {
					internalPortInput.setDisable(false);
					this.enableProxy = true;
					client.enableProxy(this.enableProxy);
					videoClient.enableProxy(this.enableProxy);
				} else {
					internalPortInput.setDisable(true);
					this.enableProxy = false;
					client.enableProxy(this.enableProxy);
					videoClient.enableProxy(this.enableProxy);
				}
			});
			if (this.enableProxy) {
				enableProxy.fire();
			}

			Button saveButton = new Button("Save");
			saveButton.setOnAction(buttonEvent -> {
				String newHost = hostInput.getText().trim();
				String rawPort = portInput.getText().trim();

				// Validity Checks
				if (newHost.isEmpty()) {
					AlertBox.alert("ERROR", "Host name must not be empty", 250, 100);
					return;
				}
				if (!rawPort.matches("[0-9]+")) {
					AlertBox.alert("ERROR", "Port must be a number", 250, 100);
					return;
				}
				int newPort = Integer.parseInt(rawPort);
				if (newPort < 0 || newPort > 65535) {
					AlertBox.alert("ERROR", "Port must be within rang 1-65535", 250, 100);
					return;
				}

				if (enableProxy.isSelected()) {
					String rawInternalPort = internalPortInput.getText().trim();
					if (!rawInternalPort.matches("[0-9]+")) {
						AlertBox.alert("ERROR", "Port must be a number", 250, 100);
						return;
					}
					int newInternalPort = Integer.parseInt(rawInternalPort);
					if (newInternalPort < 0 || newInternalPort > 65535) {
						AlertBox.alert("ERROR", "Internal port must be within rang 1-65535", 250, 100);
						return;
					}
					this.internalPort = newInternalPort;
					client.setInternalPort(this.internalPort);
					videoClient.setInternalPort(this.internalPort);
				}

				this.hostname = newHost;
				this.port = newPort;
				client.setHost(this.hostname);
				client.setPort(this.port);
				videoClient.setHost(this.hostname);
				videoClient.setPort(this.port);
				box.close();
			});
			GridPane.setConstraints(saveButton, 5, 5);

			grid.getChildren().addAll(hostLabel, hostInput, portLabel, portInput, saveButton, sslLabel, enableSsl,
					proxyLabel, enableProxy, internalPortLabel, internalPortInput);

			Scene curScene = new Scene(grid, 450, 240);
			box.initOwner(window);
			box.setScene(curScene);
			box.show();
		});

		MenuItem closeConnection = new MenuItem("Close Connection");
		closeConnection.setOnAction(event -> {
			client.disconnect();

			if (!client.isConnectionActive()) {
				closeConnection.setDisable(true);
			}
		});
		closeConnection.setDisable(true);

		MenuItem connect = new MenuItem("Connect");
		connect.setOnAction(event -> {
			if (client.isConnectionActive()) {
				Alert alert = new Alert(AlertType.ERROR, "Connection already active", ButtonType.OK);
				alert.setHeaderText("Error");
				alert.initOwner(window);
				alert.showAndWait();
				return;
			}

			Alert alert = new Alert(AlertType.INFORMATION, "Attempting to connect to the server");
			alert.setTitle("Operation in progress");
			alert.setHeaderText("Please wait...");
			ProgressBar progress = new ProgressBar();
			alert.setGraphic(progress);
			alert.setOnShown(e -> {
				while (!client.connectionAttempted() || !client.proxyConnectionAttempted()) {
				}
				alert.close();
			});
			client.connect();
			alert.initOwner(window);
			alert.show();

			Alert response;
			if (client.isConnectionReady()) {
				closeConnection.setDisable(false);
				client.closeFuture().addListener(future -> {
					if (client.isUnexpectedClose()) {
						Platform.runLater(new Runnable() {
							@Override
							public void run() {
								Alert unexpectedDisconnectAlert = new Alert(AlertType.ERROR,
										"Unexpected disconnect from server");
								unexpectedDisconnectAlert.setHeaderText("Lost Connection to Server");
								unexpectedDisconnectAlert.initOwner(window);
								unexpectedDisconnectAlert.showAndWait();
							}
						});
					}
					closeConnection.setDisable(true);
				});
				response = new Alert(AlertType.INFORMATION, "Successfully connected to the server", ButtonType.OK);
				response.setTitle("Operation completed successfully");
				response.setHeaderText("Success");
			} else {
				response = new Alert(AlertType.ERROR, client.getCloseReason(), ButtonType.OK);
				response.setTitle("Operation failed to complete");
				response.setHeaderText("Failed to connect to the server");
			}
			response.initOwner(window);
			response.showAndWait();
		});
		Menu connectionMenu = new Menu("_Connect");
		connectionMenu.getItems().addAll(connectionSettings, new SeparatorMenuItem(), connect, closeConnection);

		MenuItem stopVideo = new MenuItem("Stop Video");
		stopVideo.setOnAction(event -> {
			videoClient.disconnect();
			videoClient.stopVideo();

			if (!videoClient.isConnectionActive()) {
				stopVideo.setDisable(true);
			}
		});
		stopVideo.setDisable(true);

		ImageView videoView = new ImageView();
		GridPane.setConstraints(videoView, 0, 1);

		GridPane videoGrid = new GridPane();
		videoGrid.getChildren().add(videoView);

		MenuItem startVideo = new MenuItem("Start Video");
		startVideo.setOnAction(event -> {
			if (videoClient.isConnectionActive()) {
				Alert alert = new Alert(AlertType.ERROR, "Video already active", ButtonType.OK);
				alert.setHeaderText("Error");
				alert.initOwner(window);
				alert.showAndWait();
				return;
			}

			Alert alert = new Alert(AlertType.INFORMATION, "Readying Video Receiver");
			alert.setTitle("Operation in progress");
			alert.setHeaderText("Please wait...");
			ProgressBar progress = new ProgressBar();
			alert.setGraphic(progress);
			alert.setOnShown(e -> {
				while (!videoClient.connectionAttempted() || !videoClient.proxyConnectionAttempted()) {
				}
				alert.close();
			});
			videoClient.connect();
			alert.initOwner(window);
			alert.show();

			Alert response;
			if (videoClient.isConnectionReady()) {
				stopVideo.setDisable(false);
				videoClient.closeFuture().addListener(future -> {
					if (videoClient.isUnexpectedClose()) {
						Platform.runLater(new Runnable() {
							@Override
							public void run() {
								Alert unexpectedDisconnectAlert = new Alert(AlertType.ERROR,
										"Unexpected stop of video stream");
								unexpectedDisconnectAlert.setHeaderText("Lost Connection to Server");
								unexpectedDisconnectAlert.initOwner(window);
								unexpectedDisconnectAlert.showAndWait();
							}
						});
					}
					stopVideo.setDisable(true);
				});
				videoClient.startVideo(videoView);
				response = new Alert(AlertType.INFORMATION, "Ready to receive video", ButtonType.OK);
				response.setTitle("Operation completed successfully");
				response.setHeaderText("Success");
			} else {
				response = new Alert(AlertType.ERROR, /* videoClient.getCloseReason() */"", ButtonType.OK);
				response.setTitle("Operation failed to complete");
				response.setHeaderText("Failed to ready video");
			}
			response.initOwner(window);
			response.showAndWait();
		});

		Menu videoMenu = new Menu("_Video");
		videoMenu.getItems().addAll(startVideo, stopVideo);

		MenuBar menuBar = new MenuBar();
		menuBar.getMenus().addAll(connectionMenu, videoMenu);

		root.setTop(menuBar);

		GridPane leftCol = new GridPane();
		/** Bottom Controls */

		GridPane bottomGrid = new GridPane();

		HBox jogButtons = new HBox();

		String selectedAxis = "X";

		buttonPressed = false;

		Button jogPlus = new Button("Jog " + selectedAxis + "+");
		jogPlus.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
			buttonPressed = true;
			client.jogMill(0x1);
		});
		jogPlus.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
			if (buttonPressed) {
				buttonPressed = false;
				client.stopMill();
			}
		});
		jogPlus.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
			if (buttonPressed) {
				buttonPressed = false;
				client.stopMill();
			}
		});
		jogPlus.setPrefHeight(350);
		jogPlus.setPrefWidth(566.3118960625);

		Button jogMinus = new Button("Jog " + selectedAxis + "-");
		jogMinus.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
			buttonPressed = true;
			client.jogMill(-0x1);
		});
		jogMinus.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
			if (buttonPressed) {
				buttonPressed = false;
				client.stopMill();
			}
		});
		jogMinus.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
			if (buttonPressed) {
				buttonPressed = false;
				client.stopMill();
			}
		});
		jogMinus.setPrefHeight(350);
		jogMinus.setPrefWidth(566.3118960625);

		HBox speedControlLayer = new HBox();

		speed = 1;

		TextField speedControl = new TextField("" + speed);
		speedControl.setEditable(false);
		speedControl.setMaxSize(50, 50);

		Button incrementSpeed = new Button("Speed+");
		incrementSpeed.setOnAction(event -> {
			if (speed + 1 < 25) {
				speedControl.setText(++speed + "");
				client.setSpeed(speed);
			}
		});
		incrementSpeed.setPadding(new Insets(0, 8, 0, 8));

		Button decrementSpeed = new Button("Speed-");
		decrementSpeed.setOnAction(event -> {
			if (speed - 1 > 0) {
				speedControl.setText(--speed + "");
				client.setSpeed(speed);
			}
		});
		decrementSpeed.setPadding(new Insets(0, 8, 0, 8));

		speedControlLayer.getChildren().addAll(decrementSpeed, speedControl, incrementSpeed);
		GridPane.setConstraints(speedControlLayer, 1, 0);

		jogButtons.getChildren().addAll(jogMinus, jogPlus);
		jogButtons.setSpacing(10);
		GridPane.setConstraints(jogButtons, 1, 2);
		bottomGrid.getChildren().addAll(messages, jogButtons, speedControlLayer);
		bottomGrid.setAlignment(Pos.BASELINE_RIGHT);
		bottomGrid.setPadding(new Insets(10));
		bottomGrid.setHgap(10);
		bottomGrid.setVgap(8);

		/** End Bottom Controls */

		/** Axis Selectors and Message Box */
		VBox axisSelector = new VBox();

		Button yAxisSelector = new Button("Y Axis");
		yAxisSelector.setOnAction(event -> {
			client.setAxis("Y");
			jogPlus.setText("Jog Y+");
			jogMinus.setText("Jog Y-");

		});
		yAxisSelector.setPrefWidth(606);
		yAxisSelector.setPrefHeight(125);

		Button xAxisSelector = new Button("X Axis");
		xAxisSelector.setOnAction(event -> {
			client.setAxis("X");
			jogPlus.setText("Jog X+");
			jogMinus.setText("Jog X-");
		});
		xAxisSelector.setPrefWidth(606);
		xAxisSelector.setPrefHeight(125);

		Button zAxisSelector = new Button("Z Axis");
		zAxisSelector.setOnAction(event -> {
			client.setAxis("Z");
			jogPlus.setText("Jog Z+");
			jogMinus.setText("Jog Z-");
		});
		zAxisSelector.setPrefWidth(606);
		zAxisSelector.setPrefHeight(125);

		Button aAxisSelector = new Button("A Axis");
		aAxisSelector.setOnAction(event -> {
			client.setAxis("A");
			jogPlus.setText("Jog A+");
			jogMinus.setText("Jog A-");
		});
		aAxisSelector.setPrefWidth(606);
		aAxisSelector.setPrefHeight(125);

		axisSelector.getChildren().addAll(xAxisSelector, yAxisSelector, zAxisSelector, aAxisSelector);
		axisSelector.setSpacing(8);

		GridPane.setConstraints(axisSelector, 0, 0);
		leftCol.setVgap(8);
		leftCol.getChildren().addAll(axisSelector);
		leftCol.setPadding(new Insets(10));
		leftCol.setVgap(8);
		/** End Axis Selectors */

		root.setLeft(leftCol);
		root.setTop(menuBar);
		root.setBottom(bottomGrid);
		root.setRight(videoGrid);

		Scene scene = new Scene(root, 400, 400);
		window.setScene(scene);
		window.setTitle("Mill Client Controller");
		window.show();
	}

	public static void main(String[] args) {
		launch(args);
	}
}
