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

public class QymiaQuantSettingsController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(QymiaQuantSettingsController.class);
    private final QuPathGUI qupath;
    private final ObservableSet<PathClass> ignoreClasses;
    private final ObservableSet<PathClass> roiClasses;

    //default params
    private static DoubleProperty refNAProperty;
    private static DoubleProperty refMagProperty;

    private static DoubleProperty workingNAProperty;
    private static DoubleProperty workingMagProperty;

    private static DoubleProperty downsampleProperty;
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


    public QymiaQuantSettingsController(QuPathGUI qupath,
                                        ObservableSet<PathClass> ignoreClasses,
                                        ObservableSet<PathClass> roiClasses,
                                        DoubleProperty refNA,
                                        DoubleProperty refMag,
                                        DoubleProperty workingNA,
                                        DoubleProperty workingMag,
                                        DoubleProperty downsample){
        this.qupath = qupath;
        this.ignoreClasses = ignoreClasses;
        this.roiClasses = roiClasses;
        refNAProperty = refNA;
        refMagProperty = refMag;
        workingNAProperty = workingNA;
        workingMagProperty = workingMag;
        downsampleProperty = downsample;
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
        downsampleTextField = QymiaUtils.formatTextFields(downsampleTextField, "pos_double", String.valueOf(downsampleProperty.get()));
        downsampleTextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    if (downsampleTextField.getText().isEmpty() || downsampleTextField.getText() == null) {
                        downsampleProperty.set(1);
                        downsampleTextField.setText("1");
                    } else{
                        downsampleProperty.set(Double.parseDouble(downsampleTextField.getText()));
                    }
                    logger.info("downsampleTextField key enter: {}", downsampleProperty.get());
                }
            }
        });
        downsampleTextField.focusedProperty().addListener((ov, oldV, newV) -> {
            if (!newV) { // focus lost
                if (downsampleTextField.getText().isEmpty() || downsampleTextField.getText() == null) {
                    downsampleProperty.set(1);
                    downsampleTextField.setText("1");
                } else{
                    downsampleProperty.set(Double.parseDouble(downsampleTextField.getText()));
                }
                logger.info("downsampleTextField focus lost: {}", downsampleProperty.get());
            }
        });

        refNATextField = QymiaUtils.formatTextFields(refNATextField, "pos_double", String.valueOf(refNAProperty.get()));
        refNATextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    if (refNATextField.getText().isEmpty() || refNATextField.getText() == null) {
                        refNAProperty.set(0.75);
                        refNATextField.setText("0.75");
                    } else{
                        refNAProperty.set(Double.parseDouble(refNATextField.getText()));
                    }
                    logger.info("refNATextField key enter: {}", refNAProperty.get());
                }
            }
        });
        refNATextField.focusedProperty().addListener((ov, oldV, newV) -> {
            if (!newV) { // focus lost
                if (refNATextField.getText().isEmpty() || refNATextField.getText() == null) {
                    refNAProperty.set(0.75);
                    refNATextField.setText("0.75");
                } else{
                    refNAProperty.set(Double.parseDouble(refNATextField.getText()));
                }
                logger.info("refNATextField focus lost: {}", refNAProperty.get());
            }
        });

        refMagTextField = QymiaUtils.formatTextFields(refMagTextField, "pos_double", String.valueOf(refMagProperty.get()));
        refMagTextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    if (refMagTextField.getText().isEmpty() || refMagTextField.getText() == null) {
                        refMagProperty.set(20.0);
                        refMagTextField.setText("20.0");
                    } else{
                        refMagProperty.set(Double.parseDouble(refMagTextField.getText()));
                    }
                    logger.info("refMagTextField key enter: {}", refMagProperty.get());
                }
            }
        });
        refMagTextField.focusedProperty().addListener((ov, oldV, newV) -> {
            if (!newV) { // focus lost
                if (refMagTextField.getText().isEmpty() || refMagTextField.getText() == null) {
                    refMagProperty.set(20.0);
                    refMagTextField.setText("20.0");
                } else{
                    refMagProperty.set(Double.parseDouble(refMagTextField.getText()));
                }
                logger.info("refMagTextField focus lost: {}", refMagProperty.get());
            }
        });

        workNATextField = QymiaUtils.formatTextFields(workNATextField, "pos_double", String.valueOf(workingNAProperty.get()));
        workNATextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    if (workNATextField.getText().isEmpty() || workNATextField.getText() == null) {
                        workingNAProperty.set(0.75);
                        workNATextField.setText("0.75");
                    } else{
                        workingNAProperty.set(Double.parseDouble(workNATextField.getText()));
                    }
                    logger.info("workNATextField key enter: {}", workingNAProperty.get());
                }
            }
        });
        workNATextField.focusedProperty().addListener((ov, oldV, newV) -> {
            if (!newV) { // focus lost
                if (workNATextField.getText().isEmpty() || workNATextField.getText() == null) {
                    workingNAProperty.set(0.75);
                    workNATextField.setText("0.75");
                } else{
                    workingNAProperty.set(Double.parseDouble(workNATextField.getText()));
                }
                logger.info("workNATextField focus lost: {}", workingNAProperty.get());
            }
        });

        workMagTextField = QymiaUtils.formatTextFields(workMagTextField, "pos_double", String.valueOf(workingMagProperty.get()));
        workMagTextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    if (workMagTextField.getText().isEmpty() || workMagTextField.getText() == null) {
                        workingMagProperty.set(20.0);
                        workMagTextField.setText("20.0");
                    } else{
                        workingMagProperty.set(Double.parseDouble(workMagTextField.getText()));
                    }
                    logger.info("workMagTextField key enter: {}", workingMagProperty.get());
                }
            }
        });
        workMagTextField.focusedProperty().addListener((ov, oldV, newV) -> {
            if (!newV) { // focus lost
                if (workMagTextField.getText().isEmpty() || workMagTextField.getText() == null) {
                    workingMagProperty.set(20.0);
                    workMagTextField.setText("20.0");
                } else{
                    workingMagProperty.set(Double.parseDouble(workMagTextField.getText()));
                }
                logger.info("workMagTextField focus lost: {}", workingMagProperty.get());
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
