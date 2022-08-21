package qupath.extension.qiima.operations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableMap;

public class FromOperation extends Operation {
	
	protected String mainDisplayText;
	protected Integer myResultantID = -1;
	protected SimpleIntegerProperty optionID = new SimpleIntegerProperty(-1);
	protected SimpleStringProperty optionName = new SimpleStringProperty();
	protected SimpleStringProperty optionDisplay = new SimpleStringProperty();
	protected SimpleStringProperty fromOption = new SimpleStringProperty();
	protected ObservableMap<Integer, String> obsNameMap;
	protected ObservableMap<String, String> obsDatatypeMap;
	
	
	private void initParameters() {
		if(operation!="from_channel") {
//			String[] splitStr = myResultantID.split("resultant");
//			myResultantID.substring(myResultantID.lastIndexOf("resultant") + 1);
//			System.out.println(Integer.parseInt(splitStr[splitStr.length-1]));
//			this.obsNameMap.remove(Integer.parseInt(splitStr[splitStr.length-1]));
			this.fromOption.bind(Bindings.format("resultant%d", this.optionID.asObject()));
			this.resultantDependencies.add(fromOption);
			inputResultantType.bind(Bindings.valueAt(this.obsDatatypeMap, Bindings.format("resultant%d", this.optionID.asObject())));
			this.toolTipText = "Choose a resultant to start from.";
		} else {
			this.fromOption.bind(Bindings.format("channel%d", this.optionID.asObject()));
			setInputResultantType("continuous");
			this.toolTipText = "Choose a channel to start from.";
		}
		this.controlParameters = new HashMap<String, Object>(Map.ofEntries(
				Map.entry("controlKey", optionID),
				Map.entry("controlValue", optionName),
				Map.entry("observableMap", obsNameMap)
				));
		this.controlValue = optionName;
		this.inputControlType = "combobox";
		this.acceptableResultantInputs = new ArrayList<>(List.of("continuous", "mask"));
	}
	
	public FromOperation(String operation, String mainDisplayText, Integer optionID, Integer myResultantID, ObservableMap<Integer, String> obsNameMap, ObservableMap<String, String> obsDatatypeMap) {
		this.operation = operation;
		//Essentially this is an identity operation...
		outputResultantType.bind(inputResultantType);
		this.optionID = new SimpleIntegerProperty(optionID);
		this.myResultantID = myResultantID;
		this.obsNameMap = obsNameMap;
		this.obsDatatypeMap = obsDatatypeMap;
		
		this.optionName.bind(Bindings.valueAt(this.obsNameMap, this.optionID.asObject()));
		this.displayText.bind(Bindings.format("%s %s", mainDisplayText, Bindings.valueAt(this.obsNameMap, this.optionID.asObject())));
		//Has a delete button
		this.hasDeleteButton = new SimpleBooleanProperty(false);
		
		initParameters();
	}
	
	
	public FromOperation(String operation, String mainDisplayText, String optionDisplay) {
		this.operation = operation;
		//Essentially this is an identity operation...
		outputResultantType.bind(inputResultantType);
		setInputResultantType("mask");
		this.optionDisplay = new SimpleStringProperty(optionDisplay);
		this.displayText.bind(Bindings.format("%s %s", mainDisplayText, this.optionDisplay.getValue()));
		//This does not have an inputControl
		this.inputControlType = null;
		//Has a delete button
		this.hasDeleteButton = new SimpleBooleanProperty(false);
	}
	
	public String getFromOption() {
		if(optionID.get() != -1) {
			return String.format("%s:%s:%s", operation, String.valueOf(optionID.get()), obsNameMap.get(optionID.get()));
		} else {
			return String.format("%s:%s:%s", operation, "path", optionDisplay.get());
		}
	}


	@Override
	public String getDisplayText() {
		return displayText.get();
	}
	
	@Override
	public void setDisplayText(String displayTxt) {
		//pass, do not try to set the displayText since it is bound to the obsNameMap and optionID
		assert true;
	}
	
	@Override
	public SimpleStringProperty displayTextProperty() {
		return displayText;
	}
	
	
	@Override
	public Collection<? extends Operation> getChildren() {
		return Collections.emptyList();
	}
	
	@Override
	public void validateOperation() {
		System.out.println("validating from option....");
		if(operation.equals("from_resultant")) {
			if(myResultantID==optionID.get()) {
				setIsBroken(true, "Start choice cannot be the same resultant.");
			} else {
				setIsBroken(false, "No errors detected.");
			}
		}
		super.validateOperation();
	}

}
