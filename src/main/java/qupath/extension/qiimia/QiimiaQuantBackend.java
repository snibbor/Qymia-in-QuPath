package qupath.extension.qiimia;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.imagej.tools.IJTools;
import qupath.imagej.tools.PixelImageIJ;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.analysis.images.SimpleModifiableImage;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.ImageData;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.*;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ops.ImageOps;
import qupath.opencv.tools.OpenCVTools;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static qupath.lib.objects.classes.PathClassFactory.getPathClass;
import static qupath.lib.scripting.QP.*;
import static qupath.lib.scripting.QP.clearMeasurements;

//	https://stackoverflow.com/questions/21163108/custom-thread-pool-in-java-8-parallel-stream
public class QiimiaQuantBackend {
    private static final Logger logger = LoggerFactory.getLogger(QiimiaQuantBackend.class);
    private ForkJoinPool forkJoinPool;

    private int estNumTasks;
    private int numThreads;

    private Collection<Compartments> cellCompartments = Collections.synchronizedList(Arrays.asList(Compartments.values()));
    private Set<Measurements> measurements = Collections.synchronizedSet(new HashSet<>(Arrays.asList(Measurements.values())));
    private Map<String, Object> params = new ConcurrentHashMap<>();

    private ProgressBar progressBar;
    private Label progressLabel;
    private ObservableList<Control> controlListToToggle = FXCollections.observableArrayList();
    private ObservableList<MenuItem> menuItemListToToggle = FXCollections.observableArrayList();

    public Map<String, Object> getParams(){
        return params;
    }
    private ImageData<BufferedImage> bImageData;

    public ImageData<BufferedImage> getImageData(){
        return bImageData;
    }
    private ConcurrentHashMap<ColorTransforms.ColorTransform, Double> targets;
    private Set<PathClass> compartments;
    private Set<PathClass> ignoreClasses;
    private Set<PathClass> roiClasses;

    private Class<? extends PathObject> sourceType;

//    private Collection<PathObject> selectedObjects = new ArrayList<>();
    private final AtomicReference<BigInteger> progressValue = new AtomicReference<BigInteger>(new BigInteger("0"));
    private final AtomicReference<Boolean> isCancelled;

    //  options for tile calculations
    public enum TileOption{
        FULL_IMAGE,
        ROI_ONLY,
        ROI_AND_IMAGE,
        TMA,
        ROI_AND_TMA,
        SELECTED_OBJS;
        @Override
        public String toString() {
            switch(this) {
                case FULL_IMAGE: return "Full image";
                case ROI_ONLY: return "ROIs only";
                case ROI_AND_IMAGE: return "Full image + ROIs";
                case SELECTED_OBJS: return "Selected objects";
                case TMA: return "TMA cores";
                case ROI_AND_TMA: return "TMA and ROIs";
                default: throw new IllegalArgumentException();
            }
        }
    }
    public enum Compartments {
        /**
         * Nucleus only
         */
        NUCLEUS,
        /**
         * Full cell region, with nucleus removed
         */
        CYTOPLASM,
        /**
         * Full cell region
         */
        CELL,
        /**
         * Cell boundary, with interior removed
         */
        MEMBRANE

    }

    /**
     * Requested intensity measurements.
     */
    public enum Measurements {
        MEAN,
        MEDIAN,
        MIN,
        MAX,
        STD_DEV,
        VARIANCE;
        private String getMeasurementName() {
            switch (this) {
                case MAX:
                    return "Max";
                case MEAN:
                    return "Mean";
                case MEDIAN:
                    return "Median";
                case MIN:
                    return "Min";
                case STD_DEV:
                    return "Std.Dev.";
                case VARIANCE:
                    return "Variance";
                default:
                    throw new IllegalArgumentException("Unknown measurement " + this);
            }
        }

        private double getMeasurement(StatisticalSummary stats) {
            switch (this) {
                case MAX:
                    return stats.getMax();
                case MEAN:
                    return stats.getMean();
                case MEDIAN:
                    if (stats instanceof DescriptiveStatistics)
                        return ((DescriptiveStatistics) stats).getPercentile(50.0);
                    else
                        return Double.NaN;
                case MIN:
                    return stats.getMin();
                case STD_DEV:
                    return stats.getStandardDeviation();
                case VARIANCE:
                    return stats.getVariance();
                default:
                    throw new IllegalArgumentException("Unknown measurement " + this);
            }
        }
    }


//    Not for GUI
    QiimiaQuantBackend(ImageData<BufferedImage> bImageData,
                       Map<ColorTransforms.ColorTransform, Double> targets,
                       Set<PathClass> compartments,
                       Set<PathClass> ignoreClasses,
                       Set<PathClass> roiClasses,
//                       Collection<PathObject> selectedObjs,
                       double downsample,
                       int tileSize,
                       TileOption tileOption,
                       boolean tileUnitIsMicrons,
                       boolean deleteTilesBeforeRun,
                       Class<? extends PathObject> sourceType,
                       boolean verboseMeasures,
                       boolean rescaleScore,
                       boolean normalizeScore,
                       double maxFloatValue,
                       String result,
                       String slideType,
                       String stainType,
                       int numThreads
    ) {
        this.bImageData = bImageData;
        this.targets = new ConcurrentHashMap<>(targets);
        this.compartments = Collections.synchronizedSet(compartments);
        this.ignoreClasses = Collections.synchronizedSet(ignoreClasses);
        this.roiClasses = Collections.synchronizedSet(roiClasses);
        this.sourceType = sourceType;
//        this.selectedObjects = selectedObjs;
        this.params = new ConcurrentHashMap<>(Map.ofEntries(
                Map.entry("downsample", downsample),
                Map.entry("tileSize", tileSize),
                Map.entry("tileOption", tileOption),
                Map.entry("tileUnitIsMicrons", tileUnitIsMicrons),
                Map.entry("deleteTilesBeforeRun", deleteTilesBeforeRun),
                Map.entry("sourceString", sourceType.toString()),
                Map.entry("verboseMeasures", verboseMeasures),
                Map.entry("rescaleScore", rescaleScore),
                Map.entry("normalizeScore", normalizeScore),
                Map.entry("maxFloatValue", maxFloatValue),
                Map.entry("result", result),
                Map.entry("slide", slideType),
                Map.entry("stain", stainType)
        ));
        this.numThreads = numThreads;
        this.isCancelled = new AtomicReference<Boolean>(false);
//        this.controlListToToggle = null;
//        this.menuItemListToToggle = null;
        this.progressBar = null;
        this.progressLabel = null;
    }

    QiimiaQuantBackend(ImageData<BufferedImage> bImageData,
                       Map<ColorTransforms.ColorTransform, Double> targets,
                       Set<PathClass> compartments,
                       Set<PathClass> ignoreClasses,
                       Set<PathClass> roiClasses,
//                       Collection<PathObject> selectedObjs,
                       Map<String, Object> params,
                       int numThreads
    ) {
        this.bImageData = bImageData;
        this.targets = new ConcurrentHashMap<>(targets);
        this.compartments = Collections.synchronizedSet(compartments);
        this.ignoreClasses = Collections.synchronizedSet(ignoreClasses);
        this.roiClasses = Collections.synchronizedSet(roiClasses);
//        this.selectedObjects = selectedObjs;
        this.params = new ConcurrentHashMap<>(params);
        String sourceString = (String) params.get("sourceString");
		if(sourceString.toLowerCase().contains("cell")){
			this.sourceType = PathCellObject.class;
		} else if (sourceString.toLowerCase().contains("detection")){
			this.sourceType = PathDetectionObject.class;
		} else if(sourceString.toLowerCase().contains("annotation")){
			this.sourceType = PathAnnotationObject.class;
		}
        this.numThreads = numThreads;
        this.isCancelled = new AtomicReference<Boolean>(false);
//        this.controlListToToggle = null;
//        this.menuItemListToToggle = null;
        this.progressBar = null;
        this.progressLabel = null;
    }

    // For GUI
    QiimiaQuantBackend(ImageData<BufferedImage> bImageData,
                       Map<ColorTransforms.ColorTransform, Double> targets,
                       Set<PathClass> compartments,
                       Set<PathClass> ignoreClasses,
                       Set<PathClass> roiClasses,
//                       Collection<PathObject> selectedObjs,
                       Map<String, Object> params,
                       int numThreads,
                       AtomicReference<Boolean> runCancelled,
                       ObservableList<Control> controlListToToggle,
                       ObservableList<MenuItem> menuItemListToToggle,
                       ProgressBar progressBar,
                       Label progressLabel
    ) {
        this.bImageData = bImageData;
        this.targets = new ConcurrentHashMap<>(targets);
        this.compartments = Collections.synchronizedSet(compartments);
        this.ignoreClasses = Collections.synchronizedSet(ignoreClasses);
        this.roiClasses = Collections.synchronizedSet(roiClasses);
//        this.selectedObjects = selectedObjs;
        this.params = new ConcurrentHashMap<>(params);
        String sourceString = (String) params.get("sourceString");
        if(sourceString.toLowerCase().contains("cell")){
            this.sourceType = PathCellObject.class;
        } else if (sourceString.toLowerCase().contains("detection")){
            this.sourceType = PathDetectionObject.class;
        } else if(sourceString.toLowerCase().contains("annotation")){
            this.sourceType = PathAnnotationObject.class;
        }
        this.numThreads = numThreads;
        this.isCancelled = runCancelled;
        this.controlListToToggle = controlListToToggle;
        this.menuItemListToToggle = menuItemListToToggle;
        this.progressBar = progressBar;
        this.progressLabel = progressLabel;
    }

    public void setControlListToToggle(ObservableList<Control> controlListToToggle){
        this.controlListToToggle = controlListToToggle;
    }
    public void setMenuItemListToToggle(ObservableList<MenuItem> menuItemListToToggle){
        this.menuItemListToToggle = menuItemListToToggle;
    }

    public void setProgressBarAndLabel(ProgressBar progressBar, Label progressLabel){
        this.progressBar = progressBar;
        this.progressLabel = progressLabel;
    }

