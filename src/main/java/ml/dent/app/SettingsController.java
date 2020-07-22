package ml.dent.app;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ml.dent.net.ControllerNetworkClient;
import ml.dent.util.UIUtil;
import ml.dent.video.VideoClient;

import java.io.IOException;


public class SettingsController {
    private ControllerNetworkClient networkClient;
    private VideoClient             videoClient;

    private Stage parent;

    public SettingsController(ControllerNetworkClient networkClient, VideoClient videoClient, Stage parent) {
        this.networkClient = networkClient;
        this.videoClient = videoClient;
        this.parent = parent;
    }

    @FXML private SplitPane splitPane;
    @FXML private Label     currentViewName;

    // Network Settings
    @FXML private TextField hostInput;
    @FXML private TextField portInput;
    @FXML private CheckBox  enableSSL;
    @FXML private CheckBox  enableProxy;
    @FXML private TextField internalPortInput;

    @FXML
    public void initialize() {
    }

    @FXML
    protected boolean saveSettings() {
        String settingsView = currentViewName.getText();
        System.out.println(settingsView);
        boolean success = false;
        switch (settingsView) {
            case "network":
                success = configureNetworkSettings(hostInput.getText().trim(), portInput.getText().trim(), enableSSL.isSelected(), enableProxy.isSelected(), internalPortInput.getText().trim());
                break;
            case "video":
                // configure video
                break;
            case "file":
                // configure file stuff
                break;
        }
        return success;
    }

    @FXML
    protected void closeSettingsWindow() {
        parent.close();
    }

    @FXML
    protected void saveAndClose() {
        boolean success = saveSettings();
        if (success) {
            closeSettingsWindow();
        }
    }

    @FXML
    protected void switchToNetworkSettingsView() {
        FXMLLoader networkSettingsLoader = new FXMLLoader(getClass().getResource("/NetworkSettings.fxml"));
        networkSettingsLoader.setController(this);
        try {
            Parent view = networkSettingsLoader.load();
            hostInput.setText(networkClient.getHost());
            portInput.setText(Integer.toString(networkClient.getPort()));
            enableSSL.setSelected(networkClient.sslEnabled());
            enableProxy.setSelected(networkClient.proxyEnabled());
            internalPortInput.disableProperty().bind(enableProxy.selectedProperty().not());
            splitPane.getItems().set(1, view);
        } catch (IOException e) {
            e.printStackTrace();
            UIUtil.showError("GUI Error", e.getMessage(), "Failed to load network settings view", parent);
        }
    }

    private boolean configureNetworkSettings(String host, String port, boolean ssl, boolean proxy, String internalPort) {
        try {
            setHost(host);
            setPort(port);
            enableSSL(ssl);
            enableProxy(proxy, internalPort);
        } catch (IllegalArgumentException e) {
            UIUtil.showError("Error", e.getMessage(), "Failed to save network settings", parent);
            return false;
        }
        return true;
    }

    private void setHost(String hostname) throws IllegalArgumentException {
        if (hostname.isEmpty()) {
            throw new IllegalArgumentException("Hostname must not be empty");
        }
        networkClient.setHost(hostname);
        videoClient.setHost(hostname);
    }

    private void setPort(String port) throws IllegalArgumentException {
        if (!port.matches("[0-9]+")) {
            throw new IllegalArgumentException("Port must be a whole number");
        }
        int newPort = Integer.parseInt(port);
        if (newPort < 1 || newPort > 65535) {
            throw new IllegalArgumentException("Internal port must be between 1 and 65535");
        }
        networkClient.setPort(newPort);
        videoClient.setPort(newPort);
    }

    private void enableSSL(boolean value) {
        networkClient.enableSSL(value);
        videoClient.enableSSL(value);
    }

    private void enableProxy(boolean value, String internalPort) throws IllegalArgumentException {
        if (value) {
            if (!internalPort.matches("[0-9]+")) {
                throw new IllegalArgumentException("Internal port must be a whole number");
            }
            int newInternalPort = Integer.parseInt(internalPort);
            if (newInternalPort < 1 || newInternalPort > 65535) {
                throw new IllegalArgumentException("Internal port must be between 1 and 65535");
            }

            networkClient.setInternalPort(newInternalPort);
            videoClient.setInternalPort(newInternalPort);
        }
        networkClient.enableProxy(value);
        networkClient.enableProxy(value);
    }
}
