import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.geometry.Pos
import javafx.scene.control.TableView
import javafx.scene.control.CheckBox
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.control.ScrollPane
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import javafx.scene.input.MouseEvent
import javafx.beans.value.ChangeListener
import qupath.lib.gui.QuPathGUI

import static qupath.lib.gui.scripting.QPEx.getCurrentViewer


//Objective: A quicker way to show only certain classes and hide all others
//ANY GROUP CLASS CHECKING or UNCHECKED OVERWRITE ANY SINGLE CLASS CHANGES
//Written for 0.2.0 and 0.3.0, code fix by @Mark_Zaidi and @petebankhead regarding access to base class names
//Removed tokenization, ending backwards compatability with 0.1.2 multiplexing.

//Find all classifications of detections

/*****************************************
If you have subcellular objects, you may want 
to change this to getDetectionObjects() so that
you can turn those on and off as well
*****************************************/

//def classifications = new ArrayList<>(  getCellObjects().collect {it?.getPathClass()} as Set)
def classifications = new ArrayList<>(  getDetectionObjects().collect {it?.getPathClass()} as Set)
//def classifications = new ArrayList<>(  getAnnotationObjects().collect {it?.getPathClass()} as Set)

/////////////////////////////////////////////////////////////

List<String> classNames = new ArrayList<String>()
classifications.each{
    classNames<< it.toString()
}

Set baseClasses = []
classifications.each{
    getCurrentViewer().getOverlayOptions().hiddenClassesProperty().add(it)
    PathClassTools.splitNames(it).each{str->
        baseClasses << str.trim()
    }
}

print baseClasses

baseList = baseClasses
//Find strings with duplicates in baseClasses
//baseList = baseClasses.countBy{it}.grep{it.value > 1}.collect{it.key}

//Set up GUI
int col = 0
int row = 0
int textFieldWidth = 120
int labelWidth = 150
def gridPane = new GridPane()
gridPane.setPadding(new Insets(10, 10, 10, 10));
gridPane.setVgap(2);
gridPane.setHgap(10);

ScrollPane scrollPane = new ScrollPane(gridPane)
scrollPane.setFitToHeight(true);
BorderPane border = new BorderPane(scrollPane)
border.setPadding(new Insets(15));

//Separately set up a checkbox for All classes
allOn = new CheckBox("All")
allOn.setId("All")
gridPane.add( allOn, 1, row++, 1,1)

row = 1
ArrayList<CheckBox> boxes = new ArrayList(classifications.size());
//Create the checkboxes for each class

for (i=0; i<classifications.size();i++){
    name = classNames[i].toString()
    if (name == "null"){name = "None"}
    cb = new CheckBox(name)
    cb.setId(name)
    boxes.add(cb)
    gridPane.add( cb, col, row++, 1,1)
}


//Create checkboxes for base classes, defined as some string that showed up in more than one class entry
ArrayList<CheckBox> baseBoxes = new ArrayList(baseList.size());
row = 2
for (i=0; i<baseList.size();i++){
    cb = new CheckBox(baseList[i])
    cb.setId(baseList[i])
    baseBoxes.add(cb)
    gridPane.add( cb, 1, row++, 1,1)
}
//behavior for all single class checkboxes
//I can't seem to check which checkbox is selected when they are created dynamically, so the results are updated for all classes
for (c in boxes){
    c.selectedProperty().addListener({o, oldV, newV ->
        firstCol = gridPane.getChildren().findAll{gridPane.getColumnIndex(it) == 0}
        for (n in firstCol){
            if (n.isSelected()){
                getCurrentViewer().getOverlayOptions().hiddenClassesProperty().remove(getPathClass(n.getId()))
            }else {getCurrentViewer().getOverlayOptions().hiddenClassesProperty().add(getPathClass(n.getId()))}
        }
    } as ChangeListener)
}
//behavior for base class checkboxes
//I can't easily figure out which checkbox was last checked, so this overwrites any single class checkboxes that were selected or unselected
for (c in baseBoxes){
    c.selectedProperty().addListener({o, oldV, newV ->
        //verify that we are in the second column, and the nodes are selected
        secondColSel = gridPane.getChildren().findAll{gridPane.getColumnIndex(it) == 1 && it.isSelected()}
        secondColUnSel = gridPane.getChildren().findAll{gridPane.getColumnIndex(it) == 1 && !it.isSelected()}
        for (n in secondColUnSel){
            batch = gridPane.getChildren().findAll{gridPane.getColumnIndex(it) == 0 && it.getId().contains(n.getId())}
            batch.each{
                it.setSelected(false)
                getCurrentViewer().getOverlayOptions().hiddenClassesProperty().add(getPathClass(it.getId()))
            }
        }
        for (n in secondColSel){
            
                batch = gridPane.getChildren().findAll{gridPane.getColumnIndex(it) == 0 && it.getId().contains(n.getId())}
                batch.each{
                    it.setSelected(true)
                    getCurrentViewer().getOverlayOptions().hiddenClassesProperty().remove(getPathClass(it.getId()))
                }
                
        }

        
    } as ChangeListener)
}


//Turn all on or off based on the All checkbox
allOn.selectedProperty().addListener({o, oldV, newV ->

    if (!allOn.isSelected()){
        classifications.each{
            getCurrentViewer().getOverlayOptions().hiddenClassesProperty().add(it)
        }
        gridPane.getChildren().each{
            it.setSelected(false)
        }
    }else {    
        classifications.each{
            getCurrentViewer().getOverlayOptions().hiddenClassesProperty().remove(it)
        }
        gridPane.getChildren().each{
            it.setSelected(true)
        }
    } 
}as ChangeListener)



//Some stuff that controls the dialog box showing up. I don't really understand it but it is needed.
Platform.runLater {

    def stage = new Stage()
    stage.initOwner(QuPathGUI.getInstance().getStage())
    stage.setScene(new Scene( border))
    stage.setTitle("Select classes to display")
    stage.setWidth(800);
    stage.setHeight(500);
    stage.setResizable(true);
    stage.show()

}