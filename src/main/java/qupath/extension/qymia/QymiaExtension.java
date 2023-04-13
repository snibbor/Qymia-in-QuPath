package qupath.extension.qymia;

import qupath.lib.common.Version;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.tools.MenuTools;

public class QymiaExtension implements QuPathExtension, GitHubProject {
	
	@Override
    public void installExtension(QuPathGUI qupath) {
		
//		var actionStartQiimiaComp = ActionTools.createAction(new QiimiaCompartmentPanel(qupath), "Start Qiimia Compartment Builder...");
//		actionStartQiimiaComp.setLongText("Make tissue compartments for quantitative immunofluorescence and immunohistochemistry images."
//    			+ "Can create tissue specific compartments for downstream analysis.");
		var actionStartQymiaQuant = ActionTools.createAction(new QymiaQuantPanel(qupath, "quant"), "Start Qymia Quant...");
		actionStartQymiaQuant.setLongText("Quantify immunofluorescence and immunohistochemistry staining in defined compartments."
				+ "Can utilize compartments and calculate intensity measurements within those compartments for experiment.");
		var actionStartQymiaPreset = ActionTools.createAction(new QymiaQuantPanel(qupath, "preset"), "Run Qymia Preset...");
		actionStartQymiaPreset.setLongText("Load Qymia Preset to quantify immunofluorescence and immunohistochemistry staining."
				+ "Can create compartments via script and run preset quantification settings for assays.");

//    	MenuTools.addMenuItems(
//                qupath.getMenu("Extensions>Qiimia Toolkit", true),
//                actionStartQiimiaComp
//        );
		MenuTools.addMenuItems(
				qupath.getMenu("Extensions>Qymia Toolkit", true),
				actionStartQymiaQuant
		);
		MenuTools.addMenuItems(
				qupath.getMenu("Extensions>Qymia Toolkit", false),
				actionStartQymiaPreset
		);
    }

    @Override
    public String getName() {
        return "Qymia in QuPath";
    }

    @Override
    public String getDescription() {
        return "Quantitative Immunofluorescence/Immunohistochemitry Molecular Image Analysis (Qymia) in QuPath.\n\nQuantify immunofluorescence and chromogenic IHC in molecular compartments of tissue microarrays and whole tissue sections.";
    }
	
    
    @Override
	public GitHubRepo getRepository() {
		return GitHubRepo.create(getName(), "crobbins327", "Qymia-in-QuPath");
	}
    
	/**
	 * Returns the version stored within this jar, because it is matched to the QuPath version.
	 */
	@Override
	public Version getQuPathVersion() {
		return Version.parse("0.4.3-rc2");
	}
}
