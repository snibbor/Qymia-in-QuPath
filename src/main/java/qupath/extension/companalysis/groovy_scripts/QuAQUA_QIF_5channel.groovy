package qupath.extension.companalysis.groovy_scripts

import ij.plugin.filter.ThresholdToSelection;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageConverter;
import ij.process.AutoThresholder;
import ij.ImagePlus;


import qupath.imagej.processing.RoiLabeling;
import qupath.imagej.processing.SimpleThresholding;
import qupath.imagej.tools.IJTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.GeometryTools;
import org.locationtech.jts.geom.Geometry;
import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.imagej.tools.PixelImageIJ;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.analysis.images.SimpleImage;

import static qupath.lib.gui.scripting.QPEx.*;

// Need to add color deconvolution for multispectral images? Unless all channels are saved as separate image in stack
def getIJPlusStack(pathImage){
    def imp = pathImage.getImage();
    println('Dimensions of image... [W, H, N, S, F]');
    println(imp.getDimensions().toString());
    if (imp.getNChannels() == 1){
        println('Assuming image is an RGB composite fluorescent image');
        println('Getting RGB stack (each color is a fluorophore)');
//        def imgConv = new ImageConverter(imp);
//        imgConv.convertToRGBStack();
        new ImageConverter(imp).convertToRGBStack();
        println('Verifying conversion... [W, H, N, S, F]');
        println(imp.getDimensions().toString());
        def nChannels = imp.getNChannels();
    }
    // else pass
    return imp;
}

def calculateThreshold(fp, String method, double val1){
    if (method.toLowerCase().contains('auto')) {
//        def all_auto_methods = new AutoThresholder().getMethods().toString()
//        all_auto_methods.toLowerCase().contains(auto_method)
        def auto_method = method.split('_', 2)[-1]
        println 'Autothreshold method';
        println method;
        println auto_method;
        int[] histo = fp.getHistogram();
        def threshold = new AutoThresholder().getThreshold(auto_method, histo);
        return threshold
    } else if (method.toLowerCase() == 'mean'){
        println 'Mean method';
        def stats = fp.getStatistics();
        double threshold = stats.mean + val1 * stats.stdDev;
        return threshold
    } else if (method.toLowerCase() == 'value'){
        println 'Value method';
        return val1
    }
}

def genThresholdMask(Object server, Object channels, double downsample, double blur_sigma, String method, double threshVal, String compartment, Boolean keepLargestOnly, double minPixels){
    def request = RegionRequest.createInstance(server, downsample);
    def pathImage = IJTools.convertToImagePlus(server, request);
    def imp = getIJPlusStack(pathImage);
    // imp.show();
    
    if (method.toLowerCase().contains('auto')) {
	    // Have to convert 16bit and 32bit to 8 bit for auto thresholding masks
	    new ImageConverter(imp).convertToGray8();
	    // imp.show();
	}
    
//    println imp.getClass(); 
//    println pathImage.getClass();

    // Create a binary image for the output
    def bp = new ByteProcessor(imp.getWidth(), imp.getHeight());
    
    // Threshold channel and add all results for channels
    // Needs to be an 8bit or 16bit image for AutoThresholder
    if (channels == 'all'){
        for (c = 1; c <= imp.getNChannels(); c++){
            def fp = imp.getStack().getProcessor(c).convertToFloatProcessor();
            fp.blurGaussian(blur_sigma);
            def threshold = calculateThreshold(fp, method, threshVal)
            println 'Channel ' + c.toString();
            println 'Threshold value';
            println threshold;
            // Sets value above threshold to 255
            def bpChannel = SimpleThresholding.thresholdAbove(fp, threshold as float);
            bp.copyBits(bpChannel, 0, 0, Blitter.MAX);
        }
    } else if (channels instanceof int[]) {
        for (int c : channels){
                def fp = imp.getStack().getProcessor(c).convertToFloatProcessor();
                fp.blurGaussian(blur_sigma);
                def threshold = calculateThreshold(fp, method, threshVal)
                println 'Channel ' + c.toString();
                println 'Threshold value';
                println threshold;
                // Sets value above threshold to 255
                def bpChannel = SimpleThresholding.thresholdAbove(fp, threshold as float);
                bp.copyBits(bpChannel, 0, 0, Blitter.MAX);
            }
    } else if (channels instanceof Integer) {
        def c = channels;
        def fp = imp.getStack().getProcessor(c).convertToFloatProcessor();
        println fp;
        fp.blurGaussian(blur_sigma);
        def threshold = calculateThreshold(fp, method, threshVal)
        println 'Channel ' + c.toString();
        println 'Threshold value';
        println threshold;
        // Sets value above threshold to 255
        def bpChannel = SimpleThresholding.thresholdAbove(fp, threshold as float);
        bp.copyBits(bpChannel, 0, 0, Blitter.COPY);
    } else {
        throw new Exception('Channels argument is not supported! Use "all", int, or int[]')
        return
    }
    
    // Create an annotation
    RoiLabeling.removeSmallAreas(bp, minPixels, true);
    bp.setThreshold(254, Double.POSITIVE_INFINITY, ImageProcessor.NO_LUT_UPDATE);
    ROI roi;
    if (keepLargestOnly) {
        def ipLabels = RoiLabeling.labelImage(bp, 0.5f, false);
        def roisIJ = RoiLabeling.labelsToConnectedROIs(ipLabels, ipLabels.getStatistics().max as int);
        def rois = roisIJ.collect {r -> IJTools.convertToROI(r, pathImage)}
        rois.sort(Comparator.comparingDouble(r -> r.getArea()));
        roi = rois[-1];
    } else {
        def roiIJ = new ThresholdToSelection().convert(bp);
        roi = IJTools.convertToROI(roiIJ, pathImage);
    }
    
    def detection = PathObjects.createDetectionObject(roi, getPathClass(compartment));
    return detection;
//    def annotation = PathObjects.createAnnotationObject(roi, getPathClass(compartment));
//    annotation.setPathClass(getPathClass(compartment));
//    annotation.setLocked(true);
//    return annotation;
}

