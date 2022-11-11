package qupath.extension.qiimia;

import com.google.common.io.Files;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Modality;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.prefs.PathPrefs;

import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.scripting.languages.ScriptLanguageProvider;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;
import qupath.lib.scripting.QP;
import qupath.lib.scripting.ScriptParameters;
import qupath.lib.scripting.languages.ExecutableLanguage;
import qupath.lib.scripting.languages.ScriptLanguage;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static qupath.lib.common.Prefs.getNumThreads;
import static qupath.lib.objects.classes.PathClassFactory.getPathClass;

public class QiimiaPresetPanelController extends BaseController implements Initializable {

	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(QiimiaPresetPanelController.class);

	private final QuPathGUI qupath;

	private final AtomicReference<Boolean> runCancelled = new AtomicReference<>(false);

	private String clearQuantPathObjOption = "all";

	public LinkedHashMap<ColorTransform, Double> availableTransforms = new LinkedHashMap<>();

	//will be in settings menu
	private final ObservableSet<PathClass> ignoreClasses = FXCollections.observableSet();
	private List<PathClass> defaultIgnoreClasses = new ArrayList<>(
			List.of(
					getPathClass("Ignore*"),
					getPathClass("Necrosis"),
					getPathClass("Other")
			)
	);
	private final ObservableSet<PathClass> roiClasses = FXCollections.observableSet();
	private List<PathClass> defaultRoiClasses = new ArrayList<>(
			List.of(
					getPathClass("ROI")
			)
	);

	//default params
	private static DoubleProperty refNAProperty = PathPrefs.createPersistentPreference("refNAQiimiaQuant", 0.75);
	private static DoubleProperty refMagProperty = PathPrefs.createPersistentPreference("refMagQiimiaQuant", 20.0);

	private static DoubleProperty workingNAProperty = PathPrefs.createPersistentPreference("workingNAQiimiaQuant", 0.75);
	private static DoubleProperty workingMagProperty = PathPrefs.createPersistentPreference("workingMagQiimiaQuant", 20.0);

	private FilteredList<PathClass> compartmentList;
	private final ObservableSet<PathClass> selectedCompartments = FXCollections.observableSet();
	// target and exposure time if IF image
	private final ObservableMap<ColorTransform, Double> selectedTargets = FXCollections.observableMap(new LinkedHashMap<>());
	Map<String, Object> presetParams = new HashMap<>();


	@FXML
	Menu settingsMenu;
	@FXML
	Menu navigateMenu;
	@FXML
	Menu helpMenu;
	@FXML
	Button startQuantButton;
	@FXML
	Button cancelQuantButton;
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
	MenuItem runForProjectMenuItem;
	@FXML
	CheckMenuItem tileUnitIsMicronsMenuItem;
	private static BooleanProperty tileUnitIsMicronsProperty = PathPrefs.createPersistentPreference("tileUnitIsMicronsQiimiaQuant", false);
	@FXML
	CheckMenuItem verboseMeasuresMenuItem;
	private static BooleanProperty verboseMeasuresProperty = PathPrefs.createPersistentPreference("verboseMeasuresQiimiaQuant", true);
	@FXML
	CheckMenuItem normalizeMenuItem;
	private static BooleanProperty normalizeProperty = PathPrefs.createPersistentPreference("normalizeQiimiaQuant", true);
	@FXML
	CheckMenuItem deleteTilesMenuItem;
	private static BooleanProperty deleteTilesProperty = PathPrefs.createPersistentPreference("deleteTilesQiimiaQuant", true);
	@FXML
	CheckMenuItem rescaleMenuItem;
	private static BooleanProperty rescaleProperty = PathPrefs.createPersistentPreference("rescaleQiimiaQuant", false);
	// rescale scores using maxFloatValue and bitdepth
	private double maxFloatValue = 1000.0/4.0;

	@FXML
	MenuItem selectBatchMapMenuItem;
	private String defaultBatchMapFolder = "batch_map";
	private String batchMapPath = "";
	private String defaultMeasConvFolder = "measurement_converters";
	@FXML
	CheckMenuItem convertMeasMenuItem;
	private static BooleanProperty convertMeasurementsProperty = new SimpleBooleanProperty(false);

