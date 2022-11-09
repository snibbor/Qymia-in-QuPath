package qupath.extension.qiimia;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.images.ImageData;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.*;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.projects.Projects;

import java.awt.image.BufferedImage;
import java.io.*;


import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class QiimiaAnalysisPanelController extends BaseController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(QiimiaAnalysisPanelController.class);
    private final QuPathGUI qupath;
    private List<ProjectImageEntry<BufferedImage>> previousImages = new ArrayList<>();
//    private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();
    @FXML
    MenuItem switchToQuantMenuItem;
    @FXML
    MenuItem switchToPresetMenuItem;
    @FXML
    CheckMenuItem flipXYMenuItem;
    private static BooleanProperty flipXYProperty = PathPrefs.createPersistentPreference("flipXYQiimiaAnalysis", false);
    @FXML
    TabPane analysisTabPane;
    @FXML
    Button indexMapChooseButton;
    @FXML
    TextField indexMapFileTextField;
    @FXML
    Label errorLabel;
//    @FXML
//    ComboBox<RegTypes> regTypeComboBox;
//    public enum RegTypes{
//        LINEAR,
//        POLYNOMIAL;
//        @Override
//        public String toString() {
//            switch(this) {
//                case LINEAR: return "Linear";
//                case POLYNOMIAL: return "Polynomial";
//                default: throw new IllegalArgumentException();
//            }
//        }
//    }
//    private final RegTypes[] regTypes = {RegTypes.LINEAR, RegTypes.POLYNOMIAL};
//    private ReadOnlyObjectProperty<RegTypes> selectedRegType;
    @FXML
    ComboBox<String> sourceComboBox;
    private final String[] compartmentSources = {"Detections", "Annotations", "Cells", "Tiles"};
    private ReadOnlyObjectProperty<String> selectedSource;
    private Class<? extends PathObject> sourceType = PathRootObject.class;
    @FXML
    ComboBox<String> measComboBox;
    private ReadOnlyObjectProperty<String> selectedMeasurement;

    final NumberAxis xAxis = new NumberAxis();
    final NumberAxis yAxis = new NumberAxis();
    @FXML
    LineChart<Number, Number> regLineChart = new LineChart<>(xAxis, yAxis);
    @FXML
    Label rSquaredLabel;
    @FXML
    Label regEqLabel;
    @FXML
    Button calcRegButton;
    @FXML
    Button convertMeasButton;

    private ImageData<BufferedImage> openImageData;
    ObservableMeasurementTableData measModel = new ObservableMeasurementTableData();

