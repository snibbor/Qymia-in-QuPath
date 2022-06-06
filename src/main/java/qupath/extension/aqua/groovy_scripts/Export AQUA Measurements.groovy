import qupath.lib.gui.tools.MeasurementExporter
import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathCellObject
import qupath.lib.objects.PathObject

// Get the list of all images in the current project
def project = getProject()
def imagesToExport = project.getImageList()

// Separate each measurement value in the output file with a comma (",")
def separator = ","

// Choose the columns that will be included in the export
// Note: if 'columnsToInclude' is empty, all columns will be included
//def columnsToInclude = new String[]{"Name", "Class", "Nucleus: Area"}
//def excludeColumns = new String[]{"Parent", "Centroid X", "Nucleus: Area"}

// Choose the type of objects that the export will process
// Other possibilities include:
//    1. PathAnnotationObject
//    2. PathDetectionObject
//    3. PathRootObject
// Note: import statements should then be modified accordingly
def exportType = PathAnnotationObject.class
//def exportType = PathDetectionObject.class
//def exportType = PathObject.class

// Choose your *full* output path
def outputPath = "E:/AQUA/QuAQUA/AQUA-annotation-measurements.csv"
def outputFile = new File(outputPath)

// Create the measurementExporter and start the export
def exporter  = new MeasurementExporter()
                  .imageList(imagesToExport)            // Images from which measurements will be exported
                  .separator(separator)                 // Character that separates values
//                  .includeOnlyColumns()
//                  .excludeColumns()                     // Columns are case-sensitive
                  .exportType(exportType)               // Type of objects to export
                  .exportMeasurements(outputFile)        // Start the export process

print "Done!"