def measureObjSumInt(SimpleImage img, SimpleImage imgLabels, PathObject[] pathObjects, String targetName) {
    // Initialize array for summed pixel intensity inside label region
    int n = pathObjects.length;
    double[] allSums = new double[n];
    
    // Compute sum
    int width = img.getWidth();
    int height = img.getHeight();
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int label = (int)imgLabels.getValue(x, y);
            if (label <= 0 || label > n){
                continue;
            }
            float val = img.getValue(x, y);
            allSums[label-1] += val;
//            println allSums[label-1];
        }
    }

    // Add measurements
    for (int i = 0; i < n; i++) {
        var pathObject = pathObjects[i];
        if (pathObject == null){
            continue;
        }
        double sumInt = allSums[i];
        try (var ml = pathObject.getMeasurementList()) {
            ml.putMeasurement(targetName + " in " +pathObject.getPathClass()+ " Sum Intensity", sumInt);
        }
    }
}

// AQUA inside each intersecting compartment of ROI only
//    Map<String, Integer> targets = new LinkedHashMap<>();
def getTargetAQUAScoresForROIs(ImageServer<BufferedImage> server,
                            List<String> rois, 
                            Map<String, Integer> targets,
                            List<String> compartments,
                            double downsample,
                            Object metadata,
                            int scaleBitDepthTo,
                            Boolean microscopeNormalizedMeas
                            ) {
                            
    List<ObjectMeasurements.Measurements> measurements = ObjectMeasurements.Measurements.values() as List;
    List<ObjectMeasurements.Compartments> cellCompartments = ObjectMeasurements.Compartments.values() as List; // Won't mean much if they aren't cells...
    
    // Add annotations to heirarchy connected to ROI
    
    // Remove uninformative classes (Tissue)
    compartments.remove('Tissue')
    
    // Used for placing child objects inside ROI
    var imageData = getCurrentImageData()
    
    int roiNumber = 1;
    
    for (annotation in getAnnotationObjects()){
        if (rois.contains(annotation.getPathClass().toString()) && annotation.hasROI()){
            
            // Record null/none values for compartments not within ROI
            ROI roi = annotation.getROI();
            println annotation.getName();
            if (annotation.getName()==null||annotation.getName().isBlank()){
                annotation.setName('ROI_'+roiNumber);
                roiNumber+=1;
            }
    
            // For each compartment, get new pathObject that intersects ROI.
            // Compartments are stored in detection objects and are the only things within detection objects for now, 
            // however checking if the object is actually a compartment is best practice
        //    List<PathObject> compInterObjs = new ArrayList<>();
            // Get ROI measurement list
            
            for (compObj in getDetectionObjects()) {
                if (compartments.contains(compObj.getPathClass().toString())){
                    compInterROI = RoiTools.combineROIs(compObj.getROI(), roi, RoiTools.CombineOp.INTERSECT);
                    compInterDet = PathObjects.createAnnotationObject(compInterROI, compObj.getPathClass());
                    
                    if (!compInterROI.isEmpty()) {
                        println String.format("ROI contains %s compartment! Calculating AQUA metrics within ROI.", compObj.getPathClass().toString());
                        // For debugging, maybe helps with visualization
                        // Add object as a child of the ROI
//                        addObject(compInterDet);
                        compInterDet.setName(annotation.getName()+' ('+compObj.getPathClass().toString()+')');
                        imageData.getHierarchy().addPathObjectBelowParent(annotation, compInterDet, true);

                        println String.format('Got %s intersection with ROI', compObj.getPathClass().toString());
                        
                        // Quantify metrics/AQUA for each target in each intersecting compartment
                        for (var tar: targets.entrySet()){
                            getTargetAQUA(server, compInterDet, 
                                tar.getValue(), tar.getKey(), 
                                measurements, cellCompartments, 
                                downsample, metadata, scaleBitDepthTo, microscopeNormalizedMeas
                                );
                        }
                        
                    // Put these target/compartment measurments on the measurement list of the ROI for export
//                    compInterObjs.add(compInterDet)
                     } else {
                         println String.format("No intersection with %s compartment for ROI... skipping.", compObj.getPathClass().toString()); 
                     }
                }
            }
        }
    }
} 


