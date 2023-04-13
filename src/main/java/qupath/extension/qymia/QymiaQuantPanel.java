package qupath.extension.qymia;

import javafx.scene.Parent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;

import java.util.Objects;


public class QymiaQuantPanel implements Runnable{

	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(QymiaQuantPanel.class);

	private final QuPathGUI qupath;
	private Stage stage;
	private Parent panel;

//	private QymiaQuantPanelController qymiaQuantPanelController;
	private SceneManager sceneManager;
	private String firstWindow;

	public QymiaQuantPanel(final QuPathGUI qupath, String firstWindow) {
		this.qupath = qupath;
		this.firstWindow = firstWindow;
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
			
			stage.setTitle("Qymia Quant");
//			try {
			logger.info("Starting Qymia Quant panel...");
			stage.setMinHeight(300);
			stage.setMinWidth(300);
			stage.setMaxWidth(800);

			sceneManager = new SceneManager(stage);
			sceneManager.preloadScene("/QymiaQuantPanel.fxml", new QymiaQuantPanelController(qupath));
			sceneManager.setCSSStyle("/QymiaQuantPanel.fxml", "/application.css");
			sceneManager.preloadScene("/QymiaPresetPanel.fxml", new QymiaPresetPanelController(qupath));
			sceneManager.setCSSStyle("/QymiaPresetPanel.fxml", "/application.css");
			sceneManager.preloadScene("/QymiaAnalysisPanel.fxml", new QymiaAnalysisPanelController(qupath));
			sceneManager.setCSSStyle("/QymiaAnalysisPanel.fxml", "/application.css");

			if(Objects.equals(firstWindow, "preset")) {
				sceneManager.switchScene("/QymiaPresetPanel.fxml");
			} else{
				sceneManager.switchScene("/QymiaQuantPanel.fxml");
			}
//				FXMLLoader loader = new FXMLLoader(getClass().getResource("/QymiaQuantPanel.fxml"));
//				loader.setControllerFactory(controllerClass -> new QymiaQuantPanelController(qupath));
//				Parent panel = loader.load();
//				this.qymiaQuantPanelController = loader.getController();
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
//			qymiaQuantPanelController.updateGUI(true);
			if (stage.isShowing())
				stage.toFront();
		}
		
	}
	
	private void resetPanel () {
		logger.info("Closing Qymia Quant...");
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
