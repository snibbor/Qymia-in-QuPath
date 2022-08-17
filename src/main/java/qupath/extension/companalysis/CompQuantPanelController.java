package qupath.extension.companalysis;

import com.google.common.eventbus.EventBus;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.*;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.imagej.tools.IJTools;
import qupath.imagej.tools.PixelImageIJ;
//import qupath.lib.analysis.features.ObjectMeasurements;
//import qupath.lib.algorithms.IntensityFeaturesPlugin;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.analysis.images.SimpleModifiableImage;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.geom.ImmutableDimension;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.MeasurementExporter;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.*;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.*;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import static qupath.lib.common.Prefs.getNumThreads;
import static qupath.lib.objects.classes.PathClassFactory.getPathClass;
import static qupath.lib.scripting.QP.clearMeasurements;
import static qupath.lib.scripting.QP.getCurrentHierarchy;

import qupath.opencv.ops.ImageOps;
import qupath.opencv.tools.OpenCVTools;

//import java.awt.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
//import java.io.BufferedReader;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
//import java.nio.Buffer;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CompQuantPanelController implements Initializable{

	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(CompQuantPanelController.class);

	// this bus is used application wide
	private final EventBus appEventBus = new EventBus();

	private final QuPathGUI qupath;
//	private QuPathViewerPlus viewer;
//	private ImageData<BufferedImage> imageData;
//	private ImageServer<BufferedImage> server;
//	private PathObjectHierarchy hierarchy;

//	private CompQuantBackend compQuant;

	private final ForkJoinPool startRunFJP = new ForkJoinPool(2);

	private final AtomicReference<Boolean> runCancelled = new AtomicReference<Boolean>(false);

	public LinkedHashMap<ColorTransform, Double> availableTransforms = new LinkedHashMap<>();

	//will be in settings menu

	private final Set<PathClass> ignoreClasses = Set.of(new PathClass[]{getPathClass("Ignore*"),
																		getPathClass("Necrosis"),
																		getPathClass("Other")});
	private final Set<PathClass> roiClasses = Set.of(new PathClass[]{getPathClass("ROI")});

	//default params
	private final int defaultGridSize = 512;
	private final ObjectProperty<Integer> gridSize = new SimpleObjectProperty(defaultGridSize);

	private final ObservableSet<PathClass> selectedCompartments = FXCollections.observableSet();
	// target and exposure time if IF image
	private final ObservableMap<ColorTransform, Double> selectedTargets = FXCollections.observableMap(new LinkedHashMap<>());

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
	private final String[] compartmentSources = {"Annotations", "Cells"};
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
	@FXML
	Button exportMeasButton;
	@FXML
	MenuItem exportMeasMenuItem;
	@FXML
	MenuItem exportMaskMenuItem;
	@FXML
	MenuItem importGridOverlayMenuItem;
	@FXML
	CheckMenuItem measEssentialMenuItem;
	@FXML
	CheckMenuItem measAllMenuItem;
	@FXML
	CheckMenuItem measAnnotMenuItem;
	@FXML
	CheckMenuItem measDetMenuItem;
	@FXML
	CheckMenuItem normalizeMenuItem;
	@FXML
	CheckMenuItem rescaleMenuItem;
	// rescale scores using maxFloatValue and bitdepth
	private double maxFloatValue = 1000.0/4.0;
	private String exportMeasFields = "all";

	FileChooser fileSelector = new FileChooser();
	File initialFileDirectory;


	public CompQuantPanelController(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		appEventBus.register(this);
		setupMenu();
		setupComboBoxes();
		setupListViews();
		exportMeasButton.setOnAction(this::exportImageMeasurementsButton);
		gridSizeTextField = formatTextFields(gridSizeTextField, "integer", String.valueOf(defaultGridSize));
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
		startQuantButton.setOnAction(this::startQuant);
		cancelButton.setOnAction(this::cancelQuant);
		updateGUI(true);
//		initObservables();
	}

	private void setupMenu(){
		exportMeasMenuItem.setOnAction(this::exportAllMeasurementsButton);
		exportMaskMenuItem.setOnAction(this::exportMasksButton);
		measAnnotMenuItem.selectedProperty().set(true);
		measDetMenuItem.selectedProperty().set(true);
		measAllMenuItem.selectedProperty().set(true);
//		measAllMenuItem.selectedProperty().bindBidirectional(measEssentialMenuItem.selectedProperty().not());
		measAllMenuItem.selectedProperty().addListener((obs,old,val)-> {
			measEssentialMenuItem.selectedProperty().set(!val);
			// only need to set once
			if(val)
				exportMeasFields = "all";
			else
				exportMeasFields = "essential";
			logger.info(exportMeasFields);
		});
		measEssentialMenuItem.selectedProperty().addListener((obs,old,val)->measAllMenuItem.selectedProperty().set(!val));
	}

	private void setupComboBoxes(){
		slideTypeComboBox.getItems().addAll(slideTypes);
		slideTypeComboBox.setOnAction(this::updateResultTypes);
		selectedSlideType = slideTypeComboBox.getSelectionModel().selectedItemProperty();
		selectedSlideType.addListener((v, o, n) -> updateGUI(false));

		stainComboBox.getItems().addAll(stainTypes);
		selectedStainType = stainComboBox.getSelectionModel().selectedItemProperty();
		selectedStainType.addListener((v, o, n) -> updateGUI(true));

		sourceComboBox.getItems().addAll(compartmentSources);
		selectedSource = sourceComboBox.getSelectionModel().selectedItemProperty();
		selectedSource.addListener((v, o, n) -> updateGUI(false));

		selectedResultType = resultTypeComboBox.getSelectionModel().selectedItemProperty();
		selectedResultType.addListener((v, o, n) -> updateGUI(false));
	}

