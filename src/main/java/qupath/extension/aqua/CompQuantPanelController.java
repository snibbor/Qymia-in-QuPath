package qupath.extension.aqua;

import com.google.common.eventbus.EventBus;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.*;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.*;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.extension.aqua.backend.CompQuantBackend;
import qupath.imagej.tools.IJTools;
import qupath.imagej.tools.PixelImageIJ;
//import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.*;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOps;
import qupath.opencv.tools.OpenCVTools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static qupath.lib.scripting.QP.*;

public class CompQuantPanelController implements Initializable{

	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(CompQuantPanelController.class);

	// this bus is used application wide
	private final EventBus appEventBus = new EventBus();

	private final QuPathGUI qupath;

	public LinkedHashMap<ColorTransform, Double> availableTransforms = new LinkedHashMap<ColorTransform, Double>();

	//will be in settings menu
	private final List<String> ignoreClasses = List.of(new String[]{"Ignore*, Necrosis", "Other"});

	//default params
	private final int defaultGridSize = 512;
	private final ObjectProperty<Integer> gridSize = new SimpleObjectProperty(defaultGridSize);

	private final ObservableSet<PathClass> selectedCompartments = FXCollections.observableSet();
	// target and exposure time if IF image
	private final ObservableMap<ColorTransform, Double> selectedTargets = FXCollections.observableMap(new LinkedHashMap<ColorTransform, Double>());

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
					if(selectedStainType.get()=="Fluorescence") {
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
			targetListView.getItems().setAll(newTransforms);
		} else if (!newTransforms.equals(targetListView.getItems())){
			targetListView.getItems().setAll(newTransforms);
		}

		String slide = selectedSlideType.get();
		String stain = selectedStainType.get();
		String source = selectedSource.get();
		String result = selectedResultType.get();
		//check if something is selected for compartments and targets....
		startQuantButton.setDisable(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0);

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

		ImageServer<BufferedImage> server = (ImageServer<BufferedImage>) getCurrentServer();
		double downsample = 1.0;
		int scaleBitDepth = 0;

		if(result.toLowerCase().contains("grid")){
			if(gridSizeTextField.getText().isEmpty() || gridSizeTextField.getText() == null)
				logger.warn("Gridsize textfield cannot be 0 or empty when trying to compute grid results!");
			else if(gridSizeTextField.getText() != null && Double.parseDouble(gridSizeTextField.getText()) == 0.0)
				logger.warn("Gridsize textfield cannot be 0 or empty when trying to compute grid results!");
			else
				logger.warn("Grid scoring not implemented yet...");
		}
		if(result.toLowerCase().contains("tma") && slide == "TMA"){
			logger.info(String.format("Beginning compartment quantification of TMA cores for compartments: %s and targets: %s...", selectedCompartments.toString(), selectedTargets.toString()));
			CompQuantBackend.TMARecalcCompartmentsAndAQUA(server, ignoreClasses, selectedTargets, List.of((PathClass[]) selectedCompartments.toArray()), downsample);

		}
		if(result.toLowerCase().contains("roi")){
			logger.info(String.format("Beginning compartment quantification of ROIs for compartments: %s and targets: %s...", selectedCompartments.toString(), selectedTargets.toString()));
			CompQuantBackend.getTargetAQUAScoresForROIs(server, ignoreClasses, selectedTargets, List.of((PathClass[]) selectedCompartments.toArray()), downsample);
		}

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


	public static class CompQuantBackend {

		private static final Logger logger = LoggerFactory.getLogger(qupath.extension.aqua.backend.CompQuantBackend.class);

		public static ROI combinePathObjs(Collection<PathObject> annots, Boolean newAnnot) {
			ROI combinedROI = null;
			PathClass p_class = null;
			for (PathObject annotation : annots) {
				if (combinedROI == null) {
					combinedROI = annotation.getROI();//.duplicate();
					p_class = annotation.getPathClass();
				} else if (combinedROI.getImagePlane().equals(annotation.getROI().getImagePlane())) {
					combinedROI = RoiTools.combineROIs(combinedROI, annotation.getROI(), RoiTools.CombineOp.ADD);
				} else {
					logger.info("Cannot merge PathObjects across different image planes!");
//				continue;
				}
			}

			if (newAnnot) {
				removeObjects(annots, true);
				PathObject combinedAnnot = PathObjects.createAnnotationObject(combinedROI, p_class);
				addObject(combinedAnnot);
			}

			return combinedROI;
		}