// Exclude regions and add regions that weren't segmented well. Allows for manual adjustment of compartmentalization before AQUA.
def recalcCompartmentsAndAQUA(ImageServer<BufferedImage> server, 
                            List<String> ignoreClasses, 
                            Map<String, Integer> targets,
                            List<String> compartments,
                            double downsample,
                            Object metadata,
                            int scaleBitDepthTo,
                            Boolean microscopeNormalizedMeas
                            ) {
    // Get exclude region annotations based on ignoreClasses list
//    excludeAnnots = getAnnotationObjects().findAll{ignoreClasses.contains(it.getPathClass().toString())};

    // Combine exclude regions, but do not create a new merged object
    // Inspired by mergeAnnotations()
    // Get all the selected annotations with area and combine them into ROIs
    ROI combinedExcludeROI = null;
    Map<String, ROI> combinedCompROIMap = new HashMap<String, ROI>();
    // initialize compROIMap using loop through compartments string
    for (String comp: compartments) {
        combinedCompROIMap.put(comp, null);
    }
//    println combinedCompROIMap;
    // Simultaneously get exclude region annotations based on ignoreClasses or compartments list
    // and make combinedROIs for next step
    var allAnnotations = getAnnotationObjects();
    println 'Preparing new annotations to update detection compartments...';
    if(!allAnnotations.isEmpty()) {
        for (PathObject annotation : allAnnotations) {
            if (annotation.hasROI() && (annotation.getROI().isArea() || annotation.getROI().isPoint())) {
                if(ignoreClasses.contains(annotation.getPathClass().toString())) {
                    //Merge all ignore annotations into a single ROI
                    if (combinedExcludeROI == null) {
                        combinedExcludeROI = annotation.getROI();//.duplicate();
                    } else if (combinedExcludeROI.getImagePlane().equals(annotation.getROI().getImagePlane())) {
                        combinedExcludeROI = RoiTools.combineROIs(combinedExcludeROI, annotation.getROI(), RoiTools.CombineOp.ADD);
                    } else {
                        println "Cannot merge annotations across different image planes!";
                        continue;
                    }
                } else if(compartments.contains(annotation.getPathClass().toString())) {
                    //Merge corresponding compartment annotations into ROIs
                    compName = annotation.getPathClass().toString();
                    combinedCompROI = combinedCompROIMap.get(compName);
                    if(combinedCompROI == null) {
                        combinedCompROI = annotation.getROI();//.duplicate();
                        combinedCompROIMap.replace(compName, combinedCompROI);
                    } else if(combinedCompROI.getImagePlane().equals(annotation.getROI().getImagePlane())) {
                        combinedCompROI = RoiTools.combineROIs(combinedCompROI, annotation.getROI(), RoiTools.CombineOp.ADD);
                        combinedCompROIMap.replace(compName, combinedCompROI);
                    } else {
                        println "Cannot merge annotations across different image planes!";
                        continue;
                    }
                }
            }
        }
    }
//    println combinedCompROIMap;
        
    // Adjust each compartment by subtracting the exclude region and adding the corresponding compartment adjustments
//    List<PathObject> oldDetections = new ArrayList<PathObject>();
    // Iterate through compartments/detections to recreate them if adjustments were made
    // Calculate AQUA metrics for each target
    Boolean doAdjust = false;
    // Will set these as private variables in AQUAProcess class
    List<ObjectMeasurements.Measurements> measurements = ObjectMeasurements.Measurements.values() as List;
    List<ObjectMeasurements.Compartments> cellCompartments = ObjectMeasurements.Compartments.values() as List; // Won't mean much if they aren't cells...
    List<String> missingCompartments = new ArrayList<String>(compartments);
    println 'Updating existing detection compartments with new annotations, calcuating AQUA metrics...';
    for (PathObject detection : getDetectionObjects()){
        if (compartments.contains(detection.getPathClass().toString())) {
            // May not be the best way, but this will help keep track of which compartments were modified
            // and which were missing. 
            // At the end, will check if any of these missingCompartments were added recently by the new annotations, then add & score them.
            if(missingCompartments.contains(detection.getPathClass().toString())){
                missingCompartments.remove(detection.getPathClass().toString());
            }
            // oldDetections.add(detection);
            combinedCompROI = combinedCompROIMap.get(detection.getPathClass().toString());
            adjDetectionROI = detection.getROI();
            if(combinedExcludeROI != null) {
                adjDetectionROI = RoiTools.combineROIs(adjDetectionROI, combinedExcludeROI, RoiTools.CombineOp.SUBTRACT);
                doAdjust = true
            }
            if(combinedCompROI != null) {
                adjDetectionROI = RoiTools.combineROIs(adjDetectionROI, combinedCompROI, RoiTools.CombineOp.ADD);
                doAdjust = true
            }
            // Remove oldDetection, add adjDetection
            // This may not be efficient for many detections... Combining these to list array/hashmap and then adding them all at once could be better
//            println adjDetectionROI.isEmpty()
            if (doAdjust && adjDetectionROI.isEmpty()) {
                println String.format('Detection %s compartment is now empty, skipping AQUA metrics...', detection.getPathClass().toString());
                removeObject(detection, true);
                doAdjust = false;
                continue;
            } else if (doAdjust) {
                println String.format('Adjusting %s compartment based on new annotations...', detection.getPathClass().toString());
                adjDetection = PathObjects.createDetectionObject(adjDetectionROI, detection.getPathClass());
                addObject(adjDetection);
                removeObject(detection, true);
                doAdjust = false;
            } else {
                adjDetection = detection;
            }
                
            // Calculate AQUA scoring metrics for new compartment detections for all targets
            for (var tar: targets.entrySet()) {           
                getTargetAQUA(
                    server, adjDetection, 
                    tar.getValue(), tar.getKey(), 
                    measurements, cellCompartments, 
                    downsample, metadata, scaleBitDepthTo, microscopeNormalizedMeas
                    );
            }
        }
    }
    
    println 'Checking if any compartments were added by new annotations...';
    println missingCompartments;
    if (!missingCompartments.isEmpty()){
        for (String comp : missingCompartments) {
            combinedCompROI = combinedCompROIMap.get(comp);
            if (combinedCompROI != null){
                println String.format('Adding new %s annotations and calculating AQUA metrics!', comp);
                newCompDetection = PathObjects.createDetectionObject(combinedCompROI, getPathClass(comp));
                addObject(newCompDetection);
                
                // Calculate AQUA scoring metrics for new compartment detections for all targets
                for (var tar: targets.entrySet()) {           
                    getTargetAQUA(
                        server, newCompDetection, 
                        tar.getValue(), tar.getKey(), 
                        measurements, cellCompartments, 
                        downsample, metadata, scaleBitDepthTo, microscopeNormalizedMeas
                        );
                }
            }  
        } 
    }
}