    public void setupNewForkJoinPool(int numThreads) {
        isCancelled.set(false);
        if (forkJoinPool == null) {
            logger.info("creating new forkJoinPool");
            forkJoinPool = new ForkJoinPool(numThreads);
        } else if (forkJoinPool.isTerminated()) {
            forkJoinPool = null;
            System.gc();
            System.gc();
            logger.info("creating new forkJoinPool");
            forkJoinPool = new ForkJoinPool(numThreads);
        } else {
            logger.warn("forkJoinPool already exists and is not terminated yet!");
            try {
                shutdownPool().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            logger.warn("trying to create new forkJoinPool...");
            forkJoinPool = new ForkJoinPool(numThreads);
        }
    }

    public static void closeQuietly(AutoCloseable c) {
        if (c != null) {
            logger.info("closing...");
            try {
                c.close();
            } catch (Exception ex) {
                // ignore or trace log it
                logger.warn(ex.toString());
//					throw new RuntimeException(e);
            }
        }

    }

    public void close() {
//			this.qupath = null;
//			if(!threadImageServerMap.isEmpty()){
//				threadImageServerMap.forEach((k, c)->{
//					logger.info("trying to close image server on thread... " + k.toString());
//					closeQuietly(c);
//				});
//				threadImageServerMap.clear();
//			}
//			This shuts down the main server for the current image and causes problems....
//			else {
//				try {
//					bImageData.getServer().close();
//				} catch (Exception ex) {
////				ex.printStackTrace();
//					logger.error(ex.toString());
//				}
//			}
//			this.bImageData = null;
        this.targets = null;
        this.compartments = null;
        this.ignoreClasses = null;
        this.roiClasses = null;
        this.params = null;
        this.cellCompartments = null;
        this.measurements = null;
        try {
            shutdownPool().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        System.gc();
//        System.gc();
    }

    public void setEstNumTasks(int newEst) {
        logger.info("Estimate # tasks: " + newEst);
        estNumTasks = newEst;
    }

    public int getEstNumTasks() {
        return estNumTasks;
    }
//		public BigDecimal incrementAndGet(double amount) {
//			for (;;) {
//				BigDecimal current = progressValue.get();
//				BigDecimal next = current.add(new BigDecimal(String.format("%.2f", amount)));
//				if (progressValue.compareAndSet(current, next)) {
//					return next;
//				}
//			}
//		}

    public BigInteger incrementAndGet(Integer amount) {
        for (; ; ) {
            BigInteger current = progressValue.get();
            BigInteger next = current.add(new BigInteger(amount.toString()));
            if (progressValue.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    //	https://stackoverflow.com/questions/21083945/how-to-avoid-not-on-fx-application-thread-currentthread-javafx-application-th
    public void incrementProgress(Integer amount) {
        double prog = incrementAndGet(amount).doubleValue();
        int newEst = getEstNumTasks();
        logger.debug(String.format("Progress: %f", prog / newEst));
        if(progressBar!=null) {
            Platform.runLater(() -> {
                progressBar.setProgress(prog / newEst);
            });
        }
    }

    public CompletableFuture<Void> shutdownPool(){
        CompletableFuture<Void> result = CompletableFuture.runAsync(() -> {
            if (forkJoinPool != null) {
                forkJoinPool.shutdownNow();
                try {
                    logger.info("awaiting forkJoinPool termination...");
                    if (forkJoinPool.awaitTermination(30, TimeUnit.SECONDS)) {
                        logger.info("forkJoinPool termination finished...");
                        forkJoinPool = null;
                        System.gc();
//                        System.gc();
                    } else {
                        logger.warn("forkJoinPool termination timed-out...!");
                        forkJoinPool.shutdownNow();
                        System.gc();
//                        System.gc();
                    }
                } catch (InterruptedException ex) {
                    logger.warn(String.valueOf(ex));
                    logger.warn("interrupted before termination of forkJoinPool?...");
                }
            }
        });
        setEstNumTasks(0);
        progressValue.set(BigInteger.valueOf(0));
        return result;
    }

    public CompletableFuture<Void> cancelTasks() {
        isCancelled.set(true);
        logger.warn("Trying to shutdown running tasks!");
        return shutdownPool();
    }

    public boolean isTaskRunning() {
        if (forkJoinPool != null) {
            return !forkJoinPool.isTerminated();
        } else {
            return false;
        }
    }


    CompletableFuture<Void> runQuant(){
//		Every time you run this code, make sure that the isCancelled is false at first, buttons are toggled, progress bar is setup
        isCancelled.set(false);
        Platform.runLater(()->{
            for (Control button : controlListToToggle) {
                button.setDisable(true);
            }
            for (MenuItem menuItem : menuItemListToToggle) {
                menuItem.setDisable(true);
            }
        });
        if(progressBar!=null){
            Platform.runLater(()->{
                progressBar.setProgress(-1);
                progressLabel.setText("Starting Compartment Quantification...");
            });
        }
        ImageData<BufferedImage> imageData = this.getImageData();
        PathObjectHierarchy hierarchy = imageData.getHierarchy();
        Map<String, Object> params = this.getParams();
        String slide = (String) params.get("slide");
        String result = (String) params.get("result");
//		Remove detection objects within any ROIs that are not cells, clear source measurements
        if(result.toLowerCase().contains("roi")){
            List<PathObject> oldROIComps = hierarchy.getDetectionObjects().parallelStream()
                    .filter(p->!p.isCell() && !p.isTile() && !p.getParent().isTile() && roiClasses.contains(p.getParent().getPathClass()))
                    .collect(Collectors.toList());
            hierarchy.removeObjects(oldROIComps, true);
        }

        if(sourceType.equals(PathAnnotationObject.class)) {
            if(!result.toLowerCase().contains("roi"))
                clearMeasurements(hierarchy, hierarchy.getAnnotationObjects());
        } else if(sourceType.equals(PathCellObject.class)){
            if(!result.toLowerCase().contains("roi"))
                clearMeasurements(hierarchy, hierarchy.getCellObjects());
        } else if(sourceType.equals(PathDetectionObject.class)){
            if(!result.toLowerCase().contains("roi"))
                clearMeasurements(hierarchy, hierarchy.getDetectionObjects());
        }
        CompletableFuture<Void> runFuture = CompletableFuture.runAsync(()->{
                    if(isCancelled.get()){
                        throw new CancellationException();
                    }
                    if(result.toLowerCase().contains("tile")){
                        int tileSize = 0;
                        if(params.get("tileSize")!=null){
                            if(params.get("tileSize") instanceof Double){
                                tileSize = ((Double) params.get("tileSize")).intValue();
                            } else {
                                tileSize = (int) params.get("tileSize");
                            }
                        }

                        if(params.get("tileSize")==null) {
                            logger.warn("Tilesize cannot be null when trying to compute tile results!");
                        }else if(tileSize == 0) {
                            logger.warn("Tilesize cannot be 0 or empty when trying to compute tile results!");
                        }else {
                            if(progressLabel != null) {
                                Platform.runLater(() -> {
                                    progressLabel.setText("Quantifying Tiles...");
                                });
                            }
                            boolean deleteTilesBeforeRun = (boolean) params.get("deleteTilesBeforeRun");
                            if(deleteTilesBeforeRun){
//						        If you are making grids/tiles, delete any old tiles?
                                logger.warn("Deleting any tile objects!!");
                                hierarchy.removeObjects(hierarchy.getTileObjects(), true);
                            }

                            try{
                                this.TileRecalcCompartmentsAndScores().get();
                            }catch (ExecutionException | InterruptedException | CancellationException ex){
                                Platform.runLater(()-> {
                                    for (Control button : controlListToToggle) {
                                        button.setDisable(false);
                                    }
                                    for (MenuItem menuItem : menuItemListToToggle) {
                                        menuItem.setDisable(false);
                                    }
                                });
                                throw new RuntimeException(ex);
                            }
                        }

                    }
                })
                .thenRun(()->{
                    if(isCancelled.get()){
                        throw new CancellationException();
                    }
                    if(result.toLowerCase().contains("tma") && slide.equals("TMA")){
                        logger.info("Beginning compartment quantification of TMA cores for compartments: {} and targets: {}...", compartments.toString(), targets.toString());
                        if(progressLabel!=null) {
                            Platform.runLater(() -> {
                                progressLabel.setText("Quantifying TMA core compartments...");
                            });
                        }
                        try {
                            this.TMARecalcCompartmentsAndScores().get();
                        } catch (ExecutionException | InterruptedException | CancellationException ex) {
                            Platform.runLater(()-> {
                                for (Control button : controlListToToggle) {
                                    button.setDisable(false);
                                }
                                for (MenuItem menuItem : menuItemListToToggle) {
                                    menuItem.setDisable(false);
                                }
                            });
                            throw new RuntimeException(ex);
                        }
                    }
                })
                .thenRun(()->{
                    if(isCancelled.get()){
                        throw new CancellationException();
                    }
                    if(result.toLowerCase().contains("roi")){
                        logger.info("Beginning compartment quantification of ROIs for compartments: {} and targets: {}...", compartments.toString(), targets.toString());
                        if(progressLabel!=null) {
                            Platform.runLater(() -> {
                                progressLabel.setText("Quantifying ROI compartments...");
                            });
                        }
                        try {
                            this.getTargetScoresForROIs().get();
                        } catch (ExecutionException | InterruptedException | CancellationException ex) {
                            Platform.runLater(()-> {
                                for (Control button : controlListToToggle) {
                                    button.setDisable(false);
                                }
                                for (MenuItem menuItem : menuItemListToToggle) {
                                    menuItem.setDisable(false);
                                }
                            });
                            throw new RuntimeException(ex);
                        }
                    }
                })
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof CancellationException){
                        logger.warn("Run cancelled?");
                        if(progressBar!=null && progressLabel!=null) {
                            Platform.runLater(() -> {
                                progressLabel.setText("Run cancelled..");
                                progressBar.setProgress(1);
                            });
                        }
                    } else {
                        if(progressLabel!=null) {
                            Platform.runLater(() -> {
                                progressLabel.setText(ex.getCause().toString());
                            });
                        }
                        ex.printStackTrace();
                    }
                    logger.warn(ex.toString());
                    try {
                        this.cancelTasks().get();
                    } catch (InterruptedException | ExecutionException exc) {
                        throw new RuntimeException(exc);
                    }
                    return null;
                })
                .thenRun(()->{
//			    not necessary but just in case
                    Platform.runLater(()-> {
                        for (Control button : controlListToToggle) {
                            button.setDisable(false);
                        }
                        for (MenuItem menuItem : menuItemListToToggle) {
                            menuItem.setDisable(false);
                        }
                    });
//				cleanup vars
                    this.close();
//			var store = qupath == null ? null : qupath.getImageRegionStore();
//			if (store != null) {
////					This was the reason for the memory accumulation! makes sense in retrospect, considering all the region requests that are made...
//				logger.info("Clearing Image Region Store cache...");
//				store.clearCache();
//			}
                    System.gc();
                    logger.info("Completed with all tasks...");
                });
        return runFuture;
    }


    // AQUA inside each intersecting compartment of ROI only
    //    Map<String, Integer> targets = new LinkedHashMap<>();
    // Not for TMAs! Would be much more effective to restrict the search space for ROIS within TMA core hierarchy, however, not all the annotations will be properly incorporated into the hierarchy.....
    // TODO: How to flexibly find ROIs within TMA core hierarchy?
    public CompletableFuture<Void> getTargetScoresForROIs() throws RuntimeException {
        return getTargetScoresForROIs(ignoreClasses, roiClasses, targets, compartments, sourceType, (double) params.get("downsample"), numThreads);
    }

    //		It would be nice to set this up so that there is a static method that can be used from scripting if you didn't want to use the GUI
//		but then you would have to remove all the non-static GUI progress bar elements and use the commonPool, so the code would be different....
    public CompletableFuture<Void> getTargetScoresForROIs(Set<PathClass> ignoreClasses,
                                                          Set<PathClass> rois,
                                                          Map<ColorTransforms.ColorTransform, Double> targets,
                                                          Set<PathClass> compartments,
                                                          Class<? extends PathObject> sourceType,
                                                          double downsample,
                                                          int numThreads
    ) throws RuntimeException {

        if (numThreads <= 0)
            numThreads = 1;

        setupNewForkJoinPool(numThreads);


        Integer progAmount = 1;
        progressValue.set(BigInteger.valueOf(0));

        // Used for placing child objects inside ROI
        AtomicInteger totalROIs = new AtomicInteger(0);
        AtomicInteger roiNumber = new AtomicInteger(1);
        AtomicReference<Boolean> doAdjust = new AtomicReference<>(false);
        ROI combinedExcludeROI;
        Geometry combinedExcludeGeom;

        PathObjectHierarchy hierarchy = bImageData.getHierarchy();
        var pathObjs = hierarchy.getObjects(null, PathObject.class);
//			https://stackoverflow.com/questions/53558753/how-do-i-close-a-thread-local-autocloseable-used-in-parallel-stream
//			if(threadImageServerMap.isEmpty()) {
//				threadImageServerMap = new ConcurrentHashMap<>();
//			}else{
//				threadImageServerMap.forEach((k, c)->{
//					logger.info("trying to close image server on thread... " + k.toString());
//					closeQuietly(c);
//				});
//				threadImageServerMap.clear();
//			}
        ImageServer<BufferedImage> server = bImageData.getServer();
        CompletableFuture<Void> result = null;
        try {
            List<ROI> allIgnoreROIs = forkJoinPool.submit(() -> hierarchy.getAnnotationObjects().parallelStream()
                    .filter(p -> p.getPathClass() != null && ignoreClasses.contains(p.getPathClass()))
                    .map(p -> p.getROI())
                    .collect(Collectors.toList())).get();
            List<PathObject> compartmentObjs = Collections.synchronizedList(forkJoinPool.submit(() -> pathObjs.parallelStream()
                    .filter(p -> p.getPathClass() != null && compartments.contains(p.getPathClass()) && p.getClass() == sourceType)
                    .collect(Collectors.toList())).get());

            logger.info(allIgnoreROIs.toString());
            combinedExcludeROI = RoiTools.union(allIgnoreROIs);
            if (combinedExcludeROI != null && !combinedExcludeROI.isEmpty()) {
                doAdjust.set(true);
                combinedExcludeGeom = combinedExcludeROI.getGeometry();
            } else {
                doAdjust.set(false);
                combinedExcludeGeom = null;
            }

//				https://stackoverflow.com/questions/23320407/how-to-cancel-java-8-completable-future
//				ROI cannot be unclassified/null or else contains() throws a NullPointerException
            result = CompletableFuture.runAsync(() -> pathObjs.parallelStream().filter(p -> p.getPathClass() != null && rois.contains(p.getPathClass()) && p.hasROI())
                                    .map(f -> {
                                        // Record null/none values for compartments not within ROI
//											logger.info(f.getName());
//                                      if (f.getName() == null || f.getName().isBlank() || f.getName().matches("^ROI_[0-9]+$")) {
                                        if (f.getName() == null || f.getName().isBlank()) {
                                            f.setName(f.getPathClass().toString() + "_" + roiNumber.getAndIncrement());
                                        }

                                        PathObject adjpathObj = null;
                                        ROI adjpathObjROI = f.getROI();
                                        if (doAdjust.get() && adjpathObjROI.getGeometry().intersects(combinedExcludeGeom)) {
                                            adjpathObjROI = RoiTools.combineROIs(adjpathObjROI, combinedExcludeROI, RoiTools.CombineOp.SUBTRACT);
                                            if (adjpathObjROI.isEmpty()) {
                                                logger.info("ROI {} is now empty, skipping scoring metrics...", f.getName());
                                                return null;
                                            } else {
                                                logger.info("Adjusting ROI {} based on ignore annotations...", f.getName());

                                                adjpathObj = PathObjects.createAnnotationObject(adjpathObjROI, f.getPathClass());
//													Do I need to set the name again?
                                                adjpathObj.setName(f.getName());
                                                hierarchy.addPathObject(adjpathObj);
//													bImageData.getHierarchy().addPathObjectBelowParent(pathObj.getParent(), adjpathObj, true);
                                                hierarchy.removeObject(f, true);
                                            }
                                        } else {
                                            adjpathObj = f;
                                        }

                                        // this might work but does it scale for lots of ROIs?
                                        setEstNumTasks(totalROIs.incrementAndGet());
                                        return adjpathObj;
                                    })
                                    .filter(p -> p != null)
                                    .forEach(r -> {
                                        //Typically the number of compartments is small and these are all combined for a WSI.
                                        //Not efficient for TMA cores! but should work...
                                        if (isCancelled.get()) {
                                            throw new CancellationException();
                                        }

                                        for (PathObject compObj : compartmentObjs) {

                                            ROI compInterROI = RoiTools.combineROIs(compObj.getROI(), r.getROI(), RoiTools.CombineOp.INTERSECT);

                                            if (!compInterROI.isEmpty()) {
                                                PathObject compInterDet = PathObjects.createDetectionObject(compInterROI, compObj.getPathClass());
                                                logger.info("ROI contains {} compartment! Scoring target expression within ROI.", compObj.getPathClass().toString());
                                                // For debugging, maybe helps with visualization
                                                // Add object as a child of the ROI
                                                //                        addObject(compInterDet);
                                                compInterDet.setName(r.getName() + " (" + compObj.getPathClass().toString() + ")");
                                                bImageData.getHierarchy().addPathObjectBelowParent(r, compInterDet, true);

                                                logger.info("Got {} intersection with ROI", compObj.getPathClass().toString());

                                                // Quantify metrics/AQUA for each target in each intersecting compartment
                                                // Calculate AQUA scoring metrics for new compartment detections for all targets
                                                try {
                                                    getTargetsIntensityScores_OpenCV(server, compInterDet);
                                                } catch (IOException ex) {
                                                    logger.warn(ex.toString());
                                                }
                                            } else {
                                                logger.info("No intersection with {} compartment for ROI... skipping.", compObj.getPathClass().toString());
                                            }
                                        }
                                        incrementProgress(progAmount);
                                    }),
                            forkJoinPool)
                    .thenRun(() -> {
                        if(progressBar!=null) {
                            Platform.runLater(() -> {
                                progressLabel.setText("Completed scoring ROI compartments!");
                                progressBar.setProgress(1.0);
                                for (Control button : controlListToToggle) {
                                    button.setDisable(false);
                                }
                                for (MenuItem menuItem : menuItemListToToggle) {
                                    menuItem.setDisable(false);
                                }
                            });
                        }
                        fireHierarchyUpdate(bImageData.getHierarchy());
                    })
                    .exceptionally(ex -> {
                        if (ex.getCause() instanceof CancellationException){
                            logger.warn("Run cancelled?");
                        } else {
                            ex.printStackTrace();
                        }
//							logger.warn(Arrays.toString(ex.getStackTrace()));
                        logger.warn("getTargetScoresForROIs: " + ex);
                        logger.warn(ex.toString());
                        fireHierarchyUpdate(bImageData.getHierarchy());
                        return null;
                    });

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            forkJoinPool.shutdown();
        }
        return result;
    }

    /**
     * Try to intersect two geometries, returning null if this fails.
     * Intended for use in a stream.
     *
     * @param g1
     * @param g2
     * @return
     */
    private static Geometry intersect(Geometry g1, Geometry g2) {
//		if (g1.covers(g2))
//			return g2;
//		if (g2.covers(g1))
//			return g1;
        if (g1 == g2)
            return g1;

        try {
            return GeometryTools.homogenizeGeometryCollection(g1.intersection(g2));
        } catch (Exception e) {
            logger.warn(e.getLocalizedMessage(), e);
            return null;
        }
    }

    // fixed version of original RoiTools.computeTiledROIs function
    public static Collection<? extends ROI> computeTiledROIs(ROI parentROI, ImmutableDimension sizePreferred, ImmutableDimension sizeMax, boolean fixedSize, int overlap) {

        ROI pathArea = parentROI != null && parentROI.isArea() ? parentROI : null;
        Rectangle2D bounds = AwtTools.getBounds2D(parentROI);
        if (pathArea == null || (bounds.getWidth() <= sizeMax.width && bounds.getHeight() <= sizeMax.height)) {
            return Collections.singletonList(parentROI);
        }

        Geometry geometry = pathArea.getGeometry();
        PreparedGeometry prepared = null;

        double xMin = bounds.getMinX();
        double yMin = bounds.getMinY();
        int nx = (int)Math.ceil(bounds.getWidth() / sizePreferred.width);
        int ny = (int)Math.ceil(bounds.getHeight() / sizePreferred.height);
        double w = fixedSize ? sizePreferred.width : (int)Math.ceil(bounds.getWidth() / nx);
        double h = fixedSize ? sizePreferred.height : (int)Math.ceil(bounds.getHeight() / ny);

        // Center the tiles
        xMin = (int)(bounds.getCenterX() - (nx * w * .5));
        yMin = (int)(bounds.getCenterY() - (ny * h * .5));

        // This can be very slow if we have an extremely large number of vertices/tiles.
        // For that reason, we try to split initially by either rows or columns if needed.
        boolean byRow = false;
        boolean byColumn = false;
        Map<Integer, Geometry> rowParents = null;
        Map<Integer, Geometry> columnParents = null;
        var envelope = geometry.getEnvelopeInternal();
        if (ny > 1 && nx > 1 && geometry.getNumPoints() > 1000) {

            // If we have a lot of points, create a prepared geometry so we can check covers/intersects quickly;
            // (for a regular geometry, it would be faster to just compute an intersection and see if it's empty)
            prepared = PreparedGeometryFactory.prepare(geometry);
            var prepared2 = prepared;
            var empty = geometry.getFactory().createEmpty(2);

            byRow = nx > ny;
            byColumn = !byRow;
            double yMin2 = yMin;
            double xMin2 = xMin;
            // Compute intersection by row so that later intersections are simplified
            if (byRow) {
                rowParents = IntStream.range(0, ny)
                        .parallel()
                        .mapToObj(yi -> yi)
                        .collect(
                                Collectors.toMap(
                                        yi -> yi,
                                        yi -> {
                                            double y = yMin2 + yi * h - overlap;
                                            var row = GeometryTools.createRectangle(
                                                    envelope.getMinX(),
                                                    y,
                                                    envelope.getMaxX()-envelope.getMinX(),
                                                    h + overlap*2);
                                            if (!prepared2.intersects(row))
                                                return empty;
                                            else if (prepared2.covers(row))
                                                return row;
                                            var temp = intersect(geometry, row);
                                            return temp == null ? geometry : temp;
                                        }
                                )
                        );
            }
            if (byColumn) {
                columnParents = IntStream.range(0, nx)
                        .parallel()
                        .mapToObj(xi -> xi)
                        .collect(
                                Collectors.toMap(
                                        xi -> xi,
                                        xi -> {
                                            double x = xMin2 + xi * w - overlap;
                                            var col = GeometryTools.createRectangle(
                                                    x,
                                                    envelope.getMinY(),
                                                    w + overlap*2,
                                                    envelope.getMaxY()-envelope.getMinY());
                                            if (!prepared2.intersects(col))
                                                return empty;
                                            else if (prepared2.covers(col))
                                                return col;
                                            var temp = intersect(geometry, col);
                                            return temp == null ? geometry : temp;
                                        }
                                )
                        );
            }
        }

//		have to make all these "final" temp variables.... probably a better way
        double finalYMin = yMin;
        double finalXMin = xMin;
        boolean finalByColumn = byColumn;
        Map<Integer, Geometry> finalColumnParents = columnParents;
        boolean finalByRow = byRow;
        Map<Integer, Geometry> finalRowParents = rowParents;
        List<ROI> tileROIs = Collections.synchronizedList(new ArrayList<>());
        var plane = parentROI.getImagePlane();
        AtomicInteger nullInterExcepetions = new AtomicInteger(0);
        IntStream.range(0, nx).parallel().forEach(xi -> {
            double x = finalXMin + xi * w - overlap;
//			A very hacky way to consolidate the code into 1 loop.
//			Atomic Reference doesn't behave when getting hit by multiple streams setting potentially different values for each stream...
            Geometry outerGeometryLocal = finalByColumn ? finalColumnParents.getOrDefault(xi, geometry) : geometry;
            IntStream.range(0, ny).parallel().forEach(yi -> {
                double y = finalYMin + yi * h - overlap;
                Geometry geometryLocal = finalByRow ? finalRowParents.getOrDefault(yi, geometry) : outerGeometryLocal;

                // Create the tile
                var rect = GeometryTools.createRectangle(x, y, w + overlap * 2, h + overlap * 2);
                Geometry inter = intersect(rect, geometryLocal);
                if(inter==null) {
                    nullInterExcepetions.incrementAndGet();
                    return;
                }
                ROI roi = GeometryTools.geometryToROI(inter, plane);
                tileROIs.add(roi);
            });
        });

        if (nullInterExcepetions.get() > 0) {
            logger.warn("Tiles lost during tiling: {}", nullInterExcepetions.get());
            logger.warn("You may be able to avoid tiling errors by calling 'Simplify shape' on any complex annotations first.");
        }

        // Remove any empty/non-area tiles
        return tileROIs.stream()
                .filter(t -> !t.isEmpty() && t.isArea())
                .collect(Collectors.toList());
    }

    public Map<PathObject, Map<PathClass, ROI>> computeTiledROIsForCompartments(Rectangle2D bounds,
                                                                                List<PathObject> compartmentPathObjs,
                                                                                ImmutableDimension sizePreferred,
                                                                                boolean fixedSize,
                                                                                int overlap) throws ExecutionException, InterruptedException {

        ConcurrentHashMap<PathClass, Geometry> compartmentGeoms = new ConcurrentHashMap<>(
                compartmentPathObjs.parallelStream()
                        .map(r -> Map.entry(r.getPathClass(), r.getROI().getGeometry()))
                        .filter(m -> m.getValue() != null)
                        .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue
                                )
                        )
        );

        return computeTiledROIsForCompartments(bounds, ImagePlane.getDefaultPlane(), compartmentGeoms, sizePreferred, fixedSize, overlap);
    }

