package qupath.extension.aqua.operations.groovy_scripts

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
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.GeometryTools;
import org.locationtech.jts.geom.Geometry;
import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.imagej.tools.PixelImageIJ;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.analysis.images.SimpleImage;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import static qupath.lib.gui.scripting.QPEx.*;



def combinePathObjs(Collection<PathObject> annots, Boolean newAnnot){
    ROI combinedROI = null;
    PathClass p_class = null;
    for(PathObject annotation: annots){
        if (combinedROI == null) {
            combinedROI = annotation.getROI();//.duplicate();
            p_class = annotation.getPathClass();
        } else if (combinedROI.getImagePlane().equals(annotation.getROI().getImagePlane())) {
            combinedROI = RoiTools.combineROIs(combinedROI, annotation.getROI(), RoiTools.CombineOp.ADD);
        } else {
            println "Cannot merge PathObjects across different image planes!";
            continue;
        }
    }

    if(newAnnot){
        removeObjects(annots, true)
        combinedAnnot = PathObjects.createAnnotationObject(combinedROI, p_class);
        addObject(combinedAnnot);
    }

    return combinedROI;
}

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
// Not for TMAs! Would be much more effective to restrict the search space for ROIS within TMA core hierarchy, however, not all the annotations will be properly incorporated into the hierarchy.....
// How to flexibly find ROIs within TMA core hierarchy?
def getTargetAQUAScoresForROIs(ImageServer<BufferedImage> server,
                               List<String> rois,
                               Map<String, Integer> targets,
                               List<String> compartments,
                               double downsample,
                               Object metadata,
                               int scaleBitDepthTo
                            ) {
                            
    List<ObjectMeasurements.Measurements> measurements = ObjectMeasurements.Measurements.values() as List;
    List<ObjectMeasurements.Compartments> cellCompartments = ObjectMeasurements.Compartments.values() as List; // Won't mean much if they aren't cells...
    
    // Add annotations to heirarchy connected to ROI
    
    // Remove uninformative classes (Tissue)
    compartments.remove('Tissue');
    
    // Used for placing child objects inside ROI
    var imageData = getCurrentImageData();

    AtomicInteger roiNumber = new AtomicInteger(1);

    var pathObjs = imageData.getHierarchy().getObjects(null, PathObject.class);
    var compartmentObjs = pathObjs.parallelStream().filter(p -> compartments.contains(p.getPathClass().toString()))
												.collect(Collectors.toList());
    pathObjs.parallelStream().filter(p -> rois.contains(p.getPathClass().toString()) && p.hasROI())
							.map(f ->{
					    		// Record null/none values for compartments not within ROI
						        println f.getName();
						        if (f.getName()==null||f.getName().isBlank()){
						            f.setName('ROI_'+roiNumber.get());
						            roiNumber.incrementAndGet();
						        }
						        return f;
						    })
						    .forEach(r ->{
						    	//Typically the number of compartments is small and these are all combined for a WSI.
						    	//Not efficiient for TMA cores!
						    	for(PathObject compObj : compartmentObjs){
						    		compInterROI = RoiTools.combineROIs(compObj.getROI(), r.getROI(), RoiTools.CombineOp.INTERSECT);
					                compInterDet = PathObjects.createDetectionObject(compInterROI, compObj.getPathClass());
					                
					                if (!compInterROI.isEmpty()) {
					                    println String.format("ROI contains %s compartment! Calculating AQUA metrics within ROI.", compObj.getPathClass().toString());
					                    // For debugging, maybe helps with visualization
					                    // Add object as a child of the ROI
					//                        addObject(compInterDet);
					                    compInterDet.setName(r.getName()+' ('+compObj.getPathClass().toString()+')');
					                    imageData.getHierarchy().addPathObjectBelowParent(r, compInterDet, true);

					                    println String.format('Got %s intersection with ROI', compObj.getPathClass().toString());
					                    
					                    // Quantify metrics/AQUA for each target in each intersecting compartment
					                    // Calculate AQUA scoring metrics for new compartment detections for all targets
						                getTargetsAQUA(
						                        server, compInterDet, 
						                        targets, 
						                        measurements, cellCompartments, 
						                        downsample, metadata, scaleBitDepthTo
						                        );
					                    
					                // Put these target/compartment measurments on the measurement list of the ROI for export
					//                    compInterObjs.add(compInterDet)
					                } else {
					                    println String.format("No intersection with %s compartment for ROI... skipping.", compObj.getPathClass().toString()); 
					                }
						    	}

						    });
} 


