package qupath.extension.qiimia;

import com.google.common.eventbus.EventBus;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
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

import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.scripting.ScriptTab;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.*;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.objects.*;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import static qupath.extension.qiimia.QiimiaQuantBackend.TileOption.*;
import static qupath.lib.common.Prefs.getNumThreads;
import static qupath.lib.objects.classes.PathClassFactory.getPathClass;
import static qupath.lib.scripting.QP.*;


import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QiimiaQuantPanelController implements Initializable{

	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(QiimiaQuantPanelController.class);

	// this bus is used application wide
	private final EventBus appEventBus = new EventBus();

	private final QuPathGUI qupath;
	private final ForkJoinPool startRunFJP = new ForkJoinPool(2);

	private final AtomicReference<Boolean> runCancelled = new AtomicReference<>(false);

	public LinkedHashMap<ColorTransform, Double> availableTransforms = new LinkedHashMap<>();

	//will be in settings menu

	private final Set<PathClass> ignoreClasses = Set.of(new PathClass[]{getPathClass("Ignore*"),
																		getPathClass("Necrosis"),
																		getPathClass("Other")});
	private final Set<PathClass> roiClasses = Set.of(new PathClass[]{getPathClass("ROI")});

	//default params
	private final int defaultTileSize = 512;
	private final ObjectProperty<Integer> tileSize = new SimpleObjectProperty(defaultTileSize);

	private FilteredList<PathClass> compartmentList;
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
	private final String[] compartmentSources = {"Detections", "Annotations", "Cells"};
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
	ComboBox<QiimiaQuantBackend.TileOption> tileOptionComboBox;
	private final QiimiaQuantBackend.TileOption[] tileOptions = {FULL_IMAGE, ROI_ONLY, ROI_AND_IMAGE, SELECTED_OBJS};
	private ReadOnlyObjectProperty<QiimiaQuantBackend.TileOption> selectedTileOption;
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
	MenuItem importGridOverlayMenuItem;
	@FXML
	MenuItem runForProjectMenuItem;
	@FXML
	CheckMenuItem tileUnitIsMicronsMenuItem;
	@FXML
	CheckMenuItem verboseMeasuresMenuItem;
	@FXML
	CheckMenuItem normalizeMenuItem;
	@FXML
	CheckMenuItem deleteTilesMenuItem;
	@FXML
	CheckMenuItem rescaleMenuItem;
	// rescale scores using maxFloatValue and bitdepth
	private double maxFloatValue = 1000.0/4.0;

	FileChooser fileSelector = new FileChooser();
	File initialFileDirectory;

//	don't like how I need two observable lists to do this... because MenuItem doesn't inherit from Control.......
	private ObservableList<Control> controlListToToggle = FXCollections.observableArrayList();
	private ObservableList<MenuItem> menuItemListToToggle = FXCollections.observableArrayList();
	private List<ProjectImageEntry<BufferedImage>> previousImages = new ArrayList<>();
	private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();
	public final Action EXPORT;


	public QiimiaQuantPanelController(QuPathGUI qupath) {
		this.qupath = qupath;
		var measureCommand = new QiimiaMeasurementExportCommand(qupath);
		EXPORT = qupath.createProjectAction(project -> measureCommand.run());
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		appEventBus.register(this);
		setupMenu();
		setupComboBoxes();
		setupListViews();
		exportMeasButton.setOnAction(EXPORT);
		tileSizeTextField = formatTextFields(tileSizeTextField, "integer", String.valueOf(defaultTileSize));
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
		menuItemListToToggle.addAll(exportMeasMenuItem);

		compartmentList = qupath.getAvailablePathClasses().filtered(p -> !ignoreClasses.contains(p) && !roiClasses.contains(p) && p != null);

		updateGUI(true);
	}

	private void setupMenu(){
		exportMeasMenuItem.setOnAction(EXPORT);
		exportMaskMenuItem.setOnAction(this::exportMasksButton);
		tileUnitIsMicronsMenuItem.selectedProperty().addListener((obs, oldVal, newVal) -> {
//			Check and set prompt text for tile size if the unit is changed to microns
			if(obs.getValue()){
				tileSizeTextField.setPromptText("um");
			} else {
				tileSizeTextField.setPromptText("px");
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
		selectedStainType.addListener((v, o, n) -> updateGUI(true));

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
//		https://stackoverflow.com/questions/9062574/is-there-a-better-way-to-combine-two-string-sets-in-java
//		Set<PathClass> combinedRemove = Stream.concat(ignoreClasses.stream(), roiClasses.stream()).collect(Collectors.toSet());
//		May need to update filtered list predicate if ignoreClasses/roiClasses change
//		https://stackoverflow.com/questions/53075175/observablelist-returns-sublist-that-matches
		compartmentListView.setItems(compartmentList);
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
		QiimiaQuantBackend.TileOption tileOption = selectedTileOption.get();
		//check if something is selected for compartments and targets....
		startQuantButton.setDisable(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0);
		runForProjectMenuItem.setDisable(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0);
		cancelButton.setDisable(slide == null || stain == null || source == null || result == null || selectedCompartments.size() == 0 || selectedTargets.size() == 0);

		if(result != null && result.toLowerCase().contains("tile")){
			tileSizeTextField.setDisable(false);
			tileSizeLabel.setDisable(false);
			tileOptionComboBox.setDisable(false);
			if(tileOption == null){
				startQuantButton.setDisable(true);
				runForProjectMenuItem.setDisable(true);
				cancelButton.setDisable(true);
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
	void handleRunProject(final boolean doSave) {
		Project<BufferedImage> project = qupath.getProject();
		if (project == null) {
			Dialogs.showNoProjectError("Qiimia Quant");
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

		previousImages.addAll(ProjectDialogs.getTargetItems(listSelectionView));

		if (previousImages.isEmpty())
			return;

		List<ProjectImageEntry<BufferedImage>> imagesToProcess = new ArrayList<>(previousImages);

		QiimiaQuantPanelController.ProjectTask worker = new QiimiaQuantPanelController.ProjectTask(project, imagesToProcess, doSave, false);


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
		private boolean doSave = true;
		private boolean reload = false;

		ProjectTask(final Project<BufferedImage> project, final Collection<ProjectImageEntry<BufferedImage>> imagesToProcess, final boolean doSave, final boolean reload) {
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

			var viewersList = qupath.getViewers();
			QuPathViewerPlus currentViewer = null;
			if (viewersList.size() == 1){
				logger.info("Only one viewer found! Setting current viewer.");
				currentViewer = viewersList.get(0);
			}

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
					if (imageData == null) {
						logger.warn("Unable to open {} - will be skipped", entry.getImageName());
						continue;
					}

//					This works but not sure when there would be more than 1 viewer...
					if(reload && viewersList.size() >= 1 && imagesToProcess.size() > 1){
						logger.info("getting viewer for imagedata...");
						currentViewer = viewersList.stream().filter(v -> v.getImageData() == imageData).findFirst().orElse(null);
					}

					QiimiaQuantBackend qiimiaQuant = new QiimiaQuantBackend(
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

					runQuant(qiimiaQuant).get();

					if (doSave && !runCancelled.get()) {
						logger.info("saving image data...");
						entry.saveImageData(imageData);
					}

					if (reload && currentViewer != null){
						logger.info("reloading image data in viewer...");
//						need to run on the JavaFX application thread to avoid throwing errors
						QuPathViewerPlus finalCurrentViewer = currentViewer;
						Platform.runLater(()->{
							finalCurrentViewer.setImageData(imageData);
						});
					}

					if (imagesToProcess.size() > 1) {
						logger.warn("Closing server {}", imageData.toString());
						imageData.getServer().close();
					}

//					might be redundant because already checking to clear cache inside each QiimiaQuantBackend object after closing...
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
//		Need to allow user to select what they want to tile....
		QiimiaQuantBackend.TileOption tileOption = selectedTileOption.get();
		if (tileOption == null){
			tileOption = FULL_IMAGE;
		}
//		PathObjectSelectionModel selModel;
		List<PathObject> selectedObjs;
		if (tileOption == SELECTED_OBJS) {
			var selModel = qupath.getViewer().getHierarchy().getSelectionModel();
			var pathObjs = qupath.getViewer().getHierarchy().getObjects(null, PathObject.class);
			selectedObjs = pathObjs.parallelStream().filter(p -> selModel.isSelected(p))
					.collect(Collectors.toList());
////			selectedObjs = qupath.getViewer().getAllSelectedObjects();
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
		boolean verboseMeasures = verboseMeasuresMenuItem.selectedProperty().get();
		boolean tileUnitIsMicrons = tileUnitIsMicronsMenuItem.selectedProperty().get();

		double downsample = 1.0;
		Class<? extends PathObject> sourceType;
		if(source.equals("Cells")){
			sourceType = PathCellObject.class;
		} else if (source.equals("Detections")){
			sourceType = PathDetectionObject.class;
		} else {
			sourceType = PathAnnotationObject.class;
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
				Map.entry("selectedObjects", selectedObjs),
				Map.entry("sourceType", sourceType),
				Map.entry("verboseMeasures", verboseMeasures),
				Map.entry("rescaleScore", rescaleScore),
				Map.entry("normalizeScore", normalizeScore),
				Map.entry("maxFloatValue", maxFloatValue),
				Map.entry("result", result),
				Map.entry("slide", slide),
				Map.entry("stain", stain)
		));


		return params;
	}

	CompletableFuture<Void> runQuant(QiimiaQuantBackend qiimiaQuant){
//		Every time you run this code, make sure that the runCancelled is false
		runCancelled.set(false);
		ImageData<BufferedImage> imageData = qiimiaQuant.getImageData();
		PathObjectHierarchy hierarchy = imageData.getHierarchy();
		Map<String, Object> params = qiimiaQuant.getParams();
		String slide = (String) params.get("slide");
		Class<? extends PathObject> source = (Class<? extends PathObject>) params.get("sourceType");
		String result = (String) params.get("result");
//		Remove detection objects within any ROIs that are not cells, clear source measurements
		if(result.toLowerCase().contains("roi")){
			List<PathObject> oldROIComps = hierarchy.getDetectionObjects().parallelStream()
					.filter(p->!p.isCell() && !p.isTile() && !p.getParent().isTile() && roiClasses.contains(p.getParent().getPathClass()))
					.collect(Collectors.toList());
			hierarchy.removeObjects(oldROIComps, true);
		}

		if(source.equals(PathAnnotationObject.class)) {
			if(!result.toLowerCase().contains("roi"))
				clearMeasurements(hierarchy, hierarchy.getAnnotationObjects());
		} else if(source.equals(PathCellObject.class)){
			if(!result.toLowerCase().contains("roi"))
				clearMeasurements(hierarchy, hierarchy.getCellObjects());
		} else if(source.equals(PathDetectionObject.class)){
			if(!result.toLowerCase().contains("roi"))
				clearMeasurements(hierarchy, hierarchy.getDetectionObjects());
		}
		CompletableFuture<Void> runFuture = CompletableFuture.runAsync(()->{
			if(runCancelled.get()){
				throw new CancellationException();
			}
			if(result.toLowerCase().contains("tile")){
				if(tileSizeTextField.getText().isEmpty() || tileSizeTextField.getText() == null) {
					logger.warn("Tilesize textfield cannot be 0 or empty when trying to compute tile results!");
				}else if(tileSizeTextField.getText() != null && Integer.parseInt(tileSizeTextField.getText()) == 0) {
					logger.warn("Tilesize textfield cannot be 0 or empty when trying to compute tile results!");
				}else {
					Platform.runLater(()->{
						progressLabel.setText("Quantifying Tiles...");
					});

					if(deleteTilesMenuItem.selectedProperty().get()){
//						If you are making grids/tiles, delete any old tiles?
						logger.warn("Deleting any tile objects!!");
						hierarchy.removeObjects(hierarchy.getTileObjects(), true);
					}

					try{
						qiimiaQuant.TileRecalcCompartmentsAndScores().get();
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
		})
		.thenRun(()->{
			if(runCancelled.get()){
				throw new CancellationException();
			}
			if(result.toLowerCase().contains("tma") && slide.equals("TMA")){
				logger.info("Beginning compartment quantification of TMA cores for compartments: {} and targets: {}...", selectedCompartments.toString(), selectedTargets.toString());
				Platform.runLater(()->{
					progressLabel.setText("Quantifying TMA core compartments...");
				});
				try {
					qiimiaQuant.TMARecalcCompartmentsAndScores().get();
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
				logger.info("Beginning compartment quantification of ROIs for compartments: {} and targets: {}...", selectedCompartments.toString(), selectedTargets.toString());
				Platform.runLater(()->{
					progressLabel.setText("Quantifying ROI compartments...");
				});
				try {
					qiimiaQuant.getTargetScoresForROIs().get();
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
			if (ex.getCause() instanceof CancellationException){
				logger.warn("Run cancelled?");
				Platform.runLater(() ->{
					progressLabel.setText("Run cancelled..");
					quantProgressBar.setProgress(1);
				});
			} else {
				Platform.runLater(() ->{
					progressLabel.setText(ex.getCause().toString());
				});
				ex.printStackTrace();
			}
			logger.warn(ex.toString());
			try {
				qiimiaQuant.cancelTasks().get();
			} catch (InterruptedException | ExecutionException exc) {
				throw new RuntimeException(exc);
			}
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
			qiimiaQuant.close();
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
//		Wrap all this into a task
		Project<BufferedImage> project = qupath.getProject();
		if (project == null) {
			Dialogs.showNoProjectError("Qiimia Quant");
			return;
		}
		var entry = project == null ? null : project.getEntry(qupath.getImageData());
//		Make sure to save image data before starting or else reload doesn't work properly
		try {
			entry.saveImageData(qupath.getImageData());
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		List<ProjectImageEntry<BufferedImage>> imagesToProcess = new ArrayList<>(List.of(entry));
		if (imagesToProcess.isEmpty()){
			Dialogs.showErrorMessage("Qiimia Quant", "No image data found. Make sure image in project is opened.");
			return;
		}

		QiimiaQuantPanelController.ProjectTask worker = new QiimiaQuantPanelController.ProjectTask(project, imagesToProcess, false, true);
		// Create & run task
		runningTask.set(qupath.createSingleThreadExecutor(this).submit(worker));


//		Map<String, Object> params = setupQuantParams();
//		if(params==null)
//			return;
//		QiimiaQuantBackend qiimiaQuant = new QiimiaQuantBackend(
//				qupath.getImageData(),
//				selectedTargets,
//				selectedCompartments,
//				ignoreClasses,
//				roiClasses,
//				params,
//				getNumThreads()-3,
//				runCancelled,
//				controlListToToggle,
//				menuItemListToToggle,
//				quantProgressBar,
//				progressLabel
//		);
//		runQuant(qiimiaQuant);

	}

	public void runForProject(ActionEvent e){
//		always set saving to true for batch jobs...
		handleRunProject(true);
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
	
	//Overload these methods depending on input arguments. Export data dialog may just run these commands in isolation
	public void exportMasks(File outputFile) {
		
	}


}
