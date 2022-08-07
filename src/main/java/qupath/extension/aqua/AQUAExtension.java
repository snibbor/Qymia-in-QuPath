package qupath.extension.aqua;

import qupath.lib.common.Version;
//import qupath.lib.extension.svg.SvgExportCommand;
//import qupath.lib.extension.svg.SvgExportCommand.SvgExportType;
//import qupath.lib.extension.svg.SvgExportCommand.SvgExportType;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.tools.MenuTools;
//import qupath.extension.aqua.AQUAPanel;
//import qupath.extension.aqua.CompQuantPanel;

public class AQUAExtension implements QuPathExtension, GitHubProject {
	
	@Override
    public void installExtension(QuPathGUI qupath) {
		
		var actionStartCompMaker = ActionTools.createAction(new AQUAPanel(qupath), "Start Compartment Maker...");
		actionStartCompMaker.setLongText("Make tissue compartments for quantitative immunofluorescence and immunohistochemistry images."
    			+ "Can create tissue specific compartments for downstream analysis.");
		var actionStartCompQuant = ActionTools.createAction(new CompQuantPanel(qupath), "Start CompQuant...");
		actionStartCompQuant.setLongText("Quantify immunofluorescence and immunohistochemistry staining in defined compartments."
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
                qupath.getMenu("Extensions>CompAnalysis", true),
                actionStartCompMaker
        );
		MenuTools.addMenuItems(
				qupath.getMenu("Extensions>CompAnalysis", false),
				actionStartCompQuant
		);
//    	MenuTools.addMenuItems(
//                qupath.getMenu("Extensions>AQUAnalysis>Run preset...", true),
//                System.out.println("Running preset protocol for AQUAnalysis...")
//        );
    	
    }

    @Override
    public String getName() {
        return "Molecular Compartment Analysis in QuPath";
    }

    @Override
    public String getDescription() {
        return "Quantify immunofluorescence and chromogenic IHC in compartments.";
    }
	
    
    @Override
	public GitHubRepo getRepository() {
		return GitHubRepo.create(getName(), "crobbins327", "qupath-extension-aqua");
	}
    
	/**
	 * Returns the version stored within this jar, because it is matched to the QuPath version.
	 */
	@Override
	public Version getQuPathVersion() {
		return Version.parse("0.4.0-rc2");
	}
}