// Exclude regions and add regions that weren't segmented well. Allows for manual adjustment of compartmentalization before AQUA.
def TMARecalcCompartmentsAndAQUA(ImageServer<BufferedImage> server, 
                            List<String> ignoreClasses, 
                            Map<String, Integer> targets,
                            List<String> compartments,
                            double downsample,
                            Object metadata,
                            int scaleBitDepthTo
                            ) {
        
    // Adjust each compartment by subtracting the exclude region and adding the corresponding compartment adjustments
    // Iterate through compartments/detections to recreate them if adjustments were made
    // Calculate AQUA metrics for each target
    Boolean doAdjust = false;
    
    List<ObjectMeasurements.Measurements> measurements = ObjectMeasurements.Measurements.values() as List;
    List<ObjectMeasurements.Compartments> cellCompartments = ObjectMeasurements.Compartments.values() as List; // Won't mean much if they aren't cells...
    println 'Updating existing compartments with any new annotations, calcuating AQUA metrics...';
    
    def tmaGrid = getCurrentHierarchy().getTMAGrid();
    var tmaCores = tmaGrid.getTMACoreList()
    // Combine exclude regions, but do not create a new merged object
    ROI combinedExcludeROI = null;
    
    var allIgnoreAnnotations = getAnnotationObjects().parallelStream().findAll(p -> ignoreClasses.contains(p.getPathClass().toString()));
    combinedExcludeROI = combinePathObjs(allIgnoreAnnotations, false);

    tmaCores.parallelStream().forEach(core ->{
        // step thru all children items of TMA core object
        core.getChildObjects().parallelStream().forEach(detection ->{
            if (compartments.contains(detection.getPathClass().toString())) {
                adjDetectionROI = detection.getROI();
                if(combinedExcludeROI != null) {
                    adjDetectionROI = RoiTools.combineROIs(adjDetectionROI, combinedExcludeROI, RoiTools.CombineOp.SUBTRACT);
                    combinedExcludeROI = null;
                    doAdjust = true;
                }
                if (doAdjust && adjDetectionROI.isEmpty()) {
                    println String.format('Detection %s compartment is now empty, skipping AQUA metrics...', detection.getPathClass().toString());
                    removeObject(detection, true);
                    doAdjust = false;
                    return;
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
                getTargetsAQUA(
                        server, adjDetection, 
                        targets, 
                        measurements, cellCompartments, 
                        downsample, metadata, scaleBitDepthTo
                        );
            }
        })
    });
    
    // println 'Checking if any compartments were added by new annotations...';
    // println missingCompartments;
    // if (!missingCompartments.isEmpty()){
    //     for (String comp : missingCompartments) {
    //         combinedCompROI = combinedCompROIMap.get(comp);
    //         if (combinedCompROI != null){
    //             println String.format('Adding new %s annotations and calculating AQUA metrics!', comp);
    //             newCompDetection = PathObjects.createDetectionObject(combinedCompROI, getPathClass(comp));
    //             addObject(newCompDetection);
                
    //             // Calculate AQUA scoring metrics for new compartment detections for all targets
    //             for (var tar: targets.entrySet()) {           
    //                 getTargetAQUA(
    //                     server, newCompDetection, 
    //                     tar.getValue(), tar.getKey(), 
    //                     measurements, cellCompartments, 
    //                     downsample, metadata, scaleBitDepthTo
    //                     );
    //             }
    //         }  
    //     } 
    // }
}

// AQUA of Target inside PathObject (i.e. a compartment mask)
def getTargetsAQUA(ImageServer<BufferedImage> server,
                PathObject pathObject,
                Map<String, Integer> targets,
                Collection<ObjectMeasurements.Measurements> measurements,
                Collection<ObjectMeasurements.Compartments> cellCompartments,
                double downsample,                
                Object metaData,
                int scaleBitDepthTo
                ){

	var roi = pathObject.getROI();
    String className = pathObject.getPathClass().toString();
    
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


    var pc = server.getPixelCalibration();
    double mppSq = pc.getPixelHeightMicrons()*pc.getPixelWidthMicrons();
//    println 'Squarred MPP: ' + mppSq.toString();

    // Use mean intensity to calculate AQUA score as (mean intensity)/(MPP^2 * exposure_time)
    var measList = pathObject.getMeasurementList();

    // Add shape measurements
    double annotationArea = pathObject.getROI().getArea();
    measList.putMeasurement(className+" area px", annotationArea);
    measList.putMeasurement(className+" area um^2", annotationArea*mppSq);
    measList.putMeasurement("MPP^2", mppSq)
    measList.putMeasurement('Channel bitdepth', bitDepth);
    int bitDepthVal = Math.pow(2, bitDepth);
    
    Map<String, ImageProcessor> channels = new LinkedHashMap<>();
    Map<String, String> measNames = new LinkedHashMap<>();
    var serverChannels = server.getMetadata().getChannels();

    for(Map.Entry<String, Integer> tar : targets.entrySet()){
    	String targetName = tar.getKey();
    	int targetChannel = tar.getValue();
	    if (server.isRGB() && imp.getStackSize() == 1 && imp.getProcessor() instanceof ColorProcessor) {
	        ColorProcessor cp = (ColorProcessor)imp.getProcessor();
	//        measName = targetName + ' Intensity (' + serverChannels.get(targetChannel-1).getName() + ' channel)';
	        measName = targetName + ' Intensity in '+className;
	        measNames.put(targetName, measName);
	        channels.put(measName, cp.getChannel(targetChannel, null));
	        println String.format('AQUA of %s (channel %x) in %s', targetName, targetChannel, className) 
	    } else {
	        assert imp.getStackSize() == serverChannels.size();
	//        measName = targetName + ' Intensity (' + serverChannels.get(targetChannel-1).getName() + ' channel)';
	        measName = targetName + ' Intensity in '+className;
	        measNames.put(targetName, measName);
	        channels.put(measName, imp.getStack().getProcessor(targetChannel));
	        println String.format('AQUA of %s (channel %x) in %s', targetName, targetChannel, className) 
	    }
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
        for (Map.Entry<String, ImageProcessor> entry : channels.entrySet()) {
            var img = new PixelImageIJ(entry.getValue());
            //For mean, median, stdev, etc.
            ObjectMeasurements.measureObjects(img, imgLabels, new PathObject[] {pathObject}, entry.getKey(), measurements);
            //Calculate sum intensity in compartment
//            measureObjSumInt(img, imgLabels, new PathObject[] {pathObject}, targetName);
        }
    }

    
    for(Map.Entry<String, Integer> tar : targets.entrySet()){
    	targetName = tar.getKey();
    	targetChannel = tar.getValue();
    	measName = measNames.get(targetName)
	    double targetMean = measList.getMeasurementValue(measName+': Mean');
	    // double sumInt = targetMean*annotationArea;
	    // measList.putMeasurement(targetName+' in '+className+' Sum Intensity', sumInt);
	    // Debugging, would load from available metadata
	    double exposure_time;
	    if(metaData == null){
	    	exposure_time = 1000
	    	measList.putMeasurement(targetName+' exposure time (ms)', 0);
	    } else if(metaData instanceof Double){
	    	exposure_time = metaData
	    	measList.putMeasurement(targetName+' exposure time (ms)', exposure_time);
	    } else {
	    	exposure_time = Double.parseDouble(metadata[targetChannel-1])
	    	measList.putMeasurement(targetName+' exposure time (ms)', exposure_time);
	    }

	    // double MeanI_S = targetMean/(exposure_time/1000)
	    // measList.putMeasurement(targetName+' in '+className+' Mean I/[exp time (s)]', MeanI_S);
	    // Intensity/(um^2*sec)
	    // double QIF_area = targetMean/mppSq;
	    // measList.putMeasurement(targetName+' in '+className+' Sum I/um^2', QIF_area);
	   double QIF_areaS = (targetMean/mppSq)/(bitDepthVal*exposure_time/1000);
	   measList.putMeasurement(targetName+' in '+className+' Sum I/(um^2*[exp time (s)]*[2^bitDepth])', QIF_areaS);
	    
	//    double totalPx = server.getHeight()*server.getWidth();
	//    println 'Total pixels: '+ totalPx.toString();
	//    double QIF_areaPercent = targetMean*annotationArea/(100*annotationArea/totalPx);
	//    measList.putMeasurement(targetName+' in '+className+' Sum I/(Compartment % Area)', QIF_areaPercent);
	//    double QIF_areaPercentS = QIF_areaPercent/(exposure_time);
	//    measList.putMeasurement(targetName+' in '+className+' Sum I/([Compartment % Area]*[exp time (ms)])', QIF_areaPercentS);
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


////////////////////////////////////////////////////////////////////////////////////////////////////
// Start processing script
////////////////////////////////////////////////////////////////////////////////////////////////////

List<String> compartments = new ArrayList<String>(
                                            List.of(
                                                    'Tumor',
                                                    // 'Stroma',
                                                    'Nuclear',
                                                    'Tumor Nuc Expanded'
                                                    )
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
                                                        Map.entry("HER2", 3),
                                                        // Map.entry("CK", 2),
                                                        )
                                                        );
Map<String, Integer> targets = new LinkedHashMap<String, Integer>();
for (Map.Entry<String, Integer> entry : targetEntries) {
    targets.put(entry.getKey(), entry.getValue());
}

// Select and clear current detections (compartments)
removeObjects(getDetectionObjects(), true);
clearAnnotationMeasurements();

def server = getCurrentServer();

double metadata = 0.035*1000;
boolean calculateAQUAScore = true;
double downsample = 1.0;
int scaleBitDepthTo = 0;

// Normalize AQUA? Exposure time, CC intensity, etc
// Load metadata for image
// .tma --> ? but could use Excel file from AQUAnalysis

// Recalculate AQUA scores after annotating ROIs or excluding regions

// Recalculate for entire mask - exclude
    
TMARecalcCompartmentsAndAQUA(server,
                        ignoreClasses,
                        targets,
                        compartments,
                        downsample,
                        metadata,
                        scaleBitDepthTo
                        )

// Recalculate for each ROI


// Clear objects inside ROIs?

// getTargetScoresForROIs(server,
//                        rois, 
//                        targets,
//                        compartments,
//                        downsample,
//                        metadata,
//                        scaleBitDepthTo,
//                        )
                        
// Save AQUA scores for entire spot after exclude and each ROI 