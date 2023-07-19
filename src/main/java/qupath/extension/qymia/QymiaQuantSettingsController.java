package qupath.extension.qymia;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.objects.classes.PathClass;

import java.net.URL;
import java.util.ResourceBundle;

public class QymiaQuantSettingsController extends BaseController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(QymiaQuantSettingsController.class);
    private final QuPathGUI qupath;
    private final ObservableSet<PathClass> ignoreClasses;
    private final ObservableSet<PathClass> roiClasses;

    private final QymiaQuantModel quantModel;

    //default params
//    private static DoubleProperty refNAProperty;
//    private static DoubleProperty refMagProperty;
//
//    private static DoubleProperty workingNAProperty;
//    private static DoubleProperty workingMagProperty;
//
//    private static DoubleProperty downsampleProperty;
    @FXML
    Label progressLabel;
    @FXML
    ScrollPane roiScrollPane;
    @FXML
    ScrollPane ignoreScrollPane;

    @FXML
    ListView<PathClass> ignoreListView;
    @FXML
    ListView<PathClass> roiListView;

    @FXML
    TextField downsampleTextField;

    @FXML
    TextField refNATextField;

    @FXML
    TextField refMagTextField;

    @FXML
    TextField workNATextField;

    @FXML
    TextField workMagTextField;


    public QymiaQuantSettingsController(QuPathGUI qupath, QymiaQuantModel quantModel){
        this.qupath = qupath;
        this.ignoreClasses = quantModel.getIgnoreClasses();
        this.roiClasses = quantModel.getRoiClasses();
        this.quantModel = quantModel;
//        refNAProperty = quantModel.getRefNAProperty();
//        refMagProperty = quantModel.getRefMagProperty();
//        workingNAProperty = quantModel.getWorkingNAProperty();
//        workingMagProperty = quantModel.getWorkingMagProperty();
//        downsampleProperty = quantModel.getDownsampleProperty();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTextFields();
        ignoreListView.setItems(qupath.getAvailablePathClasses());
        roiListView.setItems(qupath.getAvailablePathClasses());
        setupListViews();

        updateGUI();
//        setup regex filter for ref and work text field to only accept numbers
//        link to properties with listener or binding?
    }

    private void setupTextFields(){
        downsampleTextField = QymiaUtils.formatTextFields(downsampleTextField, "pos_double", String.valueOf(quantModel.getDownsampleProperty().get()));
        downsampleTextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    if (downsampleTextField.getText().isEmpty() || downsampleTextField.getText() == null) {
                        quantModel.setDownsample(1);
                        downsampleTextField.setText("1");
                    } else{
                        quantModel.setDownsample(Double.parseDouble(downsampleTextField.getText()));
                    }
                    logger.info("downsampleTextField key enter: {}", quantModel.getDownsampleProperty().get());
                }
            }
        });
        downsampleTextField.focusedProperty().addListener((ov, oldV, newV) -> {
            if (!newV) { // focus lost
                if (downsampleTextField.getText().isEmpty() || downsampleTextField.getText() == null) {
                    quantModel.setDownsample(1);
                    downsampleTextField.setText("1");
                } else{
                    quantModel.setDownsample(Double.parseDouble(downsampleTextField.getText()));
                }
                logger.info("downsampleTextField focus lost: {}", quantModel.getDownsampleProperty().get());
            }
        });