//	https://stackoverflow.com/questions/44022381/keep-listview-with-checkboxes-synchronized-with-a-list-of-strings
//	https://stackoverflow.com/questions/28843858/javafx-8-listview-with-checkboxes
//	https://stackoverflow.com/questions/70058805/javafx-using-custom-listview-to-using-checkbox-with-setcellfactory
	private void setupListViews() {
		compartmentListView.setCellFactory(CheckBoxListCell.forListView(new Callback<PathClass, ObservableValue<Boolean>>() {
			@Override
			public ObservableValue<Boolean> call(PathClass item) {
				BooleanProperty observable = new SimpleBooleanProperty();
				observable.addListener((obs, wasSelected, isNowSelected) -> {
					logger.info("Check box for " + item + " changed from " + wasSelected + " to " + isNowSelected);
					if (isNowSelected) {
						selectedCompartments.add(item);
					} else {
						selectedCompartments.remove(item);
					}
					logger.info(selectedCompartments.toString());
					updateGUI(false);
				});

				observable.set(selectedCompartments.contains(item));
				selectedCompartments.addListener((SetChangeListener.Change<? extends PathClass> c) ->
						observable.set(selectedCompartments.contains(item)));

				return observable;
			}
		}));

//		targetListView.setCellFactory(CheckBoxListCell.forListView(new Callback<ColorTransform, ObservableValue<Boolean>>() {
//			@Override
//			public ObservableValue<Boolean> call(ColorTransform item) {
//				BooleanProperty observable = new SimpleBooleanProperty();
//				observable.addListener((obs, wasSelected, isNowSelected) -> {
//					logger.info("Check box for " + item + " changed from " + wasSelected + " to " + isNowSelected);
//					if (isNowSelected) {
//						selectedTargets.put(item, 0.0);
//					} else {
//						selectedTargets.remove(item);
//					}
//					logger.info(selectedTargets.toString());
//				});
//
//				observable.set(selectedTargets.containsKey(item));
//				selectedTargets.addListener((MapChangeListener.Change<? extends ColorTransform,? extends Double> c) ->
//						observable.set(selectedTargets.containsKey(item)));
//
//				return observable;
//			}
//		}));

		targetListView.setCellFactory((ListView<ColorTransform> param) -> new ListCell<ColorTransform>(){
			private HBox container;
			private CheckBox checkBox;
			private TextField expTimeTextField;
			private Label transformLabel = new Label();
			private BooleanProperty booleanProperty = new SimpleBooleanProperty();

			@Override
			public void updateItem(ColorTransform item, boolean empty){
				super.updateItem(item, empty);
				if (!(empty || item == null)) {
					transformLabel.setText(item.toString());
//					container = new HBox(0, getCheckBox(), transformLabel, expTimeTextField);
					if(Objects.equals(selectedStainType.get(), "Fluorescence")) {
						container = new HBox(4, getCheckBox(), transformLabel, getExpTextField());
					} else {
						container = new HBox(4, getCheckBox(), transformLabel);
					}
					setGraphic(container);
				} else {
					setGraphic(null);
					setText(null);
				}

			}

			private TextField getExpTextField(){
				if(expTimeTextField==null){
					expTimeTextField = new TextField();
					expTimeTextField = formatTextFields(expTimeTextField, "integer", null);
					expTimeTextField.setPromptText("ms");
					expTimeTextField.setPrefWidth(50);
					expTimeTextField.setMaxWidth(60);
					expTimeTextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
						@Override
						public void handle(KeyEvent ke) {
							if (ke.getCode().equals(KeyCode.ENTER)) {
								if (expTimeTextField.getText().isEmpty() || expTimeTextField.getText() == null) {
									selectedTargets.replace(getItem(), 0.0);
								} else{
									selectedTargets.replace(getItem(), Double.parseDouble(expTimeTextField.getText()));
								}
								logger.info(selectedTargets.toString());
							}
						}
					});
					expTimeTextField.focusedProperty().addListener((ov, oldV, newV) -> {
						if (!newV) { // focus lost
							if (expTimeTextField.getText().isEmpty() || expTimeTextField.getText() == null) {
								selectedTargets.replace(getItem(), 0.0);
							} else{
								selectedTargets.replace(getItem(), Double.parseDouble(expTimeTextField.getText()));
							}
							logger.info(selectedTargets.toString());
						}
					});
				}
				return expTimeTextField;
			}
			private CheckBox getCheckBox(){
				if(checkBox==null){
					checkBox = new CheckBox();
					checkBox.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
						logger.info("Check box for " + getItem() + " changed from " + wasSelected + " to " + isNowSelected);
						if (isNowSelected) {
							if(expTimeTextField == null || expTimeTextField.getText().isEmpty() || expTimeTextField.getText() == null) {
//								could check and set as -1 for error catching downstream....
								selectedTargets.put(getItem(), 0.0);
							} else {
								selectedTargets.put(getItem(), Double.parseDouble(expTimeTextField.getText()));
							}
						} else {
							selectedTargets.remove(getItem());
						}
						logger.info(selectedTargets.toString());
						updateGUI(false);
					});
					checkBox.selectedProperty().set(selectedTargets.containsKey(getItem()));
					selectedTargets.addListener((MapChangeListener.Change<? extends ColorTransform,? extends Double> c) ->
							checkBox.selectedProperty().set(selectedTargets.containsKey(getItem())));
				}
				return checkBox;
			}
		});
	}

	private TextField formatTextFields(TextField textField, String format, String defaultValue) {
		switch(format.toLowerCase()) {
			case "string": {
				break;
			}
			case "integer": {
				UnaryOperator<TextFormatter.Change> filter = change -> {
					String newText = change.getControlNewText();
					if (newText.matches("^\\d{0,4}$|^$")) {
						return change;
					}
					return null;
				};

				StringConverter<Integer> converter = new IntegerStringConverter() {
					@Override
					public Integer fromString(String s) {
						if (s.isEmpty()) return null;
						else if (Integer.parseInt(s) == 0.0) return 0;
						return super.fromString(s);
					}
				};

				TextFormatter<Integer> textFormatter;
				if(defaultValue!=null) {
					textFormatter = new TextFormatter<Integer>(converter, Integer.parseInt(defaultValue), filter);
				} else{
					textFormatter = new TextFormatter<Integer>(converter, null, filter);
				}

				textField.setTextFormatter(textFormatter);
				break;
			}
			case "percent": {
				UnaryOperator<TextFormatter.Change> filter = change -> {
					String newText = change.getControlNewText();
					if (newText.matches("^100(\\.0{0,2})?$|^\\d{0,2}(\\.\\d{0,2})?$")) {
						return change;
					}
					return null;
				};
				StringConverter<Double> converter = new DoubleStringConverter() {
					@Override
					public Double fromString(String s) {
						if (s.isEmpty()) return 0.0 ;
//    		                else if(Double.parseDouble(s) == 0) return 0.0;
						return super.fromString(s);
					}
				};

				TextFormatter<Double> textFormatter;
				if(defaultValue!=null) {
					textFormatter = new TextFormatter<Double>(converter, Double.parseDouble(defaultValue), filter);
				} else{
					textFormatter = new TextFormatter<Double>(converter, null, filter);
				}

				textField.setTextFormatter(textFormatter);
				break;
			}
			case "0-1": {
				UnaryOperator<TextFormatter.Change> filter = change -> {
					String newText = change.getControlNewText();
					if (newText.matches("^0{0,1}(\\.\\d{0,3})?$|^1(\\.0{0,3})?$")) {
						return change;
					}
					return null;
				};

				StringConverter<Double> converter = new DoubleStringConverter() {
					@Override
					public Double fromString(String s) {
						if (s.isEmpty()) return 0.0 ;
//    		                else if(Double.parseDouble(s) == 0) return 0.0;
						return super.fromString(s);
					}
				};

				TextFormatter<Double> textFormatter;
				if(defaultValue!=null) {
					textFormatter = new TextFormatter<Double>(converter, Double.parseDouble(defaultValue), filter);
				} else{
					textFormatter = new TextFormatter<Double>(converter, null, filter);
				}

				textField.setTextFormatter(textFormatter);
				break;
			}
		}
		return textField;
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

	public void updateGUI(Boolean forceUpdateTransforms) {
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
		if (forceUpdateTransforms) {
			targetListView.getItems().clear();
			targetListView.getItems().setAll(newTransforms);
		} else if (!newTransforms.equals(targetListView.getItems())){
			targetListView.getItems().clear();
			targetListView.getItems().setAll(newTransforms);
		}

		String slide = selectedSlideType.get();
		String stain = selectedStainType.get();
		String source = selectedSource.get();
		String result = selectedResultType.get();
		//check if something is selected for compartments and targets....
		startQuantButton.setDisable(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0);
		cancelButton.setDisable(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0);

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
	// https://stackoverflow.com/questions/8567596/how-to-make-updating-bigdecimal-within-concurrenthashmap-thread-safe

	
	//Main panel and button commands
	public void startQuant(ActionEvent e){
//		double check that all fields have values
		String slide = selectedSlideType.get();
		String stain = selectedStainType.get();
		String source = selectedSource.get();
		String result = selectedResultType.get();
		//check if something is selected for compartments and targets....
		if(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0) {
//			throw new Exception("Insufficient inputs selected. Check that compartments and targets are selected, comboboxes are filled, etc.");
			logger.warn("Insufficient inputs selected. Check that compartments and targets are selected, comboboxes are filled, etc.");
			return;
		}
		runCancelled.set(false);
		exportMeasButton.setDisable(true);
		exportMeasMenuItem.setDisable(true);
		startQuantButton.setDisable(true);
		quantProgressBar.setProgress(-1);
		progressLabel.setText("Starting Compartment Quantification...");
		boolean normalizeScore = normalizeMenuItem.selectedProperty().get();
		boolean rescaleScore = rescaleMenuItem.selectedProperty().get();

		PathObjectHierarchy hierarchy = qupath.getImageData().getHierarchy();
		double downsample = 1.0;
		Class<? extends PathObject> sourceType;
		if(source.equals("Cells")){
			sourceType = PathCellObject.class;
		} else{
			sourceType = PathAnnotationObject.class;
		}

		int inputGridSize;
		if(gridSizeTextField.getText().isEmpty() || gridSizeTextField.getText() == null)
			inputGridSize = defaultGridSize;
		else
			inputGridSize = Integer.parseInt(gridSizeTextField.getText());

		CompQuantBackend compQuant = new CompQuantBackend(qupath.getImageData(),
										selectedTargets,
										selectedCompartments,
										ignoreClasses,
										roiClasses,
										runCancelled,
										downsample,
										inputGridSize,
										sourceType,
										rescaleScore,
										normalizeScore,
										maxFloatValue,
										getNumThreads()-3);

//		Remove detection objects that are not cells, clear source measurements
		if(source.equals("Annotations")) {
			List<PathObject> notCells = hierarchy.getDetectionObjects().parallelStream().filter(p->!p.isCell())
																						.collect(Collectors.toList());
			hierarchy.removeObjects(notCells, true);
			clearMeasurements(hierarchy, hierarchy.getAnnotationObjects());
		} else if(source.equals("Cells")){
			List<PathObject> notCells = hierarchy.getDetectionObjects().parallelStream().filter(p->!p.isCell())
					.collect(Collectors.toList());
			hierarchy.removeObjects(notCells, true);
			clearMeasurements(hierarchy, hierarchy.getCellObjects());
		}
		CompletableFuture.runAsync(()->{
			if(runCancelled.get()){
				throw new CancellationException();
			}
			if(result.toLowerCase().contains("grid")){
				if(gridSizeTextField.getText().isEmpty() || gridSizeTextField.getText() == null) {
					logger.warn("Gridsize textfield cannot be 0 or empty when trying to compute grid results!");
//						return false;
				}else if(gridSizeTextField.getText() != null && Integer.parseInt(gridSizeTextField.getText()) == 0) {
					logger.warn("Gridsize textfield cannot be 0 or empty when trying to compute grid results!");
//						return false;
				}else {
//					logger.warn("Grid scoring not implemented yet...");
					Platform.runLater(()->{
						progressLabel.setText("Quantifying Grid Tiles...");
					});
					try{
						compQuant.TileRecalcCompartmentsAndScores();
					}catch (CancellationException ex){
						Platform.runLater(()-> {
							exportMeasButton.setDisable(false);
							exportMeasMenuItem.setDisable(false);
							startQuantButton.setDisable(false);
						});
						throw new RuntimeException(ex);
					}
				}

			}
//				return false;
		}, startRunFJP)
		.thenRun(()->{
			if(runCancelled.get()){
				throw new CancellationException();
			}
			if(result.toLowerCase().contains("tma") && slide.equals("TMA")){
				logger.info(String.format("Beginning compartment quantification of TMA cores for compartments: %s and targets: %s...", selectedCompartments.toString(), selectedTargets.toString()));
				Platform.runLater(()->{
					progressLabel.setText("Quantifying TMA core compartments...");
				});
				try {
					compQuant.TMARecalcCompartmentsAndScores().get();
				} catch (ExecutionException | InterruptedException | CancellationException ex) {
					Platform.runLater(()-> {
						exportMeasButton.setDisable(false);
						exportMeasMenuItem.setDisable(false);
						startQuantButton.setDisable(false);
					});
					throw new RuntimeException(ex);
				}
			}
		})
		.thenRun(()->{
			if(runCancelled.get()){
				throw new CancellationException();
			}
			if(result.toLowerCase().contains("roi")){
				logger.info(String.format("Beginning compartment quantification of ROIs for compartments: %s and targets: %s...", selectedCompartments.toString(), selectedTargets.toString()));
				Platform.runLater(()->{
					progressLabel.setText("Quantifying ROI compartments...");
				});
				try {
					compQuant.getTargetScoresForROIs().get();
				} catch (ExecutionException | InterruptedException | CancellationException ex) {
					Platform.runLater(()-> {
						exportMeasButton.setDisable(false);
						exportMeasMenuItem.setDisable(false);
						startQuantButton.setDisable(false);
					});
					throw new RuntimeException(ex);
				}
			}
		})
		.exceptionally(ex -> {
//			ex.printStackTrace();
//			logger.warn(Arrays.toString(ex.getStackTrace()));
			try {
				compQuant.cancelTasks().get();
			} catch (InterruptedException | ExecutionException exc) {
				throw new RuntimeException(exc);
			}
			logger.warn(ex.toString());
			return null;
		})
		.thenRun(()->{
//			cleanup vars
			compQuant.close();
//			compQuant = null;
			System.gc();
			logger.info("Completed with all tasks...");
//			update progress bar again.....?
		});
	}

	public void cancelQuant(ActionEvent e){
		exportMeasButton.setDisable(false);
		exportMeasMenuItem.setDisable(false);
		startQuantButton.setDisable(false);
		runCancelled.set(true);
		progressLabel.setText("Canceled task...");
//		would be cool to make progress bar red
		quantProgressBar.setProgress(0);
//		if(compQuant != null && compQuant.isTaskRunning()) {
//			logger.warn("Trying to cancel running task...");
//			compQuant.cancelTasks();
////			// garbage cleanup?
//			compQuant.close();
////			compQuant = null;
//			System.gc();
//			progressLabel.setText("Canceled task...");
////			would be cool to make progress bar red
//			quantProgressBar.setProgress(0);
//
//		} else{
//			logger.info("No task is running...");
//			if(compQuant != null) {
////				trying to cancel the tasks anyways
//				compQuant.cancelTasks();
//				compQuant.close();
////				compQuant = null;
//				System.gc();
//			}
//		}
	}
	
	public void advancedSettings(ActionEvent e) {
		logger.info("Opening advanced settings panel...");
	}
	
	public void helpButton(ActionEvent e) {
		logger.info("Opening help dialog...");
	}
	
	public void exportImageMeasurementsButton(ActionEvent e) {
		logger.info("Opening dialog to export measurements for project...");
//		fileSelector = new FileChooser();
		Project<BufferedImage> project = qupath.getProject();
		if(project!=null) {
			initialFileDirectory = Projects.getBaseDirectory(project);
			logger.info("starting at " + initialFileDirectory);
		}else {
			initialFileDirectory = Paths.get(".").toFile();
		}
		fileSelector.setInitialDirectory(initialFileDirectory);
		fileSelector.getExtensionFilters().addAll(
				new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"),
				new FileChooser.ExtensionFilter("All files", "*.*"));
		File outputFile = fileSelector.showSaveDialog(qupath.getStage());
		if(outputFile!=null) {
			progressLabel.setText("Exporting measurements for image...");
			quantProgressBar.setProgress(-1);
			try {
				exportMeasurements(outputFile, false);
			} catch (IOException ex) {
				progressLabel.setText("Didn't save measurements, exception encountered...");
				quantProgressBar.setProgress(0.0);
				throw new RuntimeException(ex);
			}
		} else{
			logger.warn("Did not save measurements, file output path is null.");
			progressLabel.setText("Didn't save measurements, file output is null");
			quantProgressBar.setProgress(0.0);
		}
	}

	public void exportAllMeasurementsButton(ActionEvent e) {
		logger.info("Opening dialog to export measurements for project...");
//		fileSelector = new FileChooser();
		Project<BufferedImage> project = qupath.getProject();
		if(project!=null) {
			initialFileDirectory = Projects.getBaseDirectory(project);
			logger.info("starting at " + initialFileDirectory);
		}else {
			initialFileDirectory = Paths.get(".").toFile();
		}
		fileSelector.setInitialDirectory(initialFileDirectory);
		fileSelector.getExtensionFilters().addAll(
				new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"),
				new FileChooser.ExtensionFilter("All files", "*.*"));
		File outputFile = fileSelector.showSaveDialog(qupath.getStage());
		if(outputFile!=null) {
			progressLabel.setText("Exporting measurements for all images in project...");
			quantProgressBar.setProgress(-1);
			try {
				exportMeasurements(outputFile, true);
			} catch (IOException ex) {
				progressLabel.setText("Didn't save measurements, exception encountered...");
				quantProgressBar.setProgress(0.0);
				throw new RuntimeException(ex);
			}
		} else{
			logger.warn("Did not save measurements, file output path is null.");
			progressLabel.setText("Didn't save measurements, file output is null");
			quantProgressBar.setProgress(0.0);
		}
	}

	public List<String> getMeasExcludeColumns(String excludeType) {
		if (excludeType.equals("essential")) {
			List<String> excludeColumns = new ArrayList<String>();
			excludeColumns.add("ROI");
			excludeColumns.add("Area Âµm^2");
			excludeColumns.add("Perimeter Âµm");
			excludeColumns.add("Missing");

			for(Map.Entry<ColorTransform, Double> tar  : selectedTargets.entrySet()) {
				//	removing double quotes....
				String tarName = tar.getKey().toString().replaceAll("\"", "");
				for(PathClass comp : selectedCompartments) {
					String compName = comp.toString();
					excludeColumns.add(String.format("%s Intensity in %s: Median",tarName, compName));
					excludeColumns.add(String.format("%s Intensity in %s: Min",tarName, compName));
					excludeColumns.add(String.format("%s Intensity in %s: Max",tarName, compName));
					excludeColumns.add(String.format("%s Intensity in %s: Std.Dev.",tarName, compName));
					excludeColumns.add(String.format("%s Intensity in %s: Variance",tarName, compName));
					excludeColumns.add(String.format("%s area px", compName));
				}

			}
			logger.info("Excluding columns: "+excludeColumns.toString());
			return excludeColumns;
		}else {
			return Collections.<String>emptyList();
		}
	}
	public void exportMeasurements(File outputFile, boolean exportAllImages) throws IOException {
		// Get the list of all images in the current project
		Project<BufferedImage> project = qupath.getProject();
		if (project==null) {
			logger.error("Cannot export measurements for null project!");
			progressLabel.setText("Cannot export measurements for null project!");
			quantProgressBar.setProgress(0.0);
			return;
		}

		exportMeasButton.setDisable(true);
		exportMeasMenuItem.setDisable(true);

		// save current image before exporting measurements
		ImageData<BufferedImage> thisImageData = qupath.getImageData();
		project.getEntry(thisImageData).saveImageData(thisImageData);
		List<ProjectImageEntry<BufferedImage>> imagesToExport;
		if(exportAllImages) {
			imagesToExport = project.getImageList();
		}else{
			imagesToExport = List.of(project.getEntry(thisImageData));
		}

		// Separate each measurement value in the output file with a comma (",")
		String separator = ",";

		// Choose the columns that will be included in the export
		// Note: if 'columnsToInclude' is empty, all columns will be included
		//def columnsToInclude = new String[]{"Name", "Class", "Nucleus: Area"}
		String[] excludeColumns = getMeasExcludeColumns(exportMeasFields).toArray(new String[0]);
//		logger.info("Excluding columns: "+excludeColumns.toString());

		// Choose the type of objects that the export will process
		// Other possibilities include:
		//    1. PathAnnotationObject
		//    2. PathDetectionObject
		//    3. PathRootObject
		// Note: import statements should then be modified accordingly
		Class<? extends PathObject> exportType;
		if(measAnnotMenuItem.selectedProperty().get() && measDetMenuItem.selectedProperty().get() || !measAnnotMenuItem.selectedProperty().get() && !measDetMenuItem.selectedProperty().get()){
			//	export all objects
			//	If both of these menu items are deselected, assume it was a mistake and export all objects anyways
			exportType = PathObject.class;
		} else if(measDetMenuItem.selectedProperty().get() && !measAnnotMenuItem.selectedProperty().get()){
			//	only export detections
			exportType = PathDetectionObject.class;
		} else{
			//  last option, export annotations. Also is kinda the default
			exportType = PathAnnotationObject.class;
		}

		// Create the measurementExporter and start the export
		MeasurementExporter exporter = new MeasurementExporter()
							.imageList(imagesToExport)            // Images from which measurements will be exported
							.separator(separator)                 // Character that separates values
			//                  .includeOnlyColumns()
							.excludeColumns(excludeColumns)                     // Columns are case-sensitive
							.exportType(exportType);               // Type of objects to export

		// Start the export process
		CompletableFuture.runAsync(()->exporter.exportMeasurements(outputFile))
				.exceptionally(ex -> {ex.printStackTrace(); return null;})
				.thenRun(()->{
					Platform.runLater(()->{
						progressLabel.setText("Completed exporting measurements");
						quantProgressBar.setProgress(1.0);
						exportMeasButton.setDisable(false);
						exportMeasMenuItem.setDisable(false);
					});
				});
	}
	public void exportMasksButton(ActionEvent e) {
		logger.info("Opening dialog to export masks for project...");
	}
	
	//Overload these methods depending on input arguments. Export data dialog may just run these commands in isolation
	public void exportMasks(File outputFile) {
		
	}

	//	https://stackoverflow.com/questions/21163108/custom-thread-pool-in-java-8-parallel-stream
	public class CompQuantBackend {
		private static final Logger logger = LoggerFactory.getLogger(CompQuantBackend.class);
		private ForkJoinPool forkJoinPool;
//		private ForkJoinTask mainTask;

		private int estNumTasks;
		private int numThreads;

		private Collection<Compartments> cellCompartments = Collections.synchronizedList(Arrays.asList(Compartments.values()));
		private Set<Measurements> measurements = Collections.synchronizedSet(new HashSet<>(Arrays.asList(Measurements.values())));
		//		public final double maxFloatValue;
//		public final boolean normalizeScore;
//		public final boolean rescaleScore;
		private Map<String, Object> params = new ConcurrentHashMap<>();

		//		private QuPathGUI qupath;
		private ImageData<BufferedImage> bImageData;
		//		private Map<Thread, ImageServer<BufferedImage>> threadImageServerMap = new ConcurrentHashMap<>();
		private ConcurrentHashMap<ColorTransform, Double> targets;
		private Set<PathClass> compartments;
		private Set<PathClass> ignoreClasses;
		private Set<PathClass> roiClasses;

		//		private final AtomicReference<BigDecimal> progressValue = new AtomicReference<BigDecimal>(new BigDecimal(String.format("%.2f", 0.0)));
		private final AtomicReference<BigInteger> progressValue = new AtomicReference<BigInteger>(new BigInteger("0"));
		private final AtomicReference<Boolean> isCancelled;

		CompQuantBackend(ImageData<BufferedImage> bImageData,
//						 QuPathGUI qupath,
						 Map<ColorTransform, Double> targets,
						 Set<PathClass> compartments,
						 Set<PathClass> ignoreClasses,
						 Set<PathClass> roiClasses,
						 AtomicReference<Boolean> runCancelled,
						 double downsample,
						 int tileSize,
						 Class<? extends PathObject> sourceType,
						 boolean rescaleScore,
						 boolean normalizeScore,
						 double maxFloatValue,
						 int numThreads) {
			this.bImageData = bImageData;
//			this.qupath = qupath;
			this.targets = new ConcurrentHashMap<>(targets);
			this.compartments = Collections.synchronizedSet(compartments);
			this.ignoreClasses = Collections.synchronizedSet(ignoreClasses);
			this.roiClasses = Collections.synchronizedSet(roiClasses);
			this.isCancelled = runCancelled;
			this.params = new ConcurrentHashMap<>(Map.ofEntries(
					Map.entry("downsample", downsample),
					Map.entry("tileSize", tileSize),
					Map.entry("sourceType", sourceType),
					Map.entry("rescaleScore", rescaleScore),
					Map.entry("normalizeScore", normalizeScore),
					Map.entry("maxFloatValue", maxFloatValue)
			));
			this.numThreads = numThreads;
		}

		CompQuantBackend(ImageData<BufferedImage> bImageData,
//						 QuPathGUI qupath,
						 Map<ColorTransform, Double> targets,
						 Set<PathClass> compartments,
						 Set<PathClass> ignoreClasses,
						 Set<PathClass> roiClasses,
						 AtomicReference<Boolean> runCancelled,
						 double downsample,
						 int tileSize,
						 Class<? extends PathObject> sourceType,
						 boolean rescaleScore,
						 boolean normalizeScore,
						 double maxFloatValue,
						 int numThreads,
						 List<Compartments> cellCompartments,
						 HashSet<Measurements> measurements) {
//			this.qupath = qupath;
			this.bImageData = bImageData;
			this.targets = new ConcurrentHashMap<>(targets);
			this.compartments = Collections.synchronizedSet(compartments);
			this.ignoreClasses = Collections.synchronizedSet(ignoreClasses);
			this.roiClasses = Collections.synchronizedSet(roiClasses);
			this.isCancelled = runCancelled;
			this.params = new ConcurrentHashMap<>(Map.ofEntries(
					Map.entry("downsample", downsample),
					Map.entry("tileSize", tileSize),
					Map.entry("sourceType", sourceType),
					Map.entry("rescaleScore", rescaleScore),
					Map.entry("normalizeScore", normalizeScore),
					Map.entry("maxFloatValue", maxFloatValue)
			));
			this.numThreads = numThreads;
			this.cellCompartments = Collections.synchronizedList(cellCompartments);
			this.measurements = Collections.synchronizedSet(measurements);
		}

		public void setupNewForkJoinPool(int numThreads) {
			isCancelled.set(false);
			if (forkJoinPool == null) {
				logger.info("creating new forkJoinPool");
				forkJoinPool = new ForkJoinPool(numThreads);
			} else if (forkJoinPool.isTerminated()) {
				forkJoinPool = null;
				System.gc();
				System.gc();
				logger.info("creating new forkJoinPool");
				forkJoinPool = new ForkJoinPool(numThreads);
			} else {
				logger.warn("forkJoinPool already exists and is not terminated yet!");
				try {
					cancelTasks().get();
				} catch (InterruptedException | ExecutionException e) {
					throw new RuntimeException(e);
				}
				logger.warn("trying to create new forkJoinPool...");
				forkJoinPool = new ForkJoinPool(numThreads);
			}
		}

		public static void closeQuietly(AutoCloseable c) {
			if (c != null) {
				logger.info("closing...");
				try {
					c.close();
				} catch (Exception ex) {
					// ignore or trace log it
					logger.warn(ex.toString());
//					throw new RuntimeException(e);
				}
			}

		}

		public void close() {
//			this.qupath = null;
//			if(!threadImageServerMap.isEmpty()){
//				threadImageServerMap.forEach((k, c)->{
//					logger.info("trying to close image server on thread... " + k.toString());
//					closeQuietly(c);
//				});
//				threadImageServerMap.clear();
//			}
//			This shuts down the main server for the current image and causes problems....
//			else {
//				try {
//					bImageData.getServer().close();
//				} catch (Exception ex) {
////				ex.printStackTrace();
//					logger.error(ex.toString());
//				}
//			}
//			this.bImageData = null;
			this.targets = null;
			this.compartments = null;
			this.ignoreClasses = null;
			this.roiClasses = null;
			this.params = null;
			this.cellCompartments = null;
			this.measurements = null;
			try {
				cancelTasks().get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
			System.gc();
			System.gc();
		}

		public void setEstNumTasks(int newEst) {
			logger.info("Estimate # tasks: " + newEst);
			estNumTasks = newEst;
		}

		public int getEstNumTasks() {
			return estNumTasks;
		}
//		public BigDecimal incrementAndGet(double amount) {
//			for (;;) {
//				BigDecimal current = progressValue.get();
//				BigDecimal next = current.add(new BigDecimal(String.format("%.2f", amount)));
//				if (progressValue.compareAndSet(current, next)) {
//					return next;
//				}
//			}
//		}

		public BigInteger incrementAndGet(Integer amount) {
			for (; ; ) {
				BigInteger current = progressValue.get();
				BigInteger next = current.add(new BigInteger(amount.toString()));
				if (progressValue.compareAndSet(current, next)) {
					return next;
				}
			}
		}

		//	https://stackoverflow.com/questions/21083945/how-to-avoid-not-on-fx-application-thread-currentthread-javafx-application-th
		public void incrementProgress(Integer amount) {
			double prog = incrementAndGet(amount).doubleValue();
			int newEst = getEstNumTasks();
			logger.info(String.format("%f", prog / newEst));
			Platform.runLater(() -> {
//				just to make sure that GUI resets and GC happens
				if (prog / newEst + 0.005 >= 1.0) {
					exportMeasButton.setDisable(false);
					exportMeasMenuItem.setDisable(false);
					startQuantButton.setDisable(false);
//					should you force the cancel?
//					cancelTasks();
					System.gc();
					System.gc();
				}
				quantProgressBar.setProgress(prog / newEst);
			});
		}

		public CompletableFuture<Void> cancelTasks() {
			logger.warn("Trying to shutdown running tasks!");
			isCancelled.set(true);
			CompletableFuture<Void> result = CompletableFuture.runAsync(() -> {
				if (forkJoinPool != null) {
					forkJoinPool.shutdownNow();
					try {
						logger.info("awaiting forkJoinPool termination...");
						if (forkJoinPool.awaitTermination(30, TimeUnit.SECONDS)) {
							logger.info("forkJoinPool termination finished...");
							forkJoinPool = null;
							System.gc();
							System.gc();
						} else {
							logger.warn("forkJoinPool termination timed-out...!");
							forkJoinPool.shutdownNow();
							System.gc();
							System.gc();
						}
					} catch (InterruptedException ex) {
						logger.warn(String.valueOf(ex));
						logger.warn("interrupted before termination of forkJoinPool?...");
					}
				}
			});
			setEstNumTasks(0);
			return result;
		}

		public boolean isTaskRunning() {
			if (forkJoinPool != null) {
				return !forkJoinPool.isTerminated();
			} else {
				return false;
			}
		}

//		public ROI combinePathObjs(List<PathObject> annots) throws ExecutionException, InterruptedException {
//			List<ROI> rois = forkJoinPool.submit(()-> annots.parallelStream().map(a -> a.getROI()).collect(Collectors.toList())).get();
//			ROI combinedROI = RoiTools.union(rois);
//			return combinedROI;
//		}

//		public ROI combinePathObjs(Collection<PathObject> annots, Boolean newAnnot) {
//			ROI combinedROI = null;
//			PathClass p_class = null;
//			for (PathObject annotation : annots) {
//				if (combinedROI == null) {
//					combinedROI = annotation.getROI();//.duplicate();
//					p_class = annotation.getPathClass();
//				} else if (combinedROI.getImagePlane().equals(annotation.getROI().getImagePlane())) {
//					combinedROI = RoiTools.combineROIs(combinedROI, annotation.getROI(), RoiTools.CombineOp.ADD);
//				} else {
//					logger.info("Cannot merge PathObjects across different image planes!");
////				continue;
//				}
//			}
//
//			if (newAnnot) {
//				PathObjectHierarchy hierarchy = bImageData.getHierarchy();
//				hierarchy.removeObjects(annots, true);
//				PathObject combinedAnnot = PathObjects.createAnnotationObject(combinedROI, p_class);
//				hierarchy.addPathObject(combinedAnnot);
//			}
//
//			return combinedROI;
//		}

		// AQUA inside each intersecting compartment of ROI only
		//    Map<String, Integer> targets = new LinkedHashMap<>();
		// Not for TMAs! Would be much more effective to restrict the search space for ROIS within TMA core hierarchy, however, not all the annotations will be properly incorporated into the hierarchy.....
		// How to flexibly find ROIs within TMA core hierarchy?
		public CompletableFuture<Void> getTargetScoresForROIs() throws RuntimeException {
			return getTargetScoresForROIs(ignoreClasses, roiClasses, targets, compartments, (Class<? extends PathObject>) params.get("sourceType"), (double) params.get("downsample"), numThreads);
		}

		//		It would be nice to set this up so that there is a static method that can be used from scripting if you didn't want to use the GUI
//		but then you would have to remove all the non-static GUI progress bar elements and use the commonPool, so the code would be different....
		public CompletableFuture<Void> getTargetScoresForROIs(Set<PathClass> ignoreClasses,
															  Set<PathClass> rois,
															  Map<ColorTransform, Double> targets,
															  Set<PathClass> compartments,
															  Class<? extends PathObject> sourceType,
															  double downsample,
															  int numThreads
		) throws RuntimeException {

			if (numThreads <= 0)
				numThreads = 1;

			setupNewForkJoinPool(numThreads);

			AtomicInteger totalROIs = new AtomicInteger(0);
			Integer progAmount = 1;

			// Used for placing child objects inside ROI
			AtomicInteger roiNumber = new AtomicInteger(1);
			AtomicReference<Boolean> doAdjust = new AtomicReference<>(false);
			ROI combinedExcludeROI;
			Geometry combinedExcludeGeom;

			PathObjectHierarchy hierarchy = bImageData.getHierarchy();
			var pathObjs = hierarchy.getObjects(null, PathObject.class);
//			https://stackoverflow.com/questions/53558753/how-do-i-close-a-thread-local-autocloseable-used-in-parallel-stream
//			if(threadImageServerMap.isEmpty()) {
//				threadImageServerMap = new ConcurrentHashMap<>();
//			}else{
//				threadImageServerMap.forEach((k, c)->{
//					logger.info("trying to close image server on thread... " + k.toString());
//					closeQuietly(c);
//				});
//				threadImageServerMap.clear();
//			}
			ImageServer<BufferedImage> server = bImageData.getServer();
			CompletableFuture<Void> result = null;
			try {
				List<ROI> allIgnoreROIs = forkJoinPool.submit(() -> hierarchy.getAnnotationObjects().parallelStream()
						.filter(p -> p.getPathClass() != null && ignoreClasses.contains(p.getPathClass()))
						.map(p -> p.getROI())
						.collect(Collectors.toList())).get();
				List<PathObject> compartmentObjs = Collections.synchronizedList(forkJoinPool.submit(() -> pathObjs.parallelStream()
						.filter(p -> p.getPathClass() != null && compartments.contains(p.getPathClass()) && p.getClass() == sourceType)
						.collect(Collectors.toList())).get());

				logger.info(allIgnoreROIs.toString());
				combinedExcludeROI = RoiTools.union(allIgnoreROIs);
				if (combinedExcludeROI != null && !combinedExcludeROI.isEmpty()) {
					doAdjust.set(true);
					combinedExcludeGeom = combinedExcludeROI.getGeometry();
				} else {
					doAdjust.set(false);
					combinedExcludeGeom = null;
				}

//				https://stackoverflow.com/questions/23320407/how-to-cancel-java-8-completable-future
//				ROI cannot be unclassified/null or else contains() throws a NullPointerException
				result = CompletableFuture.runAsync(() -> pathObjs.parallelStream().filter(p -> p.getPathClass() != null && rois.contains(p.getPathClass()) && p.hasROI())
										.map(f -> {
											// Record null/none values for compartments not within ROI
//											logger.info(f.getName());
											if (f.getName() == null || f.getName().isBlank() || f.getName().matches("^ROI_[0-9]+$")) {
												f.setName("ROI_" + roiNumber.get());
												roiNumber.incrementAndGet();
											}

											PathObject adjpathObj = null;
											ROI adjpathObjROI = f.getROI();
											boolean intersectsExclude = false;
											if (doAdjust.get() && combinedExcludeGeom != null) {
												intersectsExclude = adjpathObjROI.getGeometry().intersects(combinedExcludeGeom);
												if (intersectsExclude) {
													adjpathObjROI = RoiTools.combineROIs(adjpathObjROI, combinedExcludeROI, RoiTools.CombineOp.SUBTRACT);
												}
											}
											if (adjpathObjROI.isEmpty()) {
												logger.info(String.format("ROI %s is now empty, skipping AQUA metrics...", f.getName()));
											} else if (doAdjust.get() && intersectsExclude) {
												logger.info(String.format("Adjusting ROI %s based on ignore annotations...", f.getName()));
												adjpathObj = PathObjects.createAnnotationObject(adjpathObjROI, f.getPathClass());
												hierarchy.addPathObject(adjpathObj);
//												bImageData.getHierarchy().addPathObjectBelowParent(pathObj.getParent(), adjpathObj, true);
												hierarchy.removeObject(f, true);
											} else {
												adjpathObj = f;
											}

											// this might work but does it scale for lots of ROIs?
											totalROIs.incrementAndGet();
											setEstNumTasks(totalROIs.get());
											return adjpathObj;
										})
										.forEach(r -> {
											//Typically the number of compartments is small and these are all combined for a WSI.
											//Not efficient for TMA cores! but should work...
											if (isCancelled.get()) {
												throw new CancellationException();
											}

											if (r != null) {
//												ImageServer<BufferedImage> server = threadImageServerMap.computeIfAbsent(Thread.currentThread(), t -> bImageData.getServer());
												for (PathObject compObj : compartmentObjs) {

													ROI compInterROI = RoiTools.combineROIs(compObj.getROI(), r.getROI(), RoiTools.CombineOp.INTERSECT);

													if (!compInterROI.isEmpty()) {
														PathObject compInterDet = PathObjects.createDetectionObject(compInterROI, compObj.getPathClass());
														logger.info(String.format("ROI contains %s compartment! Scoring target expression within ROI.", compObj.getPathClass().toString()));
														// For debugging, maybe helps with visualization
														// Add object as a child of the ROI
														//                        addObject(compInterDet);
														compInterDet.setName(r.getName() + " (" + compObj.getPathClass().toString() + ")");
														bImageData.getHierarchy().addPathObjectBelowParent(r, compInterDet, true);

														logger.info(String.format("Got %s intersection with ROI", compObj.getPathClass().toString()));

														// Quantify metrics/AQUA for each target in each intersecting compartment
														// Calculate AQUA scoring metrics for new compartment detections for all targets
														try {
															getTargetsIntensityScores_OpenCV(server, compInterDet);
														} catch (IOException ex) {
															logger.warn(ex.toString());
														}
													} else {
														logger.info(String.format("No intersection with %s compartment for ROI... skipping.", compObj.getPathClass().toString()));
													}
												}
											}
											incrementProgress(progAmount);
										}),
								forkJoinPool)
						.thenRun(() -> {
							Platform.runLater(() -> {
								progressLabel.setText("Completed scoring ROI compartments!");
								quantProgressBar.setProgress(1.0);
								exportMeasButton.setDisable(false);
								exportMeasMenuItem.setDisable(false);
								startQuantButton.setDisable(false);
							});
//							threadImageServerMap.forEach((k, c)->{
//								logger.info("trying to close image server on thread... " + k.toString());
//								closeQuietly(c);
//							});
//							threadImageServerMap.clear();
						})
						.exceptionally(ex -> {
							ex.printStackTrace();
//							logger.warn(Arrays.toString(ex.getStackTrace()));
							logger.warn("getTargetScoresForROIs: " + ex);
							logger.warn(ex.toString());
							return null;
						});

			} catch (Exception ex) {
				throw new RuntimeException(ex);
			} finally {
				forkJoinPool.shutdown();
			}
			return result;
		}

		/**
		 * Try to intersect two geometries, returning null if this fails.
		 * Intended for use in a stream.
		 *
		 * @param g1
		 * @param g2
		 * @return
		 */
		private static Geometry intersect(Geometry g1, Geometry g2) {
//		if (g1.covers(g2))
//			return g2;
//		if (g2.covers(g1))
//			return g1;
			if (g1 == g2)
				return g1;

			try {
				return GeometryTools.homogenizeGeometryCollection(g1.intersection(g2));
			} catch (Exception e) {
				logger.warn(e.getLocalizedMessage(), e);
				return null;
			}
		}

		public Map<PathObject, Map<PathClass, ROI>> computeTiledROIsForCompartments(Rectangle2D bounds,
																							List<PathObject> compartmentPathObjs,
																							ImmutableDimension sizePreferred,
																							boolean fixedSize,
																							int overlap) throws ExecutionException, InterruptedException {

			ConcurrentHashMap<PathClass, Geometry> compartmentGeoms = new ConcurrentHashMap<>(
					compartmentPathObjs.parallelStream()
							.map(r -> Map.entry(r.getPathClass(), r.getROI().getGeometry()))
							.filter(m -> m.getValue() != null)
							.collect(Collectors.toMap(
											Map.Entry::getKey,
											Map.Entry::getValue
									)
							)
			);

			return computeTiledROIsForCompartments(bounds, ImagePlane.getDefaultPlane(), compartmentGeoms, sizePreferred, fixedSize, overlap);
		}

		public Map<PathObject, Map<PathClass, ROI>> computeTiledROIsForCompartments(Rectangle2D bounds,
																							Map<PathClass, ROI> compartmentPathROIs,
																							ImmutableDimension sizePreferred,
																							boolean fixedSize,
																							int overlap) throws ExecutionException, InterruptedException {

			ConcurrentHashMap<PathClass, Geometry> compartmentGeoms = new ConcurrentHashMap<>(
					compartmentPathROIs.entrySet().parallelStream()
							.map(r -> Map.entry(r.getKey(), r.getValue().getGeometry()))
							.filter(m -> m.getValue() != null)
							.collect(Collectors.toMap(
											Map.Entry::getKey,
											Map.Entry::getValue
									)
							)
			);


			return computeTiledROIsForCompartments(bounds, ImagePlane.getDefaultPlane(), compartmentGeoms, sizePreferred, fixedSize, overlap);
		}


		public Map<PathObject, Map<PathClass, ROI>> computeTiledROIsForCompartments(Rectangle2D bounds,
																							ImagePlane plane,
																							ConcurrentHashMap<PathClass, Geometry> compartmentGeoms,
																							ImmutableDimension sizePreferred,
																							boolean fixedSize,
																							int overlap) throws ExecutionException, InterruptedException {

//			Bound for entire image

//			if (pathArea == null || (bounds.getWidth() <= sizeMax.width && bounds.getHeight() <= sizeMax.height)) {
//				return Collections.singletonList(parentROI);
//			}

			if (compartmentGeoms.size() <= 0) {
				logger.warn("Found no valid geometries for compartment PathObjects...");
				return Map.ofEntries(
						Map.entry(null,
							Map.ofEntries(Map.entry(getPathClass("null"), ROIs.createEmptyROI()))
						)
				);
			}


			ConcurrentHashMap<PathClass, PreparedGeometry> preparedGeoms = new ConcurrentHashMap<>(
					forkJoinPool.submit(()->compartmentGeoms.entrySet().parallelStream()
							.map(m -> {
								if (m.getValue().getNumPoints() > 1000) {
									return Map.entry(m.getKey(), PreparedGeometryFactory.prepare(m.getValue()));
								} else {
									return Map.entry(m.getKey(), null);
								}
							})
		//					.filter(m -> m.getValue() != null)
							.collect(Collectors.toMap(
											m -> m.getKey(),
											m -> (PreparedGeometry) m.getValue()
									)
							)
					).get());

			int xMin = (int) bounds.getMinX();
			int yMin = (int) bounds.getMinY();
			int nx = (int) Math.ceil(bounds.getWidth() / sizePreferred.width);
			int ny = (int) Math.ceil(bounds.getHeight() / sizePreferred.height);
			int w = fixedSize ? sizePreferred.width : (int) Math.ceil(bounds.getWidth() / nx);
			int h = fixedSize ? sizePreferred.height : (int) Math.ceil(bounds.getHeight() / ny);

			// Center the tiles
//			xMin = (int) (bounds.getCenterX() - (nx * w * .5));
//			yMin = (int) (bounds.getCenterY() - (ny * h * .5));

			// This can be very slow if we have an extremely large number of vertices/tiles.
			// For that reason, we try to split initially by either rows or columns if needed.
			boolean byRow = false;
			boolean byColumn = false;
			ConcurrentHashMap<Integer, ConcurrentHashMap<PathClass, Geometry>> localGeoms = new ConcurrentHashMap<>();
			// make the empty based on one of the entries in compartmentGeoms... may error if the key/value selected is null?
			if (ny > 1 && nx > 1 && preparedGeoms.size() >= 1) {
				// If we have a lot of points, create a prepared geometry so we can check covers/intersects quickly;
				// (for a regular geometry, it would be faster to just compute an intersection and see if it's empty)
				logger.info(String.format("Preparing %d sets of local geometries...", preparedGeoms.size()));

				byRow = nx > ny;
//				byRow = true;
				byColumn = !byRow;
				double yMin2 = yMin;
				double xMin2 = xMin;

				PathClass compKey = compartmentGeoms.keySet().iterator().next();
				Geometry empty = compartmentGeoms.get(compKey).getFactory().createEmpty(2);

				Map<PathClass, Envelope> compartmentEnvel = compartmentGeoms.entrySet().parallelStream()
						.map(m -> Map.entry(m.getKey(), m.getValue().getEnvelopeInternal()))
						.collect(Collectors.toMap(
								m -> m.getKey(),
								m -> m.getValue()
						));

				// Compute intersection by row so that later intersections are simplified
				if (byRow) {
					localGeoms = new ConcurrentHashMap<>(
							forkJoinPool.submit(()->
								IntStream.range(0, ny)
										.parallel()
										.boxed()
										.collect(
												Collectors.toMap(
														yi -> yi,
														yi -> {
															double y = yMin2 + yi * h - overlap;
															return new ConcurrentHashMap<PathClass, Geometry>(
																	preparedGeoms.entrySet().parallelStream()
																			.map(prep -> {
																				var prepared2 = prep.getValue();
																				var geometry = compartmentGeoms.get(prep.getKey());
																				if (prepared2 == null) {
																					// This would happen if the geometry was too small to be prepared
																					// use the compartment geometry in this case
																					return Map.entry(prep.getKey(), geometry);
																				}
																				var envelope = compartmentEnvel.get(prep.getKey());
																				var row = GeometryTools.createRectangle(
																						envelope.getMinX(),
																						y,
																						envelope.getMaxX(),
																						h + overlap * 2);
																				if (!prepared2.intersects(row)) {
																					return Map.entry(prep.getKey(), empty);
																				} else if (prepared2.covers(row)) {
																					return Map.entry(prep.getKey(), row);
																				}
																				var temp = intersect(geometry, row);
																				return Map.entry(prep.getKey(), temp == null ? geometry : temp);
																			})
																			.collect(Collectors.toMap(
																					m -> m.getKey(),
																					m -> m.getValue())
																			)
															);
														}
												)
										)
							).get());
				}
				if (byColumn) {
					localGeoms = new ConcurrentHashMap<>(
							forkJoinPool.submit(()->
								IntStream.range(0, nx)
										.parallel()
										.boxed()
										.collect(
												Collectors.toMap(
														xi -> xi,
														xi -> {
															double x = xMin2 + xi * w - overlap;
															return new ConcurrentHashMap<PathClass, Geometry>(
																preparedGeoms.entrySet().parallelStream()
																	.map(prep -> {
																		var prepared2 = prep.getValue();
																		var geometry = compartmentGeoms.get(prep.getKey());
																		if (prepared2 == null) {
																			// This would happen if the geometry was too small to be prepared
																			// use the compartment geometry in this case
																			return Map.entry(prep.getKey(), geometry);
																		}
																		var envelope = compartmentEnvel.get(prep.getKey());
																		var col = GeometryTools.createRectangle(
																				x,
																				envelope.getMinY(),
																				w + overlap * 2,
																				envelope.getMaxY());
																		if (!prepared2.intersects(col)) {
																			return Map.entry(prep.getKey(), empty);
																		} else if (prepared2.covers(col)) {
																			return Map.entry(prep.getKey(), col);
																		}
																		var temp = intersect(geometry, col);
																		return Map.entry(prep.getKey(), temp == null ? geometry : temp);
																	}
																	).collect(Collectors.toMap(
																				m->m.getKey(),
																				m->m.getValue()
																		))
															);
														}
												)
										)
							).get());
				}
			}

			// Generate all the rectangles as geometries
//			Map<Geometry, Geometry> tileGeometries = new LinkedHashMap<>();
			Map<PathObject, Map<PathClass, ROI>> tileIntersectROIs = new ConcurrentHashMap<>();


			ConcurrentHashMap<Integer, ConcurrentHashMap<PathClass, Geometry>> finalLocalGeoms = localGeoms;

			if(!byRow && !byColumn){
				ConcurrentHashMap<PathClass, Geometry> theseLocalGeoms = compartmentGeoms;
//				always using full compartment geometries to compute intersections
//				when geometries are small (< 1000 pts)
				forkJoinPool.submit(()->
					IntStream.range(0, nx).parallel().forEach(xi -> {
						if (isCancelled.get()) {
							throw new CancellationException();
						}
						int x = xMin + xi * w - overlap;
						IntStream.range(0, ny).parallel().forEach(yi -> {
							int y = yMin + yi * h - overlap;
							// Create the tile
							var rect = GeometryTools.createRectangle(x, y, w + overlap * 2, h + overlap * 2);

	//						Map<PathClass, ROI> thisIntersectMap = new HashMap<>();
							Map<PathClass, ROI> thisIntersectMap = theseLocalGeoms.entrySet().parallelStream()
									.filter(m -> m.getValue() != null && !m.getValue().isEmpty())
									.map(m -> Map.entry(m.getKey(), intersect(rect, m.getValue())))
									.filter(m -> m.getValue() != null && !m.getValue().isEmpty())
									.map(m -> Map.entry(m.getKey(), GeometryTools.geometryToROI(m.getValue(), plane)))
									.collect(Collectors.toMap(
											m -> m.getKey(),
											m -> m.getValue()
									));
							// handle empty/null thisIntersectMaps?
							if (!thisIntersectMap.isEmpty()) {
								PathObject tileObj = PathObjects.createTileObject(GeometryTools.geometryToROI(rect, plane));
								tileObj.setName(String.format("Tile-r%dc%d_x%dy%d", yi, xi, x, y));
	//							logger.info(thisIntersectMap.toString());
								logger.info(String.format("Tile-r%dc%d_x%dy%d",yi, xi, x, y));
								tileIntersectROIs.put(tileObj, thisIntersectMap);
							}
						});
					})
				).get();
			} else if(byRow) {
				forkJoinPool.submit(()->
					IntStream.range(0, ny).parallel().forEach(yi -> {
						if (isCancelled.get()) {
							throw new CancellationException();
						}
						int y = yMin + yi * h - overlap;
						// always row
						ConcurrentHashMap<PathClass, Geometry> theseLocalGeoms = finalLocalGeoms.getOrDefault(yi, compartmentGeoms);
						IntStream.range(0, nx).parallel().forEach(xi -> {
							int x = xMin + xi * w - overlap;
							// Create the tile
							var rect = GeometryTools.createRectangle(x, y, w + overlap * 2, h + overlap * 2);
	//						logger.info(String.format("r%dc%d_x%dy%d",yi, xi, x, y));
							Map<PathClass, ROI> thisIntersectMap = theseLocalGeoms.entrySet().parallelStream()
									.filter(m -> m.getValue() != null && !m.getValue().isEmpty())
									.map(m -> Map.entry(m.getKey(), intersect(rect, m.getValue())))
									.filter(m -> m.getValue() != null && !m.getValue().isEmpty())
									.map(m -> Map.entry(m.getKey(), GeometryTools.geometryToROI(m.getValue(), plane)))
									.collect(Collectors.toMap(
											m -> m.getKey(),
											m -> m.getValue()
									));

							// handle empty/null thisIntersectMaps?
							if (!thisIntersectMap.isEmpty()) {
								PathObject tileObj = PathObjects.createTileObject(GeometryTools.geometryToROI(rect, plane));
								tileObj.setName(String.format("Tile-r%dc%d_x%dy%d", yi, xi, x, y));
	//							logger.info(thisIntersectMap.toString());
								logger.info(String.format("Tile-r%dc%d_x%dy%d",yi, xi, x, y));
								tileIntersectROIs.put(tileObj, thisIntersectMap);
							}
						});
					})
				).get();
			} else if(byColumn){
				forkJoinPool.submit(()->
					IntStream.range(0, nx).parallel().forEach(xi -> {
						if (isCancelled.get()) {
							throw new CancellationException();
						}
						int x = xMin + xi * w - overlap;
						// always column
						ConcurrentHashMap<PathClass, Geometry> theseLocalGeoms = finalLocalGeoms.getOrDefault(xi, compartmentGeoms);
						IntStream.range(0, ny).parallel().forEach(yi -> {
							int y = yMin + yi * h - overlap;
							// Create the tile
							var rect = GeometryTools.createRectangle(x, y, w + overlap * 2, h + overlap * 2);
	//						logger.info(String.format("r%dc%d_x%dy%d",yi, xi, x, y));
							Map<PathClass, ROI> thisIntersectMap = theseLocalGeoms.entrySet().parallelStream()
									.filter(m -> m.getValue() != null && !m.getValue().isEmpty())
									.map(m -> Map.entry(m.getKey(), intersect(rect, m.getValue())))
									.filter(m -> m.getValue() != null && !m.getValue().isEmpty())
									.map(m -> Map.entry(m.getKey(), GeometryTools.geometryToROI(m.getValue(), plane)))
									.collect(Collectors.toMap(
											m -> m.getKey(),
											m -> m.getValue()
									));

							// handle empty/null thisIntersectMaps?
							if(!thisIntersectMap.isEmpty()) {
								PathObject tileObj = PathObjects.createTileObject(GeometryTools.geometryToROI(rect, plane));
								tileObj.setName(String.format("Tile-r%dc%d_x%dy%d",yi, xi, x, y));
	//							logger.info(thisIntersectMap.toString());
								logger.info(String.format("Tile-r%dc%d_x%dy%d",yi, xi, x, y));
								tileIntersectROIs.put(tileObj, thisIntersectMap);
							}
						});
					})
				).get();

			}

			return tileIntersectROIs;

			// If there was an exception, the tile will be null
//			if (tileROIs.size() < tileGeometries.size()) {
//				logger.warn("Tiles lost during tiling: {}", tileGeometries.size() - tileROIs.size());
//				logger.warn("You may be able to avoid tiling errors by calling 'Simplify shape' on any complex annotations first.");
//			}
//
//			// Remove any empty/non-area tiles
//			return tileROIs.stream()
//					.filter(t -> !t.isEmpty() && t.isArea())
//					.collect(Collectors.toList());
		}

		public CompletableFuture<Void>  TileRecalcCompartmentsAndScores() throws RuntimeException {
			return TileRecalcCompartmentsAndScores(ignoreClasses, targets, compartments, (Class<? extends PathObject>) params.get("sourceType"),
					(double) params.get("downsample"),  (int) params.get("tileSize"), numThreads);
		}

		public CompletableFuture<Void> TileRecalcCompartmentsAndScores(Set<PathClass> ignoreClasses,
																	   Map<ColorTransform, Double> targets,
																	   Set<PathClass> compartments,
																	   Class<? extends PathObject> sourceType,
																	   double downsample,
																	   int tileSize,
																	   int numThreads
		) throws RuntimeException{

			if (numThreads <= 0)
				numThreads = 1;

			setupNewForkJoinPool(numThreads);

			Integer progAmount = 1;

			// Used for placing child objects inside ROI
			AtomicReference<Boolean> doAdjust = new AtomicReference<>(false);
			ROI combinedExcludeROI;
			Geometry combinedExcludeGeom;

			PathObjectHierarchy hierarchy = bImageData.getHierarchy();
			var pathObjs = hierarchy.getObjects(null, PathObject.class);
			ImageServer<BufferedImage> server = bImageData.getServer();
			Rectangle2D bounds = new Rectangle2D.Double();
			bounds.setFrame(0.0, 0.0, server.getWidth(), server.getHeight());
			CompletableFuture<Void> result = null;
			try {
				List<ROI> allIgnoreROIs = forkJoinPool.submit(() -> hierarchy.getAnnotationObjects().parallelStream()
						.filter(p -> p.getPathClass() != null && ignoreClasses.contains(p.getPathClass()))
						.map(p -> p.getROI())
						.collect(Collectors.toList())).get();
				List<PathObject> compartmentObjs = forkJoinPool.submit(() -> pathObjs.parallelStream().filter(p -> p.getPathClass() != null && compartments.contains(p.getPathClass()) && p.getClass() == sourceType)
						.collect(Collectors.toList())).get();

				logger.info(allIgnoreROIs.toString());
				combinedExcludeROI = RoiTools.union(allIgnoreROIs);
				// TODO: is there a simpler way to get this Map<PathClass, List<ROI>> from one stream, and then combine if there are multiple entries in the List<ROI>?
				// combine compartments into path objects
				// TODO: remove ignore annotations
				Map<PathClass, ROI> combCompartmentROIMap = new HashMap<>();
				for(PathClass c : compartments){
					List<ROI> theseCROIs = forkJoinPool.submit(()->compartmentObjs.parallelStream()
							.filter(p-> c == p.getPathClass())
							.map(p -> p.getROI())
							.collect(Collectors.toList())).get();
					ROI combinedC = RoiTools.union(theseCROIs);
					if(combinedC!=null && !combinedC.isEmpty()){
						combCompartmentROIMap.put(c, combinedC);
					}
				}
				if(combCompartmentROIMap.isEmpty()){
					logger.error("Combining compartments resulted in null? Check compartment annotations/sources...");
					return null;
				}

//				Uses default image plane, will not work for timeseries or z slices
				Map<PathObject, Map<PathClass, ROI>> tileIntersectROIs = computeTiledROIsForCompartments(bounds,
																										combCompartmentROIMap,
																										ImmutableDimension.getInstance(tileSize, tileSize),
																								true,
																									0);
//				Make pathObjects out of intersections and add to tileObj as children
				tileIntersectROIs.entrySet().parallelStream()
						.forEach(tileM ->{
							List<PathObject> intersectChildren = tileM.getValue().entrySet().parallelStream()
											.map(m -> PathObjects.createTileObject(m.getValue(), m.getKey(), null))
											.collect(Collectors.toList());
							PathObject tileObj = tileM.getKey();
							tileObj.addPathObjects(intersectChildren);
							hierarchy.addPathObject(tileObj);
						});


			} catch (ExecutionException | InterruptedException e) {
				throw new RuntimeException(e);
			}


			return null;

			// Make tiles for entire image of tileSize x tileSize
				// label tiles "Tile_(row, col)"
				// get empty tiles?
				// get intersections with compartments here?
			// for each tile, get intersection with each compartment
				// add compartment intersection as detection to parent tile hierarchy?
				// get intensity measurements, add to detection object AND parent tile



		}


		public CompletableFuture<Void>  TMARecalcCompartmentsAndScores() throws RuntimeException {
			return TMARecalcCompartmentsAndScores(ignoreClasses, targets, compartments, (Class<? extends PathObject>) params.get("sourceType"), (double) params.get("downsample"), numThreads);
		}

		// Exclude regions and add regions that weren't segmented well. Allows for manual adjustment of compartmentalization before scoring targets.
		public CompletableFuture<Void> TMARecalcCompartmentsAndScores(Set<PathClass> ignoreClasses,
																Map<ColorTransform, Double> targets,
																Set<PathClass> compartments,
																Class<? extends PathObject> sourceType,
																double downsample,
																int numThreads
		) throws RuntimeException {

//			progressBar.setProgress(-1);
//			progressL.setText("Quantifying TMA compartments...");
			// Adjust each compartment by subtracting the exclude region and adding the corresponding compartment adjustments
			// Iterate through compartments/detections to recreate them if adjustments were made
			// Calculate AQUA metrics for each target
			CompletableFuture<Void> result = null;
			AtomicReference<Boolean> doAdjust = new AtomicReference<>(false);
			// Combine exclude regions, but do not create a new merged object
			ROI combinedExcludeROI;
			Geometry combinedExcludeGeom;

			logger.info("Updating existing compartments with any new annotations, calcuating AQUA metrics...");

//			ImageData<BufferedImage> imageData = qupath.getImageData();
			PathObjectHierarchy hierarchy = bImageData.getHierarchy();

			TMAGrid tmaGrid = hierarchy.getTMAGrid();
			if(tmaGrid==null){
				logger.error("TMA grid is null, de-array TMA before scoring!");
//				throw new RuntimeException();
				return result;
			}
			List<TMACoreObject> tmaCores = tmaGrid.getTMACoreList();
			// an estimate if there are the same amount of compartments per TMA spot....
			// could just try and use the amount of tasks queued... doesn't work in time before forkJoinPool is done with submit/invoke
//			setEstNumTasks((int) tmaCores.size() * compartments.size());
			Integer progAmount = 1;
			if (numThreads <= 0)
				numThreads = 1;

			setupNewForkJoinPool(numThreads);

			ImageServer<BufferedImage> server = bImageData.getServer();

			try {
				// These operations block the GUI threads.... can't really replace them though because I need to collect the annotations before starting
				// maybe can rewrite this whole block as a sequential task to submit to the pool?
				List<ROI> allIgnoreROIs = forkJoinPool.submit(() -> hierarchy.getAnnotationObjects().parallelStream()
						.filter(p -> p.getPathClass() != null && ignoreClasses.contains(p.getPathClass()))
						.map(p -> p.getROI())
						.collect(Collectors.toList())).get();
				List<PathObject> tmaCoreChildren = Collections.synchronizedList(forkJoinPool.submit(() -> tmaCores.parallelStream()
						.flatMap(core -> core.getChildObjects().stream())
						.collect(Collectors.toList())).get());

				if(tmaCoreChildren.size() < 1){
					logger.error("Compartments and annotations must be inserted into TMA hierarchy before scoring!");
					return result;
				}

//				Would be faster to associate each ignore annotation into the TMACore hierarchy, and then just subtract the ignore annotation for each core.
//				But the insert hierarchy doesn't work unless there is a sufficient overlap exists between children and parent objects...
				// Need to make sure that all TMA cores have their annotations inserted into the hierarchy or else the getChildObjects() will miss annotations...
				// insertHierarchy can miss annotations that are outside of TMA core parent. Maybe there is a way to use the missing annotations
				// and check if any TMA cores x,y contain that annotation (or vice versa).
				setEstNumTasks(tmaCoreChildren.size());
				logger.info(allIgnoreROIs.toString());
//				combinedExcludeROI = combinePathObjs(allIgnoreAnnotations, false);
				combinedExcludeROI = RoiTools.union(allIgnoreROIs);
				if (combinedExcludeROI != null && !combinedExcludeROI.isEmpty()) {
					doAdjust.set(true);
					combinedExcludeGeom = combinedExcludeROI.getGeometry();
				} else{
					doAdjust.set(false);
					combinedExcludeGeom = null;
				}

//				https://stackoverflow.com/questions/53558753/how-do-i-close-a-thread-local-autocloseable-used-in-parallel-stream
//				if (threadImageServerMap.isEmpty()) {
//					threadImageServerMap = new ConcurrentHashMap<>();
//				} else {
//					threadImageServerMap.forEach((k, c) -> {
//						logger.info("trying to close image server on thread... " + k.toString());
//						closeQuietly(c);
//					});
//					threadImageServerMap.clear();
//				}

//				Ugly, better to make this a forkJoinTask or runnable without lambda?
//				https://stackoverflow.com/questions/23320407/how-to-cancel-java-8-completable-future
				result = CompletableFuture.runAsync(() -> tmaCoreChildren.parallelStream().forEach(pathObj -> {
									if (isCancelled.get()) {
										throw new CancellationException();
									}
//					ignore the objects that are unclassified/PathClass == null
									if (pathObj.getPathClass() != null && compartments.contains(pathObj.getPathClass()) && pathObj.getClass() == sourceType) {
//										ImageServer<BufferedImage> server = threadImageServerMap.computeIfAbsent(Thread.currentThread(), t -> bImageData.getServer());
										PathObject adjpathObj;
										ROI adjpathObjROI = pathObj.getROI();
										boolean intersectsExclude = false;
										// is not very efficient as the excluded areas may only be in certain TMA spots....
										// getting an excluded ROI for each TMA core is not as parallellizable and does not work if the excluded region does not fit within the QuPath hierarchy
										// not very efficient use of if statements when these variables are set before the parallelStream starts
										// case switch inside parallelStream? does this work?
										if (doAdjust.get() && combinedExcludeGeom != null) {
											intersectsExclude = adjpathObjROI.getGeometry().intersects(combinedExcludeGeom);
											if (intersectsExclude) {
												adjpathObjROI = RoiTools.combineROIs(adjpathObjROI, combinedExcludeROI, RoiTools.CombineOp.SUBTRACT);
											}
										}
										if (adjpathObjROI.isEmpty()) {
											logger.info(String.format("Detection %s compartment is now empty, skipping AQUA metrics...", pathObj.getPathClass().toString()));
											//						removeObject(detection, true);
											return;
										} else if (doAdjust.get() && intersectsExclude) {
											logger.info(String.format("Adjusting %s compartment based on new annotations...", pathObj.getPathClass().toString()));
											adjpathObj = PathObjects.createAnnotationObject(adjpathObjROI, pathObj.getPathClass());
											hierarchy.addPathObject(adjpathObj);
											bImageData.getHierarchy().addPathObjectBelowParent(pathObj.getParent(), adjpathObj, true);
											hierarchy.removeObject(pathObj, true);
										} else {
											adjpathObj = pathObj;
										}
										// Calculate AQUA scoring metrics for new compartment detections for all targets
										try {
											getTargetsIntensityScores_OpenCV(server, adjpathObj);
//											getTargetsIntensityScores(adjpathObj);
										} catch (IOException ex) {
											logger.warn(ex.toString());
										}
									}
									incrementProgress(progAmount);
								}),
								forkJoinPool)
						.thenRun(() -> {
							Platform.runLater(() -> {
								progressLabel.setText("Completed scoring TMA compartments!");
								quantProgressBar.setProgress(1.0);
								exportMeasButton.setDisable(false);
								exportMeasMenuItem.setDisable(false);
								startQuantButton.setDisable(false);
							});
//							threadImageServerMap.forEach((k, c) -> {
//								logger.info("trying to close image server on thread... " + k.toString());
//								closeQuietly(c);
//							});
//							threadImageServerMap.clear();
						})
						.exceptionally(ex -> {
							ex.printStackTrace();
//							logger.warn(Arrays.toString(ex.getStackTrace()));
							logger.warn("TMARecalcCompartmentsAndScores: " + ex);
							return null;
						});
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			} finally {
//				no effect on commonPool
				forkJoinPool.shutdown();
			}
			return result;
		}

		public enum Compartments {
			/**
			 * Nucleus only
			 */
			NUCLEUS,
			/**
			 * Full cell region, with nucleus removed
			 */
			CYTOPLASM,
			/**
			 * Full cell region
			 */
			CELL,
			/**
			 * Cell boundary, with interior removed
			 */
			MEMBRANE

		}

		/**
		 * Requested intensity measurements.
		 */
		public enum Measurements {
			/**
			 * Arithmetic mean
			 */
			MEAN,
			/**
			 * Median value
			 */
			MEDIAN,
			/**
			 * Minimum value
			 */
			MIN,
			/**
			 * Maximum value
			 */
			MAX,
			/**
			 * Standard deviation value
			 */
			STD_DEV,
			/**
			 * Variance value
			 */
			VARIANCE;

			private String getMeasurementName() {
				switch (this) {
					case MAX:
						return "Max";
					case MEAN:
						return "Mean";
					case MEDIAN:
						return "Median";
					case MIN:
						return "Min";
					case STD_DEV:
						return "Std.Dev.";
					case VARIANCE:
						return "Variance";
					default:
						throw new IllegalArgumentException("Unknown measurement " + this);
				}
			}

			private double getMeasurement(StatisticalSummary stats) {
				switch (this) {
					case MAX:
						return stats.getMax();
					case MEAN:
						return stats.getMean();
					case MEDIAN:
						if (stats instanceof DescriptiveStatistics)
							return ((DescriptiveStatistics) stats).getPercentile(50.0);
						else
							return Double.NaN;
					case MIN:
						return stats.getMin();
					case STD_DEV:
						return stats.getStandardDeviation();
					case VARIANCE:
						return stats.getVariance();
					default:
						throw new IllegalArgumentException("Unknown measurement " + this);
				}
			}
		}

		public boolean getTargetsIntensityScores_OpenCV(ImageServer<BufferedImage> server, PathObject pathObject) throws IOException {
			//get params required
			double downsample;
			boolean rescaleScore;
			boolean normalizeScore;
			double maxFloatValue;
			try {
				downsample = (double) params.get("downsample");
				rescaleScore = (boolean) params.get("rescaleScore");
				normalizeScore = (boolean) params.get("normalizeScore");
				maxFloatValue = (double) params.get("maxFloatValue");
			} catch (Exception ex) {
//				ex.printStackTrace();
				throw new RuntimeException(ex);
			}
			return getTargetsIntensityScores_OpenCV(server, pathObject, targets, cellCompartments, measurements,
					downsample, rescaleScore, normalizeScore, maxFloatValue);

		}

		public boolean getTargetsIntensityScores_OpenCV(ImageServer<BufferedImage> server, PathObject pathObject,
															Map<ColorTransform, Double> targets,
															Collection<Compartments> cellCompartments,
															Collection<Measurements> measurements,
															double downsample, boolean rescaleScore, boolean normalizeScore,
															double maxFloatValue) throws IOException {
//			It would be nice to close the server after use, but doing this also closes the main server across all threads....
			try {
				// Determine amount to downsample
//				var server = imageData.getServer();
				String className = pathObject.getPathClass().toString();
				PixelCalibration pc = server.getPixelCalibration();
				PixelType pixType = server.getPixelType();
				int bitDepth = server.getPixelType().getBitsPerPixel();
				double mppSq = pc.getPixelHeightMicrons() * pc.getPixelWidthMicrons();
				//    println 'Squarred MPP: ' + mppSq.toString();

				// Use mean intensity to calculate AQUA score as (mean intensity)/(MPP^2 * exposure_time)
				MeasurementList measList = pathObject.getMeasurementList();

				// Add shape measurements
				double annotationArea = pathObject.getROI().getArea();
				measList.putMeasurement(className + " area px", annotationArea);
				measList.putMeasurement(className + " area um^2", annotationArea * mppSq);
				measList.putMeasurement("MPP^2", mppSq);
				measList.putMeasurement("Channel bitdepth", bitDepth);
				int bitDepthVal = (int) Math.pow(2, bitDepth);

				if (downsample <= 0) {
					logger.warn("Effective downsample must be > 0 (requested value {})", downsample);
					downsample = 1.0;
				}

				measList.putMeasurement("downsample", downsample);

				Map<String, DescriptiveStatistics> allStats = new ConcurrentHashMap<>();
				Map<String, String> measNames = new ConcurrentHashMap<>();
				for (Map.Entry<ColorTransform, Double> tar : targets.entrySet()) {
					String targetName = tar.getKey().toString();
					allStats.put(targetName, new DescriptiveStatistics(DescriptiveStatistics.INFINITE_WINDOW));
					String measName = targetName + " Intensity in " + className;
					measNames.put(targetName, measName);
					logger.info(String.format("Scoring %s in %s", targetName, className));
				}

				if (pathObject instanceof PathCellObject) {
					PathCellObject cell = (PathCellObject) pathObject;
					if (cell.getROI() == null) {
						logger.warn("ROI is null, cannot get intensity scores...");
						return false;
					}

					// Get bounds
					RegionRequest region = RegionRequest.createInstance(server.getPath(), downsample, cell.getROI());
					BufferedImage img = server.readBufferedImage(region);
					if (img == null) {
						logger.error("Could not read image - unable to compute intensity features for {}", pathObject);
						return false;
					}

					// Create mask ROIs for cell and nucleus
					// If we just have 1 pixel, we want to use it so that the mean/min/max measurements are valid (even if nothing else is)
					byte[] cellBytes = null;
					if (img.getWidth() * img.getHeight() > 1) {
						BufferedImage imgMask = BufferedImageTools.createROIMask(img.getWidth(), img.getHeight(), cell.getROI(), region);
						cellBytes = ((DataBufferByte) imgMask.getRaster().getDataBuffer()).getData();
					}
					byte[] nucBytes = null;
					if (cell.getNucleusROI() != null) {
						if (img.getWidth() * img.getHeight() > 1) {
							BufferedImage imgMask = BufferedImageTools.createROIMask(img.getWidth(), img.getHeight(), cell.getNucleusROI(), region);
							nucBytes = ((DataBufferByte) imgMask.getRaster().getDataBuffer()).getData();
						}
					}

					//				not implemented yet
					return false;

					//For mean, median, stdev, etc.
					//				measureCells_OpenCV(nucBytes, cellBytes, Map.of(1.0, cell), channels, cellCompartments, measurements);
				} else {
					ROI roi = pathObject.getROI();
					if (roi == null) {
						logger.warn("ROI is null, cannot get intensity scores...");
						return false;
					}
					// Create tiled ROIs, if required
					ImmutableDimension sizePreferred = ImmutableDimension.getInstance((int) (3000 * downsample), (int) (3000 * downsample));
					Collection<? extends ROI> rois = RoiTools.computeTiledROIs(roi, sizePreferred, sizePreferred, false, 0);
					if (rois.size() > 1)
						logger.info("Splitting {} into {} tiles for intensity measurements", roi, rois.size());

					for (ROI pathROI : rois) {

						if (Thread.currentThread().isInterrupted()) {
							logger.warn("Measurement skipped - thread interrupted!");
							return false;
						}

						// Get bounds
//						int pad = (int) Math.ceil(downsample * 2);
						RegionRequest region = RegionRequest.createInstance(server.getPath(), downsample, pathROI);
//								.pad2D(pad, pad)
//								.intersect2D(0, 0, server.getWidth(), server.getHeight());
						BufferedImage img = server.readBufferedImage(region);
						if (img == null) {
							logger.error("Could not read image - unable to compute intensity features for {}", pathObject);
							return false;
						}

						// Create mask ROI if necessary
						// If we just have 1 pixel, we want to use it so that the mean/min/max measurements are valid (even if nothing else is)
						byte[] maskBytes = null;
						if (img.getWidth() * img.getHeight() > 1) {
							BufferedImage imgMask = BufferedImageTools.createROIMask(img.getWidth(), img.getHeight(), pathROI, region);
							maskBytes = ((DataBufferByte) imgMask.getRaster().getDataBuffer()).getData();
						}

						int w = img.getWidth();
						int h = img.getHeight();
						float[] pixels = null;

						for (Map.Entry<ColorTransform, Double> tar : targets.entrySet()) {
							ColorTransform transform = tar.getKey();
							//						double expTime = tar.getValue();
							DescriptiveStatistics thisStats = allStats.get(transform.toString());

							// Transform the pixels
							pixels = transform.extractChannel(server, img, pixels);

							// Create the simple image
							SimpleModifiableImage pixelImage = SimpleImages.createFloatImage(pixels, w, h);

//							assert pixelImage.getHeight() * pixelImage.getWidth() == pixels.length;

							// Apply any arbitrary mask and add values to stats
							if (maskBytes != null) {
								for (int i = 0; i < pixels.length; i++) {
									if (maskBytes[i] == (byte) 0) {
//										pixelImage.setValue(i % w, i / w, Float.NaN);
										continue;
									}
									thisStats.addValue((double) pixelImage.getValue(i % w, i / w));
								}
								allStats.put(transform.toString(), thisStats);
							}
						}
					}
					addMeasurements_OpenCV(allStats, measNames, pathObject, measurements);
				}

				for (Map.Entry<ColorTransform, Double> tar : targets.entrySet()) {
					String targetName = tar.getKey().toString();
					double exposure_time = tar.getValue();
					String measName = measNames.get(targetName);
					double targetMean = measList.getMeasurementValue(measName + ": Mean");
					// double sumInt = targetMean*annotationArea;
					// measList.putMeasurement(targetName+' in '+className+' Sum Intensity', sumInt);
					// Debugging, would load from available metadata
					if (exposure_time == 0.0 || exposure_time < 0) {
						exposure_time = 1000;
						measList.putMeasurement(targetName + " exposure time (ms)", 0);
					} else {
						measList.putMeasurement(targetName + " exposure time (ms)", exposure_time);
					}

					// double MeanI_S = targetMean/(exposure_time/1000)
					// measList.putMeasurement(targetName+' in '+className+' Mean I/[exp time (s)]', MeanI_S);
					// Intensity/(um^2*sec)
					// double QIF_area = targetMean/mppSq;
					// measList.putMeasurement(targetName+' in '+className+' Sum I/um^2', QIF_area);
					//if pixelType float, skip [vetra Polaris data]
					if (pixType.isFloatingPoint()) {
						double QIF_areaS = (targetMean / mppSq);
						measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
					} else if (rescaleScore && !normalizeScore) {
						//assumes score has already been normalized, but turned into an unsigned int datatype for image manipulation
						//using bitdepth and maxFloatValue to rescale
						double rescaleFactor = (maxFloatValue / bitDepthVal);
						double QIF_areaS = (targetMean / mppSq) * rescaleFactor;
						measList.putMeasurement("Rescale factor", rescaleFactor);
						measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
					} else if (normalizeScore) {
						double QIF_areaS = (targetMean / mppSq) / (bitDepthVal * exposure_time / 1000);
						measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2*[exp time (s)]*[2^bitDepth])", QIF_areaS);
					} else {
						// no normalization
						double QIF_areaS = (targetMean / mppSq);
						measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
					}
					//    double totalPx = server.getHeight()*server.getWidth();
					//    println 'Total pixels: '+ totalPx.toString();
					//    double QIF_areaPercent = targetMean*annotationArea/(100*annotationArea/totalPx);
					//    measList.putMeasurement(targetName+' in '+className+' Sum I/(Compartment % Area)', QIF_areaPercent);
					//    double QIF_areaPercentS = QIF_areaPercent/(exposure_time);
					//    measList.putMeasurement(targetName+' in '+className+' Sum I/([Compartment % Area]*[exp time (ms)])', QIF_areaPercentS);
				}

				// Lock any measurements that require it
//				if (pathObject instanceof PathAnnotationObject)
//					((PathAnnotationObject) pathObject).setLocked(true);
//				else if (pathObject instanceof TMACoreObject)
//					((TMACoreObject) pathObject).setLocked(true);
//			clean up vars?
//			server.close();
//			pathObject.getMeasurementList().close();
			measList.close();
//			server = null;
			pathObject = null;
			measList = null;
			measNames = null;
			System.gc();

		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
//			clean up vars?
//			targets = null;
//			measurements = null;
//			cellCompartments = null;
			System.gc();
		}
			return true;
		}

//		private static void measureCells_OpenCV(
//				byte[] nucBytes, byte[] cellBytes,
//				Map<? extends Number, ? extends PathObject> pathObjects,
//				Map<String, ColorTransform> channels,
//				Collection<Compartments> cellCompartments,
//				Collection<Measurements> measurements) {
//
//			var array = mapToArray(pathObjects);
//			int width = ipNuclei.getWidth();
//			int height = ipNuclei.getHeight();
//			ImageProcessor ipMembrane = new FloatProcessor(width, height);
//			ImageProcessor ipCytoplasm = ipCells.duplicate();
//			for (int y = 0; y < height; y++) {
//				for (int x = 0; x < width; x++) {
//					float cell = ipCells.getf(x, y);
//					float nuc = ipNuclei.getf(x, y);
//					if (nuc != 0f)
//						ipCytoplasm.setf(x, y, 0f);
//					if (cell == 0f)
//						continue;
//					// Check 4-neighbours to decide if we're at the membrane
//					if ((y >= 1 && ipCells.getf(x, y-1) != cell) ||
//							(y < height-1 && ipCells.getf(x, y+1) != cell) ||
//							(x >= 1 && ipCells.getf(x-1, y) != cell) ||
//							(x < width-1 && ipCells.getf(x+1, y) != cell))
//						ipMembrane.setf(x, y, cell);
//				}
//			}
//
//			var imgNuclei = new PixelImageIJ(ipNuclei);
//			var imgCells = new PixelImageIJ(ipCells);
//			var imgCytoplasm = new PixelImageIJ(ipCytoplasm);
//			var imgMembrane = new PixelImageIJ(ipMembrane);
//
//			for (var entry : channels.entrySet()) {
//				var img = new PixelImageIJ(entry.getValue());
//				if (cellCompartments.contains(Compartments.NUCLEUS))
//					measureObjects(img, imgNuclei, array, entry.getKey().trim() + ": " + "Nucleus", measurements);
//				if (cellCompartments.contains(Compartments.CYTOPLASM))
//					measureObjects(img, imgCytoplasm, array, entry.getKey().trim() + ": " + "Cytoplasm", measurements);
//				if (cellCompartments.contains(Compartments.MEMBRANE))
//					measureObjects(img, imgMembrane, array, entry.getKey().trim() + ": " + "Membrane", measurements);
//				if (cellCompartments.contains(Compartments.CELL))
//					measureObjects(img, imgCells, array, entry.getKey().trim() + ": " + "Cell", measurements);
//			}
//
//		}

		public void addMeasurements_OpenCV(DescriptiveStatistics allStats,
												  String allMeasNames,
												  PathObject pathObject,
												  Collection<Measurements> measurements){
			// Add measurements
			if (pathObject == null)
				return;
			try (var ml = pathObject.getMeasurementList()) {
				for (var m : measurements) {
					ml.putMeasurement(allMeasNames + ": " + m.getMeasurementName(), m.getMeasurement(allStats));
				}
			}
		}


		public void addMeasurements_OpenCV(Map<String, DescriptiveStatistics> allStats,
												  Map<String, String> allMeasNames,
												  PathObject pathObject,
												  Collection<Measurements> measurements){
			// Add measurements
			if (pathObject == null)
				return;
			for (Map.Entry<String, DescriptiveStatistics> stats : allStats.entrySet()) {
				try (var ml = pathObject.getMeasurementList()) {
					for (var m : measurements) {
						ml.putMeasurement(allMeasNames.get(stats.getKey()) + ": " + m.getMeasurementName(), m.getMeasurement(stats.getValue()));
					}
				}
			}
		}

		private void measureObject_OpenCV(SimpleModifiableImage img,  byte[] maskBytes,
												 PathObject pathObject, String baseName,
												 Collection<Measurements> measurements){
			DescriptiveStatistics stats = null;
			stats = measureObject_OpenCV(img, maskBytes, stats);
			addMeasurements_OpenCV(stats, baseName, pathObject, measurements);
		}
		private DescriptiveStatistics measureObject_OpenCV(SimpleModifiableImage img,
																  byte[] maskBytes,
																  DescriptiveStatistics stats) {

			// Initialize stats
			if(stats == null) {
				stats = new DescriptiveStatistics(DescriptiveStatistics.INFINITE_WINDOW);
			}

			int w = img.getWidth();
			int h = img.getHeight();
			// Apply any arbitrary mask and compute stats
			if (maskBytes != null) {
				for (int i = 0; i < w * h; i++) {
					if (maskBytes[i] == (byte) 0) {
//						img.setValue(i % w, i / w, Float.NaN);
						continue;
					}
					stats.addValue((double) img.getValue(i % w, i / w));
				}
			}
			return stats;
		}


		public void getTargetsIntensityScores(PathObject pathObject) throws IOException {
			//get params required
			double downsample;
			boolean rescaleScore;
			boolean normalizeScore;
			double maxFloatValue;
			try {
				downsample = (double) params.get("downsample");
				rescaleScore = (boolean) params.get("rescaleScore");
				normalizeScore = (boolean) params.get("normalizeScore");
				maxFloatValue = (double) params.get("maxFloatValue");
			} catch (Exception ex) {
//				ex.printStackTrace();
				throw new RuntimeException(ex);
			}
			getTargetsIntensityScores(qupath.getImageData(), pathObject, targets, cellCompartments, measurements, downsample, rescaleScore, normalizeScore, maxFloatValue);
		}

		public void getTargetsIntensityScores(ImageData<BufferedImage> imageData, PathObject pathObject,
													 Map<ColorTransform, Double> targets,
													 Collection<Compartments> cellCompartments,
													 Collection<Measurements> measurements,
													 double downsample, boolean rescaleScore, boolean normalizeScore,
													 double maxFloatValue) throws IOException {

			try {
				// Convert to binary mask Mat
				ROI roi = pathObject.getROI();
				String className = pathObject.getPathClass().toString();
				ImageServer<BufferedImage> server = imageData.getServer();

				int pad = (int) Math.ceil(downsample * 2);
				RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, roi)
						.pad2D(pad, pad)
						.intersect2D(0, 0, server.getWidth(), server.getHeight());

				PathImage<ImagePlus> pathImage = IJTools.convertToImagePlus(server, request);
				//			ImagePlus imp = pathImage.getImage();

				PixelCalibration pc = server.getPixelCalibration();
				PixelType pixType = server.getPixelType();
				int bitDepth = server.getPixelType().getBitsPerPixel();
				double mppSq = pc.getPixelHeightMicrons() * pc.getPixelWidthMicrons();
				//    println 'Squarred MPP: ' + mppSq.toString();

				// Use mean intensity to calculate AQUA score as (mean intensity)/(MPP^2 * exposure_time)
				MeasurementList measList = pathObject.getMeasurementList();

				// Add shape measurements
				double annotationArea = pathObject.getROI().getArea();
				measList.putMeasurement(className + " area px", annotationArea);
				measList.putMeasurement(className + " area um^2", annotationArea * mppSq);
				measList.putMeasurement("MPP^2", mppSq);
				measList.putMeasurement("Channel bitdepth", bitDepth);
				int bitDepthVal = (int) Math.pow(2, bitDepth);
				//			int bitDepthVal = (int) Math.pow(2, 16);

				Map<String, ImageProcessor> channels = new LinkedHashMap<>();
				Map<String, String> measNames = new LinkedHashMap<>();

				//Don't like this, is there a way to convert ROI to a binary mask OpenCV Mat directly??
				//Using ImageJ to create a binary mask [0,1] of ROI
				ByteProcessor bpCell = new ByteProcessor(request.getWidth(), request.getHeight());
				bpCell.setValue(1.0);
				Roi roiIJ = IJTools.convertToIJRoi(roi, pathImage);
				bpCell.fill(roiIJ);

				//Might not be the best performance. Would like to recode to use only OpenCV_core Mats and pointers/mask indexing.
				for (Map.Entry<ColorTransform, Double> tar : targets.entrySet()) {
					ColorTransform targetTransform = tar.getKey();
					String targetName = targetTransform.toString();
					ImageProcessor ipChannel = OpenCVTools.matToImageProcessor(ImageOps.buildImageDataOp(targetTransform).apply(imageData, request));
					String measName = targetName + " Intensity in " + className;
					measNames.put(targetName, measName);
					channels.put(measName, ipChannel);
					logger.info(String.format("Scoring %s in %s", targetName, className));
				}

				if (pathObject instanceof PathCellObject) {
					PathCellObject cell = (PathCellObject) pathObject;
					ByteProcessor bpNucleus = new ByteProcessor(request.getWidth(), request.getHeight());
					if (cell.getNucleusROI() != null) {
						bpNucleus.setValue(1.0);
						Roi roiNucleusIJ = IJTools.convertToIJRoi(cell.getNucleusROI(), pathImage);
						bpNucleus.fill(roiNucleusIJ);
					}
					//For mean, median, stdev, etc.
					measureCells(bpNucleus, bpCell, Map.of(1.0, cell), channels, cellCompartments, measurements);
					//Calculate sum intensity in compartment
					//        measureObjSumInt(img, imgLabels, new PathObject[] {pathObject}, targetName);
				} else {
					var imgLabels = new PixelImageIJ(bpCell);
					for (Map.Entry<String, ImageProcessor> entry : channels.entrySet()) {
						var img = new PixelImageIJ(entry.getValue());
						//For mean, median, stdev, etc.
						measureObjects(img, imgLabels, new PathObject[]{pathObject}, entry.getKey(), measurements);
						//Calculate sum intensity in compartment
						//            measureObjSumInt(img, imgLabels, new PathObject[] {pathObject}, targetName);
					}
				}


				for (Map.Entry<ColorTransform, Double> tar : targets.entrySet()) {
					String targetName = tar.getKey().toString();
					double exposure_time = tar.getValue();
					String measName = measNames.get(targetName);
					double targetMean = measList.getMeasurementValue(measName + ": Mean");
					// double sumInt = targetMean*annotationArea;
					// measList.putMeasurement(targetName+' in '+className+' Sum Intensity', sumInt);
					// Debugging, would load from available metadata
					if (exposure_time == 0.0 || exposure_time < 0) {
						exposure_time = 1000;
						measList.putMeasurement(targetName + " exposure time (ms)", 0);
					} else {
						measList.putMeasurement(targetName + " exposure time (ms)", exposure_time);
					}

					// double MeanI_S = targetMean/(exposure_time/1000)
					// measList.putMeasurement(targetName+' in '+className+' Mean I/[exp time (s)]', MeanI_S);
					// Intensity/(um^2*sec)
					// double QIF_area = targetMean/mppSq;
					// measList.putMeasurement(targetName+' in '+className+' Sum I/um^2', QIF_area);
					//if pixelType float, skip [vetra Polaris data]
					if (pixType.isFloatingPoint()) {
						double QIF_areaS = (targetMean / mppSq);
						measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
					} else if (rescaleScore && !normalizeScore) {
						//assumes score has already been normalized, but turned into an unsigned int datatype for image manipulation
						//using bitdepth and maxFloatValue to rescale
						double rescaleFactor = (maxFloatValue / bitDepthVal);
						double QIF_areaS = (targetMean / mppSq) * rescaleFactor;
						measList.putMeasurement("Rescale factor", rescaleFactor);
						measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
					} else if (normalizeScore) {
						double QIF_areaS = (targetMean / mppSq) / (bitDepthVal * exposure_time / 1000);
						measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2*[exp time (s)]*[2^bitDepth])", QIF_areaS);
					} else {
						// no normalization
						double QIF_areaS = (targetMean / mppSq);
						measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
					}
					//    double totalPx = server.getHeight()*server.getWidth();
					//    println 'Total pixels: '+ totalPx.toString();
					//    double QIF_areaPercent = targetMean*annotationArea/(100*annotationArea/totalPx);
					//    measList.putMeasurement(targetName+' in '+className+' Sum I/(Compartment % Area)', QIF_areaPercent);
					//    double QIF_areaPercentS = QIF_areaPercent/(exposure_time);
					//    measList.putMeasurement(targetName+' in '+className+' Sum I/([Compartment % Area]*[exp time (ms)])', QIF_areaPercentS);
				}

//				clean up vars?
//				server.close();
				measList.close();
				server = null;
				pathImage = null;
				channels = null;
				request = null;
				measList = null;
				measNames = null;
				roiIJ = null;
				bpCell = null;
				roi = null;
				System.gc();

			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {

//				clean up vars?
				imageData = null;
				targets = null;
				measurements = null;
				cellCompartments = null;
				System.gc();

			}
		}


		/**
		 * Make cell measurements based on labelled images.
		 * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
		 *
		 * @param ipNuclei labelled image representing nuclei
		 * @param ipCells labelled image representing cells
		 * @param pathObjects cell objects mapped to integer values in the labelled images
		 * @param channels channels to measure, mapped to the name to incorporate into the measurements for that channel
		 */
		private static void measureCells(
				ImageProcessor ipNuclei, ImageProcessor ipCells,
				Map<? extends Number, ? extends PathObject> pathObjects,
				Map<String, ImageProcessor> channels,
				Collection<Compartments> cellCompartments,
				Collection<Measurements> measurements) {

			var array = mapToArray(pathObjects);
			int width = ipNuclei.getWidth();
			int height = ipNuclei.getHeight();
			ImageProcessor ipMembrane = new FloatProcessor(width, height);
			ImageProcessor ipCytoplasm = ipCells.duplicate();
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					float cell = ipCells.getf(x, y);
					float nuc = ipNuclei.getf(x, y);
					if (nuc != 0f)
						ipCytoplasm.setf(x, y, 0f);
					if (cell == 0f)
						continue;
					// Check 4-neighbours to decide if we're at the membrane
					if ((y >= 1 && ipCells.getf(x, y-1) != cell) ||
							(y < height-1 && ipCells.getf(x, y+1) != cell) ||
							(x >= 1 && ipCells.getf(x-1, y) != cell) ||
							(x < width-1 && ipCells.getf(x+1, y) != cell))
						ipMembrane.setf(x, y, cell);
				}
			}

			var imgNuclei = new PixelImageIJ(ipNuclei);
			var imgCells = new PixelImageIJ(ipCells);
			var imgCytoplasm = new PixelImageIJ(ipCytoplasm);
			var imgMembrane = new PixelImageIJ(ipMembrane);

			for (var entry : channels.entrySet()) {
				var img = new PixelImageIJ(entry.getValue());
				if (cellCompartments.contains(Compartments.NUCLEUS))
					measureObjects(img, imgNuclei, array, entry.getKey().trim() + ": " + "Nucleus", measurements);
				if (cellCompartments.contains(Compartments.CYTOPLASM))
					measureObjects(img, imgCytoplasm, array, entry.getKey().trim() + ": " + "Cytoplasm", measurements);
				if (cellCompartments.contains(Compartments.MEMBRANE))
					measureObjects(img, imgMembrane, array, entry.getKey().trim() + ": " + "Membrane", measurements);
				if (cellCompartments.contains(Compartments.CELL))
					measureObjects(img, imgCells, array, entry.getKey().trim() + ": " + "Cell", measurements);
			}

		}


		private static PathObject[] mapToArray(Map<? extends Number, ? extends PathObject> pathObjects) {
			Number[] labels = new Number[pathObjects.size()];
			int n = 0;
			long maxLabel = 0L;
			int invalidLabels = 0;
			for (var label : pathObjects.keySet()) {
				long lab = label.longValue();
				if (lab < 0 || lab != label.doubleValue() || lab >= Integer.MAX_VALUE) {
					invalidLabels++;
				} else {
					labels[n] = label;
					maxLabel = Math.max(lab, maxLabel);
					n++;
				}
			}

			if (invalidLabels > 0) {
				logger.warn("Only {}/{} labels are integer values >= 0 and < Integer.MAX_VALUE, the rest will be discarded!",
						n, pathObjects.size());
			}

			PathObject[] array = new PathObject[n];
			for (var label : labels) {
				array[label.intValue()-1] = pathObjects.get(label);
			}
			return array;
		}


		/**
		 * Measure objects within the specified image, adding them to the corresponding measurement lists.
		 * @param img intensity values to measure
		 * @param imgLabels labels corresponding to objects
		 * @param pathObjects array of objects, where array index for an object is 1 less than the label in imgLabels
		 * @param baseName base name to include when adding measurements (e.g. the channel name)
		 */
		private static void measureObjects(
				SimpleImage img, SimpleImage imgLabels,
				PathObject[] pathObjects,
				String baseName,
				Collection<Measurements> measurements) {

			// Initialize stats
			int n = pathObjects.length;
			DescriptiveStatistics[] allStats = new DescriptiveStatistics[n];
			for (int i = 0; i < n; i++)
				allStats[i] = new DescriptiveStatistics(DescriptiveStatistics.INFINITE_WINDOW);

			// Compute statistics
			int width = img.getWidth();
			int height = img.getHeight();
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int label = (int)imgLabels.getValue(x, y);
					if (label <= 0 || label > n)
						continue;
					float val = img.getValue(x, y);
					allStats[label-1].addValue(val);
				}
			}

			// Add measurements
			for (int i = 0; i < n; i++) {
				var pathObject = pathObjects[i];
				if (pathObject == null)
					continue;
				var stats = allStats[i];
				try (var ml = pathObject.getMeasurementList()) {
					for (var m : measurements) {
						ml.putMeasurement(baseName + ": " + m.getMeasurementName(), m.getMeasurement(stats));
					}
				}
			}
		}