		// AQUA inside each intersecting compartment of ROI only
		//    Map<String, Integer> targets = new LinkedHashMap<>();
		// Not for TMAs! Would be much more effective to restrict the search space for ROIS within TMA core hierarchy, however, not all the annotations will be properly incorporated into the hierarchy.....
		// How to flexibly find ROIs within TMA core hierarchy?
		public static void getTargetAQUAScoresForROIs(ImageServer<BufferedImage> server,
													  List<String> rois,
													  Map<ColorTransform, Double> targets,
													  List<PathClass> compartments,
													  double downsample
		) {

			List<CompQuantMeasurements.Measurements> measurements = Arrays.asList(CompQuantMeasurements.Measurements.values());
			List<CompQuantMeasurements.Compartments> cellCompartments = Arrays.asList(CompQuantMeasurements.Compartments.values());
			// Won't mean much if they aren't cells...

			// Add annotations to heirarchy connected to ROI

			// Remove uninformative classes (Tissue)
//			compartments.remove("Tissue");

			// Used for placing child objects inside ROI
			var imageData = getCurrentImageData();

			AtomicInteger roiNumber = new AtomicInteger(1);

			var pathObjs = imageData.getHierarchy().getObjects(null, PathObject.class);
			var compartmentObjs = pathObjs.parallelStream().filter(p -> compartments.contains(p.getPathClass()))
																		.collect(Collectors.toList());
			pathObjs.parallelStream().filter(p -> rois.contains(p.getPathClass().toString()) && p.hasROI())
					.map(f -> {
						// Record null/none values for compartments not within ROI
						logger.info(f.getName());
						if (f.getName() == null || f.getName().isBlank()) {
							f.setName("ROI_" + roiNumber.get());
							roiNumber.incrementAndGet();
						}
						return f;
					})
					.forEach(r -> {
						//Typically the number of compartments is small and these are all combined for a WSI.
						//Not efficiient for TMA cores!
						for (PathObject compObj : compartmentObjs) {
							ROI compInterROI = RoiTools.combineROIs(compObj.getROI(), r.getROI(), RoiTools.CombineOp.INTERSECT);
							PathObject compInterDet = PathObjects.createDetectionObject(compInterROI, compObj.getPathClass());

							if (!compInterROI.isEmpty()) {
								logger.info(String.format("ROI contains %s compartment! Calculating AQUA metrics within ROI.", compObj.getPathClass().toString()));
								// For debugging, maybe helps with visualization
								// Add object as a child of the ROI
								//                        addObject(compInterDet);
								compInterDet.setName(r.getName() + " (" + compObj.getPathClass().toString() + ")");
								imageData.getHierarchy().addPathObjectBelowParent(r, compInterDet, true);

								logger.info(String.format("Got %s intersection with ROI", compObj.getPathClass().toString()));

								// Quantify metrics/AQUA for each target in each intersecting compartment
								// Calculate AQUA scoring metrics for new compartment detections for all targets
								try {
									getTargetsAQUA(
											server, compInterDet,
											targets,
											measurements, cellCompartments,
											downsample
									);
								} catch (IOException e) {
									logger.warn(e.toString());
								}

								// Put these target/compartment measurments on the measurement list of the ROI for export
								//                    compInterObjs.add(compInterDet)
							} else {
								logger.info(String.format("No intersection with %s compartment for ROI... skipping.", compObj.getPathClass().toString()));
							}
						}

					});
		}


