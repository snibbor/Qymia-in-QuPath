package qupath.extension.companalysis;

import com.google.common.eventbus.EventBus;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.*;
import javafx.concurrent.Task;
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
import javafx.stage.Modality;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;

import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.scripting.DefaultScriptEditor;
import qupath.lib.gui.scripting.ScriptTab;
import qupath.lib.gui.tools.MeasurementExporter;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.*;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.objects.*;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;

import static qupath.lib.common.Prefs.getNumThreads;
import static qupath.lib.objects.classes.PathClassFactory.getPathClass;
import static qupath.lib.scripting.QP.clearMeasurements;


import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

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
	MenuItem runForProjectMenuItem;
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

//	don't like how I need two observable lists to do this... because MenuItem doesn't inherit from Control.......
	private ObservableList<Control> controlListToToggle = FXCollections.observableArrayList();
	private ObservableList<MenuItem> menuItemListToToggle = FXCollections.observableArrayList();
	private List<ProjectImageEntry<BufferedImage>> previousImages = new ArrayList<>();
	private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();


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
		runForProjectMenuItem.setOnAction(this::runForProject);
		cancelButton.setOnAction(this::cancelQuant);
//		setup controls list to disable during quantification
		controlListToToggle.addAll(exportMeasButton, startQuantButton);
		menuItemListToToggle.addAll(exportMeasMenuItem);

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

	public void updateGUI(Boolean forceUpdateTransforms) {
		logger.info("updating GUI...");
		var viewer = qupath.getViewer();
		var imageData = viewer.getImageData();

		compartmentListView.setItems(qupath.getAvailablePathClasses());
		if (imageData == null) {
			targetListView.getItems().clear();
			targetListView.setDisable(true);
			startQuantButton.setDisable(true);
			runForProjectMenuItem.setDisable(true);
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
		runForProjectMenuItem.setDisable(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0);
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
	void handleRunProject(final boolean doSave) {
		Project<BufferedImage> project = qupath.getProject();
		if (project == null) {
			Dialogs.showNoProjectError("CompQuant");
			return;
		}

		// Ensure that the previous images remain selected if the project still contains them
//		FilteredList<ProjectImageEntry<?>> sourceList = new FilteredList<>(FXCollections.observableArrayList(project.getImageList()));

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
//		previousImages.addAll(listSelectionView.getTargetItems());

		previousImages.addAll(ProjectDialogs.getTargetItems(listSelectionView));

		if (previousImages.isEmpty())
			return;

		List<ProjectImageEntry<BufferedImage>> imagesToProcess = new ArrayList<>(previousImages);

		CompQuantPanelController.ProjectTask worker = new CompQuantPanelController.ProjectTask(project, imagesToProcess, doSave);


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
		runningTask.set(qupath.createSingleThreadExecutor(this).submit(worker));
		progress.show();
	}

	class ProjectTask extends Task<Void> {

		private Project<BufferedImage> project;
		private Collection<ProjectImageEntry<BufferedImage>> imagesToProcess;
		private ScriptTab tab;
		private boolean quietCancel = false;
		private boolean doSave = false;

		ProjectTask(final Project<BufferedImage> project, final Collection<ProjectImageEntry<BufferedImage>> imagesToProcess, final boolean doSave) {
			this.project = project;
			this.imagesToProcess = imagesToProcess;
			this.doSave = doSave;
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
					if (imageData == null) {
						logger.warn("Unable to open {} - will be skipped", entry.getImageName());
						continue;
					}

					CompQuantBackend compQuant = new CompQuantBackend(
							imageData,
							selTargets,
							selCompartments,
							ignoreClasses,
							roiClasses,
							params,
							getNumThreads()-3,
							runCancelled,
							controlListToToggle,
							menuItemListToToggle,
							quantProgressBar,
							progressLabel
					);

					runQuant(compQuant).get();

					if (doSave)
						entry.saveImageData(imageData);
					imageData.getServer().close();

//					might be redundant because already checking to clear cache inside each CompQuantBackend object after closing...
					try {
						var store = qupath == null ? null : qupath.getImageRegionStore();
						if (store != null)
							store.clearCache();
						System.gc();
					} catch (Exception e) {

					}
				} catch (Exception e) {
					logger.error("Error running batch script: {}", e);
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
		//check if something is selected for compartments and targets....
		if(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0) {
//			throw new Exception("Insufficient inputs selected. Check that compartments and targets are selected, comboboxes are filled, etc.");
			logger.warn("Insufficient inputs selected. Check that compartments and targets are selected, comboboxes are filled, etc.");
			return null;
		}
		runCancelled.set(false);
		Platform.runLater(()->{
			exportMeasButton.setDisable(true);
			exportMeasMenuItem.setDisable(true);
			startQuantButton.setDisable(true);
			runForProjectMenuItem.setDisable(true);
			quantProgressBar.setProgress(-1);
			progressLabel.setText("Starting Compartment Quantification...");
		});
		boolean normalizeScore = normalizeMenuItem.selectedProperty().get();
		boolean rescaleScore = rescaleMenuItem.selectedProperty().get();

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

		Map<String, Object> params = new ConcurrentHashMap<>(Map.ofEntries(
				Map.entry("downsample", downsample),
				Map.entry("tileSize", inputGridSize),
				Map.entry("sourceType", sourceType),
				Map.entry("rescaleScore", rescaleScore),
				Map.entry("normalizeScore", normalizeScore),
				Map.entry("maxFloatValue", maxFloatValue),
				Map.entry("result", result),
				Map.entry("slide", slide),
				Map.entry("stain", stain)
		));


		return params;
	}

	CompletableFuture<Void> runQuant(CompQuantBackend compQuant){
//		Every time you run this code, make sure that the runCancelled is false
		runCancelled.set(false);
		ImageData<BufferedImage> imageData = compQuant.getImageData();
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		Map<String, Object> params = compQuant.getParams();
		String slide = (String) params.get("slide");
		Class<? extends PathObject> source = (Class<? extends PathObject>) params.get("sourceType");
		String result = (String) params.get("result");
//		Remove detection objects that are not cells, clear source measurements
		if(source.equals(PathAnnotationObject.class)) {
			List<PathObject> notCells = hierarchy.getDetectionObjects().parallelStream().filter(p->!p.isCell() && !p.isTile() && !p.getParent().isTile())
					.collect(Collectors.toList());
			hierarchy.removeObjects(notCells, true);
			clearMeasurements(hierarchy, hierarchy.getAnnotationObjects());
		} else if(source.equals(PathCellObject.class)){
			List<PathObject> notCells = hierarchy.getDetectionObjects().parallelStream().filter(p->!p.isCell() && !p.isTile() && !p.getParent().isTile())
					.collect(Collectors.toList());
			hierarchy.removeObjects(notCells, true);
			clearMeasurements(hierarchy, hierarchy.getCellObjects());
		}
		CompletableFuture<Void> runFuture = CompletableFuture.runAsync(()->{
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
//						If you are making grids/tiles, delete any old tiles?
						logger.warn("Deleting any tile objects!!");
						hierarchy.removeObjects(hierarchy.getTileObjects(), true);
						compQuant.TileRecalcCompartmentsAndScores().get();
					}catch (ExecutionException | InterruptedException | CancellationException ex){
						Platform.runLater(()-> {
							exportMeasButton.setDisable(false);
							exportMeasMenuItem.setDisable(false);
							startQuantButton.setDisable(false);
							runForProjectMenuItem.setDisable(false);
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
						runForProjectMenuItem.setDisable(false);
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
						runForProjectMenuItem.setDisable(false);
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
//			not necessary but just in case
			Platform.runLater(()-> {
				exportMeasButton.setDisable(false);
				exportMeasMenuItem.setDisable(false);
				startQuantButton.setDisable(false);
				runForProjectMenuItem.setDisable(false);
			});
//				cleanup vars
			compQuant.close();
			var store = qupath == null ? null : qupath.getImageRegionStore();
			if (store != null) {
//					This was the reason for the memory accumulation! makes sense in retrospect, considering all the region requests that are made...
				logger.info("Clearing Image Region Store cache...");
				store.clearCache();
			}
			System.gc();
			logger.info("Completed with all tasks...");
//			update progress bar again.....?
		});
		return runFuture;
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
		Map<String, Object> params = setupQuantParams();
		if(params==null)
			return;
		CompQuantBackend compQuant = new CompQuantBackend(
				qupath.getImageData(),
				selectedTargets,
				selectedCompartments,
				ignoreClasses,
				roiClasses,
				params,
				getNumThreads()-3,
				runCancelled,
				controlListToToggle,
				menuItemListToToggle,
				quantProgressBar,
				progressLabel
		);
//		make runQuant it's own task??
		runQuant(compQuant);
	}

	public void runForProject(ActionEvent e){
//		always set saving to true for batch jobs...
		handleRunProject(true);
	}

	public void cancelQuant(ActionEvent e){
		runCancelled.set(true);
		cancelRunningTask();
		exportMeasButton.setDisable(false);
		exportMeasMenuItem.setDisable(false);
		startQuantButton.setDisable(false);
		runForProjectMenuItem.setDisable(false);

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
	
	void advancedSettings(ActionEvent e) {
		logger.info("Opening advanced settings panel...");
	}
	
	void helpButton(ActionEvent e) {
		logger.info("Opening help dialog...");
	}
	
	void exportImageMeasurementsButton(ActionEvent e) {
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

	void exportAllMeasurementsButton(ActionEvent e) {
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

	List<String> getMeasExcludeColumns(String excludeType) {
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
	void exportMasksButton(ActionEvent e) {
		logger.info("Opening dialog to export masks for project...");
	}
	
	//Overload these methods depending on input arguments. Export data dialog may just run these commands in isolation
	public void exportMasks(File outputFile) {
		
	}


}
