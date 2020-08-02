package ml.dent.app;

import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import ml.dent.net.ControllerNetworkClient;
import ml.dent.util.UIUtil;
import ml.dent.video.VideoClient;

public class DiagnosticController {

    private ControllerNetworkClient networkClient;
    private VideoClient             videoClient;

    public DiagnosticController(ControllerNetworkClient networkClient, VideoClient videoClient) {
        this.networkClient = networkClient;
        this.videoClient = videoClient;
    }


    @FXML private Label lastPingLabel;
    @FXML private Label bitrateLabel;

    @FXML private LineChart<Number, Number> pingChart;
    @FXML private LineChart<Number, Number> bitrateChart;

    private long startTime = 0;

    @FXML
    public void initialize() {
        startTime = System.currentTimeMillis();
        XYChart.Series<Number, Number> pingSeries = new XYChart.Series<>();
        pingChart.getData().add(pingSeries);

        networkClient.lastPingTimeProperty().addListener((obv, oldVal, newVal) -> {
            UIUtil.runOnJFXThread(() -> {
                lastPingLabel.setText(networkClient.lastPingTimeProperty().getValue().toString() + "ms");
                XYChart.Data<Number, Number> dataPoint = new XYChart.Data<>(System.currentTimeMillis() - startTime, newVal);
                StackPane valueDisplay = new StackPane();
                Label dataValue = new Label(dataPoint.getYValue().toString());
                valueDisplay.setOnMouseEntered(event -> {
                    valueDisplay.getChildren().add(dataValue);
                });
                valueDisplay.setOnMouseExited(event -> {
                    valueDisplay.getChildren().clear();
                });
                dataPoint.setNode(new StackPane());
                pingSeries.getData().add(dataPoint);
            });
        });
    }
}
