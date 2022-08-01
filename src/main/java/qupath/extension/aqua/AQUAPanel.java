package qupath.extension.aqua;

import qupath.lib.gui.QuPathGUI;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AQUAPanel implements Runnable{
	
	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(AQUAPanel.class);

	private final QuPathGUI qupath;
	private Stage stage;
	private Parent panel;
	
	public AQUAPanel (final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		if (stage == null) {
			stage = new Stage();
			if (qupath != null)
				stage.initOwner(qupath.getStage());
			
			stage.setTitle("AQUAnalysis in QuPath");
			try {
				logger.info("Starting AQUAnalysis panel...");
//				Parent panel = FXMLLoader.load(getClass().getResource("AQUAPanel.fxml"));
				FXMLLoader loader = new FXMLLoader(getClass().getResource("/AQUAPanel.fxml"));
				loader.setControllerFactory(controllerClass -> new AQUAPanelController());
				Parent panel = loader.load();
				Scene scene = new Scene(panel);
				scene.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());
				stage.setScene(scene);
				stage.setMinHeight(210);
				stage.setMinWidth(300);
				stage.setMaxWidth(500);
				
				// Closing the dialog
				stage.setOnCloseRequest(e -> {
					resetPanel();
					return;
				});
				
				stage.show();			
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
	}
	
	private void resetPanel () {
		logger.info("Closing AQUAnalysis...");
		if (stage == null)
			return;
//		qupath.removeImageDataChangeListener(panel);
//		panel.closePanel(); // Removes all listeners
		
		if (stage != null) 
			stage.setOnCloseRequest(null);
		stage = null;
		panel = null;
		logger.debug("panel Object:");
		logger.debug(String.valueOf(panel));
		logger.debug("stage Object:");
		logger.debug(String.valueOf(stage));
	}

}
