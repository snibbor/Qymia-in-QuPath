package qupath.extension.qiima.backend;

import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageConverter;
import ij.ImagePlus;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.imagej.tools.IJTools;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.*;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.imagej.tools.PixelImageIJ;
import qupath.lib.measurements.MeasurementList;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static qupath.lib.gui.scripting.QPEx.*;


public class CompQuantBackendBackup {

	private static final Logger logger = LoggerFactory.getLogger(CompQuantBackendBackup.class);

	public ROI combinePathObjs(Collection<PathObject> annots, Boolean newAnnot) {
		ROI combinedROI = null;
		PathClass p_class = null;
		for (PathObject annotation : annots) {
			if (combinedROI == null) {
				combinedROI = annotation.getROI();//.duplicate();
				p_class = annotation.getPathClass();
			} else if (combinedROI.getImagePlane().equals(annotation.getROI().getImagePlane())) {
				combinedROI = RoiTools.combineROIs(combinedROI, annotation.getROI(), RoiTools.CombineOp.ADD);
			} else {
				logger.info("Cannot merge PathObjects across different image planes!");
//				continue;
			}
		}

		if (newAnnot) {
			removeObjects(annots, true);
			PathObject combinedAnnot = PathObjects.createAnnotationObject(combinedROI, p_class);
			addObject(combinedAnnot);
		}

		return combinedROI;
	}

	// AQUA inside each intersecting compartment of ROI only
	//    Map<String, Integer> targets = new LinkedHashMap<>();
	// Not for TMAs! Would be much more effective to restrict the search space for ROIS within TMA core hierarchy, however, not all the annotations will be properly incorporated into the hierarchy.....
	// How to flexibly find ROIs within TMA core hierarchy?
	public void getTargetAQUAScoresForROIs(ImageServer<BufferedImage> server,
								   List<String> rois,
								   Map<String, Integer> targets,
								   List<String> compartments,
								   double downsample,
								   Object metadata,
								   int scaleBitDepthTo
	) {

		List<ObjectMeasurements.Measurements> measurements = Arrays.asList(ObjectMeasurements.Measurements.values());
		List<ObjectMeasurements.Compartments> cellCompartments = Arrays.asList(ObjectMeasurements.Compartments.values());
		// Won't mean much if they aren't cells...

		// Add annotations to heirarchy connected to ROI

		// Remove uninformative classes (Tissue)
		compartments.remove("Tissue");

		// Used for placing child objects inside ROI
		var imageData = getCurrentImageData();

		AtomicInteger roiNumber = new AtomicInteger(1);

		var pathObjs = imageData.getHierarchy().getObjects(null, PathObject.class);
		var compartmentObjs = pathObjs.parallelStream().filter(p -> compartments.contains(p.getPathClass().toString()))
				.collect(Collectors.toList());
		pathObjs.parallelStream().filter(p -> rois.contains(p.getPathClass().toString()) && p.hasROI())
				.map(f -> {
					// Record null/none values for compartments not within ROI
					logger.info(f.getName());
					if (f.getName() == null || f.getName().isBlank()) {
						f.setName("ROI_" + roiNumber.get());
						roiNumber.incrementAndGet();
					}
					return f;
				})
				.forEach(r -> {
					//Typically the number of compartments is small and these are all combined for a WSI.
					//Not efficiient for TMA cores!
					for (PathObject compObj : compartmentObjs) {
						ROI compInterROI = RoiTools.combineROIs(compObj.getROI(), r.getROI(), RoiTools.CombineOp.INTERSECT);
						PathObject compInterDet = PathObjects.createDetectionObject(compInterROI, compObj.getPathClass());

						if (!compInterROI.isEmpty()) {
							logger.info(String.format("ROI contains %s compartment! Calculating AQUA metrics within ROI.", compObj.getPathClass().toString()));
							// For debugging, maybe helps with visualization
							// Add object as a child of the ROI
							//                        addObject(compInterDet);
							compInterDet.setName(r.getName() + " (" + compObj.getPathClass().toString() + ")");
							imageData.getHierarchy().addPathObjectBelowParent(r, compInterDet, true);

							logger.info(String.format("Got %s intersection with ROI", compObj.getPathClass().toString()));

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
							logger.info(String.format("No intersection with %s compartment for ROI... skipping.", compObj.getPathClass().toString()));
						}
					}

				});
	}