//    FileChooser fileSelector = new FileChooser();
//    File initialFileDirectory;


    public QiimiaAnalysisPanelController(QuPathGUI qupath) {
        this.qupath = qupath;
        this.openImageData = qupath.getImageData();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupMenu();
        setupComboBoxes();
        setupCharts();
        indexMapChooseButton.setOnAction(e->{
            File dirBase = qupath.getProject() != null ? Projects.getBaseDirectory(qupath.getProject()) : new File(System.getProperty("user.home"));
            File indexMapPath = Dialogs.promptForFile("TMA Index Map", dirBase, "JSON (.json)", ".json");
            if (indexMapPath != null) {
                indexMapFileTextField.setText(indexMapPath.getAbsolutePath());
            }
            doStandardsRegressionPlot();
        });
        calcRegButton.setOnAction(this::calcTMAStandardRegs);
        convertMeasButton.setOnAction(this::convertMeasurements);
//        qupath.getViewer().addViewerListener(new QuPathViewerListener() {
//            @Override
//            public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
//                logger.info("imageData changed inside main viewer");
//                if(imageDataOld == null && imageDataNew != null){
//                    updateGUI(true);
//                    return;
//                } else if (imageDataNew == null) {
//                    return;
//                }
//                if(imageDataOld!=null && imageDataNew!=null) {
//                    if (!imageDataOld.getProperties().equals(imageDataNew.getProperties())){
//                        updateGUI(true);
//                    }
//                }
//            }
//            @Override
//            public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}
//            @Override
//            public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}
//            @Override
//            public void viewerClosed(QuPathViewer viewer) {
////                viewer.removeViewerListener(this);
////                viewer.repaint();
//            }
//        });
        updateGUI(false);
    }

    private void setupMenu(){
        switchToQuantMenuItem.setOnAction(e->{
            try {
                switchToQuantMode(e);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        switchToPresetMenuItem.setOnAction(e->{
            try {
                switchToPresetMode(e);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        flipXYMenuItem.selectedProperty().bindBidirectional(flipXYProperty);
        flipXYProperty.addListener((v, o, n) -> doStandardsRegressionPlot());
    }

    private void setupComboBoxes(){
        sourceComboBox.getItems().addAll(compartmentSources);
        selectedSource = sourceComboBox.getSelectionModel().selectedItemProperty();
        selectedSource.addListener((v, o, n) -> updateGUI(true));

        selectedMeasurement = measComboBox.getSelectionModel().selectedItemProperty();
        selectedMeasurement.addListener((v, o, n) -> {
            doStandardsRegressionPlot();
        });

//        regTypeComboBox.getItems().addAll(regTypes);
//        selectedRegType = regTypeComboBox.getSelectionModel().selectedItemProperty();
//        selectedRegType.addListener((v, o, n) -> updateGUI(false));

    }

    private void refreshMeasurementModel(){
//        check if image data/project entry changed from previous... add a listener?
        String previousMeas = selectedMeasurement.get();
        setType(selectedSource.get());
        measModel.setImageData(openImageData, openImageData == null ? Collections.emptyList() : openImageData.getHierarchy().getObjects(null, sourceType));
        measComboBox.getItems().setAll(measModel.getMeasurementNames());
        if (measComboBox.getItems().isEmpty()) {
            logger.info("No items to display!");
			return;
        }

        if(measModel.getMeasurementNames().contains(previousMeas)){
            measComboBox.getSelectionModel().select(previousMeas);
        } else {
            // Try to select the first column that isn't for 'centroids'...
            String initialMeas = null;
            for (String name : measModel.getMeasurementNames()) {
                if (!name.toLowerCase().startsWith("centroid")) {
                    initialMeas = name;
                    break;
                }
                if (initialMeas == null)
                    initialMeas = name;
            }
            if (initialMeas != null)
                measComboBox.getSelectionModel().select(initialMeas);
        }
    }

    private void updateGUI(Boolean forceModelChange){
        logger.info("updating GUI...");
        if(openImageData==null){
            logger.info("openImageData null, setting to current imageData open in viewer");
            openImageData = qupath.getImageData();
            refreshMeasurementModel();
        } else if(forceModelChange){
            logger.info("forcing imageData and measurement model to refresh");
            openImageData = qupath.getImageData();
            refreshMeasurementModel();
        } else if(!qupath.getImageData().getProperties().equals(openImageData.getProperties())){
            logger.info("properties of current imageData does not equal previous imageData. Refreshing...");
            openImageData = qupath.getImageData();
            refreshMeasurementModel();
        }
    }

    private void doStandardsRegressionPlot(){
//        Return if no TMA grid is found
        logger.info("trying regression plot...");
        if(openImageData == null || openImageData.getHierarchy().getTMAGrid() == null){
            logger.warn("openImageData is null or there is no TMA grid in heirarchy, cannot perform regression...");
            errorLabel.setVisible(true);
            errorLabel.setText("openImageData or TMA grid is missing!");
            return;
        }
//        Check that everything has a non-null selection in GUI
        if(selectedMeasurement.get()==null || selectedSource.get()==null || indexMapFileTextField.getText().isEmpty()){
            logger.warn("parameter selection incomplete, cannot do regression");
            errorLabel.setVisible(true);
            errorLabel.setText("incomplete parameter selection");
            return;
        }
        String measurementName = selectedMeasurement.get();
//        Check index map is valid
        String curExt = Files.getFileExtension(indexMapFileTextField.getText());
        if(!curExt.equals("json")){
            logger.error("{}\nfile is not .json", indexMapFileTextField.getText());
            errorLabel.setVisible(true);
            errorLabel.setText("selected file not JSON");
            return;
        }
//        Load index map JSON file
//        GsonTools.getInstance(true).toJson(map)
        Map<String, Double> indexMap = new HashMap<>();
        String standardName = null;
        try(
            BufferedReader reader = Files.newReader(new File(indexMapFileTextField.getText()), StandardCharsets.UTF_8);
            ){
            Gson gson = new Gson();
            // convert JSON file to map
            Map<String, ?> standardArrayMap = gson.fromJson(reader, Map.class);
            standardName = (String) standardArrayMap.get("standard_name");
            indexMap = (Map<String, Double>) standardArrayMap.get("index_map");
            logger.info(standardName);
            logger.info(indexMap.toString());
        } catch(Exception ex){
            errorLabel.setVisible(true);
            errorLabel.setText("error reading index map file");
            ex.printStackTrace();
        }
        if(indexMap == null || indexMap.isEmpty()){
            errorLabel.setVisible(true);
            errorLabel.setText("index map null or empty, bad file?");
            return;
        }
        errorLabel.setVisible(false);

//        pull out corresponding measurement values for TMA indices from measModel
//          use parentObj to determine if inside TMA core with correct index. sourceType is set inside the measModel already
//        measModel.getDoubleValues(selectedMeasurement.get());
        Map<String, Double> measureMap = new HashMap<>();

//        duplicate TMA compartments will overwrite the measurement map.... may need to handle this in the future
        measModel.getItems().parallelStream()
                .filter(p -> p.getParent().isTMACore())
                .filter(c -> !((TMACoreObject)c.getParent()).isMissing())
                .forEach(p -> {
                    TMACoreObject parentCore = (TMACoreObject) p.getParent();
                    Double measValue = measModel.getNumericValue(p, measurementName);
//                    logger.info("{} with {}", parentCore.getName(), measValue);
                    if(Double.isNaN(measValue)){
                        return;
                    }
                    measureMap.put(parentCore.getName(), measValue);
                });
        logger.info(measureMap.toString());

        List<String> intersectKeys = new ArrayList<String>(measureMap.keySet());
        intersectKeys.retainAll(indexMap.keySet());
        logger.info(intersectKeys.toString());
//        Flip XY check
        ObservableList<XYChart.Data<Number, Number>> xyData = FXCollections.observableArrayList();
        String xLabel;
        String yLabel;
        if(!flipXYProperty.get()) {
            xLabel = standardName;
            yLabel = measurementName;
            for (String key : intersectKeys) {
                xyData.add(new XYChart.Data<>(indexMap.get(key), measureMap.get(key)));
            }
        }else{
            xLabel = measurementName;
            yLabel = standardName;
            for (String key : intersectKeys) {
                xyData.add(new XYChart.Data<>(measureMap.get(key), indexMap.get(key)));
            }
        }
//        logger.info(intersectKeys.toString());
//        logger.info(xyData.toString());
        SimpleRegression reg = plotScatterAndLinearRegression(xyData, regLineChart, xLabel, yLabel);
//        update R-squared and equation label
//        logger.info(String.valueOf(reg.getRSquare()));
//        logger.info(String.valueOf(reg.getSlope()));
//        logger.info(String.valueOf(reg.getIntercept()));
        rSquaredLabel.setText(String.format("%.3f", reg.getRSquare()));
        regEqLabel.setText(String.format("y = %.3f x + %.3f", reg.getSlope(), reg.getIntercept()));
    }

    private void setupCharts(){
        XYChart.Series<Number, Number> scatterSeries = new XYChart.Series<Number, Number>();
        XYChart.Series<Number, Number> regSeries = new XYChart.Series<Number, Number>();
        Platform.runLater(()->{
            regLineChart.getData().setAll(scatterSeries, regSeries);
            regLineChart.setLegendVisible(false);
        });
    }
    public SimpleRegression plotScatterAndLinearRegression (ObservableList<XYChart.Data<Number, Number>> xyData,
                                   LineChart<Number, Number> lineChart,
//                                   RegTypes regType,
                                   String xLabel,
                                   String yLabel){
        XYChart.Series<Number, Number> scatterSeries = lineChart.getData().get(0);
        XYChart.Series<Number, Number> regSeries = lineChart.getData().get(1);

        SimpleRegression reg = new SimpleRegression();
        List<Double> xVals = new ArrayList<>();
        for(XYChart.Data<Number, Number> xyPoint : xyData){
            xVals.add(xyPoint.getXValue().doubleValue());
            reg.addData(xyPoint.getXValue().doubleValue(), xyPoint.getYValue().doubleValue());
        }

        double minX = Collections.min(xVals);
        double maxX = Collections.max(xVals);
        Platform.runLater(()->{
            scatterSeries.setData(xyData);
            regSeries.getData().clear();
            regSeries.getData().add(new XYChart.Data<>(minX, reg.predict(minX)));
            regSeries.getData().add(new XYChart.Data<>((maxX+minX)/2.0, reg.predict((maxX+minX)/2.0)));
            regSeries.getData().add(new XYChart.Data<>(maxX, reg.predict(maxX)));
//            lineChart.getData().setAll(scatterSeries, regSeries);
//            lineChart.getData().addAll(scatterSeries, regSeries);
            lineChart.lookup(".default-color0.chart-series-line").setStyle("-fx-stroke: transparent");
            lineChart.lookup(".default-color1.chart-line-symbol").setStyle("-fx-background-color: transparent, transparent");
            lineChart.getXAxis().setLabel(xLabel);
            lineChart.getYAxis().setLabel(yLabel);
            lineChart.setLegendVisible(false);
        });
        return reg;
    }

    public void calcTMAStandardRegs(ActionEvent e){
        handleTMAStandardCalcRegs();
    }
    void handleTMAStandardCalcRegs() {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            Dialogs.showNoProjectError("Qiimia Analysis");
            return;
        }

        if (!QiimiaQuantCommands.checkSaveChangesPrompt(qupath.getImageData(), project)){
//			If the prompt was cancelled by user or it returns false for some reason, do not create and show measurement dialog
            return;
        }

        if(qupath.getImageData() != null) {
            ProjectImageEntry<BufferedImage> currentEntry = project.getEntry(qupath.getImageData());
            //		Add to list of images
            if (previousImages.isEmpty() || !previousImages.contains(currentEntry))
                previousImages.add(currentEntry);
        }



        String standardTMAMessage = "Only select TMA standards\nfor standard curve regressions!";
        var listSelectionView = ProjectDialogs.createImageChoicePane(qupath, project.getImageList(), previousImages, standardTMAMessage);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(qupath.getStage());
        dialog.setTitle("Select project images for TMA standard curves");
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

        QiimiaAnalysisPanelController.TMAStandardRegTask worker = new QiimiaAnalysisPanelController.TMAStandardRegTask(project, imagesToProcess);


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
//                runCancelled.set(true);
//				worker.cancel(false);
                progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
            }
            e.consume();
        });


        // Create & run task
        qupath.createSingleThreadExecutor(this).submit(worker);
//        runningTask.set(qupath.createSingleThreadExecutor(this).submit(worker));
        progress.show();
    }

    public class TMAStandardRegTask extends Task<Void> {

        private Project<BufferedImage> project;
        private Collection<ProjectImageEntry<BufferedImage>> imagesToProcess;
        private boolean quietCancel = false;

        TMAStandardRegTask(final Project<BufferedImage> project, final Collection<ProjectImageEntry<BufferedImage>> imagesToProcess) {
            this.project = project;
            this.imagesToProcess = imagesToProcess;
        }

        public void quietCancel() {
            this.quietCancel = true;
        }

        public boolean isQuietlyCancelled() {
            return quietCancel;
        }

        @Override
        public Void call() {
            String measurementName = selectedMeasurement.get();
            if(indexMapFileTextField.getText().isEmpty()){
                logger.warn("no index array map specified, exiting task");
                return null;
            }
//            try to load the index map using the selected file
            Map<String, Double> indexMap = new HashMap<>();
            String standardName = null;
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            try(
                BufferedReader reader = Files.newReader(new File(indexMapFileTextField.getText()), StandardCharsets.UTF_8);
                ){
                // convert JSON file to map
                Map<String, ?> standardArrayMap = gson.fromJson(reader, Map.class);
                standardName = (String) standardArrayMap.get("standard_name");
                indexMap = (Map<String, Double>) standardArrayMap.get("index_map");
                logger.info(standardName);
                logger.info(indexMap.toString());
            } catch(Exception ex){
                logger.error("error reading index map file");
                ex.printStackTrace();
            }
            if(indexMap == null || indexMap.isEmpty()){
                logger.error("index map null or empty, bad file?");
                return null;
            }
            if(standardName == null){
                logger.error("standardName for index array map file is null, bad file?");
                return null;
            }
            Path saveDir;
            try {
                logger.info("trying to create directory for measurement converters...");
                saveDir = Paths.get(Projects.getBaseDirectory(project) + File.separator + "measurement_converters");
                java.nio.file.Files.createDirectories(saveDir);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            long startTime = System.currentTimeMillis();
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
                    String entryImagePath = entry.getUris().stream().findFirst().orElse(new URI("")).getPath();
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

//                    assuming that you apply the same index map to all the TMA images being processed
//                    invalidate if no TMA grid found
                    if(imageData.getHierarchy().getTMAGrid() == null){
                        logger.warn("no TMA grid found for {}", entry.getImageName());
                        logger.warn("Closing server {}", imageData);
                        Platform.runLater(()->{
                            try {
                                imageData.getServer().close();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                        continue;
                    }
                    SimpleRegression reg = calculateTMAStandardRegression(imageData, indexMap, measurementName, standardName);
                    if(reg != null) {
//                    save regression, merge existing json
                        MeasurementConverter newMeasConv = new MeasurementConverter(
                                entryImageName, reg, measurementName, standardName, true);
                        BufferedWriter file = Files.newWriter(
                                new File(saveDir.toAbsolutePath() + File.separator + entryImageName + ".json"),
                                StandardCharsets.UTF_8);
                        file.write(gson.toJson(newMeasConv));
                        file.close();
                    } else {
                        logger.error("Error in {}, not creating measurement converter based on regression", entryImageName);
                    }

                    if (imagesToProcess.size() > 1) {
                        logger.warn("Closing server {}", imageData.toString());
                        Platform.runLater(()->{
                            try {
                                imageData.getServer().close();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }

                } catch (Exception ex) {
                    logger.error("Error running batch script: {}", ex);
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
//            Platform.runLater(() -> runningTask.setValue(null));
        }
    };

    public SimpleRegression calculateTMAStandardRegression(ImageData<BufferedImage> imageData,
                                                    Map<String, Double> indexMap,
                                                    String measurementName,
                                                    String standardName){

        Map<String, Double> measureMap = new HashMap<>();
//        duplicate TMA compartments will overwrite the measurement map.... may need to handle this in the future
        imageData.getHierarchy().getObjects(null, sourceType).parallelStream()
                .filter(p -> p.getParent().isTMACore())
                .filter(c -> !((TMACoreObject)c.getParent()).isMissing())
                .forEach(p -> {
                    TMACoreObject parentCore = (TMACoreObject) p.getParent();
                    Double measValue = p.getMeasurementList().getMeasurementValue(measurementName);
//                    logger.info("{} with {}", parentCore.getName(), measValue);
                    if(Double.isNaN(measValue)){
                        return;
                    }
                    measureMap.put(parentCore.getName(), measValue);
                });
        logger.info(measureMap.toString());

        List<String> intersectKeys = new ArrayList<String>(measureMap.keySet());
        intersectKeys.retainAll(indexMap.keySet());
        if(intersectKeys.isEmpty() || intersectKeys == null){
            logger.warn("No TMA names are found within selected index map file");
            return null;
        }

        SimpleRegression reg = new SimpleRegression();
//      For conversion, X is the measurement Y is the standard value
        for (String key : intersectKeys) {
            reg.addData(measureMap.get(key), indexMap.get(key));
        }
        return reg;
    }

    public static class MeasurementConverter implements Serializable{
        private String tmaImageName;
        private SimpleRegression regObj;
        private double rSquared;
        private List<Double> coeffs = new ArrayList<>();
        private String measurementName;
        private String convertValueName;
        private Boolean doClamp;

        public MeasurementConverter(String tmaImageName, SimpleRegression regObj, String measurementName, String convertValueName, Boolean doClamp) {
            this.tmaImageName = tmaImageName;
            this.regObj = regObj;
//            if (regObj.getClass() == SimpleRegression.class) {
            this.rSquared = regObj.getRSquare();
            this.coeffs.addAll(new ArrayList<Double>(
                    List.of(regObj.getSlope(), regObj.getIntercept())
            ));
//            }
//            else if (regObj.getClass() == MultipleLinearRegression.class) {
//            }
            this.measurementName = measurementName;
            this.convertValueName = convertValueName;
            this.doClamp = doClamp;
        }

        public void convert(PathObject pathObj){
            MeasurementList measList = pathObj.getMeasurementList();
            Double val = measList.getMeasurementValue(measurementName);
            if(val != null && !Double.isNaN(val)){
                double convertVal = regObj.predict(val);
                if(convertVal < 0 && doClamp){
                    convertVal = 0.0;
                }
                measList.putMeasurement(convertValueName, convertVal);
            } else{
                measList.putMeasurement(convertValueName, Double.NaN);
            }
        }
        public String getTmaImageName(){return tmaImageName;}
        public SimpleRegression getReg(){return regObj;}
        public Double getRSquared(){return rSquared;}
        public List<Double> getCoeffs(){return coeffs;}
        public String getMeasurementName(){return measurementName;}
        public String getConvertValueName(){return convertValueName;}
        public Boolean getDoClamp(){return doClamp;}
        public void setDoClamp(Boolean doClamp){this.doClamp = doClamp;}
    }

    public void convertMeasurements(ActionEvent e){
        handleConvertMeasurements(true, true);
    }
    void handleConvertMeasurements(final boolean doSave, final boolean reload) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            Dialogs.showNoProjectError("Qiimia Analysis");
            return;
        }

        if (!QiimiaQuantCommands.checkSaveChangesPrompt(qupath.getImageData(), project)){
//			If the prompt was cancelled by user or it returns false for some reason, do not create and show measurement dialog
            return;
        }

        Dialog<ButtonType> dialog;

        BorderPane mainPane = new BorderPane();

        BorderPane imageEntryPane = new BorderPane();
        GridPane optionPane = new GridPane();
        optionPane.setHgap(5.0);
        optionPane.setVgap(5.0);


        // TOP PANE (SELECT PROJECT ENTRY FOR EXPORT)
        TextField measConverterText = new TextField();
        TextField batchMapText = new TextField();

        if(qupath.getImageData() != null) {
            ProjectImageEntry<BufferedImage> currentEntry = project.getEntry(qupath.getImageData());
            //		Add to list of images
            if (previousImages.isEmpty() || !previousImages.contains(currentEntry))
                previousImages.add(currentEntry);
        }

        String sameImageWarning = doSave ? "A selected image is open in the viewer!\nUse 'File>Reload data' to see changes." : null;
        var listSelectionView = ProjectDialogs.createImageChoicePane(qupath, project.getImageList(), previousImages, sameImageWarning);

        // BOTTOM PANE (OPTIONS)
        int row = 0;
        Label inputMeasConvLabel = new Label("Measurement Converter File");
        var btnChooseMeasConv = new Button("Choose");
        btnChooseMeasConv.setOnAction(e -> {
            File dirBase = qupath.getProject() != null ? Projects.getBaseDirectory(qupath.getProject()) : new File(System.getProperty("user.home"));
            File measConvFile = Dialogs.promptForFile("Measurement Converter File", dirBase, "JSON (.json)", ".json");
            if (measConvFile != null) {
                measConverterText.setText(measConvFile.getAbsolutePath());
            }
        });

        inputMeasConvLabel.setLabelFor(measConverterText);
        PaneTools.addGridRow(optionPane, row++, 0, "Choose measurement converter file", inputMeasConvLabel, measConverterText, measConverterText, btnChooseMeasConv, btnChooseMeasConv);
        measConverterText.setMaxWidth(Double.MAX_VALUE);
        btnChooseMeasConv.setMaxWidth(Double.MAX_VALUE);

        Label inputBatchMapLabel = new Label("Batch Map File");
        var btnChooseBatchMap= new Button("Choose");
        btnChooseBatchMap.setOnAction(e -> {
            File dirBase = qupath.getProject() != null ? Projects.getBaseDirectory(qupath.getProject()) : new File(System.getProperty("user.home"));
            File batchMapFile = Dialogs.promptForFile("Batch Map File", dirBase, "CSV (.csv)", ".csv");
            if (batchMapFile != null) {
                batchMapText.setText(batchMapFile.getAbsolutePath());
            }
        });

        inputBatchMapLabel.setLabelFor(batchMapText);
        PaneTools.addGridRow(optionPane, row++, 0, "Choose batch map file for measurement conversion", inputBatchMapLabel, batchMapText, batchMapText, btnChooseBatchMap, btnChooseBatchMap);
        batchMapText.setMaxWidth(Double.MAX_VALUE);
        btnChooseBatchMap.setMaxWidth(Double.MAX_VALUE);

        ButtonType btnConvert = new ButtonType("Convert", ButtonBar.ButtonData.OK_DONE);

        dialog = Dialogs.builder()
                .title("Convert measurements")
                .buttons(btnConvert, ButtonType.CANCEL)
                .content(mainPane)
                .build();

        dialog.getDialogPane().setPrefSize(600, 400);
        imageEntryPane.setCenter(listSelectionView);

        // Set the disabledProperty according to (1) targetItems.size() > 0 and (2) both input text fields isEmpty()
        var targetItemBinding = Bindings.size(listSelectionView.getTargetItems()).isEqualTo(0);
        var emptyTextBinding = Bindings.and(
                measConverterText.textProperty().isEmpty(), batchMapText.textProperty().isEmpty()
        );
        dialog.getDialogPane().lookupButton(btnConvert).disableProperty().bind(Bindings.or(emptyTextBinding, targetItemBinding));

        mainPane.setTop(imageEntryPane);
        mainPane.setBottom(optionPane);

//        Dialog<ButtonType> dialog = new Dialog<>();
//        dialog.initOwner(qupath.getStage());
//        dialog.setTitle("Select project images for converting measurements");
//        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
//        dialog.getDialogPane().setContent(listSelectionView);
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(600);
        dialog.initModality(Modality.APPLICATION_MODAL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (!result.isPresent() || result.get() != btnConvert || result.get() == ButtonType.CANCEL)
            return;

        previousImages.clear();

        previousImages.addAll(ProjectDialogs.getTargetItems(listSelectionView));

        if (previousImages.isEmpty())
            return;

        List<ProjectImageEntry<BufferedImage>> imagesToProcess = new ArrayList<>(previousImages);

        ConvertMeasurementTask worker = new ConvertMeasurementTask(project, imagesToProcess,
                measConverterText.getText(), batchMapText.getText(), doSave, reload, qupath.getViewers());


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
//                runCancelled.set(true);
//				worker.cancel(false);
                progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
            }
            e.consume();
        });


        // Create & run task
        qupath.createSingleThreadExecutor(this).submit(worker);
//        runningTask.set(qupath.createSingleThreadExecutor(this).submit(worker));
        progress.show();
    }

    public static class ConvertMeasurementTask extends Task<Void> {

        private Project<BufferedImage> project;
        private Collection<ProjectImageEntry<BufferedImage>> imagesToProcess;
        private String measConvPath;
        private String batchMapPath;
        private boolean doSave = true;
        private boolean reload = false;
        private List<QuPathViewerPlus> viewersList;
        private boolean doBatchMap = false;
        private boolean quietCancel = false;

        ConvertMeasurementTask(final Project<BufferedImage> project,
                               final Collection<ProjectImageEntry<BufferedImage>> imagesToProcess,
                               final String measurementConverterPath,
                               final String batchMapPath,
                               final boolean doSave,
                               final boolean reload,
                               final List<QuPathViewerPlus> viewersList) {
            this.project = project;
            this.imagesToProcess = imagesToProcess;
            this.measConvPath = measurementConverterPath;
            this.batchMapPath = batchMapPath;
            this.doSave = doSave;
            this.reload = reload;
            this.viewersList = viewersList;
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
            if(batchMapPath.isEmpty() && measConvPath.isEmpty()){
                logger.error("both measConvPath and batchMapPath are empty, check file input");
                return null;
            }
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
//          Try to use the batch map for processing measurement conversions if available
            Map<String, String> batchMap = new HashMap<>();
//          TODO: for multiple measurement conversions per index array, not fully implemented
            List<MeasurementConverter> selectedConverters = new ArrayList<>();
            if(!batchMapPath.isEmpty()){
                doBatchMap = true;
                batchMap = loadTwoColMap(batchMapPath);
                if(batchMap == null){
                    return null;
                }
            } else{
//                try to load the measurementConverter from json
                try(
                    BufferedReader reader = Files.newReader(new File(measConvPath), StandardCharsets.UTF_8);
                    ) {
//                  TODO: will be trickier when deserializing a list of these things....
                    MeasurementConverter measConv = gson.fromJson(reader, MeasurementConverter.class);
                    if(measConv == null){
                        logger.error("measurement converter is null, bad file?");
                        return null;
                    }
                    selectedConverters.add(measConv);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

//          Vars for batchMap processing
            File pathMeasConvs = new File(Projects.getBaseDirectory(project) + File.separator + "measurement_converters");
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

            if(doBatchMap && allMeasConvList.isEmpty()){
                logger.error("cannot do batchMap conversion if there are no meas. convs. in PROJ/measurement_converters directory!");
                return null;
            }

//            var viewersList = qupath.getViewers();
            List<QuPathViewerPlus> currentViewers = new ArrayList<>();
//            if (viewersList.size() == 1){
//                logger.info("Only one viewer found! Setting current viewer.");
//                currentViewers.add(viewersList.get(0));
//            }
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
                    File entryImageFile = new File(entry.getMetadataMap().get("URI"));
                    if (imageData == null) {
                        logger.warn("Unable to open {} - will be skipped", entry.getImageName());
                        continue;
                    }

                    if(reload){
                        logger.info("trying to get viewer for imagedata...");
//						Could there be a case where the properties are the same but the image is not the one opened in the viewer? I do not know, but this works for now.
                        currentViewers = viewersList.stream().filter(v -> v.getImageData().getProperties().equals(imageData.getProperties())).collect(Collectors.toList());
                        logger.info(currentViewers.toString());
                    }

                    if(doBatchMap){
                        List<MeasurementConverter> currentMeasConvs = getMeasConvsFromBatchMap(
                                entryImageFile.getName(),
                                batchMap,
                                allMeasConvList
                        );
                        if(currentMeasConvs == null){
                            continue;
                        }
                        calculateMeasurementConversions(imageData, currentMeasConvs);
                    } else{
                        calculateMeasurementConversions(imageData, selectedConverters);
                    }

                    if (doSave) {
                        logger.info("saving image data...");
                        entry.saveImageData(imageData);
                    }

                    if (reload && !currentViewers.isEmpty()){
                        logger.info("reloading image data in viewer(s)...");
                        for(var openViewer : currentViewers){
//							need to run on the JavaFX application thread to avoid throwing errors
                            Platform.runLater(()->{
                                openViewer.setImageData(imageData);
                            });
                        }
                    }

                    if (imagesToProcess.size() > 1) {
                        logger.warn("Closing server {}", imageData.toString());
//					    need to run on the JavaFX application thread to avoid throwing errors
                        Platform.runLater(()->{
                            try {
                                imageData.getServer().close();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }

//                    Do you need to clear the tile cache for this? I don't think so.

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
//            Platform.runLater(() -> runningTask.setValue(null));
        }
    };



    public static Map<String, String> loadTwoColMap(String batchMapPath){
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        Map<String, String> twoColMap = new HashMap<>();
//      try to load the batch map file
        try(
            BufferedReader reader = Files.newReader(new File(batchMapPath), StandardCharsets.UTF_8);
            ){
            String ext = Files.getFileExtension(batchMapPath);
            if(ext.equals("csv")){
                String line =  null;
                while((line=reader.readLine())!=null){
                    String str[] = line.split(",");
//                           take first two entries and put into map
                    if(str.length < 2){
                        logger.error("not enough entries in line for .csv, skipping line");
                        continue;
                    }
                    twoColMap.put(str[0], str[1]);
                }
            } else if(ext.equals("json")){
                // convert JSON file to map
                twoColMap = gson.fromJson(reader, Map.class);
            } else{
                logger.error("DF file ({}) is not .csv or .json", batchMapPath);
                return null;
            }
            logger.debug(twoColMap.toString());
            if(twoColMap.isEmpty()){
                logger.error("DF map null or empty, bad file?");
                return null;
            }
            return twoColMap;
        } catch(Exception ex){
            logger.error("error reading index map file");
            ex.printStackTrace();
            return null;
        }
    }

    public static List<MeasurementConverter> getMeasConvsFromBatchMap(String imageName,
                                                               Map<String, String> batchMap,
                                                               List<File> allMeasConvList){
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        String closestBatchKey = "";
        for(String key : batchMap.keySet()){
//          beginning of key must match imageName
            if(imageName.matches(key+"(.*)")){
                closestBatchKey = key;
                break;
            }
        }
        if(closestBatchKey==null||closestBatchKey.isEmpty()){
            logger.error("Could not find {} in batch map, skipping", imageName);
            return null;
        }

        String indexName = batchMap.get(closestBatchKey);
//      find the corresponding measurement converter(s) in proj/measurement_converter
        File closestMeasConvFile = null;
        for(File file : allMeasConvList){
            String fileName = file.getName();
            if(fileName.matches(indexName+"(.*)")){
                closestMeasConvFile = file;
                break;
            }
        }
        if(closestMeasConvFile==null||!closestMeasConvFile.isFile()){
            logger.error("Could not find {} measurement converter, skipping", indexName);
            return null;
        }
//      load measurement converter(s) from file
        List<MeasurementConverter> currentMeasConvs = new ArrayList<>();
        try(
            BufferedReader reader = Files.newReader(closestMeasConvFile, StandardCharsets.UTF_8);
            ){
//          TODO: will be trickier when deserializing a list of these things....
            MeasurementConverter measConv = gson.fromJson(reader, MeasurementConverter.class);
            if (measConv == null) {
                logger.error("measurement converter {} is null, bad file?\nskipping...", closestMeasConvFile.getAbsolutePath());
                return null;
            }
            currentMeasConvs.add(measConv);
        } catch (IOException ex) {
//          should this invalidate the processing?
//            throw new RuntimeException(e);
            logger.error("measurment converter file error");
            ex.printStackTrace();
            return null;
        }
        return currentMeasConvs;
    }

    public static void calculateMeasurementConversions(ImageData<BufferedImage> imageData,
                                                       List<MeasurementConverter> measConverters){
        for(MeasurementConverter measConv : measConverters) {
            String measurementName = measConv.getMeasurementName();
            String convertValueName = measConv.getConvertValueName();
            logger.info("converting {} to {}", measurementName, convertValueName);
            imageData.getHierarchy().getObjects(null, PathObject.class).parallelStream()
                    .filter(p -> p.getMeasurementList().containsNamedMeasurement(measurementName))
                    .forEach(p -> {
                        measConv.convert(p);
                    });
        }
    }

    void switchToQuantMode(ActionEvent e) throws IOException{
        sceneManager.switchScene("/QiimiaQuantPanel.fxml");
    }

    void switchToPresetMode(ActionEvent e) throws IOException{
        sceneManager.switchScene("/QiimiaPresetPanel.fxml");
    }

    private void setType(String typeString){
        if (typeString != null) {
            switch (typeString) {
                case "Image":
                    sourceType = PathRootObject.class;
                    break;
                case "Annotations":
                    sourceType = PathAnnotationObject.class;
                    break;
                case "Detections":
                    sourceType = PathDetectionObject.class;
                    break;
                case "ROIs":
                    sourceType = PathDetectionObject.class;
                    break;
                case "Tiles":
                    sourceType = PathTileObject.class;
                    break;
                case "Cells":
                    sourceType = PathCellObject.class;
                    break;
                case "TMA cores":
                    sourceType = TMACoreObject.class;
                    break;
            };
        }
    }

}