    public Map<PathObject, Map<PathClass, ROI>> computeTiledROIsForCompartments(Rectangle2D bounds,
                                                                                Map<PathClass, ROI> compartmentPathROIs,
                                                                                ImmutableDimension sizePreferred,
                                                                                boolean fixedSize,
                                                                                int overlap) throws ExecutionException, InterruptedException {

        ConcurrentHashMap<PathClass, Geometry> compartmentGeoms = new ConcurrentHashMap<>(
                compartmentPathROIs.entrySet().parallelStream()
                        .map(r -> Map.entry(r.getKey(), r.getValue().getGeometry()))
                        .filter(m -> m.getValue() != null)
                        .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue
                                )
                        )
        );


        return computeTiledROIsForCompartments(bounds, ImagePlane.getDefaultPlane(), compartmentGeoms, sizePreferred, fixedSize, overlap);
    }


    public Map<PathObject, Map<PathClass, ROI>> computeTiledROIsForCompartments(Rectangle2D bounds,
                                                                                ImagePlane plane,
                                                                                ConcurrentHashMap<PathClass, Geometry> compartmentGeoms,
                                                                                ImmutableDimension sizePreferred,
                                                                                boolean fixedSize,
                                                                                int overlap) throws ExecutionException, InterruptedException {

//			Bound for entire image or ROI annotation

//			if (pathArea == null || (bounds.getWidth() <= sizeMax.width && bounds.getHeight() <= sizeMax.height)) {
//				return Collections.singletonList(parentROI);
//			}

        if (compartmentGeoms.size() <= 0) {
            logger.warn("Found no valid geometries for compartment PathObjects...");
            return null;
        }

        ConcurrentHashMap<PathClass, Optional<PreparedGeometry>> preparedGeoms = new ConcurrentHashMap<>(
                forkJoinPool.submit(()->compartmentGeoms.entrySet().parallelStream()
                        .map(m -> {
                            if (m.getValue().getNumPoints() > 1000) {
                                return Map.entry(m.getKey(), Optional.of(PreparedGeometryFactory.prepare(m.getValue())));
                            } else {
                                return Map.entry(m.getKey(), Optional.empty());
                            }
                        })
//                        .filter(m -> m != null)
                        .collect(Collectors.toMap(
                                        m -> m.getKey(),
                                        m -> (Optional<PreparedGeometry>) m.getValue()
                                )
                        )
                ).get());

        int xMin = (int) bounds.getMinX();
        int yMin = (int) bounds.getMinY();
        int nx = (int) Math.ceil(bounds.getWidth() / sizePreferred.width);
        int ny = (int) Math.ceil(bounds.getHeight() / sizePreferred.height);
        int w = fixedSize ? sizePreferred.width : (int) Math.ceil(bounds.getWidth() / nx);
        int h = fixedSize ? sizePreferred.height : (int) Math.ceil(bounds.getHeight() / ny);

        // Center the tiles
        xMin = (int) (bounds.getCenterX() - (nx * w * .5));
        yMin = (int) (bounds.getCenterY() - (ny * h * .5));

        // This can be very slow if we have an extremely large number of vertices/tiles.
        // For that reason, we try to split initially by either rows or columns if needed.
        boolean byRow = false;
        boolean byColumn = false;
        ConcurrentHashMap<Integer, ConcurrentHashMap<PathClass, Geometry>> localGeoms = new ConcurrentHashMap<>();
        // make the empty based on one of the entries in compartmentGeoms... may error if the key/value selected is null?
        if (ny > 1 && nx > 1 && preparedGeoms.size() >= 1) {
            // If we have a lot of points, create a prepared geometry so we can check covers/intersects quickly;
            // (for a regular geometry, it would be faster to just compute an intersection and see if it's empty)
            String prepString = String.format("Preparing %d sets of local geometries (1/4)", preparedGeoms.size());
            logger.info(prepString);
            if(progressBar!=null) {
                Platform.runLater(() -> {
                    progressLabel.setText(prepString);
                    progressBar.setProgress(-1);
                });
            }

            byRow = nx > ny;
//				byRow = true;
            byColumn = !byRow;
            double yMin2 = yMin;
            double xMin2 = xMin;

            PathClass compKey = compartmentGeoms.keySet().iterator().next();
            Geometry empty = compartmentGeoms.get(compKey).getFactory().createEmpty(2);

            Map<PathClass, Envelope> compartmentEnvel = compartmentGeoms.entrySet().parallelStream()
                    .map(m -> Map.entry(m.getKey(), m.getValue().getEnvelopeInternal()))
                    .collect(Collectors.toMap(
                            m -> m.getKey(),
                            m -> m.getValue()
                    ));

            // Compute intersection by row so that later intersections are simplified
            if (byRow) {
                localGeoms = new ConcurrentHashMap<>(
                forkJoinPool.submit(()->
                        IntStream.range(0, ny)
                                .parallel()
                                .boxed()
                                .collect(
                                    Collectors.toMap(
                                        yi -> yi,
                                        yi -> {
                                            double y = yMin2 + yi * h - overlap;
                                            return new ConcurrentHashMap<PathClass, Geometry>(
                                                preparedGeoms.entrySet().parallelStream()
                                                    .map(prep -> {
                                                        var preparedOpt = prep.getValue();
                                                        var geometry = compartmentGeoms.get(prep.getKey());
                                                        if (preparedOpt.isEmpty()) {
                                                            // This would happen if the geometry was too small to be prepared
                                                            // use the compartment geometry in this case
                                                            return Map.entry(prep.getKey(), geometry);
                                                        }
                                                        var prepared2 = preparedOpt.get();
                                                        var envelope = compartmentEnvel.get(prep.getKey());
                                                        var row = GeometryTools.createRectangle(
                                                                envelope.getMinX(),
                                                                y,
                                                                envelope.getMaxX()-envelope.getMinX(),
                                                                h + overlap * 2);
                                                        if (!prepared2.intersects(row)) {
                                                            return Map.entry(prep.getKey(), empty);
                                                        } else if (prepared2.covers(row)) {
                                                            return Map.entry(prep.getKey(), row);
                                                        }
                                                        var temp = intersect(geometry, row);
                                                        return Map.entry(prep.getKey(), temp == null ? geometry : temp);
                                                    })
                                                    .collect(Collectors.toMap(
                                                            m -> m.getKey(),
                                                            m -> m.getValue()
                                                        )
                                                    )
                                                );
                                    }
                                )
                            )
                        ).get()
                );
            }
            if (byColumn) {
                localGeoms = new ConcurrentHashMap<>(
                    forkJoinPool.submit(()->
                        IntStream.range(0, nx)
                            .parallel()
                            .boxed()
                            .collect(
                                Collectors.toMap(
                                    xi -> xi,
                                    xi -> {
                                        double x = xMin2 + xi * w - overlap;
                                        return new ConcurrentHashMap<PathClass, Geometry>(
                                            preparedGeoms.entrySet().parallelStream()
                                                .map(prep -> {
                                                    var preparedOpt = prep.getValue();
                                                    var geometry = compartmentGeoms.get(prep.getKey());
                                                    if (preparedOpt.isEmpty()) {
                                                        // This would happen if the geometry was too small to be prepared
                                                        // use the compartment geometry in this case
                                                        return Map.entry(prep.getKey(), geometry);
                                                    }
                                                    var prepared2 = preparedOpt.get();
                                                    var envelope = compartmentEnvel.get(prep.getKey());
                                                    var col = GeometryTools.createRectangle(
                                                            x,
                                                            envelope.getMinY(),
                                                            w + overlap * 2,
                                                            envelope.getMaxY()-envelope.getMinY());
                                                    if (!prepared2.intersects(col)) {
                                                        return Map.entry(prep.getKey(), empty);
                                                    } else if (prepared2.covers(col)) {
                                                        return Map.entry(prep.getKey(), col);
                                                    }
                                                    var temp = intersect(geometry, col);
                                                    return Map.entry(prep.getKey(), temp == null ? geometry : temp);
                                                })
                                                .collect(Collectors.toMap(
                                                    m->m.getKey(),
                                                    m->m.getValue()
                                                    )
                                                )
                                            );
                                    }
                                )
                            )
                        ).get()
                );
            }
        }

        // Generate all the rectangles as geometries
//			Map<Geometry, Geometry> tileGeometries = new LinkedHashMap<>();
        ConcurrentHashMap<PathObject, Map<PathClass, ROI>> tileIntersectROIs = new ConcurrentHashMap<>();

        ConcurrentHashMap<Integer, ConcurrentHashMap<PathClass, Geometry>> finalLocalGeoms = localGeoms;

//			always using full compartment geometries to compute intersections
//			when geometries are small (< 1000 pts)
//			AtomicReference<ConcurrentHashMap<PathClass, Geometry>> theseLocalGeoms = new AtomicReference<>(compartmentGeoms);
        setEstNumTasks(nx*ny);
        int progAmount = 1;
        progressValue.set(BigInteger.valueOf(0));
        logger.info("Computing tile & compartment intersections (2/4)");
        if(progressBar!=null) {
            Platform.runLater(() -> {
                progressLabel.setText("Computing tile & compartment intersections (2/4)");
            });
        }

        boolean finalByColumn = byColumn;
        boolean finalByRow = byRow;
//		ConcurrentHashMap<PathClass, Geometry> theseLocalGeoms = compartmentGeoms;
        int finalXMin = xMin;
        int finalYMin = yMin;
        forkJoinPool.submit(()->
            IntStream.range(0, nx).parallel().forEach(xi -> {
                if (isCancelled.get()) {
                    throw new CancellationException();
                }
                int x = finalXMin + xi * w - overlap;
//				A very hacky way to consolidate the code into 1 loop.
//				Atomic Reference doesn't behave when getting hit by multiple streams setting potentially different values for each stream...
                ConcurrentHashMap<PathClass, Geometry> outerLocalGeoms = finalByColumn ? finalLocalGeoms.getOrDefault(xi, compartmentGeoms) : compartmentGeoms;

                IntStream.range(0, ny).parallel().forEach(yi -> {
                    int y = finalYMin + yi * h - overlap;
                    ConcurrentHashMap<PathClass, Geometry> innerLocalGeoms = finalByRow ? finalLocalGeoms.getOrDefault(yi, compartmentGeoms) : outerLocalGeoms;

                    // Create the tile
                    var rect = GeometryTools.createRectangle(x, y, w + overlap * 2, h + overlap * 2);

//						Map<PathClass, ROI> thisIntersectMap = new HashMap<>();
                    Map<PathClass, ROI> thisIntersectMap = innerLocalGeoms.entrySet().parallelStream()
                            .filter(m -> m.getValue() != null && !m.getValue().isEmpty())
                            .map(m -> Map.entry(m.getKey(), intersect(rect, m.getValue())))
                            .filter(m -> m.getValue() != null && !m.getValue().isEmpty())
                            .map(m -> Map.entry(m.getKey(), GeometryTools.geometryToROI(m.getValue(), plane)))
                            .collect(Collectors.toMap(
                                    m -> m.getKey(),
                                    m -> m.getValue()
                            ));
                    // handle empty/null thisIntersectMaps?
                    if (!thisIntersectMap.isEmpty()) {
                        PathObject tileObj = PathObjects.createTileObject(GeometryTools.geometryToROI(rect, plane));
                        String tileName = String.format("Tile-r%dc%d_x%dy%d", yi, xi, x, y);
                        tileObj.setName(tileName);
//							logger.info(thisIntersectMap.toString());
                        logger.debug("Creating {}", tileName);
                        tileIntersectROIs.put(tileObj, thisIntersectMap);
                    }
                    incrementProgress(progAmount);
                });
            })
        ).get();

        return tileIntersectROIs;

        // If there was an exception, the tile will be null
//			if (tileROIs.size() < tileGeometries.size()) {
//				logger.warn("Tiles lost during tiling: {}", tileGeometries.size() - tileROIs.size());
//				logger.warn("You may be able to avoid tiling errors by calling 'Simplify shape' on any complex annotations first.");
//			}
//
//			// Remove any empty/non-area tiles
//			return tileROIs.stream()
//					.filter(t -> !t.isEmpty() && t.isArea())
//					.collect(Collectors.toList());
    }

    public List<PathObject> checkIntersectObjects(PathObject parentObj,
                                                Collection<PathObject> testObjs){
        Geometry parentGeom = parentObj.getROI().getGeometry();
        List<PathObject> interObjs = testObjs.parallelStream().filter(obj -> obj.getROI().getGeometry().intersects(parentGeom))
                .collect(Collectors.toList());
        return interObjs;
    }

    public Map<PathClass, ROI> combineAnnotationsIntoMap(
            List<PathObject> compartmentObjs,
            Geometry combinedExcludeGeom,
            ROI combinedExcludeROI,
            Boolean doAdjust) throws ExecutionException, InterruptedException {
        // TODO: is there a simpler way to get this Map<PathClass, List<ROI>> from one stream, and then combine if there are multiple entries in the List<ROI>?
        // combine compartments into path objects
        Map<PathClass, ROI> combCompartmentROIMap = new HashMap<>();
        for(PathClass c : compartments){
            List<ROI> theseCROIs = forkJoinPool.submit(()->compartmentObjs.parallelStream()
                    .filter(p-> c == p.getPathClass())
                    .map(p -> p.getROI())
                    .collect(Collectors.toList())).get();
            logger.info("Combining all {} annotations...", c.toString());
            logger.info(theseCROIs.toString());
            ROI combinedC;
            if (theseCROIs.size() == 1) {
                combinedC = theseCROIs.get(0);
            } else{
                combinedC = RoiTools.union(theseCROIs);
            }
            if(combinedC!=null && !combinedC.isEmpty()){
                if (doAdjust && combinedC.getGeometry().intersects(combinedExcludeGeom)) {
                    combinedC = RoiTools.combineROIs(combinedC, combinedExcludeROI, RoiTools.CombineOp.SUBTRACT);
                    if(combinedC.isEmpty()){
                        continue;
                    }
                }
                // Do not remake the pathObject
                combCompartmentROIMap.put(c, combinedC);
            }
        }
        return combCompartmentROIMap;
    }

    public CompletableFuture<Void>  TileRecalcCompartmentsAndScores() throws RuntimeException {
        int tileSize = 0;
        if(params.get("tileSize")!=null){
            if(params.get("tileSize") instanceof Double){
                tileSize = ((Double) params.get("tileSize")).intValue();
            } else {
                tileSize = (int) params.get("tileSize");
            }
        }
        return TileRecalcCompartmentsAndScores(
                ignoreClasses,
//                targets,
                compartments,
                roiClasses,
                sourceType,
//                (double) params.get("downsample"),
                tileSize,
                (boolean) params.get("tileUnitIsMicrons"),
                (TileOption) params.get("tileOption"),
//                selectedObjects,
                numThreads);
    }

    public CompletableFuture<Void> TileRecalcCompartmentsAndScores(
            Set<PathClass> ignoreClasses,
//            Map<ColorTransforms.ColorTransform, Double> targets,
            Set<PathClass> compartments,
            Set<PathClass> rois,
            Class<? extends PathObject> sourceType,
//            double downsample,
            int tileSize,
            boolean tileUnitIsMicrons,
            TileOption tileOption,
//            Collection<PathObject> selectedObjs,
            int numThreads
    ) throws RuntimeException{

        if (numThreads <= 0)
            numThreads = 1;

        setupNewForkJoinPool(numThreads);
        Integer progAmount = 1;
        progressValue.set(BigInteger.valueOf(0));

        // Used for placing child objects inside ROI
        AtomicReference<Boolean> doAdjust = new AtomicReference<>(false);
        ROI combinedExcludeROI;
        Geometry combinedExcludeGeom;

        PathObjectHierarchy hierarchy = bImageData.getHierarchy();
        var pathObjs = hierarchy.getObjects(null, PathObject.class);
        ImageServer<BufferedImage> server = bImageData.getServer();
//      Getting tile size if the unit is microns
        int tileSizeX = tileSize;
        int tileSizeY = tileSize;
        if(tileUnitIsMicrons) {
            PixelCalibration pixCal = server.getPixelCalibration();
//          get microns per pixel in x and y from current image data
            double MPPx = pixCal.getPixelWidthMicrons();
            double MPPy = pixCal.getPixelHeightMicrons();
            double MPPavg = pixCal.getAveragedPixelSizeMicrons();
            if(MPPx != Double.NaN && MPPy != Double.NaN) {
                tileSizeX = (int) Math.ceil(tileSize / MPPx);
                tileSizeY = (int) Math.ceil(tileSize / MPPy);
            } else if (MPPavg != Double.NaN){
                tileSizeX = (int) Math.ceil(tileSize / MPPavg);
                tileSizeY = (int) Math.ceil(tileSize / MPPavg);
            } else {
                logger.warn("Could not find micron per pixel value for image, defaulting to tileSize in pixels...");
            }
        }

        CompletableFuture<Void> result = null;
        try {
            List<ROI> allIgnoreROIs = forkJoinPool.submit(() -> hierarchy.getAnnotationObjects().parallelStream()
                    .filter(p -> p.getPathClass() != null && ignoreClasses.contains(p.getPathClass()))
                    .map(p -> p.getROI())
                    .collect(Collectors.toList())).get();
            List<PathObject> compartmentObjs = forkJoinPool.submit(() -> pathObjs.parallelStream().filter(p -> p.getPathClass() != null && compartments.contains(p.getPathClass()) && p.getClass() == sourceType)
                    .collect(Collectors.toList())).get();

            logger.info(allIgnoreROIs.toString());
            combinedExcludeROI = RoiTools.union(allIgnoreROIs);
            if (combinedExcludeROI != null && !combinedExcludeROI.isEmpty()) {
                doAdjust.set(true);
                combinedExcludeGeom = combinedExcludeROI.getGeometry();
            } else {
                doAdjust.set(false);
                combinedExcludeGeom = null;
            }

            Map<PathObject, Map<PathObject, Map<PathClass, ROI>>> allTileIntersectROIs = new HashMap<>();
//            TODO: implement this in getTargetScoresForROIs()
            Map<PathClass, ROI> combCompartmentROIMap = combineAnnotationsIntoMap(
                    compartmentObjs, combinedExcludeGeom, combinedExcludeROI, doAdjust.get());

            if (combCompartmentROIMap.isEmpty()) {
                logger.error("Combining compartments resulted in null? Check compartment annotations/sources...");
                return null;
            }

            switch (tileOption) {
                case ROI_AND_IMAGE: {
                    logger.info("Computing tiles for full image [ROI_AND_IMAGE]");
                    Rectangle2D bounds = new Rectangle2D.Double();
                    bounds.setFrame(0.0, 0.0, server.getWidth(), server.getHeight());

//				    Uses default image plane, will not work for timeseries or z slices
                    Map<PathObject, Map<PathClass, ROI>> tileIntersectROIs = computeTiledROIsForCompartments(
                            bounds,
                            combCompartmentROIMap,
                            ImmutableDimension.getInstance(tileSizeX, tileSizeY),
                            true,
                            0);
                    if(tileIntersectROIs!=null) {
                        allTileIntersectROIs.put(hierarchy.getRootObject(), tileIntersectROIs);
                    }
                }
                case ROI_ONLY: {
                    logger.info("Computing tiles for ROIs [ROI_ONLY || ROI_AND_IMAGE]");
                    AtomicInteger totalROIs = new AtomicInteger(0);
                    AtomicInteger roiNumber = new AtomicInteger(1);
//                    Might be unnecessary since I subtract the ignore regions from the compartments before this
                    List<PathObject> roiObjs = pathObjs.parallelStream().filter(p -> p.getPathClass() != null && rois.contains(p.getPathClass()) && p.hasROI())
                            .map(f -> {
                                // Record null/none values for compartments not within ROI
//											logger.info(f.getName());
//                                      if (f.getName() == null || f.getName().isBlank() || f.getName().matches("^ROI_[0-9]+$")) {
                                if (f.getName() == null || f.getName().isBlank()) {
                                    f.setName(f.getPathClass().toString() + "_" + roiNumber.getAndIncrement());
                                }
                                PathObject adjpathObj = null;
                                ROI adjpathObjROI = f.getROI();
                                if (doAdjust.get() && adjpathObjROI.getGeometry().intersects(combinedExcludeGeom)) {
                                    adjpathObjROI = RoiTools.combineROIs(adjpathObjROI, combinedExcludeROI, RoiTools.CombineOp.SUBTRACT);
                                    if (adjpathObjROI.isEmpty()) {
                                        logger.info("ROI {} is now empty, skipping scoring metrics...", f.getName());
                                        return null;
                                    } else {
                                        logger.info("Adjusting ROI {} based on ignore annotations...", f.getName());

                                        adjpathObj = PathObjects.createAnnotationObject(adjpathObjROI, f.getPathClass());
//													Do I need to set the name again?
                                        adjpathObj.setName(f.getName());
                                        hierarchy.addPathObject(adjpathObj);
//													bImageData.getHierarchy().addPathObjectBelowParent(pathObj.getParent(), adjpathObj, true);
                                        hierarchy.removeObject(f, true);
                                    }
                                } else {
                                    adjpathObj = f;
                                }

                                // this might work but does it scale for lots of ROIs?
                                setEstNumTasks(totalROIs.incrementAndGet());
                                return adjpathObj;
                            })
                            .filter(p -> p != null)
                            .collect(Collectors.toList());
//                  Do this part sequentially so that you don't mess up the tiling
                    int i = 1;
                    for (PathObject roiObj : roiObjs){
                        logger.info("Computing tiles for roi ({}/{})", i, totalROIs.get());
                        Rectangle2D bounds = new Rectangle2D.Double();
                        ROI roi = roiObj.getROI();
                        bounds.setFrame(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
                        Map<PathClass, ROI> simpleComparmentROIMap = new HashMap<>();
//                      Make ROI intersected compartments here?
                        for(Map.Entry<PathClass, ROI> c : combCompartmentROIMap.entrySet()){
                            ROI compInterROI = RoiTools.combineROIs(c.getValue(), roi, RoiTools.CombineOp.INTERSECT);
                            if(!compInterROI.isEmpty()){
                                logger.info("Adding simple intersection... compartment {}", c.getKey());
                                simpleComparmentROIMap.put(c.getKey(), compInterROI);
                            } else{
                                logger.info("No intersection with {} compartment for ROI... skipping.", c.getKey().toString());
                            }
                        }
//				        Uses default image plane, will not work for timeseries or z slices
                        Map<PathObject, Map<PathClass, ROI>> tileIntersectROIs = computeTiledROIsForCompartments(
                                bounds,
                                simpleComparmentROIMap,
                                ImmutableDimension.getInstance(tileSizeX, tileSizeY),
                                true,
                                0);
                        if(tileIntersectROIs!=null) {
//                      Check that tile objects are within ROI object
                            logger.info("checking intersect: tiles before {}", tileIntersectROIs.keySet().size());
                            List<PathObject> includeTiles = checkIntersectObjects(roiObj, tileIntersectROIs.keySet());
                            tileIntersectROIs.keySet().retainAll(includeTiles);
                            logger.info("checking intersect: tiles after {}", tileIntersectROIs.keySet().size());
                            allTileIntersectROIs.put(roiObj, tileIntersectROIs);
                        }
                        i++;
                    }
                    break;
                }
                case SELECTED_OBJS: {
                    Collection<PathObject> selectedObjs = pathObjs.parallelStream().filter(p -> hierarchy.getSelectionModel().isSelected(p))
                            .collect(Collectors.toList());
////                    Collection<PathObject> selectedObjs = getSelectedObjects();
                    if (selectedObjs.isEmpty() || selectedObjs == null || selectedObjs.size() == 0){
                        logger.error("No objects selected! Cannot compute tiles");
                        return null;
                    }
                    logger.info(selectedObjs.toString());
//                    hierarchy.removeObjects(selectedObjs, false);

//                  Do this part sequentially so that you don't mess up the tiling
                    int i = 1;
                    int totalSelectedObjs = selectedObjs.size();
                    logger.info("Total selected objects: {}", totalSelectedObjs);
                    for (PathObject sObj : selectedObjs){
                        hierarchy.updateObject(sObj, true);
                        logger.info("Computing tiles for selected objects ({}/{})", i, totalSelectedObjs);
                        Rectangle2D bounds = new Rectangle2D.Double();
                        ROI roi = sObj.getROI();
                        bounds.setFrame(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
                        Map<PathClass, ROI> simpleComparmentROIMap = new HashMap<>();
//                      Make ROI intersected compartments here?
                        for(Map.Entry<PathClass, ROI> c : combCompartmentROIMap.entrySet()){
                            ROI compInterROI = RoiTools.combineROIs(c.getValue(), roi, RoiTools.CombineOp.INTERSECT);
                            if(!compInterROI.isEmpty()){
                                simpleComparmentROIMap.put(c.getKey(), compInterROI);
                            } else{
                                logger.info("No intersection with {} compartment for Selected Object... skipping.", c.getKey().toString());
                            }
                        }
//				        Uses default image plane, will not work for timeseries or z slices
                        Map<PathObject, Map<PathClass, ROI>> tileIntersectROIs = computeTiledROIsForCompartments(
                                bounds,
                                simpleComparmentROIMap,
                                ImmutableDimension.getInstance(tileSizeX, tileSizeY),
                                true,
                                0);
                        if(tileIntersectROIs!=null) {
//                            Check that tile objects are within ROI object
                            logger.info("checking intersect: tiles before {}", tileIntersectROIs.keySet().size());
                            List<PathObject> includeTiles = checkIntersectObjects(sObj, tileIntersectROIs.keySet());
                            tileIntersectROIs.keySet().retainAll(includeTiles);
                            logger.info("checking intersect: tiles after {}", tileIntersectROIs.keySet().size());
                            allTileIntersectROIs.put(sObj, tileIntersectROIs);
                        }
                        i++;
                    }
                    break;
                }
                case FULL_IMAGE: {
                    logger.info("Computing tiles for full image [FULL_IMAGE]");
                    Rectangle2D bounds = new Rectangle2D.Double();
                    bounds.setFrame(0.0, 0.0, server.getWidth(), server.getHeight());

//				    Uses default image plane, will not work for timeseries or z slices
                    Map<PathObject, Map<PathClass, ROI>> tileIntersectROIs = computeTiledROIsForCompartments(
                            bounds,
                            combCompartmentROIMap,
                            ImmutableDimension.getInstance(tileSizeX, tileSizeY),
                            true,
                            0);
                    if(tileIntersectROIs!=null) {
                        allTileIntersectROIs.put(hierarchy.getRootObject(), tileIntersectROIs);
                    }
                    break;
                }
            }

//          Make pathObjects out of intersections and add to tileObj as children
            progressValue.set(BigInteger.valueOf(0));
            logger.info("Creating tile objects... (3/4)");
            if (progressBar != null) {
                Platform.runLater(() -> {
                    progressLabel.setText("Creating tile objects... (3/4)");
                    progressBar.setProgress(-1);
                });
            }
//		    I don't like how this blocks the main thread and GUI, can freeze up the UI easily.... should wrap inside the completable future?
            ConcurrentHashMap<PathObject, Map<PathClass, ROI>> combinedTileIntersectROIs = new ConcurrentHashMap<>();
            int totalParents = allTileIntersectROIs.entrySet().size();
            int p = 1;
            for (Map.Entry<PathObject, Map<PathObject, Map<PathClass, ROI>>> tEntry : allTileIntersectROIs.entrySet()){
                PathObject parentObject = tEntry.getKey();
                ConcurrentHashMap<PathObject, Map<PathClass, ROI>> tileIntersectROIs = new ConcurrentHashMap<>(tEntry.getValue());
                forkJoinPool.submit(() -> tileIntersectROIs.entrySet().parallelStream()
                        .forEach(tileM -> {
                            List<PathObject> intersectChildren = null;
                            try {
                                intersectChildren = forkJoinPool.submit(() -> tileM.getValue().entrySet().parallelStream()
                                        .map(m -> PathObjects.createTileObject(m.getValue(), m.getKey(), null))
                                        .collect(Collectors.toList())).get();
                            } catch (InterruptedException | ExecutionException e) {
                                logger.error("Could not create tile children....");
                                throw new RuntimeException(e);
                            }
//                            hierarchy.updateObject(parentObject, true);
                            PathObject tileObj = tileM.getKey();
                            tileObj.addPathObjects(intersectChildren);
                            hierarchy.addPathObjectBelowParent(parentObject, tileObj, true);
//                            parentObject.addPathObject(tileObj);
                        })
                ).get();
                logger.info("{} of {} completed with creating tiles", p, totalParents);
                combinedTileIntersectROIs.putAll(tileIntersectROIs);
                if (progressBar != null) {
                    int finalP = p;
                    Platform.runLater(() -> {
                        progressBar.setProgress(finalP/totalParents);
                    });
                }
                p++;
            }

            logger.info("Scoring tiles... (4/4)");

            if(progressBar!=null) {
                Platform.runLater(() -> {
                    progressLabel.setText("Scoring tiles... (4/4)");
                });
            }

            setEstNumTasks(combinedTileIntersectROIs.size());
            result = CompletableFuture.runAsync(() -> combinedTileIntersectROIs.entrySet().parallelStream().forEach(tileM ->{
                                if (isCancelled.get()) {
                                    throw new CancellationException();
                                }
                                try {
                                    getTargetsIntensityScores_OpenCV(server, tileM.getKey(), tileM.getValue());
                                } catch (IOException ex) {
                                    logger.warn(ex.toString());
                                }
                                incrementProgress(progAmount);
                            })
                            ,forkJoinPool)
                    .thenRun(() -> {
                        logger.info("Completed scoring Tiles!");
                        if(progressBar!=null) {
                            Platform.runLater(() -> {
                                progressLabel.setText("Completed scoring Tiles!");
                                progressBar.setProgress(1.0);
                                for(Control button : controlListToToggle){
                                    button.setDisable(false);
                                }
                                for (MenuItem menuItem : menuItemListToToggle) {
                                    menuItem.setDisable(false);
                                }
                            });
                        }
                        fireHierarchyUpdate(hierarchy);
                    })
                    .exceptionally(ex -> {
                        if (ex.getCause() instanceof CancellationException){
                            logger.warn("Run cancelled?");
                        } else {
                            ex.printStackTrace();
                        }
//							logger.warn(Arrays.toString(ex.getStackTrace()));
                        logger.warn("TileRecalcCompartmentsAndScores: " + ex);
                        fireHierarchyUpdate(hierarchy);
                        return null;
                    });
        } catch (ExecutionException | InterruptedException ex) {
            throw new RuntimeException(ex);
        } finally {
//				no effect on commonPool
            forkJoinPool.shutdown();
        }
        return result;
    }


    public CompletableFuture<Void>  TMARecalcCompartmentsAndScores() throws RuntimeException {
        return TMARecalcCompartmentsAndScores(ignoreClasses, targets, compartments, sourceType, (double) params.get("downsample"), numThreads);
    }

    // Exclude regions and add regions that weren't segmented well. Allows for manual adjustment of compartmentalization before scoring targets.
    public CompletableFuture<Void> TMARecalcCompartmentsAndScores(Set<PathClass> ignoreClasses,
                                                                  Map<ColorTransforms.ColorTransform, Double> targets,
                                                                  Set<PathClass> compartments,
                                                                  Class<? extends PathObject> sourceType,
                                                                  double downsample,
                                                                  int numThreads
    ) throws RuntimeException {

        // Adjust each compartment by subtracting the exclude region and adding the corresponding compartment adjustments
        // Iterate through compartments/detections to recreate them if adjustments were made
        // Calculate AQUA metrics for each target
        CompletableFuture<Void> result = null;
        AtomicReference<Boolean> doAdjust = new AtomicReference<>(false);
        // Combine exclude regions, but do not create a new merged object
        ROI combinedExcludeROI;
        Geometry combinedExcludeGeom;

        logger.info("Updating existing compartments with any new annotations, calcuating AQUA metrics...");

//			ImageData<BufferedImage> imageData = qupath.getImageData();
        PathObjectHierarchy hierarchy = bImageData.getHierarchy();

        TMAGrid tmaGrid = hierarchy.getTMAGrid();
        if(tmaGrid==null){
            logger.error("TMA grid is null, de-array TMA before scoring!");
//				throw new RuntimeException();
            return result;
        }
        List<TMACoreObject> tmaCores = tmaGrid.getTMACoreList();
        // an estimate if there are the same amount of compartments per TMA spot....
        // could just try and use the amount of tasks queued... doesn't work in time before forkJoinPool is done with submit/invoke
//			setEstNumTasks((int) tmaCores.size() * compartments.size());
        Integer progAmount = 1;
        progressValue.set(BigInteger.valueOf(0));
        if (numThreads <= 0)
            numThreads = 1;

        setupNewForkJoinPool(numThreads);

        ImageServer<BufferedImage> server = bImageData.getServer();

        try {
            // These operations block the GUI threads.... can't really replace them though because I need to collect the annotations before starting
            // maybe can rewrite this whole block as a sequential task to submit to the pool?
            List<ROI> allIgnoreROIs = forkJoinPool.submit(() -> hierarchy.getAnnotationObjects().parallelStream()
                    .filter(p -> p.getPathClass() != null && ignoreClasses.contains(p.getPathClass()))
                    .map(p -> p.getROI())
                    .collect(Collectors.toList())).get();
            List<PathObject> tmaCoreChildren = Collections.synchronizedList(forkJoinPool.submit(() -> tmaCores.parallelStream()
                    .filter(core -> !core.isMissing())
                    .flatMap(core -> core.getChildObjects().stream())
                    .collect(Collectors.toList())).get());

            if(tmaCoreChildren.size() < 1){
                logger.error("No valid TMA cores found or");
                logger.error("Compartments and annotations must be inserted into TMA hierarchy before scoring!");
                return result;
            }

//				Would be faster to associate each ignore annotation into the TMACore hierarchy, and then just subtract the ignore annotation for each core.
//				But the insert hierarchy doesn't work unless there is a sufficient overlap exists between children and parent objects...
            // Need to make sure that all TMA cores have their annotations inserted into the hierarchy or else the getChildObjects() will miss annotations...
            // insertHierarchy can miss annotations that are outside of TMA core parent. Maybe there is a way to use the missing annotations
            // and check if any TMA cores x,y contain that annotation (or vice versa).
            setEstNumTasks(tmaCoreChildren.size());
            logger.info(allIgnoreROIs.toString());
//				combinedExcludeROI = combinePathObjs(allIgnoreAnnotations, false);
            combinedExcludeROI = RoiTools.union(allIgnoreROIs);
            if (combinedExcludeROI != null && !combinedExcludeROI.isEmpty()) {
                doAdjust.set(true);
                combinedExcludeGeom = combinedExcludeROI.getGeometry();
            } else{
                doAdjust.set(false);
                combinedExcludeGeom = null;
            }

//				Ugly, better to make this a forkJoinTask or runnable without lambda?
//				https://stackoverflow.com/questions/23320407/how-to-cancel-java-8-completable-future
            result = CompletableFuture.runAsync(() -> tmaCoreChildren.parallelStream().forEach(pathObj -> {
                                if (isCancelled.get()) {
                                    throw new CancellationException();
                                }
//					ignore the objects that are unclassified/PathClass == null
                                if (pathObj.getPathClass() != null && compartments.contains(pathObj.getPathClass()) && pathObj.getClass() == sourceType) {
                                    PathObject adjpathObj;
                                    ROI adjpathObjROI = pathObj.getROI();
                                    // is not very efficient as the excluded areas may only be in certain TMA spots....
                                    // getting an excluded ROI for each TMA core is not as parallellizable and does not work if the excluded region does not fit within the QuPath hierarchy
                                    // not very efficient use of if statements when these variables are set before the parallelStream starts
                                    // case switch inside parallelStream? does this work?
                                    if (doAdjust.get() && adjpathObjROI.getGeometry().intersects(combinedExcludeGeom)) {
                                        adjpathObjROI = RoiTools.combineROIs(adjpathObjROI, combinedExcludeROI, RoiTools.CombineOp.SUBTRACT);
                                        if (adjpathObjROI.isEmpty()) {
                                            logger.info("{} compartment is now empty, skipping AQUA metrics...", pathObj.getPathClass().toString());
                                            //removeObject(detection, true);
                                            return;
                                        } else {
                                            if (sourceType == PathDetectionObject.class){
                                                logger.info("Adjusting {} compartment [Detection] based on new/ignore annotations...", pathObj.getPathClass().toString());
                                                adjpathObj = PathObjects.createDetectionObject(adjpathObjROI, pathObj.getPathClass());
                                            } else {
                                                logger.info("Adjusting {} compartment [Annotation] based on new/ignore annotations...", pathObj.getPathClass().toString());
                                                adjpathObj = PathObjects.createAnnotationObject(adjpathObjROI, pathObj.getPathClass());
                                            }
                                            hierarchy.addPathObject(adjpathObj);
                                            bImageData.getHierarchy().addPathObjectBelowParent(pathObj.getParent(), adjpathObj, true);
                                            hierarchy.removeObject(pathObj, true);
                                        }
                                    } else {
                                        adjpathObj = pathObj;
                                    }
                                    // Calculate AQUA scoring metrics for new compartment detections for all targets
                                    try {
                                        getTargetsIntensityScores_OpenCV(server, adjpathObj);
//											getTargetsIntensityScores(adjpathObj);
                                    } catch (IOException ex) {
                                        logger.warn(ex.toString());
                                    }
                                }
                                incrementProgress(progAmount);
                            }),
                            forkJoinPool)
                    .thenRun(() -> {
                        if(progressBar!=null) {
                            Platform.runLater(() -> {
                                progressLabel.setText("Completed scoring TMA compartments!");
                                progressBar.setProgress(1.0);
                                for(Control button : controlListToToggle) {
                                    button.setDisable(false);
                                }
                                for (MenuItem menuItem : menuItemListToToggle) {
                                    menuItem.setDisable(false);
                                }
                            });
                        }
                        fireHierarchyUpdate(bImageData.getHierarchy());
                    })
                    .exceptionally(ex -> {
                        if (ex.getCause() instanceof CancellationException){
                            logger.warn("Run cancelled?");
                        } else {
                            ex.printStackTrace();
                        }
//							logger.warn(Arrays.toString(ex.getStackTrace()));
                        logger.warn("TMARecalcCompartmentsAndScores: " + ex);
                        fireHierarchyUpdate(bImageData.getHierarchy());
                        return null;
                    });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
//				no effect on commonPool
            forkJoinPool.shutdown();
        }
        return result;
    }

    public boolean getTargetsIntensityScores_OpenCV(ImageServer<BufferedImage> server, PathObject pathObject) throws IOException {
        return getTargetsIntensityScores_OpenCV(server, pathObject, null);
    }

    public boolean getTargetsIntensityScores_OpenCV(ImageServer<BufferedImage> server, PathObject parentObject, Map<PathClass, ROI> intersectROIs) throws IOException {
        //get params required
        double downsample;
        int tileSize = -1;
        boolean tileUnitIsMicrons = false;
        boolean verboseMeasures;
        boolean rescaleScore;
        boolean normalizeScore;
        double maxFloatValue;
        try {
            downsample = (double) params.get("downsample");
            if(parentObject.isTile()) {
                tileSize = (int) params.get("tileSize");
                tileUnitIsMicrons = (boolean) params.get("tileUnitIsMicrons");
            }
            verboseMeasures = (boolean) params.get("verboseMeasures");
            rescaleScore = (boolean) params.get("rescaleScore");
            normalizeScore = (boolean) params.get("normalizeScore");
            maxFloatValue = (double) params.get("maxFloatValue");
        } catch (Exception ex) {
//				ex.printStackTrace();
            throw new RuntimeException(ex);
        }
//      Specify measurements if verbose or not

        Set<Measurements> theseMeasurements;
        if(!verboseMeasures){
            theseMeasurements = Collections.singleton(Measurements.MEAN);
        }else{
            theseMeasurements = measurements;
        }

        return getTargetsIntensityScores_OpenCV(server, parentObject, intersectROIs, targets, cellCompartments, theseMeasurements,
                downsample, tileSize, tileUnitIsMicrons, rescaleScore, normalizeScore, maxFloatValue);

    }



    public boolean getTargetsIntensityScores_OpenCV(ImageServer<BufferedImage> server,
                                                    PathObject parentObject,
                                                    Map<PathClass, ROI> intersectROIs,
                                                    Map<ColorTransforms.ColorTransform, Double> targets,
                                                    Collection<Compartments> cellCompartments,
                                                    Collection<Measurements> measurements,
                                                    double downsample, int tileSize, boolean tileUnitIsMicrons,
                                                    boolean rescaleScore, boolean normalizeScore,
                                                    double maxFloatValue) throws IOException {
//			It would be nice to close the server after use, but doing this also closes the main server across all threads....
        try {
            // Determine amount to downsample
//				var server = imageData.getServer();
            if(intersectROIs==null){
                intersectROIs = new ConcurrentHashMap<>(Map.ofEntries(
                        Map.entry(parentObject.getPathClass(), parentObject.getROI())
                )
                );
            }
//				String className = pathObject.getPathClass().toString();
            PixelCalibration pc = server.getPixelCalibration();
            PixelType pixType = server.getPixelType();
            int bitDepth = server.getPixelType().getBitsPerPixel();
            double mppSq = pc.getPixelHeightMicrons() * pc.getPixelWidthMicrons();
            //    println 'Squarred MPP: ' + mppSq.toString();

            // get the parent pathObject measurement list
            MeasurementList measList = parentObject.getMeasurementList();
            // add basic metadata
            if(tileUnitIsMicrons){
                measList.putMeasurement("Tile Size (um)", tileSize);
            } else{
                measList.putMeasurement("Tile Size (px)", tileSize);
            }
            measList.putMeasurement("MPPx", pc.getPixelWidthMicrons());
            measList.putMeasurement("MPPy", pc.getPixelHeightMicrons());
            measList.putMeasurement("MPP^2", mppSq);
            measList.putMeasurement("Channel bitdepth", bitDepth);
            int bitDepthVal = (int) Math.pow(2, bitDepth);

            if (downsample <= 0) {
                logger.warn("Effective downsample must be > 0 (requested value {})", downsample);
                downsample = 1.0;
            }

            measList.putMeasurement("downsample", downsample);

            // Add shape measurements and setup stat trackers
            Map<PathClass, Map<String, DescriptiveStatistics>> allStats = new ConcurrentHashMap<>();
            Map<PathClass, Map<String, String>> measNames = new ConcurrentHashMap<>();
            for(Map.Entry<PathClass, ROI> interROI : intersectROIs.entrySet()) {
                double annotationArea = interROI.getValue().getArea();
                PathClass pathClass = interROI.getKey();
                String className = pathClass.toString();
                measList.putMeasurement(className + " area px", annotationArea);
                measList.putMeasurement(className + " area um^2", annotationArea * mppSq);
                allStats.put(pathClass, new ConcurrentHashMap<>());
                measNames.put(pathClass, new ConcurrentHashMap<>());
                for (Map.Entry<ColorTransforms.ColorTransform, Double> tar : targets.entrySet()) {
                    String targetName = tar.getKey().toString();
                    allStats.get(pathClass).put(targetName, new DescriptiveStatistics(DescriptiveStatistics.INFINITE_WINDOW));
                    String measName = targetName + " Intensity in " + className;
                    measNames.get(pathClass).put(targetName, measName);
                    logger.debug("Scoring {} in {}", targetName, className);
                }
            }

            if (parentObject instanceof PathCellObject) {
                PathCellObject cell = (PathCellObject) parentObject;
                if (cell.getROI() == null) {
                    logger.warn("ROI is null, cannot get intensity scores...");
                    return false;
                }

                // Get bounds
                RegionRequest region = RegionRequest.createInstance(server.getPath(), downsample, cell.getROI());
                BufferedImage img = server.readBufferedImage(region);
                if (img == null) {
                    logger.error("Could not read image - unable to compute intensity features for {}", parentObject);
                    return false;
                }

                // Create mask ROIs for cell and nucleus
                // If we just have 1 pixel, we want to use it so that the mean/min/max measurements are valid (even if nothing else is)
                byte[] cellBytes = null;
                if (img.getWidth() * img.getHeight() > 1) {
                    BufferedImage imgMask = BufferedImageTools.createROIMask(img.getWidth(), img.getHeight(), cell.getROI(), region);
                    cellBytes = ((DataBufferByte) imgMask.getRaster().getDataBuffer()).getData();
                }
                byte[] nucBytes = null;
                if (cell.getNucleusROI() != null) {
                    if (img.getWidth() * img.getHeight() > 1) {
                        BufferedImage imgMask = BufferedImageTools.createROIMask(img.getWidth(), img.getHeight(), cell.getNucleusROI(), region);
                        nucBytes = ((DataBufferByte) imgMask.getRaster().getDataBuffer()).getData();
                    }
                }

//					not implemented yet
                return false;

//					For mean, median, stdev, etc.
//					measureCells_OpenCV(nucBytes, cellBytes, Map.of(1.0, cell), channels, cellCompartments, measurements);
            } else {
//                TODO: This code will be slow for many intersections, very big ROIs, and quantifying many targets
//                 how to parallelize these operations in a thread safe way?
//                 Should it even be considered when this function is already being executed as a parallelStream?
//                 Should I be creating several deep copies of image servers for the thread pool and then closing them after use?
//                 How does the imageServer handle being hit with multiple readBufferedImage requests?
//                 How does the PixelClassifier classes perform region requests so quickly?
//                 Maybe there is a way to refactor this whole thing into a set of ImageOps? and then process the stats? --> This is the way
                for(Map.Entry<PathClass, ROI> interROI: intersectROIs.entrySet()) {
                    ROI roi = interROI.getValue();
                    PathClass pathClass = interROI.getKey();
                    String className = pathClass.toString();
                    if (roi == null) {
                        logger.warn("ROI is null, cannot get intensity scores...");
                        return false;
                    }
                    // Create tiled ROIs, if required
                    ImmutableDimension sizePreferred = ImmutableDimension.getInstance((int) (3000 * downsample), (int) (3000 * downsample));
                    Collection<? extends ROI> rois = QiimiaQuantBackend.computeTiledROIs(roi, sizePreferred, sizePreferred, false, 0);
                    if (rois.size() > 1)
                        logger.info("Splitting {} into {} tiles for intensity measurements", roi, rois.size());

//                    Maybe should:
//                    1) iterate through rois to create binary masks. Store region requests?
//                    2) apply binary masks to image region(s) with an ImageOp bitwise operation the target channels simultaneously
//                    3) add all non-zero values into each target DescriptiveStats object for each roi

                    for (ROI pathROI : rois) {

                        if (Thread.currentThread().isInterrupted()) {
                            logger.warn("Measurement skipped - thread interrupted!");
                            return false;
                        }

                        // Get bounds
                        RegionRequest region = RegionRequest.createInstance(server.getPath(), downsample, pathROI);
//                        This is likely a very slow step across threads if only one image server resource is used....
                        BufferedImage img = server.readBufferedImage(region);
                        if (img == null) {
                            logger.error("Could not read image - unable to compute intensity feature");
                            return false;
                        }

                        // Create mask ROI if necessary
                        // If we just have 1 pixel, we want to use it so that the mean/min/max measurements are valid (even if nothing else is)
                        byte[] maskBytes = null;
                        if (img.getWidth() * img.getHeight() > 1) {
                            BufferedImage imgMask = BufferedImageTools.createROIMask(img.getWidth(), img.getHeight(), pathROI, region);
                            maskBytes = ((DataBufferByte) imgMask.getRaster().getDataBuffer()).getData();
                        }

                        int w = img.getWidth();
                        int h = img.getHeight();
                        float[] pixels = null;

//                        should apply the same mask to all target channels simultaneously. How to combine transforms into a Mat?
                        for (Map.Entry<ColorTransforms.ColorTransform, Double> tar : targets.entrySet()) {
                            ColorTransforms.ColorTransform transform = tar.getKey();
//								double expTime = tar.getValue();
                            DescriptiveStatistics thisStats = allStats.get(pathClass).get(transform.toString());

                            // Transform the pixels
                            pixels = transform.extractChannel(server, img, pixels);

                            // Create the simple image
                            SimpleModifiableImage pixelImage = SimpleImages.createFloatImage(pixels, w, h);

//								assert pixelImage.getHeight() * pixelImage.getWidth() == pixels.length;

                            // Apply any arbitrary mask and add values to stats
                            if (maskBytes != null) {
                                for (int i = 0; i < pixels.length; i++) {
                                    if (maskBytes[i] == (byte) 0) {
//											pixelImage.setValue(i % w, i / w, Float.NaN);
                                        continue;
                                    }
                                    thisStats.addValue((double) pixelImage.getValue(i % w, i / w));
                                }
                                allStats.get(pathClass).put(transform.toString(), thisStats);
                            }
                        }
                    }
                    addMeasurements_OpenCV(allStats.get(pathClass), measNames.get(pathClass), parentObject, measurements);
                }
            }

            for(Map.Entry<PathClass, Map<String, String>> measEntry : measNames.entrySet()) {
                String className = measEntry.getKey().toString();
                Map<String, String> theseMeas = measEntry.getValue();
                for (Map.Entry<ColorTransforms.ColorTransform, Double> tar : targets.entrySet()) {
                    String targetName = tar.getKey().toString();
                    double exposure_time = tar.getValue();
                    String measName = theseMeas.get(targetName);
                    double targetMean = measList.getMeasurementValue(measName + ": Mean");
                    // double sumInt = targetMean*annotationArea;
                    // measList.putMeasurement(targetName+' in '+className+' Sum Intensity', sumInt);
                    // Debugging, would load from available metadata
                    if (exposure_time == 0.0 || exposure_time < 0) {
                        exposure_time = 1000;
                        measList.putMeasurement(targetName + " exposure time (ms)", 0);
                    } else {
                        measList.putMeasurement(targetName + " exposure time (ms)", exposure_time);
                    }

                    // double MeanI_S = targetMean/(exposure_time/1000)
                    // measList.putMeasurement(targetName+' in '+className+' Mean I/[exp time (s)]', MeanI_S);
                    // Intensity/(um^2*sec)
                    // double QIF_area = targetMean/mppSq;
                    // measList.putMeasurement(targetName+' in '+className+' Sum I/um^2', QIF_area);
                    //if pixelType float, skip [vetra Polaris data]
//                    if (pixType.isFloatingPoint()) {
//                        double QIF_areaS = (targetMean / mppSq);
//                        measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
//                    } else
                    if (rescaleScore && !normalizeScore) {
                        //assumes score has already been normalized, but turned into an unsigned int datatype for image manipulation
                        //using bitdepth and maxFloatValue to rescale
                        double rescaleFactor = (maxFloatValue / bitDepthVal);
                        double QIF_areaS = (targetMean / mppSq) * rescaleFactor;
                        measList.putMeasurement("Rescale factor", rescaleFactor);
                        measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
                    } else if (normalizeScore) {
                        double QIF_areaS = (targetMean / mppSq) / (bitDepthVal * exposure_time / 1000);
                        measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2*[exp time (s)]*[2^bitDepth])", QIF_areaS);
                    } else {
                        // no normalization
                        double QIF_areaS = (targetMean / mppSq);
                        measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
                    }
                    //    double totalPx = server.getHeight()*server.getWidth();
                    //    println 'Total pixels: '+ totalPx.toString();
                    //    double QIF_areaPercent = targetMean*annotationArea/(100*annotationArea/totalPx);
                    //    measList.putMeasurement(targetName+' in '+className+' Sum I/(Compartment % Area)', QIF_areaPercent);
                    //    double QIF_areaPercentS = QIF_areaPercent/(exposure_time);
                    //    measList.putMeasurement(targetName+' in '+className+' Sum I/([Compartment % Area]*[exp time (ms)])', QIF_areaPercentS);
                }
            }

            // Lock any measurements that require it
//				if (pathObject instanceof PathAnnotationObject)
//					((PathAnnotationObject) pathObject).setLocked(true);
//				else if (pathObject instanceof TMACoreObject)
//					((TMACoreObject) pathObject).setLocked(true);
//			clean up vars?
//			server.close();
//			pathObject.getMeasurementList().close();
            measList.close();
//			server = null;
            parentObject = null;
            measList = null;
            measNames = null;
            allStats = null;
//            System.gc();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
//        finally {
////			clean up vars?
////			targets = null;
////			measurements = null;
////			cellCompartments = null;
//            System.gc();
//        }
        return true;
    }

//		private static void measureCells_OpenCV(
//				byte[] nucBytes, byte[] cellBytes,
//				Map<? extends Number, ? extends PathObject> pathObjects,
//				Map<String, ColorTransform> channels,
//				Collection<Compartments> cellCompartments,
//				Collection<Measurements> measurements) {
//
//			var array = mapToArray(pathObjects);
//			int width = ipNuclei.getWidth();
//			int height = ipNuclei.getHeight();
//			ImageProcessor ipMembrane = new FloatProcessor(width, height);
//			ImageProcessor ipCytoplasm = ipCells.duplicate();
//			for (int y = 0; y < height; y++) {
//				for (int x = 0; x < width; x++) {
//					float cell = ipCells.getf(x, y);
//					float nuc = ipNuclei.getf(x, y);
//					if (nuc != 0f)
//						ipCytoplasm.setf(x, y, 0f);
//					if (cell == 0f)
//						continue;
//					// Check 4-neighbours to decide if we're at the membrane
//					if ((y >= 1 && ipCells.getf(x, y-1) != cell) ||
//							(y < height-1 && ipCells.getf(x, y+1) != cell) ||
//							(x >= 1 && ipCells.getf(x-1, y) != cell) ||
//							(x < width-1 && ipCells.getf(x+1, y) != cell))
//						ipMembrane.setf(x, y, cell);
//				}
//			}
//
//			var imgNuclei = new PixelImageIJ(ipNuclei);
//			var imgCells = new PixelImageIJ(ipCells);
//			var imgCytoplasm = new PixelImageIJ(ipCytoplasm);
//			var imgMembrane = new PixelImageIJ(ipMembrane);
//
//			for (var entry : channels.entrySet()) {
//				var img = new PixelImageIJ(entry.getValue());
//				if (cellCompartments.contains(Compartments.NUCLEUS))
//					measureObjects(img, imgNuclei, array, entry.getKey().trim() + ": " + "Nucleus", measurements);
//				if (cellCompartments.contains(Compartments.CYTOPLASM))
//					measureObjects(img, imgCytoplasm, array, entry.getKey().trim() + ": " + "Cytoplasm", measurements);
//				if (cellCompartments.contains(Compartments.MEMBRANE))
//					measureObjects(img, imgMembrane, array, entry.getKey().trim() + ": " + "Membrane", measurements);
//				if (cellCompartments.contains(Compartments.CELL))
//					measureObjects(img, imgCells, array, entry.getKey().trim() + ": " + "Cell", measurements);
//			}
//
//		}

    public void addMeasurements_OpenCV(DescriptiveStatistics allStats,
                                       String allMeasNames,
                                       PathObject pathObject,
                                       Collection<Measurements> measurements){
        // Add measurements
        if (pathObject == null)
            return;
        try (var ml = pathObject.getMeasurementList()) {
            for (var m : measurements) {
                ml.putMeasurement(allMeasNames + ": " + m.getMeasurementName(), m.getMeasurement(allStats));
            }
        }
    }


    public void addMeasurements_OpenCV(Map<String, DescriptiveStatistics> allStats,
                                       Map<String, String> allMeasNames,
                                       PathObject pathObject,
                                       Collection<Measurements> measurements){
        // Add measurements
        if (pathObject == null)
            return;
        for (Map.Entry<String, DescriptiveStatistics> stats : allStats.entrySet()) {
            try (var ml = pathObject.getMeasurementList()) {
                for (var m : measurements) {
                    ml.putMeasurement(allMeasNames.get(stats.getKey()) + ": " + m.getMeasurementName(), m.getMeasurement(stats.getValue()));
                }
            }
        }
    }

    private void measureObject_OpenCV(SimpleModifiableImage img,  byte[] maskBytes,
                                      PathObject pathObject, String baseName,
                                      Collection<Measurements> measurements){
        DescriptiveStatistics stats = null;
        stats = measureObject_OpenCV(img, maskBytes, stats);
        addMeasurements_OpenCV(stats, baseName, pathObject, measurements);
    }
    private DescriptiveStatistics measureObject_OpenCV(SimpleModifiableImage img,
                                                       byte[] maskBytes,
                                                       DescriptiveStatistics stats) {

        // Initialize stats
        if(stats == null) {
            stats = new DescriptiveStatistics(DescriptiveStatistics.INFINITE_WINDOW);
        }

        int w = img.getWidth();
        int h = img.getHeight();
        // Apply any arbitrary mask and compute stats
        if (maskBytes != null) {
            for (int i = 0; i < w * h; i++) {
                if (maskBytes[i] == (byte) 0) {
//						img.setValue(i % w, i / w, Float.NaN);
                    continue;
                }
                stats.addValue((double) img.getValue(i % w, i / w));
            }
        }
        return stats;
    }


//    public void getTargetsIntensityScores(PathObject pathObject) throws IOException {
//        //get params required
//        double downsample;
//        boolean rescaleScore;
//        boolean normalizeScore;
//        double maxFloatValue;
//        try {
//            downsample = (double) params.get("downsample");
//            rescaleScore = (boolean) params.get("rescaleScore");
//            normalizeScore = (boolean) params.get("normalizeScore");
//            maxFloatValue = (double) params.get("maxFloatValue");
//        } catch (Exception ex) {
////				ex.printStackTrace();
//            throw new RuntimeException(ex);
//        }
//        getTargetsIntensityScores(bImageData, pathObject, targets, cellCompartments, measurements, downsample, rescaleScore, normalizeScore, maxFloatValue);
//    }
//
//    public void getTargetsIntensityScores(ImageData<BufferedImage> imageData, PathObject pathObject,
//                                          Map<ColorTransforms.ColorTransform, Double> targets,
//                                          Collection<Compartments> cellCompartments,
//                                          Collection<Measurements> measurements,
//                                          double downsample, boolean rescaleScore, boolean normalizeScore,
//                                          double maxFloatValue) throws IOException {
//
//        try {
//            // Convert to binary mask Mat
//            ROI roi = pathObject.getROI();
//            String className = pathObject.getPathClass().toString();
//            ImageServer<BufferedImage> server = imageData.getServer();
//
//            int pad = (int) Math.ceil(downsample * 2);
//            RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, roi)
//                    .pad2D(pad, pad)
//                    .intersect2D(0, 0, server.getWidth(), server.getHeight());
//
//            PathImage<ImagePlus> pathImage = IJTools.convertToImagePlus(server, request);
//            //			ImagePlus imp = pathImage.getImage();
//
//            PixelCalibration pc = server.getPixelCalibration();
//            PixelType pixType = server.getPixelType();
//            int bitDepth = server.getPixelType().getBitsPerPixel();
//            double mppSq = pc.getPixelHeightMicrons() * pc.getPixelWidthMicrons();
//            //    println 'Squarred MPP: ' + mppSq.toString();
//
//            // Use mean intensity to calculate AQUA score as (mean intensity)/(MPP^2 * exposure_time)
//            MeasurementList measList = pathObject.getMeasurementList();
//
//            // Add shape measurements
//            double annotationArea = pathObject.getROI().getArea();
//            measList.putMeasurement(className + " area px", annotationArea);
//            measList.putMeasurement(className + " area um^2", annotationArea * mppSq);
//            measList.putMeasurement("MPP^2", mppSq);
//            measList.putMeasurement("Channel bitdepth", bitDepth);
//            int bitDepthVal = (int) Math.pow(2, bitDepth);
//            //			int bitDepthVal = (int) Math.pow(2, 16);
//
//            Map<String, ImageProcessor> channels = new LinkedHashMap<>();
//            Map<String, String> measNames = new LinkedHashMap<>();
//
//            //Don't like this, is there a way to convert ROI to a binary mask OpenCV Mat directly??
//            //Using ImageJ to create a binary mask [0,1] of ROI
//            ByteProcessor bpCell = new ByteProcessor(request.getWidth(), request.getHeight());
//            bpCell.setValue(1.0);
//            Roi roiIJ = IJTools.convertToIJRoi(roi, pathImage);
//            bpCell.fill(roiIJ);
//
//            //Might not be the best performance. Would like to recode to use only OpenCV_core Mats and pointers/mask indexing.
//            for (Map.Entry<ColorTransforms.ColorTransform, Double> tar : targets.entrySet()) {
//                ColorTransforms.ColorTransform targetTransform = tar.getKey();
//                String targetName = targetTransform.toString();
//                ImageProcessor ipChannel = OpenCVTools.matToImageProcessor(ImageOps.buildImageDataOp(targetTransform).apply(imageData, request));
//                String measName = targetName + " Intensity in " + className;
//                measNames.put(targetName, measName);
//                channels.put(measName, ipChannel);
//                logger.info("Scoring {} in {}", targetName, className);
//            }
//
//            if (pathObject instanceof PathCellObject) {
//                PathCellObject cell = (PathCellObject) pathObject;
//                ByteProcessor bpNucleus = new ByteProcessor(request.getWidth(), request.getHeight());
//                if (cell.getNucleusROI() != null) {
//                    bpNucleus.setValue(1.0);
//                    Roi roiNucleusIJ = IJTools.convertToIJRoi(cell.getNucleusROI(), pathImage);
//                    bpNucleus.fill(roiNucleusIJ);
//                }
//                //For mean, median, stdev, etc.
//                measureCells(bpNucleus, bpCell, Map.of(1.0, cell), channels, cellCompartments, measurements);
//                //Calculate sum intensity in compartment
//                //        measureObjSumInt(img, imgLabels, new PathObject[] {pathObject}, targetName);
//            } else {
//                var imgLabels = new PixelImageIJ(bpCell);
//                for (Map.Entry<String, ImageProcessor> entry : channels.entrySet()) {
//                    var img = new PixelImageIJ(entry.getValue());
//                    //For mean, median, stdev, etc.
//                    measureObjects(img, imgLabels, new PathObject[]{pathObject}, entry.getKey(), measurements);
//                    //Calculate sum intensity in compartment
//                    //            measureObjSumInt(img, imgLabels, new PathObject[] {pathObject}, targetName);
//                }
//            }
//
//
//            for (Map.Entry<ColorTransforms.ColorTransform, Double> tar : targets.entrySet()) {
//                String targetName = tar.getKey().toString();
//                double exposure_time = tar.getValue();
//                String measName = measNames.get(targetName);
//                double targetMean = measList.getMeasurementValue(measName + ": Mean");
//                // double sumInt = targetMean*annotationArea;
//                // measList.putMeasurement(targetName+' in '+className+' Sum Intensity', sumInt);
//                // Debugging, would load from available metadata
//                if (exposure_time == 0.0 || exposure_time < 0) {
//                    exposure_time = 1000;
//                    measList.putMeasurement(targetName + " exposure time (ms)", 0);
//                } else {
//                    measList.putMeasurement(targetName + " exposure time (ms)", exposure_time);
//                }
//
//                // double MeanI_S = targetMean/(exposure_time/1000)
//                // measList.putMeasurement(targetName+' in '+className+' Mean I/[exp time (s)]', MeanI_S);
//                // Intensity/(um^2*sec)
//                // double QIF_area = targetMean/mppSq;
//                // measList.putMeasurement(targetName+' in '+className+' Sum I/um^2', QIF_area);
//                //if pixelType float, skip [vetra Polaris data]
//                if (pixType.isFloatingPoint()) {
//                    double QIF_areaS = (targetMean / mppSq);
//                    measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
//                } else if (rescaleScore && !normalizeScore) {
//                    //assumes score has already been normalized, but turned into an unsigned int datatype for image manipulation
//                    //using bitdepth and maxFloatValue to rescale
//                    double rescaleFactor = (maxFloatValue / bitDepthVal);
//                    double QIF_areaS = (targetMean / mppSq) * rescaleFactor;
//                    measList.putMeasurement("Rescale factor", rescaleFactor);
//                    measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
//                } else if (normalizeScore) {
//                    double QIF_areaS = (targetMean / mppSq) / (bitDepthVal * exposure_time / 1000);
//                    measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2*[exp time (s)]*[2^bitDepth])", QIF_areaS);
//                } else {
//                    // no normalization
//                    double QIF_areaS = (targetMean / mppSq);
//                    measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2)", QIF_areaS);
//                }
//                //    double totalPx = server.getHeight()*server.getWidth();
//                //    println 'Total pixels: '+ totalPx.toString();
//                //    double QIF_areaPercent = targetMean*annotationArea/(100*annotationArea/totalPx);
//                //    measList.putMeasurement(targetName+' in '+className+' Sum I/(Compartment % Area)', QIF_areaPercent);
//                //    double QIF_areaPercentS = QIF_areaPercent/(exposure_time);
//                //    measList.putMeasurement(targetName+' in '+className+' Sum I/([Compartment % Area]*[exp time (ms)])', QIF_areaPercentS);
//            }
//
////				clean up vars?
////				server.close();
//            measList.close();
//            server = null;
//            pathImage = null;
//            channels = null;
//            request = null;
//            measList = null;
//            measNames = null;
//            roiIJ = null;
//            bpCell = null;
//            roi = null;
//            System.gc();
//
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        } finally {
//
////				clean up vars?
//            imageData = null;
//            targets = null;
//            measurements = null;
//            cellCompartments = null;
//            System.gc();
//
//        }
//    }


    /**
     * Make cell measurements based on labelled images.
     * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
     *
     * @param ipNuclei labelled image representing nuclei
     * @param ipCells labelled image representing cells
     * @param pathObjects cell objects mapped to integer values in the labelled images
     * @param channels channels to measure, mapped to the name to incorporate into the measurements for that channel
     */
    private static void measureCells(
            ImageProcessor ipNuclei, ImageProcessor ipCells,
            Map<? extends Number, ? extends PathObject> pathObjects,
            Map<String, ImageProcessor> channels,
            Collection<Compartments> cellCompartments,
            Collection<Measurements> measurements) {

        var array = mapToArray(pathObjects);
        int width = ipNuclei.getWidth();
        int height = ipNuclei.getHeight();
        ImageProcessor ipMembrane = new FloatProcessor(width, height);
        ImageProcessor ipCytoplasm = ipCells.duplicate();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float cell = ipCells.getf(x, y);
                float nuc = ipNuclei.getf(x, y);
                if (nuc != 0f)
                    ipCytoplasm.setf(x, y, 0f);
                if (cell == 0f)
                    continue;
                // Check 4-neighbours to decide if we're at the membrane
                if ((y >= 1 && ipCells.getf(x, y-1) != cell) ||
                        (y < height-1 && ipCells.getf(x, y+1) != cell) ||
                        (x >= 1 && ipCells.getf(x-1, y) != cell) ||
                        (x < width-1 && ipCells.getf(x+1, y) != cell))
                    ipMembrane.setf(x, y, cell);
            }
        }

        var imgNuclei = new PixelImageIJ(ipNuclei);
        var imgCells = new PixelImageIJ(ipCells);
        var imgCytoplasm = new PixelImageIJ(ipCytoplasm);
        var imgMembrane = new PixelImageIJ(ipMembrane);

        for (var entry : channels.entrySet()) {
            var img = new PixelImageIJ(entry.getValue());
            if (cellCompartments.contains(Compartments.NUCLEUS))
                measureObjects(img, imgNuclei, array, entry.getKey().trim() + ": " + "Nucleus", measurements);
            if (cellCompartments.contains(Compartments.CYTOPLASM))
                measureObjects(img, imgCytoplasm, array, entry.getKey().trim() + ": " + "Cytoplasm", measurements);
            if (cellCompartments.contains(Compartments.MEMBRANE))
                measureObjects(img, imgMembrane, array, entry.getKey().trim() + ": " + "Membrane", measurements);
            if (cellCompartments.contains(Compartments.CELL))
                measureObjects(img, imgCells, array, entry.getKey().trim() + ": " + "Cell", measurements);
        }

    }


    private static PathObject[] mapToArray(Map<? extends Number, ? extends PathObject> pathObjects) {
        Number[] labels = new Number[pathObjects.size()];
        int n = 0;
        long maxLabel = 0L;
        int invalidLabels = 0;
        for (var label : pathObjects.keySet()) {
            long lab = label.longValue();
            if (lab < 0 || lab != label.doubleValue() || lab >= Integer.MAX_VALUE) {
                invalidLabels++;
            } else {
                labels[n] = label;
                maxLabel = Math.max(lab, maxLabel);
                n++;
            }
        }

        if (invalidLabels > 0) {
            logger.warn("Only {}/{} labels are integer values >= 0 and < Integer.MAX_VALUE, the rest will be discarded!",
                    n, pathObjects.size());
        }

        PathObject[] array = new PathObject[n];
        for (var label : labels) {
            array[label.intValue()-1] = pathObjects.get(label);
        }
        return array;
    }


    /**
     * Measure objects within the specified image, adding them to the corresponding measurement lists.
     * @param img intensity values to measure
     * @param imgLabels labels corresponding to objects
     * @param pathObjects array of objects, where array index for an object is 1 less than the label in imgLabels
     * @param baseName base name to include when adding measurements (e.g. the channel name)
     */
    private static void measureObjects(
            SimpleImage img, SimpleImage imgLabels,
            PathObject[] pathObjects,
            String baseName,
            Collection<Measurements> measurements) {

        // Initialize stats
        int n = pathObjects.length;
        DescriptiveStatistics[] allStats = new DescriptiveStatistics[n];
        for (int i = 0; i < n; i++)
            allStats[i] = new DescriptiveStatistics(DescriptiveStatistics.INFINITE_WINDOW);

        // Compute statistics
        int width = img.getWidth();
        int height = img.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int label = (int)imgLabels.getValue(x, y);
                if (label <= 0 || label > n)
                    continue;
                float val = img.getValue(x, y);
                allStats[label-1].addValue(val);
            }
        }

        // Add measurements
        for (int i = 0; i < n; i++) {
            var pathObject = pathObjects[i];
            if (pathObject == null)
                continue;
            var stats = allStats[i];
            try (var ml = pathObject.getMeasurementList()) {
                for (var m : measurements) {
                    ml.putMeasurement(baseName + ": " + m.getMeasurementName(), m.getMeasurement(stats));
                }
            }
        }
    }