//  This might be useful for handling other csv metadata, but honestly should just use a dataframe library if that is really necessary
//	import java.io.BufferedReader;
//	import java.io.FileReader;
//
//	def readCSVtoDF(String csvpath, String indexName) {
//		// Create BufferedReader
//		BufferedReader csvReader = new BufferedReader(new FileReader(csvpath));
//		Map<String, ArrayList<String>> dataframe = new LinkedHashMap<String, ArrayList<String>>();
//		header = csvReader.readLine();
//		//    header = "test,test1,test2";
//		ArrayList<String> headerContent = new ArrayList<String>(header.split(",").toList());
//		//    println headerContent
//		int index = headerContent.indexOf(indexName);
//		//    println index
//		//    println headerContent[index]
//		int r = 0;
//		useRowNumbers = false;
//		if (index == -1) {
//			prinln String.format('Header does not contain %s! Defaulting to using row numbers...', indexName)
//			useRowNumbers = true;
//		}
//		dataframe.put('Header', headerContent);
//		while ((row = csvReader.readLine()) != null) {
//			//        println row
//			ArrayList<String> rowContent = new ArrayList<String>(row.split(",").toList());
//			if (useRowNumbers) {
//				dataframe.put(r, rowContent);
//				r += 1;
//			} else {
//				rowName = rowContent[index];
//				int j = 1;
//				while (true) {
//					if (dataframe.containsKey(rowName)) {
//						println String.format('rowName %s is duplicated! Resolving by appending integer...', rowName);
//						rowName = String.format('%1$s_%2$x', rowContent[index], j);
//						j += 1;
//					} else {
//						break;
//					}
//				}
//				dataframe.put(rowName, rowContent);
//			}
//		}
//		//    println dataframe;
//		return dataframe;
//	}
	}