// AQUA of Target inside PathObject (i.e. a compartment mask)
def getTargetAQUA(ImageServer<BufferedImage> server,
                PathObject pathObject,
                int targetChannel,
                String targetName,
                Collection<ObjectMeasurements.Measurements> measurements,
                Collection<ObjectMeasurements.Compartments> cellCompartments,
                double downsample,                
                Object metaData,
                int scaleBitDepthTo,
                Boolean microscopeNormalizedMeas
                ){
    
    
    var roi = pathObject.getROI();
    String className = pathObject.getPathClass().toString();
    println String.format('AQUA of %s (channel %x) in %s', targetName, targetChannel, className) 
    
    int pad = (int)Math.ceil(downsample * 2);
    var request = RegionRequest.createInstance(server.getPath(), downsample, roi)
        .pad2D(pad, pad)
        .intersect2D(0, 0, server.getWidth(), server.getHeight());

    var pathImage = IJTools.convertToImagePlus(server, request);
    var imp = pathImage.getImage();
    
    // Normalize/scale bit depth?
    bitDepth = imp.getBitDepth();    
    println 'Current bitdepth:';
    println bitDepth;
    // Current built in scaling functions are not great. Uses the max & min value of the image to linearly scale the image down bitdepth size 
    // and does not offer a solution to scale up bitdepth size... 
    if (scaleBitDepthTo != 0){
        List<Integer> bitConversions = Arrays.asList(8, 16, 32);
        if (bitConversions.contains(scaleBitDepthTo)){
            if (scaleBitDepthTo==8){
                new ImageConverter(imp).convertToGray8();
            } else if(scaleBitDepthTo==16){
                new ImageConverter(imp).convertToGra16();
            } else if(scaleBitDepthTo==32){
                new ImageConverter(imp).convertToGra32();
            }
        } else {
            println String.format('Converting to bitdepth %s not supported...',scaleBitDepthTo.toString());
            return;
        }
    }
    
    Map<String, ImageProcessor> channels = new LinkedHashMap<>();
    var serverChannels = server.getMetadata().getChannels();
    
    if (server.isRGB() && imp.getStackSize() == 1 && imp.getProcessor() instanceof ColorProcessor) {
        ColorProcessor cp = (ColorProcessor)imp.getProcessor();
//        measName = targetName + ' Intensity (' + serverChannels.get(targetChannel-1).getName() + ' channel)';
        measName = targetName + ' Intensity in '+className;
        channels.put(measName, cp.getChannel(targetChannel, null));
    } else {
        assert imp.getStackSize() == serverChannels.size();
//        measName = targetName + ' Intensity (' + serverChannels.get(targetChannel-1).getName() + ' channel)';
        measName = targetName + ' Intensity in '+className;
        channels.put(measName, imp.getStack().getProcessor(targetChannel));
    }

    ByteProcessor bpCell = new ByteProcessor(imp.getWidth(), imp.getHeight());
    bpCell.setValue(1.0);
    var roiIJ = IJTools.convertToIJRoi(roi, pathImage);
    bpCell.fill(roiIJ);
    
    if (pathObject instanceof PathCellObject) {
        var cell = (PathCellObject)pathObject;
        ByteProcessor bpNucleus = new ByteProcessor(imp.getWidth(), imp.getHeight());
        if (cell.getNucleusROI() != null) {
            bpNucleus.setValue(1.0);
            var roiNucleusIJ = IJTools.convertToIJRoi(cell.getNucleusROI(), pathImage);
            bpNucleus.fill(roiNucleusIJ);
        }
        //For mean, median, stdev, etc.
        ObjectMeasurements.measureCells(bpNucleus, bpCell, Map.of(1.0, cell), channels, cellCompartments, measurements);
        //Calculate sum intensity in compartment
//        measureObjSumInt(img, imgLabels, new PathObject[] {pathObject}, targetName);
    } else {
        var imgLabels = new PixelImageIJ(bpCell);
        for (var entry : channels.entrySet()) {
            var img = new PixelImageIJ(entry.getValue());
            //For mean, median, stdev, etc.
            ObjectMeasurements.measureObjects(img, imgLabels, new PathObject[] {pathObject}, entry.getKey(), measurements);
            //Calculate sum intensity in compartment
//            measureObjSumInt(img, imgLabels, new PathObject[] {pathObject}, targetName);
        }
    }
    var pc = server.getPixelCalibration();
    double mppSq = pc.getPixelHeightMicrons()*pc.getPixelWidthMicrons();
//    println 'Squarred MPP: ' + mppSq.toString();

    // Use mean intensity to calculate AQUA score as (mean intensity)/(MPP^2 * exposure_time)
    var measList = pathObject.getMeasurementList();
    // Add shape measurements
    double annotationArea = pathObject.getROI().getArea();
    measList.putMeasurement(className+" area px", annotationArea);
    measList.putMeasurement(className+" area um^2", annotationArea*mppSq);
    double targetMean = measList.getMeasurementValue(measName+': Mean');
    measList.putMeasurement(targetName+' in '+className+' Sum Intensity', targetMean*annotationArea);
    // Debugging, would load from available metadata
    double exposure_time;
    if(Objects.isNull(metaData)){
    	exposure_time = 1000
    	measList.putMeasurement(targetName+' exposure time (ms)', 0);
    } else {
    	exposure_time = Double.parseDouble(metadata[1])
    	measList.putMeasurement(targetName+' exposure time (ms)', exposure_time);
    }

    double MeanI_S = targetMean/(exposure_time/1000)
    measList.putMeasurement(targetName+' in '+className+' Mean I/[exp time (s)]', MeanI_S);
    // Intensity/(um^2*sec)
    double QIF_area = targetMean/mppSq;
    measList.putMeasurement(targetName+' in '+className+' Sum I/um^2', QIF_area);
    double QIF_areaS = QIF_area/(exposure_time/1000);
    measList.putMeasurement(targetName+' in '+className+' Sum I/(um^2*[exp time (s)])', QIF_areaS);
    
    if (microscopeNormalizedMeas){
        println 'Calculating normalized measurments using available microscope metadata...'
        println 'Not implemented...'
    }
    
//    double totalPx = server.getHeight()*server.getWidth();
//    println 'Total pixels: '+ totalPx.toString();
//    double QIF_areaPercent = targetMean*annotationArea/(100*annotationArea/totalPx);
//    measList.putMeasurement(targetName+' in '+className+' Sum I/(Compartment % Area)', QIF_areaPercent);
//    double QIF_areaPercentS = QIF_areaPercent/(exposure_time);
//    measList.putMeasurement(targetName+' in '+className+' Sum I/([Compartment % Area]*[exp time (ms)])', QIF_areaPercentS);
}


