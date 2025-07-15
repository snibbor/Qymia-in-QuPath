package qupath.extension.qymia;

import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;
import javafx.scene.control.Menu;

public class QymiaExtension implements QuPathExtension, GitHubProject {
	
	@Override
    public void installExtension(QuPathGUI qupath) {
        var actionStartQymiaQuant = ActionTools.createAction(new QymiaQuantPanel(qupath, "quant"), "Start Qymia Quant…");
        actionStartQymiaQuant.setLongText(
            "Quantify immunofluorescence and immunohistochemistry staining in defined compartments. "
          + "Can utilize compartments and calculate intensity measurements within those compartments for experiment."
        );

        // var actionStartQymiaPreset = ActionTools.createAction(new QymiaQuantPanel(qupath, "preset"), "Run Qymia Preset…");
        // actionStartQymiaPreset.setLongText(
        //     "Load Qymia Preset to quantify immunofluorescence and immunohistochemistry staining. "
        //   + "Can create compartments via script and run preset quantification settings for assays."
        // );

        Menu menu = qupath.getMenu("Extensions>Qymia Toolkit", true);

        MenuTools.addMenuItems(menu,
            actionStartQymiaQuant
            // actionStartQymiaPreset
        );
    }
		
    @Override public String getName()        { return "Qymia in QuPath"; }
    @Override public String getDescription() { return "Quantitative IF/IHC analysis toolkit for QuPath"; }
    @Override public GitHubRepo getRepository() {
		return GitHubRepo.create(getName(), "snibbor", "Qymia-in-QuPath");
	}
    @Override public Version getQuPathVersion() {
		return Version.parse("0.6.0");
	}
}