	@FXML
	MenuItem selectSyncMapMenuItem;
	private String defaultSyncMapFolder = "sync_map";
	private String syncMapPath = "";

	@FXML
	MenuItem selectScriptDirMenuItem;
	private String defaultScriptFolder = "scripts";
	private String scriptDir = "";
	private Map<String, File> scriptFileMap = new HashMap<>();
	@FXML
	ComboBox<String> scriptComboBox;
	private ReadOnlyObjectProperty<String> selectedScriptName;
	@FXML
	MenuItem selectPresetDirMenuItem;
	private String defaultPresetFolder = "quant_presets";
	private String presetDir = "";
	private Map<String, File> presetFileMap = new HashMap<>();
	@FXML
	ComboBox<String> presetComboBox;
	private ReadOnlyObjectProperty<String> selectedPresetName;
	@FXML
	Button editPresetButton;

	@FXML
	MenuItem advancedSettingsMenuItem;
	@FXML
	MenuItem standardCurveMenuItem;
	@FXML
	MenuItem comparisonMenuItem;

	@FXML
	MenuItem switchQuantMenuItem;

//	don't like how I need two observable lists to do this... because MenuItem doesn't inherit from Control.......
	private ObservableList<Control> controlListToToggle = FXCollections.observableArrayList();
	private ObservableList<MenuItem> menuItemListToToggle = FXCollections.observableArrayList();
	private List<ProjectImageEntry<BufferedImage>> previousImages = new ArrayList<>();
	private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();
	public final Action EXPORT;
//	For defalut script imports
	private static final Collection<Class<?>> defaultClasses = getDefaultClasses();
	private static final Collection<Class<?>> defaultStaticClasses = getDefaultStaticClasses();


	public QiimiaPresetPanelController(QuPathGUI qupath) {
		this.qupath = qupath;
		var measureCommand = new QiimiaMeasurementExportCommand(qupath);
		EXPORT = qupath.createProjectAction(project -> measureCommand.run());
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		setupMenu();
		setupComboBoxes();
		editPresetButton.setOnAction(this::editQuantPreset);
		exportMeasButton.setOnAction(EXPORT);
		startQuantButton.setOnAction(this::startQuant);
		runForProjectMenuItem.setOnAction(this::runForProject);
		cancelQuantButton.setOnAction(this::cancelQuant);
//		setup controls list to disable during quantification
		controlListToToggle.addAll(exportMeasButton, startQuantButton, editPresetButton);
		menuItemListToToggle.addAll(runForProjectMenuItem, exportMeasMenuItem, standardCurveMenuItem, comparisonMenuItem);

//		setup PathClass sets
		ignoreClasses.addAll(defaultIgnoreClasses);
		roiClasses.addAll(defaultRoiClasses);
		ignoreClasses.addListener(new SetChangeListener<PathClass>() {
			@Override
			public void onChanged(Change<? extends PathClass> change) {
//				compartmentList.setPredicate(p -> !ignoreClasses.contains(p) && !roiClasses.contains(p) && p != null);
				updateGUI();
			}
		});

		roiClasses.addListener(new SetChangeListener<PathClass>() {
			@Override
			public void onChanged(Change<? extends PathClass> change) {
//				compartmentList.setPredicate(p -> !ignoreClasses.contains(p) && !roiClasses.contains(p) && p != null);
				updateGUI();
			}
		});

//		compartmentList = qupath.getAvailablePathClasses().filtered(p -> !ignoreClasses.contains(p) && !roiClasses.contains(p) && p != null);

		updateGUI();
	}