// Find the specific parts of annot2 inside of annot1
// Exactly the same as intersection of geometries if doFillHoles == false
def getWithinPathObject(PathObject annot1, PathObject annot2, String annotWithinClass, Boolean doFillHoles){
    // Fill the holes in annot1 and then find the intersection with annot2
    Geometry annot1Geom = annot1.getROI().getGeometry();
    Geometry annot2Geom = annot2.getROI().getGeometry();
    
    // To replace with private/public variable for processing class (AQUAProcess), along with other specific details on image
    // Creation of multiple AQUAProcess classes will allow for multithreading of image processing potentially and also faster processing of Z-stacks
    ///////////////////////////////////////////////////////////////////////
    var plane = ImagePlane.getDefaultPlane();
    ///////////////////////////////////////////////////////////////////////
    
    if (doFillHoles){
        Geometry annot1FillGeom = GeometryTools.fillHoles(annot1Geom);
        Geometry withinGeom = annot1FillGeom.intersection(annot2Geom);
        var withinROI = GeometryTools.geometryToROI(withinGeom, plane);
        var withinDet = PathObjects.createDetectionObject(withinROI, getPathClass(annotWithinClass));
        return withinDet;
    } else {
        Geometry withinGeom = annot1Geom.intersection(annot2Geom);
        var withinROI = GeometryTools.geometryToROI(withinGeom, plane);
        var withinDet = PathObjects.createDetectionObject(withinROI, getPathClass(annotWithinClass));
        return withinDet;
    }
}

