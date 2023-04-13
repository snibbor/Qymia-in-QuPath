package qupath.extension.qymia;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.writers.ImageWriter;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Function;

public class QymiaQuantCommands {

    private static Logger logger = LoggerFactory.getLogger(QymiaQuantCommands.class);

    private static DoubleProperty exportDownsample = PathPrefs.createPersistentPreference("exportRegionDownsample", 1.0);

    private static ImageWriter<BufferedImage> lastWriter = null;

    public static boolean checkSaveChangesPrompt(ImageData<BufferedImage> imageData, Project<BufferedImage> project){
//      Conditions to ignore this prompt
        if (imageData == null)
            return true;
        if (!imageData.isChanged())
            return true;
        ProjectImageEntry<BufferedImage> entry = (project == null ? null : project.getEntry(imageData));
        String name = entry == null ? ServerTools.getDisplayableImageName(imageData.getServer()) : entry.getImageName();
        var response = Dialogs.showYesNoCancelDialog("Save changes", "Save changes to " + name + "?");
        if (response == Dialogs.DialogButton.CANCEL)
            return false;
        if (response == Dialogs.DialogButton.NO)
            return true;

        try {
            if (entry == null) {
                String lastPath = imageData.getLastSavedPath();
                File lastFile = lastPath == null ? null : new File(lastPath);
                File dirBase = lastFile == null ? null : lastFile.getParentFile();
                String defaultName = lastFile == null ? null : lastFile.getName();
                File file = Dialogs.promptToSaveFile("Save data", dirBase, defaultName, "QuPath data files", PathPrefs.getSerializationExtension());
                if (file == null)
                    return false;
                PathIO.writeImageData(file, imageData);
            } else {
                entry.saveImageData(imageData);
                if (project != null)
                    project.syncChanges();
            }
            return true;
        } catch (IOException e) {
            Dialogs.showErrorMessage("Save ImageData", e);
            return false;
        }
    }

