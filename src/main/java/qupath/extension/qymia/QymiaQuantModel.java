package qupath.extension.qymia;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.classes.PathClass;

import java.util.ArrayList;
import java.util.List;

import static qupath.lib.objects.classes.PathClassFactory.getPathClass;

public class QymiaQuantModel {

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
    private static DoubleProperty refNAProperty = PathPrefs.createPersistentPreference("refNAQymiaQuant", 0.75);
    private static DoubleProperty refMagProperty = PathPrefs.createPersistentPreference("refMagQymiaQuant", 20.0);
    private static DoubleProperty workingNAProperty = PathPrefs.createPersistentPreference("workingNAQymiaQuant", 0.75);
    private static DoubleProperty workingMagProperty = PathPrefs.createPersistentPreference("workingMagQymiaQuant", 20.0);
    private static DoubleProperty downsampleProperty = PathPrefs.createPersistentPreference("downsampleQymiaQuant", 4.0);
    private static BooleanProperty useCUDAProperty = PathPrefs.createPersistentPreference("useCUDAQymiaQuant", true);


    public ObservableSet<PathClass> getIgnoreClasses() {
        return ignoreClasses;
    }
    public ObservableSet<PathClass> getRoiClasses() {
        return roiClasses;
    }
    public void setIgnoreClasses(ObservableSet<PathClass> ignoreClasses) {
        this.ignoreClasses.clear();
        this.ignoreClasses.addAll(ignoreClasses);
    }

    public void setRoiClasses(ObservableSet<PathClass> roiClasses) {
        this.roiClasses.clear();
        this.roiClasses.addAll(roiClasses);
    }
    public List<PathClass> getDefaultIgnoreClasses() {
        return defaultIgnoreClasses;
    }
    public void setDefaultIgnoreClasses(List<PathClass> defaultIgnoreClasses) {
        this.defaultIgnoreClasses = defaultIgnoreClasses;
    }
    public List<PathClass> getDefaultRoiClasses() {
        return defaultRoiClasses;
    }
    public void setDefaultRoiClasses(List<PathClass> defaultRoiClasses) {
        this.defaultRoiClasses = defaultRoiClasses;
    }
    public DoubleProperty getRefNAProperty() {
        return refNAProperty;
    }
    public void setRefNA(double refNAValue) {
        	refNAProperty.set(refNAValue);
    }
    public DoubleProperty getRefMagProperty() {
        return refMagProperty;
    }
    public void setRefMag(double refMagValue) {
        	refMagProperty.set(refMagValue);
    }
    public DoubleProperty getWorkingNAProperty() {
        return workingNAProperty;
    }
    public void setWorkingNA(double workingNAValue) {
        	workingNAProperty.set(workingNAValue);
    }
    public DoubleProperty getWorkingMagProperty() {
        return workingMagProperty;
    }
    public void setWorkingMag(double workingMagValue) {
        	workingMagProperty.set(workingMagValue);
    }
    public DoubleProperty getDownsampleProperty() {
        return downsampleProperty;
    }
    public void setDownsample(double downsampleValue) {
        	downsampleProperty.set(downsampleValue);
    }
    public BooleanProperty getUseCUDAProperty() {
        return useCUDAProperty;
    }
    public void setUseCUDA(boolean useCUDAValue) {
        	useCUDAProperty.set(useCUDAValue);
    }

}
