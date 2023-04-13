package qupath.extension.qymia.operations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableMap;

public class CombineOperation extends Operation {
	protected ObservableMap<String, String> obsMaskNameMap;
	protected ObservableMap<String, String> obsMaskDatatypeMap;
	protected List<OperationParameter> opParameterList;
	protected SimpleStringProperty maskNameChoice = new SimpleStringProperty(this, "maskNameChoice");
	protected SimpleStringProperty maskIDChoice = new SimpleStringProperty(this, "maskIDChoice");
	protected String myResultantID;
	
	private void initParameters() {
		//For the combineOps, all the parameter options are the same.
		this.controlParameters = new HashMap<String, Object>(Map.ofEntries(
				Map.entry("controlKey", maskIDChoice),
				Map.entry("controlValue", maskNameChoice),
				Map.entry("observableMap", obsMaskNameMap)
				));
		this.controlValue = maskNameChoice;
		this.inputControlType = "combobox";
		this.opParameterList = new ArrayList<OperationParameter>();
//		this.isBroken.set(true);
		this.acceptableResultantInputs = new ArrayList<>(List.of("continuous", "mask"));
		this.resultantDependencies.add(maskIDChoice);
		this.outputResultantType.bind(Bindings.when(
				inputResultantType.isEqualTo("mask").and(Bindings.valueAt(obsMaskDatatypeMap, maskIDChoice).isEqualTo("mask")))
				.then("mask")
				.otherwise("continuous")
				);
		this.toolTipText = "Choose resultant to combine.";
	}
	
	public CombineOperation(String operation, String maskID, String myResultantID, String operationText, ObservableMap<String, String> obsNameMap, ObservableMap<String, String> obsDatatypeMap) {
		super();
		this.obsMaskNameMap = obsNameMap;
		this.obsMaskDatatypeMap = obsDatatypeMap;
		this.maskIDChoice.set(maskID);
		this.myResultantID = myResultantID;
//		this.maskNameChoice.bind(Bindings.valueAt(this.obsMaskNameMap, maskIDChoice.asObject()));
		this.maskNameChoice.bind(Bindings.valueAt(this.obsMaskNameMap, maskIDChoice));
		this.operation = operation;
		this.displayText.bind(Bindings.format("%s %s", operationText, maskNameChoice));

		//Has a delete button
		this.hasDeleteButton = new SimpleBooleanProperty(true);
		
		initParameters();
	}
	public CombineOperation(String operation, String maskID, String myResultantID, ObservableMap<String, String> obsNameMap, ObservableMap<String, String> obsDatatypeMap) {
		super();
		this.obsMaskNameMap = obsNameMap;
		this.obsMaskDatatypeMap = obsDatatypeMap;
		this.maskIDChoice.set(maskID);
		this.myResultantID = myResultantID;
		this.maskNameChoice.bind(Bindings.valueAt(this.obsMaskNameMap, maskIDChoice));
		this.operation = operation;
		String operationCap = operation.substring(0,1).toUpperCase() + operation.substring(1);
		this.displayText.bind(Bindings.format("%s with %s", operationCap, maskNameChoice));

		//Has a delete button
		this.hasDeleteButton = new SimpleBooleanProperty(true);
		
		initParameters();
	}
	
	public String getMaskIDChoice() {
		return maskIDChoice.get();
	}
	
	public void setMaskIDChoice(String newMaskID) {
		maskIDChoice.set(newMaskID);
	}
	
	public SimpleStringProperty maskIDChoiceProperty() {
		return maskIDChoice;
		
	}
	
	public String getMaskNameChoice() {
		return maskNameChoice.get();
	}
	
	//Should this have a setter or just update the observable?
//	public void setMaskNameChoice(String newMaskName) {
//		maskNameChoice.set(newMaskName);
//	}
	
	public SimpleStringProperty maskNameChoiceProperty() {
		return maskNameChoice;
	}
	
	@Override
	public void setDisplayText(String displayTxt) {
		//pass, do not try to set the displayText since it is bound to the obsNameMap and optionID
		assert true;
	}
	
	@Override
	public void setOutputResultantType(String pass) {
		//pass, do not try to set the outputResultantType since it is determined by inputResultantType and maskIDChoice
		assert true;
	}
	
	@Override
	public void validateOperation() {
		if(maskIDChoice.get().equals(myResultantID)) {
			setIsBroken(true, "Choice cannot be the same resultant.");
		} else {
			setIsBroken(false, "No errors detected.");
		}
		super.validateOperation();
	}
	
	
	@Override
	public List<String> getExec() {
		switch(operation) {
			case "intersection":
				exec.add(String.format("ImageOpsExtras.Bitwise.intersect(getResultantMat(%s))", maskIDChoice.get()));
				break;
			case "union":
				exec.add(String.format("ImageOpsExtras.Bitwise.union(getResultantMat(%s))", maskIDChoice.get()));
				break;
			case "difference":
				exec.add(String.format("ImageOpsExtras.Bitwise.difference(getResultantMat(%s))", maskIDChoice.get()));
				break;
			case "add_within":
				exec.add("ImageOps.Filters.closing(5000)");
				exec.add(String.format("ImageOpsExtras.Bitwise.intersect(getResultantMat(%s))", maskIDChoice.get()));
				break;
		}
		return exec;
	}
}