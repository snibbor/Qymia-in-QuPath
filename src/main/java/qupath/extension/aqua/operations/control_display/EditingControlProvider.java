package qupath.extension.aqua.operations.control_display;

import javafx.scene.control.Control;
import qupath.extension.aqua.operations.Operation;

public interface EditingControlProvider {
	
	public Control getControl(ControlCell cell);

	public void updateFromControl(Operation field);

}
