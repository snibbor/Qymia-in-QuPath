package qupath.extension.qiima;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;

import java.io.IOException;


public class QIIMAQuantPanel implements Runnable{

	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(QIIMAQuantPanel.class);

	private final QuPathGUI qupath;
	private Stage stage;
	private Parent panel;

	private QIIMAQuantPanelController qiimaQuantPanelController;

	public QIIMAQuantPanel(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
//		if (qupath.getImageData() == null) {
//			Dialogs.showNoImageError("QIIMAQuant Panel");
//			return;
//		}
		if (stage == null) {
			stage = new Stage();
			if (qupath != null)
				stage.initOwner(qupath.getStage());
			
			stage.setTitle("QIIMA-Quant");
			try {
				logger.info("Starting QIIMA-Quant panel...");
				FXMLLoader loader = new FXMLLoader(getClass().getResource("/QIIMAQuantPanel.fxml"));
				loader.setControllerFactory(controllerClass -> new QIIMAQuantPanelController(qupath));
				Parent panel = loader.load();
				this.qiimaQuantPanelController = loader.getController();
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
			qiimaQuantPanelController.updateGUI(true);
			if (stage.isShowing())
				stage.toFront();
		}
		
	}
	
	private void resetPanel () {
		logger.info("Closing QIIMA-Quant...");
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
