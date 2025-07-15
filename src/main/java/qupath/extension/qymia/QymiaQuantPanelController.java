package qupath.extension.qymia;

import com.google.common.io.Files;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.converter.IntegerStringConverter;

import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.*;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.*;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;

import static qupath.extension.qymia.QymiaQuantBackend.TileOption.*;
import static qupath.lib.gui.prefs.PathPrefs.numCommandThreadsProperty;
import static qupath.lib.objects.classes.PathClassFactory.getPathClass;


import java.awt.image.BufferedImage;
import java.awt.Desktop;
import java.net.URI;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class QymiaQuantPanelController extends BaseController implements Initializable {

	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(QymiaQuantPanelController.class);

	private final QuPathGUI qupath;

	private final QymiaQuantModel quantModel;

	private final AtomicReference<Boolean> runCancelled = new AtomicReference<>(false);

	public LinkedHashMap<ColorTransform, Double> availableTransforms = new LinkedHashMap<>();

	//will be in settings menu

	private final ObservableSet<PathClass> ignoreClasses;
//	private List<PathClass> defaultIgnoreClasses = new ArrayList<>(
//			List.of(
//					getPathClass("Ignore*"),
//					getPathClass("Necrosis"),
//					getPathClass("Other")
//			)
//	);
	private final ObservableSet<PathClass> roiClasses;
//	private List<PathClass> defaultRoiClasses = new ArrayList<>(
//			List.of(
//					getPathClass("ROI")
//			)
//	);

	//default params
//	private static DoubleProperty refNAProperty = PathPrefs.createPersistentPreference("refNAQymiaQuant", 0.75);
//	private static DoubleProperty refMagProperty = PathPrefs.createPersistentPreference("refMagQymiaQuant", 20.0);
//
//	private static DoubleProperty workingNAProperty = PathPrefs.createPersistentPreference("workingNAQymiaQuant", 0.75);
//	private static DoubleProperty workingMagProperty = PathPrefs.createPersistentPreference("workingMagQymiaQuant", 20.0);
//
//	private static DoubleProperty downsampleProperty = PathPrefs.createPersistentPreference("downsampleQymiaQuant", 1.0);
//
//	private static BooleanProperty useCUDAProperty = PathPrefs.createPersistentPreference("useCUDAQymiaQuant", true);

	private final int defaultTileSize = 512;
	private final ObjectProperty<Integer> tileSize = new SimpleObjectProperty(defaultTileSize);

	private FilteredList<PathClass> compartmentList;
	private final ObservableSet<PathClass> selectedCompartments = FXCollections.observableSet();
	// target and exposure time if IF image
	private final ObservableMap<ColorTransform, Double> selectedTargets = FXCollections.observableMap(new LinkedHashMap<>());

	@FXML
	Menu settingsMenu;
	@FXML
	Menu navigateMenu;
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
//	private final String[] compartmentSources = {"Detections", "Annotations", "Cells"};
	private final String[] compartmentSources = {"Detections", "Annotations"};
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
//	private final String[] resultTypesTMA = {"TMA + ROIs", "Grids + ROIs", "TMA + Grids + ROIs", "TMA only", "Grids only", "ROIs only"};
	private final String[] resultTypesTMA = {"TMA + ROIs", "TMA only", "ROIs only"};
	private final String[] resultTypesWTS = {"Tiles + ROIs", "Tiles only", "ROIs only"};
	private ReadOnlyObjectProperty<String> selectedResultType;

	@FXML
	ComboBox<QymiaQuantBackend.TileOption> tileOptionComboBox;
	private final QymiaQuantBackend.TileOption[] tileOptions = {FULL_IMAGE, ROI_ONLY, ROI_AND_IMAGE, SELECTED_OBJS};
	private ReadOnlyObjectProperty<QymiaQuantBackend.TileOption> selectedTileOption;
	@FXML
	Button startQuantButton;
	@FXML
	Button cancelButton;
	@FXML
	TextField tileSizeTextField;
	@FXML
	Label tileSizeLabel;
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
	MenuItem savePresetMenuItem;
	@FXML
	MenuItem loadPresetMenuItem;
	@FXML
	MenuItem runForProjectMenuItem;
	@FXML
	CheckMenuItem tileUnitIsMicronsMenuItem;
	private static BooleanProperty tileUnitIsMicronsProperty = PathPrefs.createPersistentPreference("tileUnitIsMicronsQymiaQuant", false);
	@FXML
	CheckMenuItem verboseMeasuresMenuItem;
	private static BooleanProperty verboseMeasuresProperty = PathPrefs.createPersistentPreference("verboseMeasuresQymiaQuant", false);
	@FXML
	CheckMenuItem normalizeMenuItem;
	private static BooleanProperty normalizeProperty = PathPrefs.createPersistentPreference("normalizeQymiaQuant", true);
	@FXML
	CheckMenuItem deleteTilesMenuItem;
	private static BooleanProperty deleteTilesProperty = PathPrefs.createPersistentPreference("deleteTilesQymiaQuant", true);
	@FXML
	CheckMenuItem rescaleMenuItem;
	private static BooleanProperty rescaleProperty = PathPrefs.createPersistentPreference("rescaleQymiaQuant", false);
	// rescale scores using maxFloatValue and bitdepth
	private double maxFloatValue = 1000.0/4.0;

	@FXML
	MenuItem selectBatchMapMenuItem;
	private String defaultBatchMapFolder = "batch_map";
	private String batchMapPath = "";
	private String defaultMeasConvFolder = "measurement_converters";
	@FXML
	CheckMenuItem convertMeasMenuItem;
	private static BooleanProperty convertMeasurementsProperty = new SimpleBooleanProperty(true);

	@FXML
	MenuItem advancedSettingsMenuItem;
	@FXML
	MenuItem standardCurveMenuItem;
	@FXML
	MenuItem comparisonMenuItem;
	@FXML
	MenuItem switchToPresetMenuItem;
	@FXML
	MenuItem aboutMenuItem;

//	don't like how I need two observable lists to do this... because MenuItem doesn't inherit from Control.......
	private ObservableList<Control> controlListToToggle = FXCollections.observableArrayList();
	private ObservableList<MenuItem> menuItemListToToggle = FXCollections.observableArrayList();
	private List<ProjectImageEntry<BufferedImage>> previousImages = new ArrayList<>();
	private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();
	public final Action EXPORT;


	public QymiaQuantPanelController(QuPathGUI qupath, QymiaQuantModel quantModel) {
		this.qupath = qupath;
		var measureCommand = new QymiaMeasurementExportCommand(qupath);
		EXPORT = qupath.createProjectAction(project -> measureCommand.run());
		this.quantModel = quantModel;
		this.ignoreClasses = quantModel.getIgnoreClasses();
		this.roiClasses = quantModel.getRoiClasses();
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		setupMenu();
		setupComboBoxes();
		setupListViews();
		exportMeasButton.setOnAction(EXPORT);
		tileSizeTextField = QymiaUtils.formatTextFields(tileSizeTextField, "integer", String.valueOf(defaultTileSize));
		tileSizeTextField.textProperty().bindBidirectional(tileSize, new IntegerStringConverter());
		tileSizeTextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent ke) {
				if (ke.getCode().equals(KeyCode.ENTER)) {
					logger.info("tileSize property: " + tileSize.getValue());
					logger.info("textfield property: " + tileSizeTextField.getText());
				}
			}
		});
		tileSizeTextField.focusedProperty().addListener((ov, oldV, newV) -> {
			if (!newV) { // focus lost
				logger.info("tileSize property: " + tileSize.getValue());
				logger.info("textfield property: " + tileSizeTextField.getText());
			}
		});
		startQuantButton.setOnAction(this::startQuant);
		runForProjectMenuItem.setOnAction(this::runForProject);
		cancelButton.setOnAction(this::cancelQuant);
