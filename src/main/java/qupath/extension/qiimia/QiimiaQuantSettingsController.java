package qupath.extension.qiimia;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
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

public class QiimiaQuantSettingsController extends BaseController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(QiimiaQuantSettingsController.class);
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
    ListView<PathClass> ignoreListView;
    @FXML
    ListView<PathClass> roiListView;

    @FXML
    TextField downsampleTextField;


    public QiimiaQuantSettingsController(QuPathGUI qupath,
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
        downsampleTextField = QiimiaUtils.formatTextFields(downsampleTextField, "pos_double", String.valueOf(downsampleProperty.get()));
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
