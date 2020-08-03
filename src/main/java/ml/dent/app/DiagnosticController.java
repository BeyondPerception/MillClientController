package ml.dent.app;

import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
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

    private XYChart.Series<Number, Number> pingSeries;
    private XYChart.Series<Number, Number> bitrateSeries;

    @FXML
    public void initialize() {
        pingSeries = new XYChart.Series<>();
        pingChart.getData().add(pingSeries);

        networkClient.millAccessProperty().addListener((obv, oldVal, newVal) -> UIUtil.runOnJFXThread(() -> {
            pingSeries = new XYChart.Series<>();
            pingChart.getData().add(pingSeries);
        }));

        networkClient.lastPingTimeProperty().addListener((obv, oldVal, newVal) -> UIUtil.runOnJFXThread(() -> {
            if (startTime == 0) {
                startTime = System.currentTimeMillis();
            }
            lastPingLabel.setText(networkClient.lastPingTimeProperty().getValue().toString() + "ms");
            XYChart.Data<Number, Number> dataPoint = new XYChart.Data<>((System.currentTimeMillis() - startTime) / 1000, newVal);
//                StackPane valueDisplay = new StackPane();
//                Label dataValue = new Label(dataPoint.getYValue().toString());
//                valueDisplay.setOnMouseEntered(event -> {
//                    valueDisplay.getChildren().add(dataValue);
//                });
//                valueDisplay.setOnMouseExited(event -> {
//                    valueDisplay.getChildren().clear();
//                });
//                dataPoint.setNode(new StackPane());
            pingSeries.getData().add(dataPoint);
        }));

        bitrateSeries = new XYChart.Series<>();
        bitrateChart.getData().add(bitrateSeries);
        videoClient.bitrateProperty().addListener((obv, oldVal, newVal) -> UIUtil.runOnJFXThread(() -> {
            double bitrate = newVal.doubleValue() / 1000.0;
            bitrateLabel.setText(String.format("%.2f", bitrate) + "kbit/s");
            XYChart.Data<Number, Number> dataPoint = new XYChart.Data<>((System.currentTimeMillis() - startTime) / 1000, bitrate);
//                StackPane valueDisplay = new StackPane();
//                Label dataValue = new Label(dataPoint.getYValue().toString());
//                valueDisplay.setOnMouseEntered(event -> {
//                    valueDisplay.getChildren().add(dataValue);
//                });
//                valueDisplay.setOnMouseExited(event -> {
//                    valueDisplay.getChildren().clear();
//                });
//                dataPoint.setNode(new StackPane());
            bitrateSeries.getData().add(dataPoint);
        }));
    }

    @FXML
    protected void clearPingChart() {
        pingChart.getData().clear();
        pingSeries = new XYChart.Series<>();
        pingChart.getData().add(pingSeries);
    }

    @FXML
    protected void clearBitrateChart() {
        bitrateChart.getData().clear();
        bitrateSeries = new XYChart.Series<>();
        bitrateChart.getData().add(bitrateSeries);
    }
}