import java.io.BufferedReader;
import java.io.FileReader;

def readCSVtoDF(String csvpath, String indexName){
    // Create BufferedReader
    BufferedReader csvReader = new BufferedReader(new FileReader(csvpath));
    Map<String, ArrayList<String>> dataframe = new LinkedHashMap<String, ArrayList<String>>();
    header = csvReader.readLine();
//    header = "test,test1,test2";
    ArrayList<String> headerContent = new ArrayList<String>(header.split(",").toList());
//    println headerContent
    int index = headerContent.indexOf(indexName);
//    println index
//    println headerContent[index]
    int r = 0;
    useRowNumbers = false;
    if(index == -1){
        prinln String.format('Header does not contain %s! Defaulting to using row numbers...', indexName)
        useRowNumbers = true;
    }
    dataframe.put('Header', headerContent);
    while((row = csvReader.readLine()) != null){
//        println row
        ArrayList<String> rowContent = new ArrayList<String>(row.split(",").toList());
        if (useRowNumbers){
            dataframe.put(r, rowContent);
            r+=1;
        } else {
            rowName = rowContent[index];
            int j = 1;
            while (true){
                if (dataframe.containsKey(rowName)){
                    println String.format('rowName %s is duplicated! Resolving by appending integer...', rowName);
                    rowName = String.format('%1$s_%2$x',rowContent[index],j);
                    j+=1;
                } else {
                    break;
                }
            }
            dataframe.put(rowName, rowContent);
        }
    }
//    println dataframe;
    return dataframe;
}

