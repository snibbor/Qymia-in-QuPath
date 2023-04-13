package qupath.extension.qymia.operations.control_display;

import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.util.Callback;
import qupath.extension.qymia.operations.Operation;

public class ControlCellFactory
	implements Callback<TreeTableColumn<Operation, Object>, TreeTableCell<Operation, Object>> {

	    @Override
	    public TreeTableCell<Operation, Object> call(TreeTableColumn<Operation, Object> param) {
	        return new ControlCell();
	    }
}
