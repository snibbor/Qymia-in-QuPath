package qupath.extension.qiima.operations.control_display;

import javafx.scene.control.Control;
import qupath.extension.qiima.operations.Operation;

public interface EditingControlProvider {
	
	public Control getControl(ControlCell cell);

	public void updateFromControl(Operation field);

}