//String filepath = 'E:/AQUA/HER2-V2/02-09-22/YTMA263-17-39_29D8_200/AQUA_20220210_202707/YTMA263-17-39_29D8_200.csv';
////println filepath
//String indexName = 'Spot #';
////println indexName
//
//dataframe = readCSVtoDF(filepath, indexName);
////j=0;
////dataframe.get('Header').forEach{it->
////    println String.format("%s, %s", j, it);
////    j +=1;
////}
//
//// get image metadata based off of spot number in file
//var entry = getProjectEntry()
//imageName = entry.getImageName()
//spotNumber = Integer.parseInt(imageName.split('.tif')[0].split('_')[-1], 10).toString()
//println spotNumber
//// get certain entries that will be useful for AQUAscore normalization
//metadata = dataframe.get(spotNumber)[15, 29, 31]
//println 'Metadata'
//println dataframe.get('Header')[15, 29, 31]
//println metadata
//
////////////////////////////////////////////////////////////////////////////////////////////////////
// Start processing script
////////////////////////////////////////////////////////////////////////////////////////////////////

List<String> compartments = new ArrayList<String>(
                                            List.of('Nuclear',
                                                    'Tumor',
                                                    'Immune cells',
                                                    'B cells',
                                                    'CD8 T cells',
                                                    'Stroma',
//                                                    'Target',
                                                    'Tissue',
                                                    'Tumor Nuclei',
                                                    'Stroma Nuclei')
                                                    );

List<String> ignoreClasses = new ArrayList<String>(
                                            List.of('Ignore*',
                                                    'Necrosis',
                                                    'Other')
                                                    );
                                                    
List<String> rois = new ArrayList<String>(
                                        List.of('ROI')
                                                );

// List of (target name, channel) for all targets
// Not ordered correctly, could initialize with loop and arrayList of Map.entries...
// https://stackoverflow.com/questions/12184378/sorting-linkedhashmap
List<Map.Entry<String, Integer>> targetEntries = new ArrayList<Map.Entry<String, Integer>>(
                                                        List.of(
                                                        Map.entry('PD-L1', 3),
                                                        )
                                                        );
Map<String, Integer> targets = new LinkedHashMap<String, Integer>();
for (Map.Entry<String, Integer> entry : targetEntries) {
    targets.put(entry.getKey(), entry.getValue());
}
// Not ordered correclty, works fine for HashMap
//Map<String, Integer> targets = new LinkedHashMap<>(
//                                            Map.ofEntries(
//                                            Map.entry('29D8', 1),
//                                            Map.entry('CK', 2)
//                                            )
//                                            );

// Select and clear current detections (compartments)
removeObjects(getDetectionObjects(), true);

// Get the current image
def server = getCurrentServer();
server.getPixelCalibration().getAveragedPixelSizeMicrons();
// 3 Channels for RGB image
// How does it work for channels > 3?
var serverChannels = server.getMetadata().getChannels();
println serverChannels.size();
for (int i = 0; i < serverChannels.size(); i++) {
    println serverChannels.get(i).getName();
}

// ~20x image
// setPixelSizeMicrons(0.4969, 0.4969);
var plane = ImagePlane.getDefaultPlane()

// Threshold each channel and generate masks for compartments
//genThresholdMask(Object server, Object channels, double downsample, double blur_sigma, String method, double threshVal, String compartment, Boolean keepLargestOnly, double minPixels)
var nuclearDet = genThresholdMask(server, 1, 2, 0, 'auto_Otsu', 0, 'Nuclear', false, 15);
var tumorDet = genThresholdMask(server, 2, 2, 0.5, 'auto_Otsu', 0, 'Tumor', false, 30);
var bcellDet = genThresholdMask(server, 4, 2, 0.5, 'value', 1700, 'B cells', false, 30);
var cd8Det = genThresholdMask(server, 5, 2, 0.5, 'auto_Otsu', 0, 'CD8 T cells', false, 30);

addObject(nuclearDet);
addObject(tumorDet);
addObject(bcellDet);
addObject(cd8Det);

// Everything except target
int[] tissueChannels = [1, 2, 4];
var tissueDet = genThresholdMask(server, tissueChannels, 4, 1, 'auto_Percentile', 0, 'Tissue', false, 15);
addObject(tissueDet);

//For next fill steps
var tumorGeom = tumorDet.getROI().getGeometry();
var nuclearGeom = nuclearDet.getROI().getGeometry();
var tissueGeom = tissueDet.getROI().getGeometry();