    public static void promptToExportAllROIImages(QuPathViewer viewer, boolean renderedImage, Collection<PathObject> roiPathObjs) {
        if (viewer == null || viewer.getServer() == null) {
            Dialogs.showErrorMessage("Export all ROI images", "No viewer & image selected!");
            return;
        }
        if(roiPathObjs == null || roiPathObjs.isEmpty()){
            Dialogs.showErrorMessage("Export all ROI images", "No ROI PathObjects exist in image!");
            return;
        }

        ImageServer<BufferedImage> server = viewer.getServer();
        if (renderedImage)
            server = RenderedImageServer.createRenderedServer(viewer);

//      Select all ROIs by PathClass
        PathObject pathObject = viewer.getSelectedObject();
        ROI roi = pathObject == null ? null : pathObject.getROI();

        double regionWidth = roi == null ? server.getWidth() : roi.getBoundsWidth();
        double regionHeight = roi == null ? server.getHeight() : roi.getBoundsHeight();

        // Create a dialog
        GridPane pane = new GridPane();
        int row = 0;
        pane.add(new Label("Export format"), 0, row);
        ComboBox<ImageWriter<BufferedImage>> comboImageType = new ComboBox<>();

        Function<ImageWriter<BufferedImage>, String> fun = (ImageWriter<BufferedImage> writer) -> writer.getName();
        comboImageType.setCellFactory(p -> GuiTools.createCustomListCell(fun));
        comboImageType.setButtonCell(GuiTools.createCustomListCell(fun));

        var writers = ImageWriterTools.getCompatibleWriters(server, null);
        comboImageType.getItems().setAll(writers);
        comboImageType.setTooltip(new Tooltip("Choose export image format"));
        if (writers.contains(lastWriter))
            comboImageType.getSelectionModel().select(lastWriter);
        else
            comboImageType.getSelectionModel().selectFirst();
        comboImageType.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(comboImageType, Priority.ALWAYS);
        pane.add(comboImageType, 1, row++);

        TextArea textArea = new TextArea();
        textArea.setPrefRowCount(2);
        textArea.setEditable(false);
        textArea.setWrapText(true);
//		textArea.setPadding(new Insets(15, 0, 0, 0));
        comboImageType.setOnAction(e -> textArea.setText(((ImageWriter<BufferedImage>)comboImageType.getValue()).getDetails()));
        textArea.setText(((ImageWriter<BufferedImage>)comboImageType.getValue()).getDetails());
        pane.add(textArea, 0, row++, 2, 1);

        var label = new Label("Downsample factor");
        pane.add(label, 0, row);
        TextField tfDownsample = new TextField();
        label.setLabelFor(tfDownsample);
        pane.add(tfDownsample, 1, row++);
        tfDownsample.setTooltip(new Tooltip("Amount to scale down image - choose 1 to export at full resolution (note: for large images this may not succeed for memory reasons)"));
        ObservableDoubleValue downsample = Bindings.createDoubleBinding(() -> {
            try {
                return Double.parseDouble(tfDownsample.getText());
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }, tfDownsample.textProperty());

        // Define a sensible limit for non-pyramidal images
        long maxPixels = 10000*10000;

        Label labelSize = new Label();
        labelSize.setMinWidth(400);
        labelSize.setTextAlignment(TextAlignment.CENTER);
        labelSize.setContentDisplay(ContentDisplay.CENTER);
        labelSize.setAlignment(Pos.CENTER);
        labelSize.setMaxWidth(Double.MAX_VALUE);
        labelSize.setTooltip(new Tooltip("Estimated size of exported image"));
        pane.add(labelSize, 0, row++, 2, 1);
        labelSize.textProperty().bind(Bindings.createStringBinding(() -> {
            if (!Double.isFinite(downsample.get())) {
                labelSize.setStyle("-fx-text-fill: red;");
                return "Invalid downsample value!  Must be >= 1";
            }
            else {
                long w = (long)(regionWidth / downsample.get() + 0.5);
                long h = (long)(regionHeight / downsample.get() + 0.5);
                String warning = "";
                var writer = comboImageType.getSelectionModel().getSelectedItem();
                boolean supportsPyramid = writer == null ? false : writer.supportsPyramidal();
                if (!supportsPyramid && w * h > maxPixels) {
                    labelSize.setStyle("-fx-text-fill: red;");
                    warning = " (too big!)";
                } else if (w < 5 || h < 5) {
                    labelSize.setStyle("-fx-text-fill: red;");
                    warning = " (too small!)";
                } else
                    labelSize.setStyle(null);
                return String.format("Output image size: %d x %d pixels%s",
                        w, h, warning
                );
            }
        }, downsample, comboImageType.getSelectionModel().selectedIndexProperty()));

        tfDownsample.setText(Double.toString(exportDownsample.get()));

        PaneTools.setMaxWidth(Double.MAX_VALUE, labelSize, textArea, tfDownsample, comboImageType);
        PaneTools.setHGrowPriority(Priority.ALWAYS, labelSize, textArea, tfDownsample, comboImageType);

        pane.setVgap(5);
        pane.setHgap(5);

        if (!Dialogs.showConfirmDialog("Export image region", pane))
            return;

        var writer = comboImageType.getSelectionModel().getSelectedItem();
        boolean supportsPyramid = writer == null ? false : writer.supportsPyramidal();
        int w = (int)(regionWidth / downsample.get() + 0.5);
        int h = (int)(regionHeight / downsample.get() + 0.5);
        if (!supportsPyramid && w * h > maxPixels) {
            Dialogs.showErrorNotification("Export image region", "Requested export region too large - try selecting a smaller region, or applying a higher downsample factor");
            return;
        }

        if (downsample.get() < 1 || !Double.isFinite(downsample.get())) {
            Dialogs.showErrorMessage("Export image region", "Downsample factor must be >= 1!");
            return;
        }

        exportDownsample.set(downsample.get());

        // Now that we know the output, we can create a new server to ensure it is downsampled as the necessary resolution
        if (renderedImage && downsample.get() != server.getDownsampleForResolution(0))
            server = new RenderedImageServer.Builder(viewer).downsamples(downsample.get()).build();

//		selectedImageType.set(comboImageType.getSelectionModel().getSelectedItem());

        // Create RegionRequest
        RegionRequest request = null;
        if (pathObject != null && pathObject.hasROI())
            request = RegionRequest.createInstance(server.getPath(), exportDownsample.get(), roi);

        // Create a sensible default file name, and prompt for the actual name
        String ext = writer.getDefaultExtension();
        String writerName = writer.getName();
        String defaultName = GeneralTools.getNameWithoutExtension(new File(ServerTools.getDisplayableImageName(server)));
        if (roi != null) {
            if(pathObject.getName()!=null && !pathObject.getName().isEmpty()) {
                defaultName = String.format("%s_%s_(ds=%s, x=%d, y=%d, w=%d, h=%d)", defaultName, pathObject.getName(),
                        GeneralTools.formatNumber(request.getDownsample(), 2),
                        request.getX(), request.getY(), request.getWidth(), request.getHeight());
            } else{
                defaultName = String.format("%s_(ds=%s, x=%d, y=%d, w=%d, h=%d)", defaultName,
                        GeneralTools.formatNumber(request.getDownsample(), 2),
                        request.getX(), request.getY(), request.getWidth(), request.getHeight());
            }
        }
        File fileOutput = Dialogs.promptToSaveFile("Export image region", null, defaultName, writerName, ext);
        if (fileOutput == null)
            return;

        try {
            if (request == null) {
                if (exportDownsample.get() == 1.0)
                    writer.writeImage(server, fileOutput.getAbsolutePath());
                else
                    writer.writeImage(ImageServers.pyramidalize(server, exportDownsample.get()), fileOutput.getAbsolutePath());
            } else
                writer.writeImage(server, request, fileOutput.getAbsolutePath());
            lastWriter = writer;
        } catch (IOException e) {
            Dialogs.showErrorMessage("Export region", e);
        }
    }
}