		// Exclude regions and add regions that weren't segmented well. Allows for manual adjustment of compartmentalization before AQUA.
		public static void TMARecalcCompartmentsAndAQUA(ImageServer<BufferedImage> server,
														List<String> ignoreClasses,
														Map<ColorTransform, Double> targets,
														List<PathClass> compartments,
														double downsample
		) {

			// Adjust each compartment by subtracting the exclude region and adding the corresponding compartment adjustments
			// Iterate through compartments/detections to recreate them if adjustments were made
			// Calculate AQUA metrics for each target
			final Boolean[] doAdjust = {false};

			List<CompQuantMeasurements.Measurements> measurements = Arrays.asList(CompQuantMeasurements.Measurements.values());
			List<CompQuantMeasurements.Compartments> cellCompartments = Arrays.asList(CompQuantMeasurements.Compartments.values());
			// Won't mean much if they aren't cells...
			logger.info("Updating existing compartments with any new annotations, calcuating AQUA metrics...");

			TMAGrid tmaGrid = getCurrentHierarchy().getTMAGrid();
			List<TMACoreObject> tmaCores = tmaGrid.getTMACoreList();
			// Combine exclude regions, but do not create a new merged object
			ROI combinedExcludeROI = null;

			List<PathObject> allIgnoreAnnotations = getAnnotationObjects().parallelStream().filter(p -> ignoreClasses.contains(p.getPathClass().toString()))
					.collect(Collectors.toList());
			combinedExcludeROI = combinePathObjs(allIgnoreAnnotations, false);
			if (combinedExcludeROI != null)
				doAdjust[0] = true;

			ROI finalCombinedExcludeROI = combinedExcludeROI;
			tmaCores.parallelStream().forEach(core -> {
				// step thru all children items of TMA core object
				core.getChildObjects().parallelStream().forEach(detection -> {
					if (compartments.contains(detection.getPathClass())) {
						PathObject adjDetection;
						ROI adjDetectionROI = detection.getROI();
						// is not very efficient as the excluded areas may only be in certain TMA spots....
						// getting an excluded ROI for each TMA core is not as parallellizable and does not work if the excluded region does not fit within the QuPath hierarchy
						if (doAdjust[0]) {
							adjDetectionROI = RoiTools.combineROIs(adjDetectionROI, finalCombinedExcludeROI, RoiTools.CombineOp.SUBTRACT);
						}
						if (adjDetectionROI.isEmpty()) {
							logger.info(String.format("Detection %s compartment is now empty, skipping AQUA metrics...", detection.getPathClass().toString()));
//						removeObject(detection, true);
							return;
						} else if (doAdjust[0]) {
							logger.info(String.format("Adjusting %s compartment based on new annotations...", detection.getPathClass().toString()));
							adjDetection = PathObjects.createDetectionObject(adjDetectionROI, detection.getPathClass());
							addObject(adjDetection);
							removeObject(detection, true);
						} else {
							adjDetection = detection;
						}

						// Calculate AQUA scoring metrics for new compartment detections for all targets
						try {
							getTargetsAQUA(
									server, adjDetection,
									targets,
									measurements, cellCompartments,
									downsample
							);
						} catch (IOException e) {
							logger.warn(e.toString());
						}
					}
				});
			});

			// println 'Checking if any compartments were added by new annotations...';
			// println missingCompartments;
			// if (!missingCompartments.isEmpty()){
			//     for (String comp : missingCompartments) {
			//         combinedCompROI = combinedCompROIMap.get(comp);
			//         if (combinedCompROI != null){
			//             println String.format('Adding new %s annotations and calculating AQUA metrics!', comp);
			//             newCompDetection = PathObjects.createDetectionObject(combinedCompROI, getPathClass(comp));
			//             addObject(newCompDetection);

			//             // Calculate AQUA scoring metrics for new compartment detections for all targets
			//             for (var tar: targets.entrySet()) {
			//                 getTargetAQUA(
			//                     server, newCompDetection,
			//                     tar.getValue(), tar.getKey(),
			//                     measurements, cellCompartments,
			//                     downsample, metadata, scaleBitDepthTo
			//                     );
			//             }
			//         }
			//     }
			// }
		}

		// AQUA of Target inside PathObject (i.e. a compartment mask)
//		public void getTargetsAQUA(ImageServer<BufferedImage> server,
//								   PathObject pathObject,
//								   Map<String, Integer> targets,
//								   Collection<CompQuantMeasurements.Measurements> measurements,
//								   Collection<CompQuantMeasurements.Compartments> cellCompartments,
//								   double downsample,
//								   Object metaData,
//								   int scaleBitDepthTo)
//		{
//			//get color transforms from channel indices
//			//
//			// pass to color transform compatible method
//			getTargetsAQUA(server, pathObject, targets, measurements, cellCompartments, downsample, metaData, scaleBitDepthTo);
//		}
		public static void getTargetsAQUA(ImageServer<BufferedImage> server,
										  PathObject pathObject,
										  Map<ColorTransform, Double> targets,
										  Collection<CompQuantMeasurements.Measurements> measurements,
										  Collection<CompQuantMeasurements.Compartments> cellCompartments,
										  double downsample
		) throws IOException {
			// Convert to binary mask Mat
			ROI roi = pathObject.getROI();
			String className = pathObject.getPathClass().toString();

			int pad = (int) Math.ceil(downsample * 2);
			RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, roi)
					.pad2D(pad, pad)
					.intersect2D(0, 0, server.getWidth(), server.getHeight());

