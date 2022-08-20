package qupath.extension.aqua.operations;

public class OldMeasurementExporter {

//private void setupMenu(){
//      exportMeasMenuItem.setOnAction(EXPORT);
//      exportMaskMenuItem.setOnAction(this::exportMasksButton);
//		measAnnotMenuItem.selectedProperty().set(true);
//		measDetMenuItem.selectedProperty().set(true);
//		measAllMenuItem.selectedProperty().set(true);
//		measAllMenuItem.selectedProperty().addListener((obs,old,val)-> {
//			measEssentialMenuItem.selectedProperty().set(!val);
//			// only need to set once
//			if(val)
//				exportMeasFields = "all";
//			else
//				exportMeasFields = "essential";
//			logger.info(exportMeasFields);
//		});
//		measEssentialMenuItem.selectedProperty().addListener((obs,old,val)->measAllMenuItem.selectedProperty().set(!val));
//    }

//void exportImageMeasurementsButton(ActionEvent e) {
//		logger.info("Opening dialog to export measurements for project...");
////		fileSelector = new FileChooser();
//		Project<BufferedImage> project = qupath.getProject();
//		if(project!=null) {
//			initialFileDirectory = Projects.getBaseDirectory(project);
//			logger.info("starting at " + initialFileDirectory);
//		}else {
//			initialFileDirectory = Paths.get(".").toFile();
//		}
//		fileSelector.setInitialDirectory(initialFileDirectory);
//		fileSelector.getExtensionFilters().addAll(
//				new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"),
//				new FileChooser.ExtensionFilter("All files", "*.*"));
//		File outputFile = fileSelector.showSaveDialog(qupath.getStage());
//		if(outputFile!=null) {
//			progressLabel.setText("Exporting measurements for image...");
//			quantProgressBar.setProgress(-1);
//			try {
//				exportMeasurements(outputFile, false);
//			} catch (IOException ex) {
//				progressLabel.setText("Didn't save measurements, exception encountered...");
//				quantProgressBar.setProgress(0.0);
//				throw new RuntimeException(ex);
//			}
//		} else{
//			logger.warn("Did not save measurements, file output path is null.");
//			progressLabel.setText("Didn't save measurements, file output is null");
//			quantProgressBar.setProgress(0.0);
//		}
//	}

//	void exportAllMeasurementsButton(ActionEvent e) {
//		logger.info("Opening dialog to export measurements for project...");
////		fileSelector = new FileChooser();
//		Project<BufferedImage> project = qupath.getProject();
//		if(project!=null) {
//			initialFileDirectory = Projects.getBaseDirectory(project);
//			logger.info("starting at " + initialFileDirectory);
//		}else {
//			initialFileDirectory = Paths.get(".").toFile();
//		}
//		fileSelector.setInitialDirectory(initialFileDirectory);
//		fileSelector.getExtensionFilters().addAll(
//				new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"),
//				new FileChooser.ExtensionFilter("All files", "*.*"));
//		File outputFile = fileSelector.showSaveDialog(qupath.getStage());
//		if(outputFile!=null) {
//			progressLabel.setText("Exporting measurements for all images in project...");
//			quantProgressBar.setProgress(-1);
//			try {
//				exportMeasurements(outputFile, true);
//			} catch (IOException ex) {
//				progressLabel.setText("Didn't save measurements, exception encountered...");
//				quantProgressBar.setProgress(0.0);
//				throw new RuntimeException(ex);
//			}
//		} else{
//			logger.warn("Did not save measurements, file output path is null.");
//			progressLabel.setText("Didn't save measurements, file output is null");
//			quantProgressBar.setProgress(0.0);
//		}
//	}

//	List<String> getMeasExcludeColumns(String excludeType) {
//		if (excludeType.equals("essential")) {
//			List<String> excludeColumns = new ArrayList<String>();
//			excludeColumns.add("ROI");
//			excludeColumns.add("Area Âµm^2");
//			excludeColumns.add("Perimeter Âµm");
//			excludeColumns.add("Missing");
//
//			for(Map.Entry<ColorTransform, Double> tar  : selectedTargets.entrySet()) {
//				//	removing double quotes....
//				String tarName = tar.getKey().toString().replaceAll("\"", "");
//				for(PathClass comp : selectedCompartments) {
//					String compName = comp.toString();
//					excludeColumns.add(String.format("%s Intensity in %s: Median",tarName, compName));
//					excludeColumns.add(String.format("%s Intensity in %s: Min",tarName, compName));
//					excludeColumns.add(String.format("%s Intensity in %s: Max",tarName, compName));
//					excludeColumns.add(String.format("%s Intensity in %s: Std.Dev.",tarName, compName));
//					excludeColumns.add(String.format("%s Intensity in %s: Variance",tarName, compName));
//					excludeColumns.add(String.format("%s area px", compName));
//				}
//
//			}
//			logger.info("Excluding columns: "+excludeColumns.toString());
//			return excludeColumns;
//		}else {
//			return Collections.<String>emptyList();
//		}
//	}
//	public void exportMeasurements(File outputFile, boolean exportAllImages) throws IOException {
//		// Get the list of all images in the current project
//		Project<BufferedImage> project = qupath.getProject();
//		if (project==null) {
//			logger.error("Cannot export measurements for null project!");
//			progressLabel.setText("Cannot export measurements for null project!");
//			quantProgressBar.setProgress(0.0);
//			return;
//		}
//
//		exportMeasButton.setDisable(true);
//		exportMeasMenuItem.setDisable(true);
//
//		// save current image before exporting measurements
//		ImageData<BufferedImage> thisImageData = qupath.getImageData();
//		project.getEntry(thisImageData).saveImageData(thisImageData);
//		List<ProjectImageEntry<BufferedImage>> imagesToExport;
//		if(exportAllImages) {
//			imagesToExport = project.getImageList();
//		}else{
//			imagesToExport = List.of(project.getEntry(thisImageData));
//		}
//
//		// Separate each measurement value in the output file with a comma (",")
//		String separator = ",";
//
//		// Choose the columns that will be included in the export
//		// Note: if 'columnsToInclude' is empty, all columns will be included
//		//def columnsToInclude = new String[]{"Name", "Class", "Nucleus: Area"}
//		String[] excludeColumns = getMeasExcludeColumns(exportMeasFields).toArray(new String[0]);
////		logger.info("Excluding columns: "+excludeColumns.toString());
//
//		// Choose the type of objects that the export will process
//		// Other possibilities include:
//		//    1. PathAnnotationObject
//		//    2. PathDetectionObject
//		//    3. PathRootObject
//		// Note: import statements should then be modified accordingly
//		Class<? extends PathObject> exportType;
//		if(measAnnotMenuItem.selectedProperty().get() && measDetMenuItem.selectedProperty().get() || !measAnnotMenuItem.selectedProperty().get() && !measDetMenuItem.selectedProperty().get()){
//			//	export all objects
//			//	If both of these menu items are deselected, assume it was a mistake and export all objects anyways
//			exportType = PathObject.class;
//		} else if(measDetMenuItem.selectedProperty().get() && !measAnnotMenuItem.selectedProperty().get()){
//			//	only export detections
//			exportType = PathDetectionObject.class;
//		} else{
//			//  last option, export annotations. Also is kinda the default
//			exportType = PathAnnotationObject.class;
//		}
//
//		// Create the measurementExporter and start the export
//		MeasurementExporter exporter = new MeasurementExporter()
//							.imageList(imagesToExport)            // Images from which measurements will be exported
//							.separator(separator)                 // Character that separates values
//			//                  .includeOnlyColumns()
//							.excludeColumns(excludeColumns)                     // Columns are case-sensitive
//							.exportType(exportType);               // Type of objects to export
//
//		// Start the export process
//		CompletableFuture.runAsync(()->exporter.exportMeasurements(outputFile))
//				.exceptionally(ex -> {ex.printStackTrace(); return null;})
//				.thenRun(()->{
//					Platform.runLater(()->{
//						progressLabel.setText("Completed exporting measurements");
//						quantProgressBar.setProgress(1.0);
//						exportMeasButton.setDisable(false);
//						exportMeasMenuItem.setDisable(false);
//					});
//				});
//	}
}