	// Exclude regions and add regions that weren't segmented well. Allows for manual adjustment of compartmentalization before AQUA.
	public void TMARecalcCompartmentsAndAQUA(ImageServer<BufferedImage> server,
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
		final Boolean[] doAdjust = {false};

		List<ObjectMeasurements.Measurements> measurements = Arrays.asList(ObjectMeasurements.Measurements.values());
		List<ObjectMeasurements.Compartments> cellCompartments = Arrays.asList(ObjectMeasurements.Compartments.values());
		// Won't mean much if they aren't cells...
		logger.info("Updating existing compartments with any new annotations, calcuating AQUA metrics...");

		TMAGrid tmaGrid = getCurrentHierarchy().getTMAGrid();
		List<TMACoreObject> tmaCores = tmaGrid.getTMACoreList();
		// Combine exclude regions, but do not create a new merged object
		ROI combinedExcludeROI = null;

		List<PathObject> allIgnoreAnnotations = getAnnotationObjects().parallelStream().filter(p -> ignoreClasses.contains(p.getPathClass().toString()))
																					.collect(Collectors.toList());
		combinedExcludeROI = combinePathObjs(allIgnoreAnnotations, false);
		if(combinedExcludeROI != null)
			doAdjust[0] = true;

		ROI finalCombinedExcludeROI = combinedExcludeROI;
		tmaCores.parallelStream().forEach(core -> {
			// step thru all children items of TMA core object
			core.getChildObjects().parallelStream().forEach(detection -> {
				if (compartments.contains(detection.getPathClass().toString())) {
					PathObject adjDetection;
					ROI adjDetectionROI = detection.getROI();
					// is not very efficient as the excluded areas may only be in certain TMA spots....
					// getting an excluded ROI for each TMA core is not as parallellizable and does not work if the excluded region does not fit within the QuPath hierarchy
					if (doAdjust[0]) {
						adjDetectionROI = RoiTools.combineROIs(adjDetectionROI, finalCombinedExcludeROI, RoiTools.CombineOp.SUBTRACT);
					}
					if (adjDetectionROI.isEmpty()) {
						logger.info(String.format("Detection %s compartment is now empty, skipping AQUA metrics...", detection.getPathClass().toString()));
//						removeObject(detection, true);
						return;
					} else if (doAdjust[0]) {
						logger.info(String.format("Adjusting %s compartment based on new annotations...", detection.getPathClass().toString()));
						adjDetection = PathObjects.createDetectionObject(adjDetectionROI, detection.getPathClass());
						addObject(adjDetection);
						removeObject(detection, true);
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
			});
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
	public void getTargetsAQUA(ImageServer<BufferedImage> server,
					   PathObject pathObject,
					   Map<String, Integer> targets,
					   Collection<ObjectMeasurements.Measurements> measurements,
					   Collection<ObjectMeasurements.Compartments> cellCompartments,
					   double downsample,
					   Object metaData,
					   int scaleBitDepthTo
	) {

		ROI roi = pathObject.getROI();
		String className = pathObject.getPathClass().toString();

		int pad = (int) Math.ceil(downsample * 2);
		RegionRequest request = RegionRequest.createInstance(server.getPath(), downsample, roi)
				.pad2D(pad, pad)
				.intersect2D(0, 0, server.getWidth(), server.getHeight());

		PathImage<ImagePlus> pathImage = null;
		try {
			pathImage = IJTools.convertToImagePlus(server, request);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		ImagePlus imp = pathImage.getImage();

		// Normalize/scale bit depth?
		int bitDepth = imp.getBitDepth();
		logger.info("Current bitdepth: " + bitDepth);
		// Current built in scaling functions are not great. Uses the max & min value of the image to linearly scale the image down bitdepth size
		// and does not offer a solution to scale up bitdepth size...
		if (scaleBitDepthTo != 0) {
			List<Integer> bitConversions = Arrays.asList(8, 16, 32);
			if (bitConversions.contains(scaleBitDepthTo)) {
				if (scaleBitDepthTo == 8) {
					new ImageConverter(imp).convertToGray8();
				} else if (scaleBitDepthTo == 16) {
					new ImageConverter(imp).convertToGray16();
				} else if (scaleBitDepthTo == 32) {
					new ImageConverter(imp).convertToGray32();
				}
			} else {
				logger.info(String.format("Converting to bitdepth %d not supported...", scaleBitDepthTo));
				return;
			}
		}


		PixelCalibration pc = server.getPixelCalibration();
		double mppSq = pc.getPixelHeightMicrons() * pc.getPixelWidthMicrons();
		//    println 'Squarred MPP: ' + mppSq.toString();

		// Use mean intensity to calculate AQUA score as (mean intensity)/(MPP^2 * exposure_time)
		MeasurementList measList = pathObject.getMeasurementList();

		// Add shape measurements
		double annotationArea = pathObject.getROI().getArea();
		measList.putMeasurement(className + " area px", annotationArea);
		measList.putMeasurement(className + " area um^2", annotationArea * mppSq);
		measList.putMeasurement("MPP^2", mppSq);
		measList.putMeasurement("Channel bitdepth", bitDepth);
		int bitDepthVal = (int) Math.pow(2, bitDepth);

		Map<String, ImageProcessor> channels = new LinkedHashMap<>();
		Map<String, String> measNames = new LinkedHashMap<>();
		List<ImageChannel> serverChannels = server.getMetadata().getChannels();

		for (Map.Entry<String, Integer> tar : targets.entrySet()) {
			String targetName = tar.getKey();
			int targetChannel = tar.getValue();
			if (server.isRGB() && imp.getStackSize() == 1 && imp.getProcessor() instanceof ColorProcessor) {
				ColorProcessor cp = (ColorProcessor) imp.getProcessor();
				//        measName = targetName + ' Intensity (' + serverChannels.get(targetChannel-1).getName() + ' channel)';
				String measName = targetName + " Intensity in " + className;
				measNames.put(targetName, measName);
				channels.put(measName, cp.getChannel(targetChannel, null));
				logger.info(String.format("AQUA of %s (channel %x) in %s", targetName, targetChannel, className));
			} else {
				assert imp.getStackSize() == serverChannels.size();
				//        measName = targetName + ' Intensity (' + serverChannels.get(targetChannel-1).getName() + ' channel)';
				String measName = targetName + " Intensity in " + className;
				measNames.put(targetName, measName);
				channels.put(measName, imp.getStack().getProcessor(targetChannel));
				logger.info(String.format("AQUA of %s (channel %x) in %s", targetName, targetChannel, className));
			}
		}

		ByteProcessor bpCell = new ByteProcessor(imp.getWidth(), imp.getHeight());
		bpCell.setValue(1.0);
		Roi roiIJ = IJTools.convertToIJRoi(roi, pathImage);
		bpCell.fill(roiIJ);

		if (pathObject instanceof PathCellObject) {
			PathCellObject cell = (PathCellObject) pathObject;
			ByteProcessor bpNucleus = new ByteProcessor(imp.getWidth(), imp.getHeight());
			if (cell.getNucleusROI() != null) {
				bpNucleus.setValue(1.0);
				Roi roiNucleusIJ = IJTools.convertToIJRoi(cell.getNucleusROI(), pathImage);
				bpNucleus.fill(roiNucleusIJ);
			}
			//For mean, median, stdev, etc.
//			ObjectMeasurements.measureCells(bpNucleus, bpCell, Map.of(1.0, cell), channels, cellCompartments, measurements);
			//Calculate sum intensity in compartment
			//        measureObjSumInt(img, imgLabels, new PathObject[] {pathObject}, targetName);
		} else {
			var imgLabels = new PixelImageIJ(bpCell);
			for (Map.Entry<String, ImageProcessor> entry : channels.entrySet()) {
				var img = new PixelImageIJ(entry.getValue());
				//For mean, median, stdev, etc.
//				ObjectMeasurements.measureObjects(img, imgLabels, new PathObject[]{pathObject}, entry.getKey(), measurements);
				//Calculate sum intensity in compartment
				//            measureObjSumInt(img, imgLabels, new PathObject[] {pathObject}, targetName);
			}
		}


		for (Map.Entry<String, Integer> tar : targets.entrySet()) {
			String targetName = tar.getKey();
			int targetChannel = tar.getValue();
			String measName = measNames.get(targetName);
			double targetMean = measList.getMeasurementValue(measName + ": Mean");
			// double sumInt = targetMean*annotationArea;
			// measList.putMeasurement(targetName+' in '+className+' Sum Intensity', sumInt);
			// Debugging, would load from available metadata
			double exposure_time;
			if (metaData == null) {
				exposure_time = 1000;
				measList.putMeasurement(targetName + " exposure time (ms)", 0);
			} else if (metaData instanceof Double) {
				exposure_time = (double) metaData;
				measList.putMeasurement(targetName + " exposure time (ms)", exposure_time);
			} else {
				exposure_time = Double.parseDouble(((String[]) metaData)[targetChannel - 1]);
				measList.putMeasurement(targetName + " exposure time (ms)", exposure_time);
			}

			// double MeanI_S = targetMean/(exposure_time/1000)
			// measList.putMeasurement(targetName+' in '+className+' Mean I/[exp time (s)]', MeanI_S);
			// Intensity/(um^2*sec)
			// double QIF_area = targetMean/mppSq;
			// measList.putMeasurement(targetName+' in '+className+' Sum I/um^2', QIF_area);
			double QIF_areaS = (targetMean / mppSq) / (bitDepthVal * exposure_time / 1000);
			measList.putMeasurement(targetName + " in " + className + " Sum I/(um^2*[exp time (s)]*[2^bitDepth])", QIF_areaS);

			//    double totalPx = server.getHeight()*server.getWidth();
			//    println 'Total pixels: '+ totalPx.toString();
			//    double QIF_areaPercent = targetMean*annotationArea/(100*annotationArea/totalPx);
			//    measList.putMeasurement(targetName+' in '+className+' Sum I/(Compartment % Area)', QIF_areaPercent);
			//    double QIF_areaPercentS = QIF_areaPercent/(exposure_time);
			//    measList.putMeasurement(targetName+' in '+className+' Sum I/([Compartment % Area]*[exp time (ms)])', QIF_areaPercentS);
		}
	}


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

////////////////////////////////////////////////////////////////////////////////////////////////////
// Start processing script
////////////////////////////////////////////////////////////////////////////////////////////////////

//List<String> compartments = new ArrayList<String>(
//                                            List.of(
//                                                    'Tumor',
//                                                    // 'Stroma',
//                                                    'Nuclear',
//                                                    'Tumor Nuc Expanded'
//                                                    )
//                                                    );
//
//List<String> ignoreClasses = new ArrayList<String>(
//                                            List.of('Ignore*',
//                                                    'Necrosis',
//                                                    'Other')
//                                                    );
//
//List<String> rois = new ArrayList<String>(
//                                        List.of('ROI')
//                                                );
//
//// List of (target name, channel) for all targets
//// Not ordered correctly, could initialize with loop and arrayList of Map.entries...
//// https://stackoverflow.com/questions/12184378/sorting-linkedhashmap
//List<Map.Entry<String, Integer>> targetEntries = new ArrayList<Map.Entry<String, Integer>>(
//                                                        List.of(
//                                                        Map.entry("HER2", 3),
//                                                        // Map.entry("CK", 2),
//                                                        )
//                                                        );
//Map<String, Integer> targets = new LinkedHashMap<String, Integer>();
//for (Map.Entry<String, Integer> entry : targetEntries) {
//    targets.put(entry.getKey(), entry.getValue());
//}
//
//// Select and clear current detections (compartments)
//removeObjects(getDetectionObjects(), true);
//clearAnnotationMeasurements();
//
//def server = getCurrentServer();
//
//double metadata = 0.035*1000;
//boolean calculateAQUAScore = true;
//double downsample = 1.0;
//int scaleBitDepthTo = 0;

// Normalize AQUA? Exposure time, CC intensity, etc
// Load metadata for image
// .tma --> ? but could use Excel file from AQUAnalysis

// Recalculate AQUA scores after annotating ROIs or excluding regions

// Recalculate for entire mask - exclude
    
//TMARecalcCompartmentsAndScores(server,
//                        ignoreClasses,
//                        targets,
//                        compartments,
//                        downsample,
//                        metadata,
//                        scaleBitDepthTo
//                        )

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