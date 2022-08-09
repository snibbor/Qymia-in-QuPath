package qupath.extension.aqua.operations.groovy_scripts
//clear all annotations
// removeObjects(getAnnotationObjects(), true);
// removeObjects(getDetectionObjects(), true);


// create annotations for the jurkats and use a different classifier for them because they don't express CK
// def server = getCurrentServer();
// var plane = ImagePlane.getDefaultPlane()
// def box_w = 1024+150+75;
// def box_h = 1024+150+75;
// def MPP = server.getPixelCalibration().getAveragedPixelSizeMicrons();

// def jurkatROI1 = ROIs.createRectangleROI(0, 0, box_w/MPP, box_h/MPP, plane);
// def jurkatAnnot1 = PathObjects.createAnnotationObject(jurkatROI1, getPathClass(null));
// addObject(jurkatAnnot1);

// def jurkatROI2 = ROIs.createRectangleROI(server.getWidth()-box_w/MPP, server.getHeight()-box_h/MPP, box_w/MPP, box_h/MPP, plane);
// def jurkatAnnot2 = PathObjects.createAnnotationObject(jurkatROI2, getPathClass(null));
// addObject(jurkatAnnot2);

selectObjects(getAnnotationObjects());
createAnnotationsFromPixelClassifier("Tumor Nuc", 0.0, 0.0)

resetSelection();
createAnnotationsFromPixelClassifier("Tumor", 0.0, 0.0)

resetSelection();
createAnnotationsFromPixelClassifier("Nuclear", 0.0, 0.0)

if (!isTMADearrayed()) {
	runPlugin('qupath.imagej.detect.dearray.TMADearrayerPluginIJ', '{"coreDiameterMM": 1.0,  "labelsHorizontal": "1-100",  "labelsVertical": "A-Z",  "labelOrder": "Row first",  "densityThreshold": 5,  "boundsScale": 105}');
	return;
}

def tmaGrid = getCurrentHierarchy().getTMAGrid();
tmaGrid.getTMACoreList().each{it -> it.setPathClass(getPathClass('ROI'))};

