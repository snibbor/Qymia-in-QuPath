package qupath.extension.qiima;

import qupath.lib.common.Version;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.tools.MenuTools;

public class QIIMAExtension implements QuPathExtension, GitHubProject {
	
	@Override
    public void installExtension(QuPathGUI qupath) {
		
		var actionStartQIIMAComp = ActionTools.createAction(new QIIMACompartmentPanel(qupath), "Start QIIMA Compartment Builder...");
		actionStartQIIMAComp.setLongText("Make tissue compartments for quantitative immunofluorescence and immunohistochemistry images."
    			+ "Can create tissue specific compartments for downstream analysis.");
		var actionStartQIIMAQuant = ActionTools.createAction(new QIIMAQuantPanel(qupath), "Start QIIMA-Quant...");
		actionStartQIIMAQuant.setLongText("Quantify immunofluorescence and immunohistochemistry staining in defined compartments."
				+ "Can utilize compartments and calculate intensity measurements within those compartments for experiment.");
//		System.out.println("Starting AQUAnalysis...");
    	
//    	var actionExport = ActionTools.createAction(new SvgExportCommand(qupath, SvgExportType.SELECTED_REGION), "Rendered SVG");
//    	actionExport.disabledProperty().bind(qupath.imageDataProperty().isNull());
//    	actionExport.setLongText("Export the current selected region as a rendered (RGB) SVG image. "
//    			+ "Any annotations and ROIs will be stored as vectors, which can later be adjusted in other software.");
//    	var actionSnapshot = ActionTools.createAction(new SvgExportCommand(qupath, SvgExportType.VIEWER_SNAPSHOT), "Current viewer content (SVG)");
//    	actionSnapshot.setLongText("Export an RGB snapshot of the current viewer content as an SVG image. "
//    			+ "Any annotations and ROIs will be stored as vectors, which can later be adjusted in other software.");
//    	
    	MenuTools.addMenuItems(
                qupath.getMenu("Extensions>QIIMA Toolkit", true),
                actionStartQIIMAComp
        );
		MenuTools.addMenuItems(
				qupath.getMenu("Extensions>QIIMA Toolkit", false),
				actionStartQIIMAQuant
		);
//    	MenuTools.addMenuItems(
//                qupath.getMenu("Extensions>AQUAnalysis>Run preset...", true),
//                System.out.println("Running preset protocol for AQUAnalysis...")
//        );
    	
    }

    @Override
    public String getName() {
        return "Quantitative Immunofluorescence/Immunohistochemitry Molecular Analysis in QuPath";
    }

    @Override
    public String getDescription() {
        return "Quantify immunofluorescence and chromogenic IHC in molecular compartments.";
    }
	
    
    @Override
	public GitHubRepo getRepository() {
		return GitHubRepo.create(getName(), "crobbins327", "QIIMA-in-QuPath");
	}
    
	/**
	 * Returns the version stored within this jar, because it is matched to the QuPath version.
	 */
	@Override
	public Version getQuPathVersion() {
		return Version.parse("0.4.0-rc2");
	}
}