//  This might be useful for handling other csv metadata, but honestly should just use a dataframe library if that is really necessary
//	import java.io.BufferedReader;
//	import java.io.FileReader;
//
//	def readCSVtoDF(String csvpath, String indexName) {
//		// Create BufferedReader
//		BufferedReader csvReader = new BufferedReader(new FileReader(csvpath));
//		Map<String, ArrayList<String>> dataframe = new LinkedHashMap<String, ArrayList<String>>();
//		header = csvReader.readLine();
//		//    header = "test,test1,test2";
//		ArrayList<String> headerContent = new ArrayList<String>(header.split(",").toList());
//		//    println headerContent
//		int index = headerContent.indexOf(indexName);
//		//    println index
//		//    println headerContent[index]
//		int r = 0;
//		useRowNumbers = false;
//		if (index == -1) {
//			prinln String.format('Header does not contain %s! Defaulting to using row numbers...', indexName)
//			useRowNumbers = true;
//		}
//		dataframe.put('Header', headerContent);
//		while ((row = csvReader.readLine()) != null) {
//			//        println row
//			ArrayList<String> rowContent = new ArrayList<String>(row.split(",").toList());
//			if (useRowNumbers) {
//				dataframe.put(r, rowContent);
//				r += 1;
//			} else {
//				rowName = rowContent[index];
//				int j = 1;
//				while (true) {
//					if (dataframe.containsKey(rowName)) {
//						println String.format('rowName %s is duplicated! Resolving by appending integer...', rowName);
//						rowName = String.format('%1$s_%2$x', rowContent[index], j);
//						j += 1;
//					} else {
//						break;
//					}
//				}
//				dataframe.put(rowName, rowContent);
//			}
//		}
//		//    println dataframe;
//		return dataframe;
//	}
}