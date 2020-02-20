package ml.dent.app;

import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

public class AlertBox {
	public static void alert(String title, String message, int width, int height) {
		Stage window = new Stage();

		window.initModality(Modality.APPLICATION_MODAL);
		window.setTitle(title);
		window.setMinHeight(height);
		window.setMinWidth(width);

		Label label = new Label();
		label.setText(message);

		Button closeButton = new Button("OK");
		closeButton.setOnAction(event -> {
			window.close();
		});

		VBox layout = new VBox();
		layout.getChildren().addAll(label, closeButton);
		layout.setAlignment(Pos.CENTER);

		Scene scene = new Scene(layout);
		window.setScene(scene);
		window.showAndWait();
	}
}
