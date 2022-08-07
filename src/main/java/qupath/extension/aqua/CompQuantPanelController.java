package qupath.extension.aqua;

import com.google.common.eventbus.EventBus;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.objects.classes.PathClass;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.UnaryOperator;

public class CompQuantPanelController implements Initializable{

	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(CompQuantPanelController.class);

	// this bus is used application wide
	private EventBus appEventBus = new EventBus();

	private final QuPathGUI qupath;

	public LinkedHashMap<ColorTransform, Double> availableTransforms = new LinkedHashMap<ColorTransform, Double>();

	//will be in settings menu
	private String[] ignoreClasses = {"Ignore*, Necrosis", "Other"};

	//default params
	private int defaultGridSize = 512;
	private ObjectProperty<Integer> gridSize = new SimpleObjectProperty(defaultGridSize);

	@FXML
	Menu settingsMenu;
	@FXML
	Menu helpMenu;
	@FXML
	ComboBox<String> slideTypeComboBox;
	private final String[] slideTypes = {"TMA", "WTS"};
	private ReadOnlyObjectProperty<String> selectedSlideType;
	@FXML
	ComboBox<String> stainComboBox;
	private final String[] stainTypes = {"Fluorescence", "DAB"};
	private ReadOnlyObjectProperty<String> selectedStainType;
	@FXML
	ComboBox<String> sourceComboBox;
	private final String[] compartmentSources = {"Annotations", "Detections", "Cells"};
	private ReadOnlyObjectProperty<String> selectedSource;
	@FXML
	ScrollPane compartmentScrollPane;
	@FXML
	ListView<PathClass> compartmentListView;
	@FXML
	ScrollPane targetScrollPane;
	@FXML
	ListView<ColorTransform> targetListView;
	@FXML
	ComboBox<String> resultTypeComboBox;
	private final String[] resultTypesTMA = {"TMA + ROIs", "Grids + ROIs", "TMA + Grids + ROIs", "TMA only", "Grids only", "ROIs only"};
	private final String[] resultTypesWTS = {"Grids + ROIs", "Grids only", "ROIs only"};
	private ReadOnlyObjectProperty<String> selectedResultType;
	@FXML
	Button startQuantButton;
	@FXML
	Button cancelButton;
	@FXML
	TextField gridSizeTextField;
	@FXML
	Label gridSizeLabel;
	@FXML
	Label progressLabel;
	@FXML
	ProgressBar quantProgressBar;


	public CompQuantPanelController(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		appEventBus.register(this);
		setupComboBoxes();
		setupListViews();
		setupTextFields();
		startQuantButton.setOnAction(this::startQuant);
		cancelButton.setOnAction(this::cancelQuant);
		updateGUI();
//		initObservables();
	}

	private void setupComboBoxes(){
		slideTypeComboBox.getItems().addAll(slideTypes);
		slideTypeComboBox.setOnAction(this::updateResultTypes);
		selectedSlideType = slideTypeComboBox.getSelectionModel().selectedItemProperty();
		selectedSlideType.addListener((v, o, n) -> updateGUI());

		stainComboBox.getItems().addAll(stainTypes);
		selectedStainType = stainComboBox.getSelectionModel().selectedItemProperty();
		selectedStainType.addListener((v, o, n) -> updateGUI());

		sourceComboBox.getItems().addAll(compartmentSources);
		selectedSource = sourceComboBox.getSelectionModel().selectedItemProperty();
		selectedSource.addListener((v, o, n) -> updateGUI());

		selectedResultType = resultTypeComboBox.getSelectionModel().selectedItemProperty();
		selectedResultType.addListener((v, o, n) -> updateGUI());
	}

	private void setupListViews() {
		compartmentListView.setCellFactory(CheckBoxListCell.forListView(new Callback<PathClass, ObservableValue<Boolean>>() {
			@Override
			public ObservableValue<Boolean> call(PathClass item) {
				BooleanProperty observable = new SimpleBooleanProperty();
				observable.addListener((obs, wasSelected, isNowSelected) ->
						System.out.println("Check box for " + item + " changed from " + wasSelected + " to " + isNowSelected)
				);
				return observable;
			}
		}));

		targetListView.setCellFactory(CheckBoxListCell.forListView(new Callback<ColorTransform, ObservableValue<Boolean>>() {
			@Override
			public ObservableValue<Boolean> call(ColorTransform item) {
				BooleanProperty observable = new SimpleBooleanProperty();
				observable.addListener((obs, wasSelected, isNowSelected) ->
						System.out.println("Check box for " + item + " changed from " + wasSelected + " to " + isNowSelected)
				);
				return observable;
			}
		}));
	}

