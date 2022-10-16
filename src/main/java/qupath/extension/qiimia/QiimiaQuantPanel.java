package qupath.extension.qiimia;

import javafx.scene.Parent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;


public class QiimiaQuantPanel implements Runnable{

	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(QiimiaQuantPanel.class);

	private final QuPathGUI qupath;
	private Stage stage;
	private Parent panel;

	private QiimiaQuantPanelController qiimiaQuantPanelController;
	private SceneManager sceneManager;

	public QiimiaQuantPanel(final QuPathGUI qupath) {
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
			
			stage.setTitle("Qiimia Quant");
//			try {
			logger.info("Starting Qiimia Quant panel...");
			stage.setMinHeight(350);
			stage.setMinWidth(300);
			stage.setMaxWidth(800);

			sceneManager = new SceneManager(stage);
			sceneManager.preloadScene("/QiimiaQuantPanel.fxml", new QiimiaQuantPanelController(qupath));
			sceneManager.setCSSStyle("/QiimiaQuantPanel.fxml", "/application.css");
			sceneManager.preloadScene("/QiimiaAnalysisPanel.fxml", new QiimiaAnalysisPanelController(qupath));
			sceneManager.setCSSStyle("/QiimiaAnalysisPanel.fxml", "/application.css");

			sceneManager.switchScene("/QiimiaQuantPanel.fxml");

//				FXMLLoader loader = new FXMLLoader(getClass().getResource("/QiimiaQuantPanel.fxml"));
//				loader.setControllerFactory(controllerClass -> new QiimiaQuantPanelController(qupath));
//				Parent panel = loader.load();
//				this.qiimiaQuantPanelController = loader.getController();
//				Scene scene = new Scene(panel);
//				stage.setScene(scene);
			// Closing the dialog
			stage.setOnCloseRequest(e -> {
				resetPanel();
				return;
			});

			stage.show();
				
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			
		} else {
			// update GUI based on changes to color transforms, path classes, etc.
			qiimiaQuantPanelController.updateGUI(true);
			if (stage.isShowing())
				stage.toFront();
		}
		
	}
	
	private void resetPanel () {
		logger.info("Closing Qiimia Quant...");
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
