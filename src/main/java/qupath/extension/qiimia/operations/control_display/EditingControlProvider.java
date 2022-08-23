package qupath.extension.qiimia.operations.control_display;

import javafx.scene.control.Control;
import qupath.extension.qiimia.operations.Operation;

public interface EditingControlProvider {
	
	public Control getControl(ControlCell cell);

	public void updateFromControl(Operation field);

}