	private void setupMenu() {
		exportMeasMenuItem.setOnAction(EXPORT);
//		exportMaskMenuItem.setOnAction(this::exportMasksButton);
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

		switchQuantMenuItem.setOnAction(e -> {
			try{
				switchToQuantMode(e);
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		});

		normalizeMenuItem.selectedProperty().bindBidirectional(normalizeProperty);
		rescaleMenuItem.selectedProperty().bindBidirectional(rescaleProperty);
		deleteTilesMenuItem.selectedProperty().bindBidirectional(deleteTilesProperty);
		tileUnitIsMicronsMenuItem.selectedProperty().bindBidirectional(tileUnitIsMicronsProperty);
		convertMeasMenuItem.selectedProperty().bindBidirectional(convertMeasurementsProperty);
		selectBatchMapMenuItem.setOnAction(e->{
			File dirBase = qupath.getProject() != null ? Projects.getBaseDirectory(qupath.getProject()) : new File(System.getProperty("user.home"));
			File batchMapFile = Dialogs.promptForFile("Staining Batch Map File", dirBase, "CSV (.csv)", ".csv");
			if (batchMapFile != null) {
				this.batchMapPath = batchMapFile.getAbsolutePath();
			}
		});
		selectSyncMapMenuItem.setOnAction(e->{
			File dirBase = qupath.getProject() != null ? Projects.getBaseDirectory(qupath.getProject()) : new File(System.getProperty("user.home"));
			File syncMapFile = Dialogs.promptForFile("Sync Quant Map File", dirBase, "CSV (.csv)", ".csv");
			if (syncMapFile != null) {
				this.syncMapPath = syncMapFile.getAbsolutePath();
			}
		});

		selectScriptDirMenuItem.setOnAction(e->{
			File dirBase = qupath.getProject() != null ? Projects.getBaseDirectory(qupath.getProject()) : new File(System.getProperty("user.home"));
			File scriptDirFile = Dialogs.promptForDirectory("Script Directory", dirBase);
			if (scriptDirFile != null) {
				this.scriptDir = scriptDirFile.getAbsolutePath();
//				update scriptComboBox
				scriptFileMap = makeFileMap(scriptDirFile.toPath(), ".groovy");
				scriptComboBox.getSelectionModel().clearSelection();
				scriptComboBox.getItems().clear();
				scriptComboBox.getItems().addAll(scriptFileMap.keySet());
			}
			updateGUI();
		});
		selectPresetDirMenuItem.setOnAction(e->{
			File dirBase = qupath.getProject() != null ? Projects.getBaseDirectory(qupath.getProject()) : new File(System.getProperty("user.home"));
			File presetDirFile = Dialogs.promptForDirectory("QiimiaQuant Preset Directory", dirBase);
			if (presetDirFile != null) {
				this.presetDir = presetDirFile.getAbsolutePath();
//				update presetComboBox
				presetFileMap = makeFileMap(presetDirFile.toPath(), ".json");
				presetComboBox.getSelectionModel().clearSelection();
				presetComboBox.getItems().clear();
				presetComboBox.getItems().addAll(presetFileMap.keySet());
			}
			updateGUI();
		});
//		advancedSettingsMenuItem.setOnAction(e->{
//			try{
//				showAdvancedSettingsMenu(e);
//			} catch (IOException ex){
//				throw new RuntimeException(ex);
//			}
//		});
	}
	private static String nameWithoutExtension(Path path, String ext) {
		String name = path.getFileName().toString();
		if (name.endsWith(ext))
			return name.substring(0, name.length()-ext.length());
		return name;
	}
	private static Map<String, File> makeFileMap(Path path, String exten){
		if (path == null || !java.nio.file.Files.isDirectory(path)) {
			return Collections.emptyMap();
		} else {
			try (var stream = java.nio.file.Files.list(path)) {
				return stream.filter(p -> java.nio.file.Files.isRegularFile(p) && p.toString().endsWith(exten))
						.collect(Collectors.toMap(
								p -> nameWithoutExtension(p, exten),
								p -> p.toFile())
						);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
	private void setupComboBoxes(){
//		look into the directory and build file map
//		script directory
		File pathScripts = new File(Projects.getBaseDirectory(qupath.getProject()) + File.separator + defaultScriptFolder);
		scriptFileMap = makeFileMap(pathScripts.toPath(), ".groovy");
//		set combobox using script names
		scriptComboBox.getItems().setAll(scriptFileMap.keySet());
		selectedScriptName = scriptComboBox.getSelectionModel().selectedItemProperty();
		scriptComboBox.setOnAction(e -> {
			updateGUI();
		});
//		preset directory
		File pathPresets = new File(Projects.getBaseDirectory(qupath.getProject()) + File.separator + defaultPresetFolder);
		presetFileMap = makeFileMap(pathPresets.toPath(), ".json");
//		set combobox using preset names
		presetComboBox.getItems().addAll(presetFileMap.keySet());
		selectedPresetName = presetComboBox.getSelectionModel().selectedItemProperty();
		presetComboBox.setOnAction(e -> {
			if(presetFileMap.get(selectedPresetName.get())!=null) {
				loadQuantPreset(presetFileMap.get(selectedPresetName.get()), e);
			}
			updateGUI();
		});
	}

	public void updateGUI() {
		var viewer = qupath.getViewer();
		var imageData = viewer.getImageData();

		if (imageData == null) {
			startQuantButton.setDisable(true);
			runForProjectMenuItem.setDisable(true);
//			cancelQuantButton.setDisable(true);
			return;
		}

//		Check that selectedTargets are inside available transforms, otherwise the preset is invalid....
		var newTransforms = getAvailableTransforms(imageData);
		if(selectedTargets.size() > 0 && !newTransforms.containsAll(selectedTargets.keySet())){
//			Dialogs.showErrorMessage("QiimiaQuant Preset: Targets Invalid for ImageData", "Image does not contain all target channels/transforms in preset....");
			Dialogs.showWarningNotification("QiimiaQuant Preset: Targets Invalid for ImageData", "Image does not contain all target channels/transforms in preset.... \nonly will work if quant image is mapped to this image via sync map.");
//			startQuantButton.setDisable(true);
//			runForProjectMenuItem.setDisable(true);
//			return;
		}

		//check if something is selected for compartments and targets....
		String slide = (String) presetParams.get("slide");
		String stain = (String) presetParams.get("stain");
		String source = (String) presetParams.get("sourceString");
		String result = (String) presetParams.get("result");
		if(selectedPresetName.get()==null||selectedScriptName.get()==null||slide==null||stain==null||source==null||result==null||selectedCompartments.size()==0||selectedTargets.size()==0){
			startQuantButton.setDisable(true);
			cancelQuantButton.setDisable(true);
			runForProjectMenuItem.setDisable(true);
		} else {
			startQuantButton.setDisable(false);
			cancelQuantButton.setDisable(false);
			runForProjectMenuItem.setDisable(false);
		}

//		if(result != null && result.toLowerCase().contains("tile")){
//			QiimiaQuantBackend.TileOption tileOption;
//			try {
//				tileOption = Enum.valueOf(QiimiaQuantBackend.TileOption.class, (String) presetParams.get("tileOption"));
//			} catch(IllegalArgumentException ex){
//				logger.error("tileOption in preset is invalid! Cannot load/run preset... {}", presetParams.get("tileOption"));
//				startQuantButton.setDisable(true);
//				cancelQuantButton.setDisable(true);
//				runForProjectMenuItem.setDisable(true);
//				Dialogs.showErrorMessage("QiimiaQuant Preset: Tile Option Invalid", "Preset contains invalid tile option....");
//				ex.printStackTrace();
//				return;
//			}
//			startQuantButton.setDisable(false);
//			cancelQuantButton.setDisable(false);
//			runForProjectMenuItem.setDisable(false);
//		}

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

//		previousImages.addAll(ProjectDialogs.getTargetItems(listSelectionView));
		previousImages.addAll(listSelectionView.getTargetItems());

		if (previousImages.isEmpty())
			return;

		List<ProjectImageEntry<BufferedImage>> imagesToProcess = new ArrayList<>(previousImages);

		PresetTask worker = new PresetTask(project, imagesToProcess, doSave, reload);


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

	/**
	 * Get the collection of classes to import at the start of a script, if desired.
	 * @return collection of default classes
	 */
	public static Collection<Class<?>> getDefaultClasses() {
		var out = QPEx.getCoreClasses();
//		var out = QP.getCoreClasses();
		out.add(QPEx.class);	// Add itself
		return out;
	}

	/**
	 * Get the collection of static classes to import at the start of a script, if desired.
	 * @return collection of default static classes
	 */
	public static Collection<Class<?>> getDefaultStaticClasses() {
		List<Class<?>> out = new ArrayList<>();
		out.add(QPEx.class);
//		out.add(QP.class);
		return out;
	}

	class PresetTask extends Task<Void> {

		private Project<BufferedImage> project;
		private Collection<ProjectImageEntry<BufferedImage>> imagesToProcess;
		private boolean quietCancel = false;
		private boolean doSave = true;
		private boolean reload = false;

		PresetTask(final Project<BufferedImage> project,
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

			runCancelled.set(false);

			long startTime = System.currentTimeMillis();
			if(presetParams==null)
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
					batchMap = QiimiaAnalysisPanelController.loadTwoColMap(batchMapPath);
				}else{
//					trying to find the batchMap file in the default folder if there is one....
					File batchMapParent = new File(Projects.getBaseDirectory(project)+File.separator+defaultBatchMapFolder);
					File[] batchMapFiles = batchMapParent.listFiles();
					if(batchMapFiles!=null){
						batchMapPath = batchMapFiles[0].getAbsolutePath();
						logger.info("setting new project batchMapPath to {}", batchMapPath);
						batchMap = QiimiaAnalysisPanelController.loadTwoColMap(batchMapPath);
					}
				}
			}

			Map<String, String> syncMap = null;
			if(!syncMapPath.isEmpty()){
				syncMap = QiimiaAnalysisPanelController.loadTwoColMap(syncMapPath);
			}else{
//				trying to find the syncMap file in the default folder if there is one....
				File syncMapParent = new File(Projects.getBaseDirectory(project)+File.separator+defaultSyncMapFolder);
				File[] syncMapFiles = syncMapParent.listFiles();
				if(syncMapFiles!=null){
					syncMapPath = syncMapFiles[0].getAbsolutePath();
					logger.info("setting new project syncMapPath to {}", syncMapPath);
					syncMap = QiimiaAnalysisPanelController.loadTwoColMap(syncMapPath);
				}
			}

			var viewersList = qupath.getViewers();
			List<QuPathViewerPlus> thisCurrentViewers = new ArrayList<>();
			List<QuPathViewerPlus> quantCurrentViewers = new ArrayList<>();

			File scriptFile = scriptFileMap.get(selectedScriptName.get());
			String script = null;
			try {
				script = GeneralTools.readFileAsString(scriptFile.getAbsolutePath());
			} catch (IOException e) {
				logger.error("Error loading script {}", selectedScriptName.get());
				throw new RuntimeException(e);
			}

			String ext = null;
			if (scriptFile != null)
				ext = GeneralTools.getExtension(scriptFile).orElse(null);

			ScriptLanguage language = ScriptLanguageProvider.getLanguageFromExtension(ext);
			if (!(language instanceof ExecutableLanguage)) {
				logger.error("Script is not a ExecutableLanguage... {}", language);
				return null;
			}

//			ScriptContext context = null;

			List<ProjectImageEntry<BufferedImage>> allProjectEntryList = project.getImageList();

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
					logger.info("Entry URIs: {}", entry.getUris());
					String entryImagePath = entry.getUris().stream().findFirst().orElse(new URI("")).getPath();
					String entryImageName;
					if(entryImagePath.isEmpty()){
						entryImageName = entry.getImageName();
					} else {
						entryImageName = new File(entryImagePath).getName();
					}
					logger.info("Entry File Name: {}", entryImageName);
					if (imageData == null) {
						logger.warn("Unable to open {} - will be skipped", entry.getImageName());
						continue;
					}

//					use sync map to add current annotations and load correct image for quantification.....
					ImageData<BufferedImage> quantImageData = null;
					ProjectImageEntry<BufferedImage> quantEntry = null;
					String quantImageName = null;
					if(syncMap!=null) {
						String syncFileName = syncMap.get(entryImageName);
						if (syncFileName != null) {
							logger.info("Trying to get first image for quantification from sync map");
							for(ProjectImageEntry<BufferedImage> projEntry: allProjectEntryList){
								File projImageFile = new File(projEntry.getUris().stream().findFirst().orElse(new URI("")).getPath());
								if(syncFileName.equals(projImageFile.getName())){
									quantImageData = projEntry.readImageData();
									quantEntry = projEntry;
									quantImageName = projImageFile.getName();
									break;
								}
							}
						}
					}

					logger.info("trying to get viewer for this imagedata...");
//						Could there be a case where the properties are the same but the image is not the one opened in the viewer? I do not know, but this works for now.
					thisCurrentViewers = viewersList.stream().filter(v -> v.getImageData().getProperties().equals(imageData.getProperties())).collect(Collectors.toList());
					logger.info(thisCurrentViewers.toString());
					if(quantImageData!=null){
						logger.info("trying to get viewer for quant imagedata...");
						ImageData<BufferedImage> finalQuantImageData = quantImageData;
						quantCurrentViewers = viewersList.stream().filter(v -> v.getImageData().getProperties().equals(finalQuantImageData.getProperties())).collect(Collectors.toList());
						logger.info(quantCurrentViewers.toString());
					}

//					add/replace current annotations to quantification image, script preprocess/segment, quantify, convert, and add PathObjects to this imageData.....
					if(quantImageData!=null && quantEntry!=null){
						PathObjectHierarchy quantHierarchy = quantImageData.getHierarchy();
//						Clear annotations on quantImageData
						if(clearQuantPathObjOption.toLowerCase().contains("annotation")) {
							quantHierarchy.removeObjects(quantHierarchy.getAnnotationObjects(), true);
						} else if(clearQuantPathObjOption.toLowerCase().contains("all")){
							quantHierarchy.clearAll();
						}
//						add the annotations from this imageData to quantImageData
						logger.warn("SyncMap quantification does not transform annotations! e.g. Only compatible for pseudo-DAB and IF images or aligned image pairs.");
						quantHierarchy.addObjects(imageData.getHierarchy().getAnnotationObjects());

//						run script for pre-processing on quantImageData (e.g. compartment segmentation)
						Platform.runLater(()->{
							quantProgressBar.setProgress(-1);
							progressLabel.setText("Running pre-processing script...");
							for(Control button : controlListToToggle){
								button.setDisable(true);
							}
							for (MenuItem menuItem : menuItemListToToggle) {
								menuItem.setDisable(true);
							}
						});

						var builder = ScriptParameters.builder()
//							.setWriter(writer)
//							.setErrorWriter(new DefaultScriptEditor.ScriptConsoleWriter(console, true))
							.setScript(script)
//							.setFile(tab.getFile())
							.setProject(project)
							.setImageData(quantImageData)
//							.setBatchIndex(batchIndex)
//							.setBatchSize(batchSize)
//							.setBatchSaveResult(batchSave)
							.setDefaultImports(getDefaultClasses())
							.setDefaultStaticImports(getDefaultStaticClasses());
//
//
						((ExecutableLanguage) language).execute(builder.build());

						if(runCancelled.get()){
							logger.info("run cancelled...");
							Platform.runLater(()->{
								progressLabel.setText("Cancelled");
								quantProgressBar.setProgress(0);
								for(Control button : controlListToToggle){
									button.setDisable(false);
								}
								for (MenuItem menuItem : menuItemListToToggle) {
									menuItem.setDisable(false);
								}
							});
							return null;
						}

						QiimiaQuantBackend qiimiaQuant = new QiimiaQuantBackend(
								quantImageData,
								selTargets,
								selCompartments,
								ignoreClasses,
								roiClasses,
								presetParams,
								getNumThreads()-2,
								runCancelled,
								controlListToToggle,
								menuItemListToToggle,
								quantProgressBar,
								progressLabel
						);

						qiimiaQuant.runQuant().get();

						if(convertMeasurementsProperty.get()){
							if(batchMap != null && !allMeasConvList.isEmpty()){
								logger.info("trying to convert measurements for {}", quantImageName);
								List<QiimiaAnalysisPanelController.MeasurementConverter> currentMeasConvs = QiimiaAnalysisPanelController.getMeasConvsFromBatchMap(
										quantImageName,
										batchMap,
										allMeasConvList
								);
								if (currentMeasConvs != null) {
									QiimiaAnalysisPanelController.calculateMeasurementConversions(quantImageData, currentMeasConvs);
								} else{
									logger.error("Measurement converters for {} are null", quantImageName);
								}
							} else {
								logger.error("Batch map is null or PROJ/measurement_converters contains no measurement converter files\nCannot convert measurements.");
							}
						}

//						Save by default
						if (!runCancelled.get()) {
							logger.info("saving quant image data...");
							quantEntry.saveImageData(quantImageData);
						}
//						Transfer PathObjects from quantImage to this imageData
						logger.info("Transfering heirarchy from quant image to this image...");
						imageData.getHierarchy().setHierarchy(quantImageData.getHierarchy());
						if (doSave && !runCancelled.get()) {
							logger.info("saving this image data...");
							entry.saveImageData(imageData);
						}
					} else {
//						Just script process and quantify on current imageData
//						run script for pre-processing on imageData (e.g. compartment segmentation)
						Platform.runLater(()->{
							progressLabel.setText("Running pre-processing script...");
							quantProgressBar.setProgress(-1);
							for(Control button : controlListToToggle){
								button.setDisable(true);
							}
							for (MenuItem menuItem : menuItemListToToggle) {
								menuItem.setDisable(true);
							}
						});

						var builder = ScriptParameters.builder()
//							.setWriter(writer)
//							.setErrorWriter(new DefaultScriptEditor.ScriptConsoleWriter(console, true))
								.setScript(script)
//							.setFile(tab.getFile())
								.setProject(project)
								.setImageData(imageData)
//							.setBatchIndex(batchIndex)
//							.setBatchSize(batchSize)
//							.setBatchSaveResult(batchSave)
								.setDefaultImports(getDefaultClasses())
								.setDefaultStaticImports(getDefaultStaticClasses());
						((ExecutableLanguage) language).execute(builder.build());

						if(runCancelled.get()){
							logger.info("run cancelled...");
							Platform.runLater(()->{
								progressLabel.setText("Cancelled");
								quantProgressBar.setProgress(0);
								for(Control button : controlListToToggle){
									button.setDisable(false);
								}
								for (MenuItem menuItem : menuItemListToToggle) {
									menuItem.setDisable(false);
								}
							});
							return null;
						}

						QiimiaQuantBackend qiimiaQuant = new QiimiaQuantBackend(
								imageData,
								selTargets,
								selCompartments,
								ignoreClasses,
								roiClasses,
								presetParams,
								getNumThreads() - 2,
								runCancelled,
								controlListToToggle,
								menuItemListToToggle,
								quantProgressBar,
								progressLabel
						);

						qiimiaQuant.runQuant().get();

						if (convertMeasurementsProperty.get()) {
							if (batchMap != null && !allMeasConvList.isEmpty()) {
								logger.info("trying to convert measurements for {}", entryImageName);
								List<QiimiaAnalysisPanelController.MeasurementConverter> currentMeasConvs = QiimiaAnalysisPanelController.getMeasConvsFromBatchMap(
										entryImageName,
										batchMap,
										allMeasConvList
								);
								if (currentMeasConvs != null) {
									QiimiaAnalysisPanelController.calculateMeasurementConversions(imageData, currentMeasConvs);
								} else {
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
					}

//					Reload imageData and quantImageData and close imageServers
					if(quantImageData!=null){
						ImageData<BufferedImage> finalQuantImageData = quantImageData;
						if (reload && !quantCurrentViewers.isEmpty()){
							logger.info("reloading this image data in viewer(s)...");
							for(var openViewer : quantCurrentViewers){
//							need to run on the JavaFX application thread to avoid throwing errors
								Platform.runLater(()->{
									openViewer.setImageData(finalQuantImageData);
								});
							}
						}
						if(imagesToProcess.size()>1 && quantCurrentViewers.isEmpty()) {
							logger.warn("Closing server {}", finalQuantImageData.toString());
							Platform.runLater(() -> {
								try {
									finalQuantImageData.getServer().close();
								} catch (Exception e) {
									throw new RuntimeException(e);
								}
							});
						}
					}

					if (reload && !thisCurrentViewers.isEmpty()){
						logger.info("reloading this image data in viewer(s)...");
						for(var openViewer : thisCurrentViewers){
//							need to run on the JavaFX application thread to avoid throwing errors
							Platform.runLater(()->{
								openViewer.setImageData(imageData);
							});
						}
					}

//					need to run on the JavaFX application thread to avoid throwing errors
					if(imagesToProcess.size()>1 && thisCurrentViewers.isEmpty()) {
						logger.warn("Closing server {}", imageData.toString());
						Platform.runLater(() -> {
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
			Dialogs.showErrorMessage("Qiimia Quant", "No image data found. Make sure image in project is opened.");
			return;
		}

		PresetTask worker = new PresetTask(project, imagesToProcess, false, true);
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
		progressLabel.setText("Cancelling task...");
//		would be cool to make progress bar red or something
		quantProgressBar.setProgress(-1);
	}
	
	void helpButton(ActionEvent e) {
		logger.info("Opening help dialog...");
	}

	void loadQuantPreset(ActionEvent e){
		loadQuantPreset();
	}
	void loadQuantPreset(File presetFilePath, ActionEvent e){
		loadQuantPreset(presetFilePath);
	}
	void loadQuantPreset(){
		File dirBase = qupath.getProject() != null ? Projects.getBaseDirectory(qupath.getProject()) : new File(System.getProperty("user.home"));
		File presetFilePath = Dialogs.promptForFile("Load QiimiaQuant Preset", dirBase, "JSON (.json)", ".json");
		loadQuantPreset(presetFilePath);
	}
	void loadQuantPreset(File presetFilePath){
		if(!presetFilePath.toString().endsWith(".json")){
			logger.error("{} is not a JSON file and is not a QiimiaQuant Preset!", presetFilePath);
			return;
		}
		Gson gson = GsonTools.getInstance(true);
		QiimiaQuantPreset quantPreset = null;
		if (presetFilePath == null) {
			logger.error("No QiimiaQuant Preset selected....");
			return;
		}
		try(
			BufferedReader reader = Files.newReader(presetFilePath, StandardCharsets.UTF_8);
			){
			quantPreset = gson.fromJson(reader, QiimiaQuantPreset.class);
		} catch (Exception ex) {
			logger.error("error reading QiimiaQuant Preset....");
			ex.printStackTrace();
		}
		if(quantPreset == null){
			logger.error("error reading QiimiaQuant Preset... it is null...");
			return;
		}
//		set and get presets
		selectedTargets.clear();
		selectedTargets.putAll(quantPreset.getTargets());
		logger.info("updating targets using preset: {}", selectedTargets);
		selectedCompartments.clear();
		selectedCompartments.addAll(quantPreset.getCompartments());
		logger.info("updating compartments using preset: {}", selectedCompartments);
		ignoreClasses.addAll(quantPreset.getIgnoreClasses());
		logger.info("updating ignoreClasses using preset: {}", ignoreClasses);
		roiClasses.addAll(quantPreset.getROIClasses());
		logger.info("updating roiClasses using preset: {}", roiClasses);
		presetParams = quantPreset.getParams();
//		set MenuItem params only
		tileUnitIsMicronsMenuItem.selectedProperty().set((boolean) presetParams.get("tileUnitIsMicrons"));
		presetParams.put("tileSize", ((Double) presetParams.get("tileSize")).intValue());
		presetParams.put("tileOption", Enum.valueOf(QiimiaQuantBackend.TileOption.class, (String) presetParams.get("tileOption")));
		deleteTilesMenuItem.selectedProperty().set((boolean) presetParams.get("deleteTilesBeforeRun"));
		verboseMeasuresMenuItem.selectedProperty().set((boolean) presetParams.get("verboseMeasures"));
		rescaleMenuItem.selectedProperty().set((boolean) presetParams.get("rescaleScore"));
		normalizeMenuItem.selectedProperty().set((boolean) presetParams.get("normalizeScore"));
		maxFloatValue = (double) presetParams.get("maxFloatValue");
	}
	void switchToAnalysisMode(ActionEvent e, String tabName) throws IOException {
		sceneManager.switchScene("/QiimiaAnalysisPanel.fxml");
	}
	void editQuantPreset(ActionEvent e){
		sceneManager.switchScene("/QiimiaQuantPanel.fxml");
		if(presetFileMap.get(selectedPresetName.get())!=null) {
//			get controller and loadQuantPreset with current preset (file)
			QiimiaQuantPanelController quantPanelController = (QiimiaQuantPanelController) sceneManager.getController("/QiimiaQuantPanel.fxml");
			quantPanelController.loadQuantPreset(presetFileMap.get(selectedPresetName.get()));
		}
	};

	void switchToQuantMode(ActionEvent e) throws IOException{
		sceneManager.switchScene("/QiimiaQuantPanel.fxml");
		if(presetFileMap.get(selectedPresetName.get())!=null) {
//			get controller and loadQuantPreset with current preset (file)
			QiimiaQuantPanelController quantPanelController = (QiimiaQuantPanelController) sceneManager.getController("/QiimiaQuantPanel.fxml");
			quantPanelController.loadQuantPreset(presetFileMap.get(selectedPresetName.get()));
		}
	};
}