//	public class CompQuantMeasurements {
//		private final static Logger logger = LoggerFactory.getLogger(CompQuantMeasurements.class);
//
//		private final Collection<CompQuantMeasurements.Compartments> cellCompartments;
//		private final LinkedHashSet<CompQuantMeasurements.Measurements> measurements;
//
//		private final Map<ColorTransform, Double> targets;
//		private final ImageData<BufferedImage> imageData;
//		private final Map<String, Object> params;
//
//		public CompQuantMeasurements(Map<ColorTransform, Double> targets,
//									 ImageData<BufferedImage> imageData,
//									 Map<String, Object> params){
//			this.targets = targets;
//			this.imageData = imageData;
//			this.params = params;
//			this.cellCompartments = Arrays.asList(Compartments.values());
//			this.measurements = new LinkedHashSet<>(Arrays.asList(Measurements.values()));
//		}
//
//		public CompQuantMeasurements(Map<ColorTransform, Double> targets,
//									 ImageData<BufferedImage> imageData,
//									 Map<String, Object> params,
//									 Collection<CompQuantMeasurements.Compartments> cellCompartments,
//									 Collection<CompQuantMeasurements.Measurements> measurements){
//			this.targets = targets;
//			this.imageData = imageData;
//			this.params = params;
//			this.cellCompartments = cellCompartments;
//			this.measurements = new LinkedHashSet<>(measurements);
//		}
//
//		/**
//		 * Cell compartments.
//		 */
//		public enum Compartments {
//			/**
//			 * Nucleus only
//			 */
//			NUCLEUS,
//			/**
//			 * Full cell region, with nucleus removed
//			 */
//			CYTOPLASM,
//			/**
//			 * Full cell region
//			 */
//			CELL,
//			/**
//			 * Cell boundary, with interior removed
//			 */
//			MEMBRANE
//
//		}
//
//		/**
//		 * Requested intensity measurements.
//		 */
//		public enum Measurements {
//			/**
//			 * Arithmetic mean
//			 */
//			MEAN,
//			/**
//			 * Median value
//			 */
//			MEDIAN,
//			/**
//			 * Minimum value
//			 */
//			MIN,
//			/**
//			 * Maximum value
//			 */
//			MAX,
//			/**
//			 * Standard deviation value
//			 */
//			STD_DEV,
//			/**
//			 * Variance value
//			 */
//			VARIANCE;
//
//			private String getMeasurementName() {
//				switch (this) {
//					case MAX:
//						return "Max";
//					case MEAN:
//						return "Mean";
//					case MEDIAN:
//						return "Median";
//					case MIN:
//						return "Min";
//					case STD_DEV:
//						return "Std.Dev.";
//					case VARIANCE:
//						return "Variance";
//					default:
//						throw new IllegalArgumentException("Unknown measurement " + this);
//				}
//			}
//
//			private double getMeasurement(StatisticalSummary stats) {
//				switch (this) {
//					case MAX:
//						return stats.getMax();
//					case MEAN:
//						return stats.getMean();
//					case MEDIAN:
//						if (stats instanceof DescriptiveStatistics)
//							return ((DescriptiveStatistics)stats).getPercentile(50.0);
//						else
//							return Double.NaN;
//					case MIN:
//						return stats.getMin();
//					case STD_DEV:
//						return stats.getStandardDeviation();
//					case VARIANCE:
//						return stats.getVariance();
//					default:
//						throw new IllegalArgumentException("Unknown measurement " + this);
//				}
//			}
//		}
//
//
//		public void getTargetsIntensityScores(PathObject pathObject) throws IOException {
//			//get params required
//			double downsample;
//			boolean rescaleScore;
//			boolean normalizeScore;
//			double maxFloatValue;
//			try {
//				downsample = (double) params.get("downsample");
//				rescaleScore = (boolean) params.get("rescaleScore");
//				normalizeScore = (boolean) params.get("normalizeScore");
//				maxFloatValue = (double) params.get("maxFloatValue");
//			} catch(Exception ex){
////				ex.printStackTrace();
//				throw new RuntimeException(ex);
//			}
//
//			// Convert to binary mask Mat
//			ROI roi = pathObject.getROI();
//			String className = pathObject.getPathClass().toString();
//			ImageServer<BufferedImage> server = imageData.getServer();
//
//			int pad = (int) Math.ceil(downsample * 2);
//			RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, roi)
//					.pad2D(pad, pad)
//					.intersect2D(0, 0, server.getWidth(), server.getHeight());
//
//			PathImage<ImagePlus> pathImage = IJTools.convertToImagePlus(server, request);
////			ImagePlus imp = pathImage.getImage();
//
//			PixelCalibration pc = server.getPixelCalibration();
//			PixelType pixType = server.getPixelType();
//			int bitDepth = server.getPixelType().getBitsPerPixel();
//			double mppSq = pc.getPixelHeightMicrons() * pc.getPixelWidthMicrons();
//			//    println 'Squarred MPP: ' + mppSq.toString();
//
//			// Use mean intensity to calculate AQUA score as (mean intensity)/(MPP^2 * exposure_time)
//			MeasurementList measList = pathObject.getMeasurementList();
//
//			// Add shape measurements
//			double annotationArea = pathObject.getROI().getArea();
//			measList.putMeasurement(className + " area px", annotationArea);
//			measList.putMeasurement(className + " area um^2", annotationArea * mppSq);
//			measList.putMeasurement("MPP^2", mppSq);
//			measList.putMeasurement("Channel bitdepth", bitDepth);
//			int bitDepthVal = (int) Math.pow(2, bitDepth);
////			int bitDepthVal = (int) Math.pow(2, 16);
//
//			Map<String, ImageProcessor> channels = new LinkedHashMap<>();
//			Map<String, String> measNames = new LinkedHashMap<>();
//
//			//Don't like this, is there a way to convert ROI to a binary mask OpenCV Mat directly??
//			//Using ImageJ to create a binary mask [0,1] of ROI
//			ByteProcessor bpCell = new ByteProcessor(request.getWidth(), request.getHeight());
//			bpCell.setValue(1.0);
//			Roi roiIJ = IJTools.convertToIJRoi(roi, pathImage);
//			bpCell.fill(roiIJ);
//
//			//Might not be the best performance. Would like to recode to use only OpenCV_core Mats and pointers/mask indexing.
//			for (Map.Entry<ColorTransform, Double> tar : targets.entrySet()) {
//				ColorTransform targetTransform = tar.getKey();
//				String targetName = targetTransform.toString();
//				ImageProcessor ipChannel = OpenCVTools.matToImageProcessor(ImageOps.buildImageDataOp(targetTransform).apply(imageData, request));
//				String measName = targetName + " Intensity in " + className;
//				measNames.put(targetName, measName);
//				channels.put(measName, ipChannel);
//				logger.info(String.format("Scoring %s in %s", targetName, className));
//			}
//
//			if (pathObject instanceof PathCellObject) {
//				PathCellObject cell = (PathCellObject) pathObject;
//				ByteProcessor bpNucleus = new ByteProcessor(request.getWidth(), request.getHeight());
//				if (cell.getNucleusROI() != null) {
//					bpNucleus.setValue(1.0);
//					Roi roiNucleusIJ = IJTools.convertToIJRoi(cell.getNucleusROI(), pathImage);
//					bpNucleus.fill(roiNucleusIJ);
//				}
//				//For mean, median, stdev, etc.
//				measureCells(bpNucleus, bpCell, Map.of(1.0, cell), channels);
//				//Calculate sum intensity in compartment
//				//        measureObjSumInt(img, imgLabels, new PathObject[] {pathObject}, targetName);
//			} else {
//				var imgLabels = new PixelImageIJ(bpCell);
//				for (Map.Entry<String, ImageProcessor> entry : channels.entrySet()) {
//					var img = new PixelImageIJ(entry.getValue());
//					//For mean, median, stdev, etc.
//					measureObjects(img, imgLabels, new PathObject[]{pathObject}, entry.getKey());
//					//Calculate sum intensity in compartment
//					//            measureObjSumInt(img, imgLabels, new PathObject[] {pathObject}, targetName);
//				}
//			}
//
//
//			for (Map.Entry<ColorTransform, Double> tar : targets.entrySet()) {
//				String targetName = tar.getKey().toString();
//				double exposure_time = tar.getValue();
//				String measName = measNames.get(targetName);
//				double targetMean = measList.getMeasurementValue(measName + ": Mean");
//				// double sumInt = targetMean*annotationArea;
//				// measList.putMeasurement(targetName+' in '+className+' Sum Intensity', sumInt);
//				// Debugging, would load from available metadata
//				if (exposure_time == 0.0 || exposure_time < 0) {
//					exposure_time = 1000;
//					measList.putMeasurement(targetName + " exposure time (ms)", 0);
//				} else {
//					measList.putMeasurement(targetName + " exposure time (ms)", exposure_time);
//				}
//
//				// double MeanI_S = targetMean/(exposure_time/1000)
//				// measList.putMeasurement(targetName+' in '+className+' Mean I/[exp time (s)]', MeanI_S);
//				// Intensity/(um^2*sec)
//				// double QIF_area = targetMean/mppSq;
//				// measList.putMeasurement(targetName+' in '+className+' Sum I/um^2', QIF_area);
//				//if pixelType float, skip [vetra Polaris data]
//				if(pixType.isFloatingPoint()) {
//					double QIF_areaS = (targetMean / mppSq);
//					measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
//				}else if(rescaleScore && !normalizeScore){
//					//assumes score has already been normalized, but turned into an unsigned int datatype for image manipulation
//					//using bitdepth and maxFloatValue to rescale
//					double rescaleFactor = (maxFloatValue/bitDepthVal);
//					double QIF_areaS = (targetMean / mppSq) * rescaleFactor;
//					measList.putMeasurement("Rescale factor", rescaleFactor);
//					measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
//				}else if(normalizeScore) {
//					double QIF_areaS = (targetMean / mppSq) / (bitDepthVal * exposure_time / 1000);
//					measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2*[exp time (s)]*[2^bitDepth])", QIF_areaS);
//				}else{
//					// no normalization
//					double QIF_areaS = (targetMean / mppSq);
//					measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
//				}
//				//    double totalPx = server.getHeight()*server.getWidth();
//				//    println 'Total pixels: '+ totalPx.toString();
//				//    double QIF_areaPercent = targetMean*annotationArea/(100*annotationArea/totalPx);
//				//    measList.putMeasurement(targetName+' in '+className+' Sum I/(Compartment % Area)', QIF_areaPercent);
//				//    double QIF_areaPercentS = QIF_areaPercent/(exposure_time);
//				//    measList.putMeasurement(targetName+' in '+className+' Sum I/([Compartment % Area]*[exp time (ms)])', QIF_areaPercentS);
//			}
//
////			clean up vars?
//			server = null;
//			pathImage = null;
//			channels = null;
//			request = null;
//			measList = null;
//		}
//
//		/**
//		 * Measure all channels of an image for one individual object or cell.
//		 * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
//		 * <p>
//		 * Note: This implementation is likely to change in the future, to enable neighboring cells to be
//		 * measured more efficiently.
//		 *
//		 * @param server the server containing the pixels (and channels) to be measured
//		 * @param pathObject the cell to measure (the {@link MeasurementList} will be updated)
//		 * @param downsample resolution at which to request pixels
//		 * @param measurements requested measurements to make
//		 * @param compartments the cell compartments to measure; ignored if the object is not a cell
//		 * @throws IOException
//		 */
////		public static void addIntensityMeasurements(
////				ImageServer<BufferedImage> server,
////				PathObject pathObject,
////				double downsample,
////				Collection<CompQuantMeasurements.Measurements> measurements,
////				Collection<CompQuantMeasurements.Compartments> compartments) throws IOException {
////
////			var roi = pathObject.getROI();
////
////			int pad = (int)Math.ceil(downsample * 2);
////			var request = RegionRequest.createInstance(server.getPath(), downsample, roi)
////					.pad2D(pad, pad)
////					.intersect2D(0, 0, server.getWidth(), server.getHeight());
////
////			var pathImage = IJTools.convertToImagePlus(server, request);
////			var imp = pathImage.getImage();
////
////			Map<String, ImageProcessor> channels = new LinkedHashMap<>();
////			var serverChannels = server.getMetadata().getChannels();
////			if (server.isRGB() && imp.getStackSize() == 1 && imp.getProcessor() instanceof ColorProcessor) {
////				ColorProcessor cp = (ColorProcessor)imp.getProcessor();
////				for (int i = 0; i < serverChannels.size(); i++) {
////					channels.put(serverChannels.get(i).getName(), cp.getChannel(i+1, null));
////				}
////			} else {
////				assert imp.getStackSize() == serverChannels.size();
////				for (int i = 0; i < imp.getStackSize(); i++) {
////					channels.put(serverChannels.get(i).getName(), imp.getStack().getProcessor(i+1));
////				}
////			}
////
////			ByteProcessor bpCell = new ByteProcessor(imp.getWidth(), imp.getHeight());
////			bpCell.setValue(1.0);
////			var roiIJ = IJTools.convertToIJRoi(roi, pathImage);
////			bpCell.fill(roiIJ);
////
////			if (pathObject instanceof PathCellObject) {
////				var cell = (PathCellObject)pathObject;
////				ByteProcessor bpNucleus = new ByteProcessor(imp.getWidth(), imp.getHeight());
////				if (cell.getNucleusROI() != null) {
////					bpNucleus.setValue(1.0);
////					var roiNucleusIJ = IJTools.convertToIJRoi(cell.getNucleusROI(), pathImage);
////					bpNucleus.fill(roiNucleusIJ);
////				}
////				measureCells(bpNucleus, bpCell, Map.of(1.0, cell), channels, compartments, measurements);
////			} else {
////				var imgLabels = new PixelImageIJ(bpCell);
////				for (var entry : channels.entrySet()) {
////					var img = new PixelImageIJ(entry.getValue());
////					measureObjects(img, imgLabels, new PathObject[] {pathObject}, entry.getKey(), measurements);
////				}
////			}
////		}
//
//		/**
//		 * Make cell measurements based on labelled images.
//		 * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
//		 *
//		 * @param ipNuclei labelled image representing nuclei
//		 * @param ipCells labelled image representing cells
//		 * @param pathObjects cell objects mapped to integer values in the labelled images
//		 * @param channels channels to measure, mapped to the name to incorporate into the measurements for that channel
//		 */
//		private void measureCells(
//				ImageProcessor ipNuclei, ImageProcessor ipCells,
//				Map<? extends Number, ? extends PathObject> pathObjects,
//				Map<String, ImageProcessor> channels) {
//
//			var array = mapToArray(pathObjects);
////		PathObjectTools.constrainCellByScaledNucleus(cell, nucleusScaleFactor, keepMeasurements)
//			int width = ipNuclei.getWidth();
//			int height = ipNuclei.getHeight();
//			ImageProcessor ipMembrane = new FloatProcessor(width, height);
//			ImageProcessor ipCytoplasm = ipCells.duplicate();
//			for (int y = 0; y < height; y++) {
//				for (int x = 0; x < width; x++) {
//					float cell = ipCells.getf(x, y);
//					float nuc = ipNuclei.getf(x, y);
//					if (nuc != 0f)
//						ipCytoplasm.setf(x, y, 0f);
//					if (cell == 0f)
//						continue;
//					// Check 4-neighbours to decide if we're at the membrane
//					if ((y >= 1 && ipCells.getf(x, y-1) != cell) ||
//							(y < height-1 && ipCells.getf(x, y+1) != cell) ||
//							(x >= 1 && ipCells.getf(x-1, y) != cell) ||
//							(x < width-1 && ipCells.getf(x+1, y) != cell))
//						ipMembrane.setf(x, y, cell);
//				}
//			}
//
//			var imgNuclei = new PixelImageIJ(ipNuclei);
//			var imgCells = new PixelImageIJ(ipCells);
//			var imgCytoplasm = new PixelImageIJ(ipCytoplasm);
//			var imgMembrane = new PixelImageIJ(ipMembrane);
//
//			for (var entry : channels.entrySet()) {
//				var img = new PixelImageIJ(entry.getValue());
//				if (cellCompartments.contains(CompQuantMeasurements.Compartments.NUCLEUS))
//					measureObjects(img, imgNuclei, array, entry.getKey().trim() + ": " + "Nucleus");
//				if (cellCompartments.contains(CompQuantMeasurements.Compartments.CYTOPLASM))
//					measureObjects(img, imgCytoplasm, array, entry.getKey().trim() + ": " + "Cytoplasm");
//				if (cellCompartments.contains(CompQuantMeasurements.Compartments.MEMBRANE))
//					measureObjects(img, imgMembrane, array, entry.getKey().trim() + ": " + "Membrane");
//				if (cellCompartments.contains(CompQuantMeasurements.Compartments.CELL))
//					measureObjects(img, imgCells, array, entry.getKey().trim() + ": " + "Cell");
//			}
//
//		}
//
//
//		private static PathObject[] mapToArray(Map<? extends Number, ? extends PathObject> pathObjects) {
//			Number[] labels = new Number[pathObjects.size()];
//			int n = 0;
//			long maxLabel = 0L;
//			int invalidLabels = 0;
//			for (var label : pathObjects.keySet()) {
//				long lab = label.longValue();
//				if (lab < 0 || lab != label.doubleValue() || lab >= Integer.MAX_VALUE) {
//					invalidLabels++;
//				} else {
//					labels[n] = label;
//					maxLabel = Math.max(lab, maxLabel);
//					n++;
//				}
//			}
//
//			if (invalidLabels > 0) {
//				logger.warn("Only {}/{} labels are integer values >= 0 and < Integer.MAX_VALUE, the rest will be discarded!",
//						n, pathObjects.size());
//			}
//
//			PathObject[] array = new PathObject[n];
//			for (var label : labels) {
//				array[label.intValue()-1] = pathObjects.get(label);
//			}
//			return array;
//		}
//
////	/**
////	 * Measure objects within the specified image, adding them to the corresponding measurement lists.
////	 * @param img intensity values to measure
////	 * @param imgLabels labels corresponding to objects
////	 * @param pathObjects map between label values and objects
////	 * @param baseName base name to include when adding measurements (e.g. the channel name)
////	 * @param measurements requested measurements
////	 */
////	private static void measureObjects(
////			SimpleImage img, SimpleImage imgLabels,
////			Map<? extends Number, ? extends PathObject> pathObjects,
////			String baseName, Collection<Measurements> measurements) {
////
////		measureObjects(img, imgLabels, mapToArray(pathObjects), baseName, measurements);
////	}
//
//		/**
//		 * Measure objects within the specified image, adding them to the corresponding measurement lists.
//		 * @param img intensity values to measure
//		 * @param imgLabels labels corresponding to objects
//		 * @param pathObjects array of objects, where array index for an object is 1 less than the label in imgLabels
//		 * @param baseName base name to include when adding measurements (e.g. the channel name)
//		 */
//		private void measureObjects(
//				SimpleImage img, SimpleImage imgLabels,
//				PathObject[] pathObjects,
//				String baseName) {
//
//			// Initialize stats
//			int n = pathObjects.length;
//			DescriptiveStatistics[] allStats = new DescriptiveStatistics[n];
//			for (int i = 0; i < n; i++)
//				allStats[i] = new DescriptiveStatistics(DescriptiveStatistics.INFINITE_WINDOW);
//
//			// Compute statistics
//			int width = img.getWidth();
//			int height = img.getHeight();
//			for (int y = 0; y < height; y++) {
//				for (int x = 0; x < width; x++) {
//					int label = (int)imgLabels.getValue(x, y);
//					if (label <= 0 || label > n)
//						continue;
//					float val = img.getValue(x, y);
//					allStats[label-1].addValue(val);
//				}
//			}
//
//			// Add measurements
//			for (int i = 0; i < n; i++) {
//				var pathObject = pathObjects[i];
//				if (pathObject == null)
//					continue;
//				var stats = allStats[i];
//				try (var ml = pathObject.getMeasurementList()) {
//					for (var m : measurements) {
//						ml.putMeasurement(baseName + ": " + m.getMeasurementName(), m.getMeasurement(stats));
//					}
//				}
//			}
//		}
//	}
}