			var imageData = getCurrentImageData();
			PathImage<ImagePlus> pathImage = IJTools.convertToImagePlus(server, request);
//			ImagePlus imp = pathImage.getImage();

			PixelCalibration pc = server.getPixelCalibration();
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
//			int bitDepthVal = (int) Math.pow(2, bitDepth);
			int bitDepthVal = (int) Math.pow(2, 16);

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
				logger.info(String.format("AQUA of %s in %s", targetName, className));
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
				CompQuantMeasurements.measureCells(bpNucleus, bpCell, Map.of(1.0, cell), channels, cellCompartments, measurements);
				//Calculate sum intensity in compartment
				//        measureObjSumInt(img, imgLabels, new PathObject[] {pathObject}, targetName);
			} else {
				var imgLabels = new PixelImageIJ(bpCell);
				for (Map.Entry<String, ImageProcessor> entry : channels.entrySet()) {
					var img = new PixelImageIJ(entry.getValue());
					//For mean, median, stdev, etc.
				CompQuantMeasurements.measureObjects(img, imgLabels, new PathObject[]{pathObject}, entry.getKey(), measurements);
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
				double QIF_areaS = (targetMean / mppSq) / (bitDepthVal * exposure_time / 1000);
				measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2*[exp time (s)]*[2^bitDepth])", QIF_areaS);

				//    double totalPx = server.getHeight()*server.getWidth();
				//    println 'Total pixels: '+ totalPx.toString();
				//    double QIF_areaPercent = targetMean*annotationArea/(100*annotationArea/totalPx);
				//    measList.putMeasurement(targetName+' in '+className+' Sum I/(Compartment % Area)', QIF_areaPercent);
				//    double QIF_areaPercentS = QIF_areaPercent/(exposure_time);
				//    measList.putMeasurement(targetName+' in '+className+' Sum I/([Compartment % Area]*[exp time (ms)])', QIF_areaPercentS);
			}
		}


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

	public static class CompQuantMeasurements {
		private final static Logger logger = LoggerFactory.getLogger(CompQuantMeasurements.class);

		/**
		 * Cell compartments.
		 */
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
							return ((DescriptiveStatistics)stats).getPercentile(50.0);
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

		/**
		 * Measure all channels of an image for one individual object or cell.
		 * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
		 * <p>
		 * Note: This implementation is likely to change in the future, to enable neighboring cells to be
		 * measured more efficiently.
		 *
		 * @param server the server containing the pixels (and channels) to be measured
		 * @param pathObject the cell to measure (the {@link MeasurementList} will be updated)
		 * @param downsample resolution at which to request pixels
		 * @param measurements requested measurements to make
		 * @param compartments the cell compartments to measure; ignored if the object is not a cell
		 * @throws IOException
		 */
		public static void addIntensityMeasurements(
				ImageServer<BufferedImage> server,
				PathObject pathObject,
				double downsample,
				Collection<CompQuantMeasurements.Measurements> measurements,
				Collection<CompQuantMeasurements.Compartments> compartments) throws IOException {

			var roi = pathObject.getROI();

			int pad = (int)Math.ceil(downsample * 2);
			var request = RegionRequest.createInstance(server.getPath(), downsample, roi)
					.pad2D(pad, pad)
					.intersect2D(0, 0, server.getWidth(), server.getHeight());

			var pathImage = IJTools.convertToImagePlus(server, request);
			var imp = pathImage.getImage();

			Map<String, ImageProcessor> channels = new LinkedHashMap<>();
			var serverChannels = server.getMetadata().getChannels();
			if (server.isRGB() && imp.getStackSize() == 1 && imp.getProcessor() instanceof ColorProcessor) {
				ColorProcessor cp = (ColorProcessor)imp.getProcessor();
				for (int i = 0; i < serverChannels.size(); i++) {
					channels.put(serverChannels.get(i).getName(), cp.getChannel(i+1, null));
				}
			} else {
				assert imp.getStackSize() == serverChannels.size();
				for (int i = 0; i < imp.getStackSize(); i++) {
					channels.put(serverChannels.get(i).getName(), imp.getStack().getProcessor(i+1));
				}
			}

			ByteProcessor bpCell = new ByteProcessor(imp.getWidth(), imp.getHeight());
			bpCell.setValue(1.0);
			var roiIJ = IJTools.convertToIJRoi(roi, pathImage);
			bpCell.fill(roiIJ);

			if (pathObject instanceof PathCellObject) {
				var cell = (PathCellObject)pathObject;
				ByteProcessor bpNucleus = new ByteProcessor(imp.getWidth(), imp.getHeight());
				if (cell.getNucleusROI() != null) {
					bpNucleus.setValue(1.0);
					var roiNucleusIJ = IJTools.convertToIJRoi(cell.getNucleusROI(), pathImage);
					bpNucleus.fill(roiNucleusIJ);
				}
				measureCells(bpNucleus, bpCell, Map.of(1.0, cell), channels, compartments, measurements);
			} else {
				var imgLabels = new PixelImageIJ(bpCell);
				for (var entry : channels.entrySet()) {
					var img = new PixelImageIJ(entry.getValue());
					measureObjects(img, imgLabels, new PathObject[] {pathObject}, entry.getKey(), measurements);
				}
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
		 * @param compartments the cell compartments to measure
		 * @param measurements requested measurements to make
		 */
		private static void measureCells(
				ImageProcessor ipNuclei, ImageProcessor ipCells,
				Map<? extends Number, ? extends PathObject> pathObjects,
				Map<String, ImageProcessor> channels,
				Collection<CompQuantMeasurements.Compartments> compartments,
				Collection<CompQuantMeasurements.Measurements> measurements) {

			var array = mapToArray(pathObjects);
//		PathObjectTools.constrainCellByScaledNucleus(cell, nucleusScaleFactor, keepMeasurements)
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
				if (compartments.contains(CompQuantMeasurements.Compartments.NUCLEUS))
					measureObjects(img, imgNuclei, array, entry.getKey().trim() + ": " + "Nucleus", measurements);
				if (compartments.contains(CompQuantMeasurements.Compartments.CYTOPLASM))
					measureObjects(img, imgCytoplasm, array, entry.getKey().trim() + ": " + "Cytoplasm", measurements);
				if (compartments.contains(CompQuantMeasurements.Compartments.MEMBRANE))
					measureObjects(img, imgMembrane, array, entry.getKey().trim() + ": " + "Membrane", measurements);
				if (compartments.contains(CompQuantMeasurements.Compartments.CELL))
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

//	/**
//	 * Measure objects within the specified image, adding them to the corresponding measurement lists.
//	 * @param img intensity values to measure
//	 * @param imgLabels labels corresponding to objects
//	 * @param pathObjects map between label values and objects
//	 * @param baseName base name to include when adding measurements (e.g. the channel name)
//	 * @param measurements requested measurements
//	 */
//	private static void measureObjects(
//			SimpleImage img, SimpleImage imgLabels,
//			Map<? extends Number, ? extends PathObject> pathObjects,
//			String baseName, Collection<Measurements> measurements) {
//
//		measureObjects(img, imgLabels, mapToArray(pathObjects), baseName, measurements);
//	}

		/**
		 * Measure objects within the specified image, adding them to the corresponding measurement lists.
		 * @param img intensity values to measure
		 * @param imgLabels labels corresponding to objects
		 * @param pathObjects array of objects, where array index for an object is 1 less than the label in imgLabels
		 * @param baseName base name to include when adding measurements (e.g. the channel name)
		 * @param measurements requested measurements
		 */
		private static void measureObjects(
				SimpleImage img, SimpleImage imgLabels,
				PathObject[] pathObjects,
				String baseName, Collection<CompQuantMeasurements.Measurements> measurements) {

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
			if (!(measurements instanceof Set))
				measurements = new LinkedHashSet<>(measurements);
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
	}
}