//change old property variable names to quantModel properties

        refNATextField = QymiaUtils.formatTextFields(refNATextField, "pos_double", String.valueOf(quantModel.getRefNAProperty().get()));
        refNATextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    if (refNATextField.getText().isEmpty() || refNATextField.getText() == null) {
                        quantModel.setRefNA(0.75);
                        refNATextField.setText("0.75");
                    } else{
                        quantModel.setRefNA(Double.parseDouble(refNATextField.getText()));
                    }
                    logger.info("refNATextField key enter: {}", quantModel.getRefNAProperty().get());
                }
            }
        });
        refNATextField.focusedProperty().addListener((ov, oldV, newV) -> {
            if (!newV) { // focus lost
                if (refNATextField.getText().isEmpty() || refNATextField.getText() == null) {
                    quantModel.setRefNA(0.75);
                    refNATextField.setText("0.75");
                } else{
                    quantModel.setRefNA(Double.parseDouble(refNATextField.getText()));
                }
                logger.info("refNATextField focus lost: {}", quantModel.getRefNAProperty().get());
            }
        });

        refMagTextField = QymiaUtils.formatTextFields(refMagTextField, "pos_double", String.valueOf(quantModel.getRefMagProperty().get()));
        refMagTextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    if (refMagTextField.getText().isEmpty() || refMagTextField.getText() == null) {
                        quantModel.setRefMag(20.0);
                        refMagTextField.setText("20.0");
                    } else{
                        quantModel.setRefMag(Double.parseDouble(refMagTextField.getText()));
                    }
                    logger.info("refMagTextField key enter: {}", quantModel.getRefMagProperty().get());
                }
            }
        });
        refMagTextField.focusedProperty().addListener((ov, oldV, newV) -> {
            if (!newV) { // focus lost
                if (refMagTextField.getText().isEmpty() || refMagTextField.getText() == null) {
                    quantModel.setRefMag(20.0);
                    refMagTextField.setText("20.0");
                } else{
                    quantModel.setRefMag(Double.parseDouble(refMagTextField.getText()));
                }
                logger.info("refMagTextField focus lost: {}", quantModel.getRefMagProperty().get());
            }
        });

        workNATextField = QymiaUtils.formatTextFields(workNATextField, "pos_double", String.valueOf(quantModel.getWorkingNAProperty().get()));
        workNATextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    if (workNATextField.getText().isEmpty() || workNATextField.getText() == null) {
                        quantModel.setWorkingNA(0.75);
                        workNATextField.setText("0.75");
                    } else{
                        quantModel.setWorkingNA(Double.parseDouble(workNATextField.getText()));
                    }
                    logger.info("workNATextField key enter: {}", quantModel.getWorkingMagProperty().get());
                }
            }
        });
        workNATextField.focusedProperty().addListener((ov, oldV, newV) -> {
            if (!newV) { // focus lost
                if (workNATextField.getText().isEmpty() || workNATextField.getText() == null) {
                    quantModel.setWorkingNA(0.75);
                    workNATextField.setText("0.75");
                } else{
                    quantModel.setWorkingNA(Double.parseDouble(workNATextField.getText()));
                }
                logger.info("workNATextField focus lost: {}", quantModel.getWorkingNAProperty().get());
            }
        });

        workMagTextField = QymiaUtils.formatTextFields(workMagTextField, "pos_double", String.valueOf(quantModel.getWorkingMagProperty().get()));
        workMagTextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    if (workMagTextField.getText().isEmpty() || workMagTextField.getText() == null) {
                        quantModel.setWorkingMag(20.0);
                        workMagTextField.setText("20.0");
                    } else{
                        quantModel.setWorkingMag(Double.parseDouble(workMagTextField.getText()));
                    }
                    logger.info("workMagTextField key enter: {}", quantModel.getWorkingMagProperty().get());
                }
            }
        });
        workMagTextField.focusedProperty().addListener((ov, oldV, newV) -> {
            if (!newV) { // focus lost
                if (workMagTextField.getText().isEmpty() || workMagTextField.getText() == null) {
                    quantModel.setWorkingMag(20.0);
                    workMagTextField.setText("20.0");
                } else{
                    quantModel.setWorkingMag(Double.parseDouble(workMagTextField.getText()));
                }
                logger.info("workMagTextField focus lost: {}", quantModel.getWorkingMagProperty().get());
            }
        });
    }

    private void setupListViews() {
        ignoreListView.setCellFactory(CheckBoxListCell.forListView(new Callback<PathClass, ObservableValue<Boolean>>() {
            @Override
            public ObservableValue<Boolean> call(PathClass item) {
                BooleanProperty observable = new SimpleBooleanProperty();
                observable.addListener((obs, wasSelected, isNowSelected) -> {
                    logger.info("Check box for " + item + " changed from " + wasSelected + " to " + isNowSelected);
                    if (isNowSelected) {
                        ignoreClasses.add(item);
                    } else {
                        ignoreClasses.remove(item);
                    }
                    logger.info(ignoreClasses.toString());
                    updateGUI();
                });

                observable.set(ignoreClasses.contains(item));
                ignoreClasses.addListener((SetChangeListener.Change<? extends PathClass> c) ->
                        observable.set(ignoreClasses.contains(item)));

                return observable;
            }
        }));

        roiListView.setCellFactory(CheckBoxListCell.forListView(new Callback<PathClass, ObservableValue<Boolean>>() {
            @Override
            public ObservableValue<Boolean> call(PathClass item) {
                BooleanProperty observable = new SimpleBooleanProperty();
                observable.addListener((obs, wasSelected, isNowSelected) -> {
                    logger.info("Check box for " + item + " changed from " + wasSelected + " to " + isNowSelected);
                    if (isNowSelected) {
                        roiClasses.add(item);
                    } else {
                        roiClasses.remove(item);
                    }
                    logger.info(roiClasses.toString());
                    updateGUI();
                });

                observable.set(roiClasses.contains(item));
                roiClasses.addListener((SetChangeListener.Change<? extends PathClass> c) ->
                        observable.set(roiClasses.contains(item)));

                return observable;
            }
        }));
    }

    private void updateGUI(){
        ignoreListView.setItems(qupath.getAvailablePathClasses());
        roiListView.setItems(qupath.getAvailablePathClasses());
    }
}