	private void setupTextFields(){
		UnaryOperator<TextFormatter.Change> intFilter = change -> {
			String newText = change.getControlNewText();
			if (newText.matches("^\\d{0,4}$|^$")) {
				return change;
			}
			return null;
		};

		StringConverter<Integer> intConverter = new IntegerStringConverter() {
			@Override
			public Integer fromString(String s) {
				if (s.isEmpty()) return 0 ;
				else if(Integer.parseInt(s) == 0.0) return 0;
				return super.fromString(s);
			}
		};

		TextFormatter<Integer> textIntFormatter =
				new TextFormatter<Integer>(intConverter, defaultGridSize, intFilter);

		gridSizeTextField.setTextFormatter(textIntFormatter);
		gridSizeTextField.textProperty().bindBidirectional(gridSize, new IntegerStringConverter());
		gridSizeTextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent ke) {
				if (ke.getCode().equals(KeyCode.ENTER)) {
					logger.info("gridSize property: " + gridSize.getValue());
					logger.info("textfield property: " + gridSizeTextField.getText());
				}
			}
		});
		gridSizeTextField.focusedProperty().addListener((ov, oldV, newV) -> {
			if (!newV) { // focus lost
				logger.info("gridSize property: " + gridSize.getValue());
				logger.info("textfield property: " + gridSizeTextField.getText());
			}
		});
	}

//	private void initObservables() {
//
//	}

	public void updateResultTypes(ActionEvent event){
		resultTypeComboBox.valueProperty().set(null);
		resultTypeComboBox.getItems().clear();
		String currentSlideType = slideTypeComboBox.getValue();
		if (Objects.equals(currentSlideType, "TMA")){
			resultTypeComboBox.getItems().addAll(resultTypesTMA);
		} else {
			resultTypeComboBox.getItems().addAll(resultTypesWTS);
		}
	}

	public void updateGUI(){
		logger.info("updating GUI...");
		var viewer = qupath.getViewer();
		var imageData = viewer.getImageData();

		compartmentListView.setItems(qupath.getAvailablePathClasses());
		if (imageData == null) {
			targetListView.getItems().clear();
			targetListView.setDisable(true);
			startQuantButton.setDisable(true);
			return;
		}
		targetListView.setDisable(false);
		// Set the transforms if we have to
		var newTransforms = new ArrayList<>(getAvailableTransforms(imageData));
		if (!newTransforms.equals(targetListView.getItems()))
			targetListView.getItems().setAll(newTransforms);

		String slide = selectedSlideType.get();
		String stain = selectedStainType.get();
		String source = selectedSource.get();
		String result = selectedResultType.get();
		//check if something is selected for compartments and targets....
		if(slide == null || stain == null || source == null || result == null) {
			startQuantButton.setDisable(true);
		} else {
			startQuantButton.setDisable(false);
		}

		if(result != null && result.toLowerCase().contains("grid")){
			gridSizeTextField.setDisable(false);
			gridSizeLabel.setDisable(false);
		} else {
			gridSizeTextField.setDisable(true);
			gridSizeLabel.setDisable(true);
		}
	}

	/**
	 * Get a list of relevant color transforms for a specific image.
	 * @param imageData
	 * @return
	 */
	public Collection<ColorTransform> getAvailableTransforms(ImageData<BufferedImage> imageData) {
		var validChannels = new LinkedHashMap<ColorTransform, Double>();
		var server = imageData.getServer();
		double increment = server.getPixelType().isFloatingPoint() ? 0.1 : 0.5;
		double incrementDeconvolved = 0.05;

		for (var channel : server.getMetadata().getChannels()) {
			validChannels.put(ColorTransforms.createChannelExtractor(channel.getName()), increment);
		}
		var stains = imageData.getColorDeconvolutionStains();
		if (stains != null) {
			validChannels.put(ColorTransforms.createColorDeconvolvedChannel(stains, 1), incrementDeconvolved);
			validChannels.put(ColorTransforms.createColorDeconvolvedChannel(stains, 2), incrementDeconvolved);
			validChannels.put(ColorTransforms.createColorDeconvolvedChannel(stains, 3), incrementDeconvolved);
		}
//		if (server.nChannels() > 1) {
//			validChannels.put(ColorTransforms.createMeanChannelTransform(), increment);
//			validChannels.put(ColorTransforms.createMaximumChannelTransform(), increment);
//			validChannels.put(ColorTransforms.createMinimumChannelTransform(), increment);
//		}
		this.availableTransforms = validChannels;
		return validChannels.keySet();
	}

	
	//Utility methods

	
	//Main panel and button commands
	public void startQuant(ActionEvent e){

	}

	public void cancelQuant(ActionEvent e){

	}
	
	public void advancedSettings(ActionEvent e) {
		logger.info("Opening advanced settings panel...");
	}
	
	public void helpButton(ActionEvent e) {
		logger.info("Opening help dialog...");
	}
	
	public void exportMeasurements(ActionEvent e) {
		logger.info("Opening dialog to export measurements for project...");
	}
	//Overload these methods depending on input arguments. Export data dialog may just run these commands in isolation

	public void exportMasksButton(ActionEvent e) {
		logger.info("Opening dialog to export masks for project...");
	}
	
	//Overload these methods depending on input arguments. Export data dialog may just run these commands in isolation
	public void exportMasks(String outputFileDirectory) {
		
	}
	
}
