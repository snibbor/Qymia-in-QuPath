package qupath.extension.companalysis.operations.control_display;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableColumn.CellEditEvent;
import javafx.scene.control.TreeTablePosition;
import javafx.scene.control.TreeTableView;
import qupath.extension.companalysis.operations.Operation;

public class ControlCell extends TreeTableCell<Operation, Object> {
    private EditingControlProvider controlProvider = new CellEditingControlProvider();

    public ControlCell() {
        super();
    }

    @Override
    protected void updateItem(Object item, boolean empty) {
        super.updateItem(item, empty);
        
        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
//        	Operation field = getTableRow().getItem();
            setText(null);
            setGraphic(controlProvider.getControl(this));
		}
    }
    
    //Need to trigger commit edit on handle for custom control.... somehow pass the cell into the control to trigger commit edit?
    @Override
	public void commitEdit(Object newValue) {
        Operation field = getTableRow().getItem();
//      //Check if instance of OperationParameter? otherwise don't do this commit?
//      if(field instanceof OperationParameter) {
        controlProvider.updateFromControl(field);
//      }
        super.commitEdit(getItem());
        
        final TreeTableView<Operation> table = getTreeTableView();
        // JDK-8187307: fire the commit after updating cell's editing state
        if (getTableColumn() != null) {
            // Inform the TreeTableColumn of the edit being ready to be committed.
        	TreeTableColumn<Operation, Object> column = getTableColumn();
            CellEditEvent<Operation, Object> editEvent = new CellEditEvent<Operation, Object>(
                    table,
                    new TreeTablePosition<Operation, Object>(table, getIndex(), column),
                    TreeTableColumn.<Operation, Object>editCommitEvent(),
                    newValue
                    );

            Event.fireEvent(getTableColumn(), editEvent);
        }

        // update the item within this cell, so that it represents the new value
        updateItem(newValue, false);

        if (table != null) {
            // reset the editing cell on the TableView
            table.edit(-1, null);

            // request focus back onto the table, only if the current focus
            // owner has the table as a parent (otherwise the user might have
            // clicked out of the table entirely and given focus to something else.
            // It would be rude of us to request it back again.
            requestFocusOnControlOnlyIfCurrentFocusOwnerIsChild(table);
        }
    }
    
    private static void requestFocusOnControlOnlyIfCurrentFocusOwnerIsChild(Control c) {
        Scene scene = c.getScene();
        final Node focusOwner = scene == null ? null : scene.getFocusOwner();
        if (focusOwner == null) {
            c.requestFocus();
        } else if (! c.equals(focusOwner)) {
            Parent p = focusOwner.getParent();
            while (p != null) {
                if (c.equals(p)) {
                    c.requestFocus();
                    break;
                }
                p = p.getParent();
            }
        }
    }
    
    
}