// Fill holes in tumorDet and replace it
//var tumorGeom = tumorDet.getROI().getGeometry()
//var tumorFillGeom = GeometryTools.fillHoles(tumorGeom)
//var tumorFillRoi = GeometryTools.geometryToROI(tumorFillGeom, plane)
//var tumorFillDet = PathObjects.createDetectionObject(tumorFillRoi, getPathClass('Tumor'))
//removeObject(tumorDet, true)
//addObject(tumorFillDet)

// Fill small holes in tissueDet and replace it
var tissueFillGeom = GeometryTools.fillHoles(tissueGeom);;
var tissueFillRoi = GeometryTools.geometryToROI(tissueFillGeom, plane);
var tissueFillDet = PathObjects.createDetectionObject(tissueFillRoi, getPathClass('Tissue'));
removeObject(tissueDet, true);
addObject(tissueFillDet);

//genThresholdMask(server, 3, 2, 0.5, 'auto_Huang', 0, 'Target', false, 30);
//genThresholdMask(server, 2, 2, 0.5, 'auto_Huang', 0, 'Tumor', false, 30);
//genThresholdMask(server, 1, 2, 0, 'auto_Huang', 0, 'Nuclear', false, 15);
//int[] tissueChannels = [1, 2];
//genThresholdMask(server, tissueChannels, 8, 1, 'auto_Percentile', 0, 'Tissue', false, 0);

// Dilate, erode, intersection, union, set operations to manipulate masks for preprocesing
// ROITools
// buffer() to dilate or erode
// symmetric difference == union(a,b) - intersection(a,b)
// https://gist.github.com/Svidro/5829ba53f927e79bb6e370a6a6747cfd

// Get stroma compartment
//var plane = ImagePlane.getDefaultPlane();
tissueGeom = getDetectionObjects().find{it.getPathClass() == getPathClass("Tissue")}.getROI().getGeometry();
tumorGeom = getDetectionObjects().find{it.getPathClass() == getPathClass("Tumor")}.getROI().getGeometry();
nuclearGeom = getDetectionObjects().find{it.getPathClass() == getPathClass("Nuclear")}.getROI().getGeometry();
var stromaGeom = tissueGeom.difference(tumorGeom);
var nucWithinTumorDet = getWithinPathObject(tumorDet, nuclearDet, 'Tumor Nuclei', true);
addObject(nucWithinTumorDet);
// Or could do this without fill because filling the stroma approx to tissue annotation and fills too much of tumor
var nucWithinStromaGeom = nuclearGeom.difference(nucWithinTumorDet.getROI().getGeometry());
var nucWithinStromaRoi = GeometryTools.geometryToROI(nucWithinStromaGeom, plane);
var nucWithinStromaDet = PathObjects.createDetectionObject(nucWithinStromaRoi, getPathClass('Stroma Nuclei'));
addObject(nucWithinStromaDet);
stromaGeom = stromaGeom.difference(nucWithinTumorDet.getROI().getGeometry());
var stromaRoi = GeometryTools.geometryToROI(stromaGeom, plane);
var stromaDet = PathObjects.createDetectionObject(stromaRoi, getPathClass('Stroma'));
//def detStroma = PathObjects.createAnnotatioObject(roiStroma, getPathClass('Stroma'));
addObject(stromaDet);


// Add new pathClasses
// https://forum.image.sc/t/how-to-delete-default-classes-with-groovy/58548

def downsample = 1.0;
def metadata = null;   
int scaleBitDepthTo = 0;
Boolean microscopeNormalizedMeas = false; 

// Normalize AQUA? Exposure time, CC intensity, etc
// Load metadata for image
// .tma --> ? but could use Excel file from AQUAnalysis

// Recalculate AQUA scores after annotating ROIs or excluding regions

// Recalculate for entire mask - exclude
    
recalcCompartmentsAndAQUA(server, 
                        ignoreClasses,
                        targets,
                        compartments,
                        downsample,
                        metadata,
                        scaleBitDepthTo,
                        microscopeNormalizedMeas
                        )

// Recalculate for each ROI


// Clear objects inside ROIs?

getTargetAQUAScoresForROIs(server,
                       rois, 
                       targets,
                       compartments,
                       downsample,
                       metadata,
                       scaleBitDepthTo,
                       microscopeNormalizedMeas
                       )
                        
// Save AQUA scores for entire spot after exclude and each ROI 