//		setup controls list to disable during quantification
		controlListToToggle.addAll(exportMeasButton, startQuantButton);
		menuItemListToToggle.addAll(runForProjectMenuItem, exportMeasMenuItem, standardCurveMenuItem, comparisonMenuItem);

//		setup PathClass sets
		ignoreClasses.addAll(quantModel.getDefaultIgnoreClasses());
		roiClasses.addAll(quantModel.getDefaultRoiClasses());
		ignoreClasses.addListener(new SetChangeListener<PathClass>() {
			@Override
			public void onChanged(Change<? extends PathClass> change) {
				compartmentList.setPredicate(p -> !ignoreClasses.contains(p) && !roiClasses.contains(p) && p != null);
				updateGUI(false);
			}
		});

		roiClasses.addListener(new SetChangeListener<PathClass>() {
			@Override
			public void onChanged(Change<? extends PathClass> change) {
				compartmentList.setPredicate(p -> !ignoreClasses.contains(p) && !roiClasses.contains(p) && p != null);
				updateGUI(false);
			}
		});

		compartmentList = qupath.getAvailablePathClasses().filtered(p -> !ignoreClasses.contains(p) && !roiClasses.contains(p) && p != null);

		updateGUI(true);
	}

	private void setupMenu() {
		exportMeasMenuItem.setOnAction(EXPORT);
		exportMaskMenuItem.setOnAction(this::exportMasksButton);
        savePresetMenuItem.setOnAction(this::saveQuantPreset);
		loadPresetMenuItem.setOnAction(this::loadQuantPreset);
		standardCurveMenuItem.setOnAction(e -> {
				try{
					switchToAnalysisMode(e, "standardCurve");
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
		});
		comparisonMenuItem.setOnAction(e -> {
			try{
				switchToAnalysisMode(e, "comparison");
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		});

		switchToPresetMenuItem.setOnAction(e -> {
			try{
				switchToPresetMode(e);
			} catch (IOException ex){
				throw new RuntimeException(ex);
			}
		});

		// Open GitHub on About click
		aboutMenuItem.setOnAction(e -> {
			try {
				if (Desktop.isDesktopSupported()) {
					Desktop.getDesktop().browse(
						new URI("https://github.com/snibbor/Qymia-in-QuPath/")
					);
				}
			} catch (Exception ex) {
				logger.error("Unable to open GitHub link", ex);
			}
		});

		normalizeMenuItem.selectedProperty().bindBidirectional(normalizeProperty);
		rescaleMenuItem.selectedProperty().bindBidirectional(rescaleProperty);
		deleteTilesMenuItem.selectedProperty().bindBidirectional(deleteTilesProperty);
		tileUnitIsMicronsMenuItem.selectedProperty().bindBidirectional(tileUnitIsMicronsProperty);
		tileUnitIsMicronsMenuItem.selectedProperty().addListener((obs, oldVal, newVal) -> {
//			Check and set prompt text for tile size if the unit is changed to microns
			if(obs.getValue()){
				tileSizeTextField.setPromptText("um");
			} else {
				tileSizeTextField.setPromptText("px");
			}
		});
		convertMeasMenuItem.selectedProperty().bindBidirectional(convertMeasurementsProperty);
		selectBatchMapMenuItem.setOnAction(e->{
			File dirBase = qupath.getProject() != null ? Projects.getBaseDirectory(qupath.getProject()) : new File(System.getProperty("user.home"));
			File batchMapFile = Dialogs.promptForFile("Staining Batch Map File", dirBase, "CSV (.csv)", ".csv");
			if (batchMapFile != null) {
				this.batchMapPath = batchMapFile.getAbsolutePath();
			}
		});
		advancedSettingsMenuItem.setOnAction(e->{
			try{
				showAdvancedSettingsMenu(e);
			} catch (IOException ex){
				throw new RuntimeException(ex);
			}
		});
	}

	private void setupComboBoxes(){
		slideTypeComboBox.getItems().addAll(slideTypes);
		slideTypeComboBox.setOnAction(this::updateResultTypes);
		selectedSlideType = slideTypeComboBox.getSelectionModel().selectedItemProperty();
		selectedSlideType.addListener((v, o, n) -> updateGUI(false));

		stainComboBox.getItems().addAll(stainTypes);
		selectedStainType = stainComboBox.getSelectionModel().selectedItemProperty();
		selectedStainType.addListener((v, o, n) ->{
			selectedTargets.clear();
			updateGUI(true);
		});

		sourceComboBox.getItems().addAll(compartmentSources);
		selectedSource = sourceComboBox.getSelectionModel().selectedItemProperty();
		selectedSource.addListener((v, o, n) -> updateGUI(false));

		tileOptionComboBox.getItems().addAll(tileOptions);
		selectedTileOption = tileOptionComboBox.getSelectionModel().selectedItemProperty();
		selectedTileOption.addListener((v, o, n) -> updateGUI(false));

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
					expTimeTextField = QymiaUtils.formatTextFields(expTimeTextField, "integer", null);
					expTimeTextField.setPromptText("ms");
					expTimeTextField.setPrefWidth(50);
					expTimeTextField.setMaxWidth(60);
					if(selectedTargets.get(getItem())!=null && selectedTargets.get(getItem())!=0.0){
						expTimeTextField.setText(String.valueOf(selectedTargets.get(getItem()).intValue()));
					}
					expTimeTextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
						@Override
						public void handle(KeyEvent ke) {
							if (ke.getCode().equals(KeyCode.ENTER)) {
								if (expTimeTextField.getText().isEmpty() || expTimeTextField.getText() == null) {
									selectedTargets.replace(getItem(), 0.0);
								} else{
									selectedTargets.replace(getItem(), Double.parseDouble(expTimeTextField.getText()));
								}
								logger.info("expTimeTextField key enter: {}", selectedTargets);
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
							logger.info("expTimeTextField focus lost: {}", selectedTargets);
						}
					});
					selectedTargets.addListener((MapChangeListener.Change<? extends ColorTransform,? extends Double> c) -> {
						if (selectedTargets.containsKey(getItem()) && c.getKey().equals(getItem())) {
							logger.info("changing expTimeTextField {} to {}", getItem(), selectedTargets.get(getItem()).intValue());
							expTimeTextField.setText(String.valueOf(selectedTargets.get(getItem()).intValue()));
						}
						else if(c.wasRemoved() && c.getKey().equals(getItem())){
							logger.info("resetting expTimeTextField {}", getItem());
							expTimeTextField.setText("");
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
							if(!selectedTargets.containsKey(getItem())) {
								if (expTimeTextField == null || expTimeTextField.getText().isEmpty() || expTimeTextField.getText() == null) {
//								could check and set as -1 for error catching downstream....
									selectedTargets.put(getItem(), 0.0);
								} else {
									selectedTargets.put(getItem(), Double.parseDouble(expTimeTextField.getText()));
								}
							}
						} else {
							selectedTargets.remove(getItem());
						}
						logger.info("Checkbox selected: {}", selectedTargets);
						updateGUI(false);
					});
					checkBox.selectedProperty().set(selectedTargets.containsKey(getItem()));
					selectedTargets.addListener((MapChangeListener.Change<? extends ColorTransform,? extends Double> c) ->{
						checkBox.selectedProperty().set(selectedTargets.containsKey(getItem()));
					});
				}
				return checkBox;
			}
		});
	}

	private void updateResultTypes(ActionEvent event){
		resultTypeComboBox.valueProperty().set(null);
		resultTypeComboBox.getItems().clear();
		String currentSlideType = slideTypeComboBox.getValue();
		if (Objects.equals(currentSlideType, "TMA")){
			resultTypeComboBox.getItems().addAll(resultTypesTMA);
		} else {
			resultTypeComboBox.getItems().addAll(resultTypesWTS);
		}
	}
	private void updateResultTypes(){
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
		logger.info("ignore classes: {}", ignoreClasses.toString());
		logger.info("roi classes: {}", roiClasses.toString());
		var viewer = qupath.getViewer();
		var imageData = viewer.getImageData();
//		https://stackoverflow.com/questions/9062574/is-there-a-better-way-to-combine-two-string-sets-in-java
//		Set<PathClass> combinedRemove = Stream.concat(ignoreClasses.stream(), roiClasses.stream()).collect(Collectors.toSet());
//		May need to update filtered list predicate if ignoreClasses/roiClasses change
//		https://stackoverflow.com/questions/53075175/observablelist-returns-sublist-that-matches
//		compartmentList.setPredicate(p -> !ignoreClasses.contains(p) && !roiClasses.contains(p) && p != null);
		compartmentListView.setItems(compartmentList);
		if (imageData == null) {
			targetListView.getItems().clear();
			targetListView.setDisable(true);
			startQuantButton.setDisable(true);
			runForProjectMenuItem.setDisable(true);
            savePresetMenuItem.setDisable(true);
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
		QymiaQuantBackend.TileOption tileOption = selectedTileOption.get();
		//check if something is selected for compartments and targets....
		startQuantButton.setDisable(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0);
		runForProjectMenuItem.setDisable(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0);
		cancelButton.setDisable(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0);
        savePresetMenuItem.setDisable(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0);

		if(result != null && result.toLowerCase().contains("tile")){
			tileSizeTextField.setDisable(false);
			tileSizeLabel.setDisable(false);
			tileOptionComboBox.setDisable(false);
			if(tileOption == null){
				startQuantButton.setDisable(true);
				runForProjectMenuItem.setDisable(true);
				cancelButton.setDisable(true);
                savePresetMenuItem.setDisable(true);
			}
		} else {
			tileSizeTextField.setDisable(true);
			tileSizeLabel.setDisable(true);
			tileOptionComboBox.setDisable(true);
		}
	}

	/**
	 * Get a list of relevant color transforms for a specific image.
	 * @param imageData
	 * @return
	 */
	private Collection<ColorTransform> getAvailableTransforms(ImageData<BufferedImage> imageData) {
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

	/**
	 * Request project image entries to run script for.
	 * @param doSave
	 */
	void handleRunProject(final boolean doSave, final boolean reload) {
		Project<BufferedImage> project = qupath.getProject();
		if (project == null) {
			Dialogs.showNoProjectError("Qymia Quant");
			return;
		}

		String sameImageWarning = doSave ? "A selected image is open in the viewer!\nUse 'File>Reload data' to see changes." : null;
		var listSelectionView = ProjectDialogs.createImageChoicePane(qupath, project.getImageList(), previousImages, sameImageWarning);

		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Select project images");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
		dialog.getDialogPane().setContent(listSelectionView);
		dialog.setResizable(true);
		dialog.getDialogPane().setPrefWidth(600);
		dialog.initModality(Modality.APPLICATION_MODAL);
		Optional<ButtonType> result = dialog.showAndWait();
		if (!result.isPresent() || result.get() != ButtonType.OK)
			return;

		previousImages.clear();

		previousImages.addAll(listSelectionView.getTargetItems());

		if (previousImages.isEmpty())
			return;

		List<ProjectImageEntry<BufferedImage>> imagesToProcess = new ArrayList<>(previousImages);

		QuantTask worker = new QuantTask(project, imagesToProcess, doSave, reload);


		ProgressDialog progress = new ProgressDialog(worker);
		progress.initOwner(qupath.getStage());
		progress.setTitle("Batch script");
		progress.getDialogPane().setHeaderText("Batch processing...");
		progress.getDialogPane().setGraphic(null);
		progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		progress.getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, e -> {
			if (Dialogs.showYesNoDialog("Cancel batch script", "Are you sure you want to stop the running script after the current image?")) {
				worker.quietCancel();
				progress.setHeaderText("Cancelling...");
				runCancelled.set(true);
//				worker.cancel(false);
				progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
			}
			e.consume();
		});


		// Create & run task
//		ExecutorService es = qupath.createSingleThreadExecutor(this);
//		es.submit(worker);
		runningTask.set(qupath.createSingleThreadExecutor(this).submit(worker));
		progress.show();
	}

	class QuantTask extends Task<Void> {

		private Project<BufferedImage> project;
		private Collection<ProjectImageEntry<BufferedImage>> imagesToProcess;
		private boolean quietCancel = false;
		private boolean doSave = true;
		private boolean reload = false;

		QuantTask(final Project<BufferedImage> project,
                  final Collection<ProjectImageEntry<BufferedImage>> imagesToProcess,
                  final boolean doSave,
                  final boolean reload) {
			this.project = project;
			this.imagesToProcess = imagesToProcess;
			this.doSave = doSave;
			this.reload = reload;
//			if(imagesToProcess.size()==1){
//				this.reload = true;
//			} else {
//				this.reload = reload;
//			}
		}

		public void quietCancel() {
			this.quietCancel = true;
		}

		public boolean isQuietlyCancelled() {
			return quietCancel;
		}

		@Override
		public Void call() {

			long startTime = System.currentTimeMillis();
			Map<String, Object> params = setupQuantParams();
			if(params==null)
				return null;
			Map<ColorTransform, Double> selTargets = selectedTargets.entrySet().stream().collect(Collectors.toMap(
					e->e.getKey(),
					e->e.getValue())
			);
			Set<PathClass> selCompartments = selectedCompartments.parallelStream().collect(Collectors.toSet());

//			Vars for batch Map measurement conversion
			File pathMeasConvs = new File(Projects.getBaseDirectory(project) + File.separator + defaultMeasConvFolder);
			FilenameFilter jsonFilefilter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					String lowercaseName = name.toLowerCase();
					if (lowercaseName.endsWith(".json")) {
						return true;
					} else {
						return false;
					}
				}
			};
			File[] allMeasConvs = pathMeasConvs.listFiles(jsonFilefilter);
			List<File> allMeasConvList = new ArrayList<>();
			if(allMeasConvs!=null){
				allMeasConvList = new ArrayList<>(List.of(allMeasConvs));
			}

			Map<String, String> batchMap = null;
			if(convertMeasurementsProperty.get()){
				if(!batchMapPath.isEmpty()){
					batchMap = QymiaAnalysisPanelController.loadTwoColMap(batchMapPath);
				}else{
//					trying to find the batchMap file in the default folder if there is one....
					File batchMapParent = new File(Projects.getBaseDirectory(project)+File.separator+defaultBatchMapFolder);
					File[] batchMapFiles = batchMapParent.listFiles();
					if(batchMapFiles!=null){
						batchMapPath = batchMapFiles[0].getAbsolutePath();
						logger.info("setting new project batchMapPath to {}", batchMapPath);
						batchMap = QymiaAnalysisPanelController.loadTwoColMap(batchMapPath);
					}
				}
			}

			var viewersList = qupath.getViewerManager().getAllViewers();
			List<QuPathViewer> currentViewers = new ArrayList<>();
//			if (viewersList.size() == 1){
//				logger.info("Only one viewer found! Setting current viewer.");
//				currentViewers.add(viewersList.get(0));
//			}

			int counter = 0;
			for (ProjectImageEntry<BufferedImage> entry : imagesToProcess) {
				try {
					// Stop
					if (isQuietlyCancelled() || isCancelled()) {
						logger.warn("Script cancelled with " + (imagesToProcess.size() - counter) + " image(s) remaining");
						break;
					}

					updateProgress(counter, imagesToProcess.size());
					counter++;
					updateMessage(entry.getImageName() + " (" + counter + "/" + imagesToProcess.size() + ")");

					// Create a new region store if we need one
					System.gc();

					// Open saved data if there is any, or else the image itself
					ImageData<BufferedImage> imageData = entry.readImageData();
					logger.info("Working on {}", entry.getImageName());
					String entryImagePath = entry.getURIs().stream().findFirst().orElse(new URI("")).getPath();
					String entryImageName;
					if(entryImagePath.isEmpty()){
						entryImageName = entry.getImageName();
					} else {
						entryImageName = new File(entryImagePath).getName();
					}
					if (imageData == null) {
						logger.warn("Unable to open {} - will be skipped", entry.getImageName());
						continue;
					}

					logger.info("trying to get viewer for imagedata...");
//					Could there be a case where the properties are the same but the image is not the one opened in the viewer? I do not know, but this works for now.
//					currentViewers = viewersList.stream().filter(v -> v.getImageData().getProperties().equals(imageData.getProperties())).collect(Collectors.toList());
					currentViewers = viewersList.stream().filter(v -> project.getEntry(v.getImageData()).equals(entry)).collect(Collectors.toList());
					logger.info(currentViewers.toString());
					logger.info("using nThreads: {}", numCommandThreadsProperty().get());

					QymiaQuantBackend qymiaQuant = new QymiaQuantBackend(
							imageData,
							selTargets,
							selCompartments,
							ignoreClasses,
							roiClasses,
							params,
							numCommandThreadsProperty().get(),
							runCancelled,
							controlListToToggle,
							menuItemListToToggle,
							quantProgressBar,
							progressLabel,
							quantModel.getUseCUDAProperty().get()
					);

					qymiaQuant.runQuant().get();

					if(convertMeasurementsProperty.get()){
						if(batchMap != null && !allMeasConvList.isEmpty()){
							logger.info("trying to convert measurements for {}", entryImageName);
							List<QymiaAnalysisPanelController.MeasurementConverter> currentMeasConvs = QymiaAnalysisPanelController.getMeasConvsFromBatchMap(
									entryImageName,
									batchMap,
									allMeasConvList
							);
							if (currentMeasConvs != null) {
								QymiaAnalysisPanelController.calculateMeasurementConversions(imageData, currentMeasConvs);
							} else{
								logger.error("Measurement converters for {} are null", entryImageName);
							}
						} else {
							logger.error("Batch map is null or PROJ/measurement_converters contains no measurement converter files\nCannot convert measurements.");
						}
					}

					if (doSave && !runCancelled.get()) {
						logger.info("saving image data...");
						entry.saveImageData(imageData);
					}

					if (reload && !currentViewers.isEmpty()){
						logger.info("reloading image data in viewer(s)...");
						for(var openViewer : currentViewers){
//							need to run on the JavaFX application thread to avoid throwing errors
							Platform.runLater(()->{
								try {
									openViewer.setImageData(imageData);
								} catch (IOException ex) {
									logger.error("Error setting image data in viewer", ex);
								}
							});
						}
					}

					if (imagesToProcess.size() > 1 && currentViewers.isEmpty()) {
						logger.warn("Closing server {}", imageData);
//					    need to run on the JavaFX application thread to avoid throwing errors
						Platform.runLater(()->{
							try {
								imageData.getServer().close();
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						});
					}

					try {
						var store = qupath == null ? null : qupath.getImageRegionStore();
						if (store != null)
							store.clearCache();
						System.gc();
					} catch (Exception ex) {
						logger.error("Error clearing tile cache");
						ex.printStackTrace();
					}

				} catch (Exception ex) {
					logger.error("Error running batch script");
					ex.printStackTrace();
				}
			}
			updateProgress(imagesToProcess.size(), imagesToProcess.size());

			long endTime = System.currentTimeMillis();

			long timeMillis = endTime - startTime;
			String time = null;
			if (timeMillis > 1000*60)
				time = String.format("Total processing time: %.2f minutes", timeMillis/(1000.0 * 60.0));
			else if (timeMillis > 1000)
				time = String.format("Total processing time: %.2f seconds", timeMillis/(1000.0));
			else
				time = String.format("Total processing time: %d milliseconds", timeMillis);
			logger.info("Processed {} images", imagesToProcess.size());
			logger.info(time);

			return null;
		}


		@Override
		protected void done() {
			super.done();
			// Make sure we reset the running task
			Platform.runLater(() -> runningTask.setValue(null));
		}
	};

	Map<String, Object> setupQuantParams(){
		//		double check that all fields have values
		String slide = selectedSlideType.get();
		String stain = selectedStainType.get();
		String source = selectedSource.get();
		String result = selectedResultType.get();
//		Need to allow user to select what they want to tile....
		QymiaQuantBackend.TileOption tileOption = selectedTileOption.get();
		if (tileOption == null){
			tileOption = FULL_IMAGE;
		}
//		TODO: If this is a task for the project, this will fail outright after the images with no selected objects.
//		TODO: fix this so that it throws an error dialog if the run for project option was chosen
//		PathObjectSelectionModel selModel;
		List<PathObject> selectedObjs;
		if (tileOption == SELECTED_OBJS) {
//			var selModel = qupath.getViewer().getHierarchy().getSelectionModel();
//			var pathObjs = qupath.getViewer().getHierarchy().getObjects(null, PathObject.class);
//			selectedObjs = pathObjs.parallelStream().filter(p -> selModel.isSelected(p))
//					.collect(Collectors.toList());
			selectedObjs = qupath.getViewer().getAllSelectedObjects().stream().toList();
//			selModel.clearSelection();
			logger.info("selected objects:\n{}", selectedObjs.toString());
		} else {
			selectedObjs = Collections.emptyList();
		}
		logger.info("Using tile option: {}", tileOption.toString());
		//check if something is selected for compartments and targets....
		if(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0) {
//			throw new Exception("Insufficient inputs selected. Check that compartments and targets are selected, comboboxes are filled, etc.");
			logger.warn("Insufficient inputs selected. Check that compartments and targets are selected, comboboxes are filled, etc.");
			return null;
		}
//		runCancelled.set(false);
//		Platform.runLater(()->{
//			exportMeasButton.setDisable(true);
//			exportMeasMenuItem.setDisable(true);
//			startQuantButton.setDisable(true);
//			runForProjectMenuItem.setDisable(true);
//			quantProgressBar.setProgress(-1);
//			progressLabel.setText("Starting Compartment Quantification...");
//		});
		boolean normalizeScore = normalizeMenuItem.selectedProperty().get();
		boolean rescaleScore = rescaleMenuItem.selectedProperty().get();
		boolean verboseMeasures = verboseMeasuresMenuItem.selectedProperty().get();
		boolean tileUnitIsMicrons = tileUnitIsMicronsMenuItem.selectedProperty().get();

		double downsample = quantModel.getDownsampleProperty().get();
		logger.info("using downsample: {}", downsample);
//		Class<? extends PathObject> sourceType;
//		if(source.equals("Cells")){
//			sourceType = PathCellObject.class;
//		} else if (source.equals("Detections")){
//			sourceType = PathDetectionObject.class;
//		} else {
//			sourceType = PathAnnotationObject.class;
//		}

		double intensityScaleFactor = 1.0;
//		https://www.microscopyu.com/microscopy-basics/image-brightness
//		Tries to account for intensity brightness between objectives for epifluorescence...
		double refNA = quantModel.getRefNAProperty().get();
		double workingNA = quantModel.getWorkingNAProperty().get();
		double refMag = quantModel.getRefMagProperty().get();
		double workingMag = quantModel.getWorkingMagProperty().get();

		if(stain.equals("Fluorescence")) {
			intensityScaleFactor = Math.pow((Math.pow(refNA, 2) * workingMag) / (refMag * Math.pow(workingNA, 2)), 2);
		} else{
//			Accounts for brightfield/trans-illumination intensity differences
			intensityScaleFactor = Math.pow(refNA / refMag, 2) / Math.pow(workingNA / workingMag, 2);
		}

		if(Double.isNaN(intensityScaleFactor) || Double.isInfinite(intensityScaleFactor)){
			intensityScaleFactor = 1.0;
			logger.error("Intensity scale factor is invalid! Check ref and working NA and Mag input values within advanced settings...");
			logger.info("Using default intensity scale factor = 1.0");
		}

		int inputTileSize;
		if(tileSizeTextField.getText().isEmpty() || tileSizeTextField.getText() == null)
			inputTileSize = 0;
		else
			inputTileSize = Integer.parseInt(tileSizeTextField.getText());

		Map<String, Object> params = new ConcurrentHashMap<>(Map.ofEntries(
				Map.entry("downsample", downsample),
				Map.entry("tileSize", inputTileSize),
				Map.entry("tileUnitIsMicrons", tileUnitIsMicrons),
				Map.entry("tileOption", tileOption),
                Map.entry("deleteTilesBeforeRun", deleteTilesMenuItem.selectedProperty().get()),
//				Map.entry("selectedObjects", selectedObjs),
				Map.entry("sourceString", source),
				Map.entry("verboseMeasures", verboseMeasures),
				Map.entry("rescaleScore", rescaleScore),
				Map.entry("normalizeScore", normalizeScore),
				Map.entry("maxFloatValue", maxFloatValue),
				Map.entry("result", result),
				Map.entry("slide", slide),
				Map.entry("stain", stain),
				Map.entry("intensityScaleFactor", intensityScaleFactor)
		));


		return params;
	}

	public void cancelRunningTask(){
		Future<?> future = runningTask.get();
		if (future != null) {
			if (future.isDone())
				runningTask.set(null);
			else
				future.cancel(true);
		}
	}

	//Main panel and button commands
	public void startQuant(ActionEvent e){
//		Wrap all this into a task
		Project<BufferedImage> project = qupath.getProject();
		if (project == null) {
			Dialogs.showNoProjectError("Qymia Quant");
			return;
		}
//		var entry = project == null ? null : project.getEntry(qupath.getImageData());
        var entry = project.getEntry(qupath.getImageData());
//		Make sure to save image data before starting or else reload doesn't work properly
		try {
			entry.saveImageData(qupath.getImageData());
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		List<ProjectImageEntry<BufferedImage>> imagesToProcess = new ArrayList<>(List.of(entry));
		if (imagesToProcess.isEmpty()){
			Dialogs.showErrorMessage("Qymia Quant", "No image data found. Make sure image in project is opened.");
			return;
		}

		QuantTask worker = new QuantTask(project, imagesToProcess, false, true);
		// Create & run task
//		ExecutorService es = qupath.createSingleThreadExecutor(this);
//		es.submit(worker);
		runningTask.set(qupath.createSingleThreadExecutor(this).submit(worker));

	}

	public void runForProject(ActionEvent e){
//		always set saving to true for batch jobs...
		handleRunProject(true, true);
	}

	public void cancelQuant(ActionEvent e){
		runCancelled.set(true);
//		cancelRunningTask();
//		exportMeasButton.setDisable(false);
//		exportMeasMenuItem.setDisable(false);
//		startQuantButton.setDisable(false);
//		runForProjectMenuItem.setDisable(false);

		progressLabel.setText("Cancelling task...");
//		would be cool to make progress bar red or something
		quantProgressBar.setProgress(-1);
	}
	
	void advancedSettings(ActionEvent e) {
		logger.info("Opening advanced settings panel...");
	}
	
	void helpButton(ActionEvent e) {
		logger.info("Opening help dialog...");
	}

	void exportMasksButton(ActionEvent e) {
		logger.info("Opening dialog to export masks for project...");
	}

    void saveQuantPreset(ActionEvent e){
//      building QymiaQuant backend object for preset
		Map<String, Object> params = setupQuantParams();
		LinkedHashMap<ColorTransform, Double> selTargets = new LinkedHashMap<>(selectedTargets.entrySet().stream().collect(Collectors.toMap(
				m->m.getKey(),
				m->m.getValue())
			)
		);
		Set<PathClass> selCompartments = selectedCompartments.parallelStream().collect(Collectors.toSet());
        logger.info("Saving QymiaQuant parameters as preset...");
		QymiaQuantPreset newPreset = new QymiaQuantPreset(
				selTargets, selCompartments,
				ignoreClasses, roiClasses,
				params
		);
		Path presetDir;
		try {
			logger.info("trying to create default quant preset directory...");
			presetDir = Paths.get(Projects.getBaseDirectory(qupath.getProject()) + File.separator + "quant_presets");
			java.nio.file.Files.createDirectories(presetDir);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
//		File selector dialog
		File newPresetFilePath = Dialogs.promptToSaveFile("Save QymiaQuant Preset", presetDir.toFile(), "new_preset", "JSON (.json)", ".json");
		if (newPresetFilePath==null){
			logger.error("null preset file name, cannot save!");
			return;
		}
		Gson gson = GsonTools.getInstance(true);
		try {
			BufferedWriter file = Files.newWriter(
					newPresetFilePath,
					StandardCharsets.UTF_8);
			file.write(gson.toJson(newPreset));
			file.close();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	void loadQuantPreset(ActionEvent e){
		loadQuantPreset();
	}
	void loadQuantPreset(){
		File dirBase = qupath.getProject() != null ? Projects.getBaseDirectory(qupath.getProject()) : new File(System.getProperty("user.home"));
		File presetFilePath = Dialogs.promptForFile("Load QymiaQuant Preset", dirBase, "JSON (.json)", ".json");
		loadQuantPreset(presetFilePath);
	}

	public void loadQuantPreset(File presetFilePath){
		if (presetFilePath == null) {
			logger.error("No QymiaQuant Preset selected....");
			return;
		}
		if(!presetFilePath.toString().endsWith(".json")){
			logger.error("{} is not a JSON file and is not a QymiaQuant Preset!", presetFilePath);
			return;
		}
		Gson gson = GsonTools.getInstance(true);
		QymiaQuantPreset quantPreset = null;

		try(
			BufferedReader reader = Files.newReader(presetFilePath, StandardCharsets.UTF_8);
			){
			quantPreset = gson.fromJson(reader, QymiaQuantPreset.class);
		} catch (Exception ex) {
			logger.error("error reading QymiaQuant Preset....");
			ex.printStackTrace();
		}
		if(quantPreset == null){
			logger.error("error reading QymiaQuant Preset... it is null...");
			return;
		}
//		get presets
		Map<ColorTransforms.ColorTransform, Double> presetTargets = quantPreset.getTargets();
		Set<PathClass> presetCompartments = quantPreset.getCompartments();
		Set<PathClass> presetIgnoreClasses = quantPreset.getIgnoreClasses();
		Set<PathClass> presetROIClasses = quantPreset.getROIClasses();
		Map<String, Object> presetParams = quantPreset.getParams();
//		set gui params
		quantModel.setDownsample((double) presetParams.get("downsample"));
		Integer inputTileSize = ((Double) presetParams.get("tileSize")).intValue();
		if(inputTileSize > 0) {
			tileSizeTextField.setText(String.valueOf(inputTileSize));
		}
		tileUnitIsMicronsMenuItem.selectedProperty().set((boolean) presetParams.get("tileUnitIsMicrons"));
		tileOptionComboBox.getSelectionModel().select(
				Enum.valueOf(QymiaQuantBackend.TileOption.class, (String) presetParams.get("tileOption"))
		);
		deleteTilesMenuItem.selectedProperty().set((boolean) presetParams.get("deleteTilesBeforeRun"));
		sourceComboBox.getSelectionModel().select((String) presetParams.get("sourceString"));
		verboseMeasuresMenuItem.selectedProperty().set((boolean) presetParams.get("verboseMeasures"));
		rescaleMenuItem.selectedProperty().set((boolean) presetParams.get("rescaleScore"));
		normalizeMenuItem.selectedProperty().set((boolean) presetParams.get("normalizeScore"));
		maxFloatValue = (double) presetParams.get("maxFloatValue");
		stainComboBox.getSelectionModel().select((String) presetParams.get("stain"));
		slideTypeComboBox.getSelectionModel().select((String) presetParams.get("slide"));
//		update result types based on slide type
		updateResultTypes();
		resultTypeComboBox.getSelectionModel().select((String) presetParams.get("result"));
//		set presets
		selectedTargets.clear();
		selectedTargets.putAll(presetTargets);
		logger.info("updating targets using preset: {}", selectedTargets);
		logger.info("updating targets using preset (preset): {}", presetTargets);
		selectedCompartments.clear();
		selectedCompartments.addAll(presetCompartments);
		ignoreClasses.addAll(presetIgnoreClasses);
		roiClasses.addAll(presetROIClasses);
	}

	void switchToAnalysisMode(ActionEvent e, String tabName) throws IOException {
//		FXMLLoader analysisSceneLoader = new FXMLLoader(getClass().getResource("/QymiaAnalysisPanel.fxml"));
//		logger.info("starting analysis pane from tab: {}", tabName);
//		analysisSceneLoader.setControllerFactory(controllerClass -> this);
//		Parent newRoot = analysisSceneLoader.load();
////		just a hack to get the current Quant scene easily
//		exportMeasButton.getScene().setRoot(newRoot);
		sceneManager.switchScene("/QymiaAnalysisPanel.fxml");
	}

	void switchToPresetMode(ActionEvent e) throws IOException{
		sceneManager.switchScene("/QymiaPresetPanel.fxml");
	}

	void showAdvancedSettingsMenu(ActionEvent e) throws IOException{
		final Stage dialog = new Stage();
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.initOwner(qupath.getStage());
		//		preload this with scene manager
		Scene scene = sceneManager.getScene("/QymiaQuantSettings.fxml");

//		old, might have incompatibilities based on file location
//		FXMLLoader loader = new FXMLLoader();
//		loader.setLocation(getClass().getResource("/QymiaQuantSettings.fxml"));
//		loader.setControllerFactory(controllerClass -> new QymiaQuantSettingsController(
//				qupath, quantModel)
//		);
//		Parent panel = loader.load();
//		this.qymiaQuantPanelController = loader.getController();
//		Scene scene = new Scene(loader.load());
		dialog.setScene(scene);
		dialog.show();
	}
	
	//Overload these methods depending on input arguments. Export data dialog may just run these commands in isolation
	public void exportMasks(File outputFile) {
		
	}


}
