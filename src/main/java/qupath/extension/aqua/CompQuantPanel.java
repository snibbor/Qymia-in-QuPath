package qupath.extension.aqua;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import java.io.IOException;


public class CompQuantPanel implements Runnable{

	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(CompQuantPanel.class);

	private final QuPathGUI qupath;
	private Stage stage;
	private Parent panel;

	private CompQuantPanelController compQuantController;

	public CompQuantPanel(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
//		if (qupath.getImageData() == null) {
//			Dialogs.showNoImageError("CompQuant Panel");
//			return;
//		}
		if (stage == null) {
			stage = new Stage();
			if (qupath != null)
				stage.initOwner(qupath.getStage());
			
			stage.setTitle("CompQuant");
			try {
				logger.info("Starting CompQuant panel...");
//				Parent panel = FXMLLoader.load(getClass().getResource("AQUAPanel.fxml"));
				FXMLLoader loader = new FXMLLoader(getClass().getResource("/CompQuantPanel.fxml"));
				loader.setControllerFactory(controllerClass -> new CompQuantPanelController(qupath));
				Parent panel = loader.load();
				this.compQuantController = loader.getController();
				Scene scene = new Scene(panel);
				scene.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());
				stage.setScene(scene);
				stage.setMinHeight(350);
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
			
		} else {
			// update GUI based on changes to color transforms, path classes, etc.
			compQuantController.updateGUI(true);
			if (stage.isShowing())
				stage.toFront();
		}
		
	}
	
	private void resetPanel () {
		logger.info("Closing CompQuant...");
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
