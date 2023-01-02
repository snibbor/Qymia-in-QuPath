package qupath.extension.qiimia;

import qupath.lib.common.Version;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.tools.MenuTools;

public class QiimiaExtension implements QuPathExtension, GitHubProject {
	
	@Override
    public void installExtension(QuPathGUI qupath) {
		
//		var actionStartQiimiaComp = ActionTools.createAction(new QiimiaCompartmentPanel(qupath), "Start Qiimia Compartment Builder...");
//		actionStartQiimiaComp.setLongText("Make tissue compartments for quantitative immunofluorescence and immunohistochemistry images."
//    			+ "Can create tissue specific compartments for downstream analysis.");
		var actionStartQiimiaQuant = ActionTools.createAction(new QiimiaQuantPanel(qupath, "quant"), "Start Qiimia Quant...");
		actionStartQiimiaQuant.setLongText("Quantify immunofluorescence and immunohistochemistry staining in defined compartments."
				+ "Can utilize compartments and calculate intensity measurements within those compartments for experiment.");
		var actionStartQiimiaPreset = ActionTools.createAction(new QiimiaQuantPanel(qupath, "preset"), "Run Qiimia Preset...");
		actionStartQiimiaPreset.setLongText("Load Qiimia Preset to quantify immunofluorescence and immunohistochemistry staining."
				+ "Can create compartments via script and run preset quantification settings for assays.");

//    	MenuTools.addMenuItems(
//                qupath.getMenu("Extensions>Qiimia Toolkit", true),
//                actionStartQiimiaComp
//        );
		MenuTools.addMenuItems(
				qupath.getMenu("Extensions>Qiimia Toolkit", true),
				actionStartQiimiaQuant
		);
		MenuTools.addMenuItems(
				qupath.getMenu("Extensions>Qiimia Toolkit", false),
				actionStartQiimiaPreset
		);
    }

    @Override
    public String getName() {
        return "Qiimia in QuPath";
    }

    @Override
    public String getDescription() {
        return "Quantitative Immunofluorescence/Immunohistochemitry Molecular Image Analysis (Qiimia) in QuPath.\n\nQuantify immunofluorescence and chromogenic IHC in molecular compartments of tissue microarrays and whole tissue sections.";
    }
	
    
    @Override
	public GitHubRepo getRepository() {
		return GitHubRepo.create(getName(), "crobbins327", "Qiimia-in-QuPath");
	}
    
	/**
	 * Returns the version stored within this jar, because it is matched to the QuPath version.
	 */
	@Override
	public Version getQuPathVersion() {
		return Version.parse("0.4.1-rc2");
	}
}
