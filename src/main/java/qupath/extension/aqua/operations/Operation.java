package qupath.extension.aqua.operations;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.WritableIntegerValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.control.TreeItem;

//Should be an abstract class and not constructed directly
public abstract class Operation implements Serializable {
	protected SimpleStringProperty displayText = new SimpleStringProperty();
	protected String inputControlType;
	@SuppressWarnings("rawtypes")
	protected Property controlValue;
	protected SimpleBooleanProperty hasDeleteButton = new SimpleBooleanProperty();
	protected String operation;
	protected List<String> exec;
	//If a dependency for an operation was deleted or cannot be found (mask or channelID)
	protected SimpleBooleanProperty isBroken = new SimpleBooleanProperty(false);
	protected SimpleStringProperty brokenErrorText = new SimpleStringProperty("No errors detected.");
	protected String toolTipText = null;
	protected Map<String, Object> controlParameters = new HashMap<String, Object>();
	protected List<StringProperty> resultantDependencies = new ArrayList<>();
	protected List<String> acceptableResultantInputs = new ArrayList<>(List.of("continuous", "mask"));
	protected SimpleStringProperty inputResultantType = new SimpleStringProperty();
	protected SimpleStringProperty outputResultantType = new SimpleStringProperty();
	
	
	private static final Logger logger = LoggerFactory.getLogger(Operation.class);
	
	
	public Operation (String operation, String displayText, String inputControlType, String controlValue, boolean hasDeleteButton) {
		this.operation = operation;
		this.displayText = new SimpleStringProperty(displayText);
		this.inputControlType = inputControlType;
		this.hasDeleteButton = new SimpleBooleanProperty(hasDeleteButton);
		this.controlValue = new SimpleObjectProperty<>(controlValue);
		this.exec = null;
	}
	
	public Operation () {
		
	}
	
	public String getOperation() {
		return operation;
	}
	
	public String getDisplayText() {
		return displayText.get();
	}
	
	public void setDisplayText(String displayTxt) {
		displayText.set(displayTxt);
	}
	
	public SimpleStringProperty displayTextProperty() {
		return displayText;
	}
	
	public String getInputControlType() {
		return inputControlType;
	}
	
	public void setInputControlType(String inputControlType) {
		this.inputControlType = inputControlType;
	}
	
	public Object getControlValue() {
		return controlValue.getValue();
	}
	
	@SuppressWarnings("unchecked")
	public void setControlValue(Object value) {
		if(controlValue instanceof IntegerProperty) {
			controlValue.setValue(Integer.parseInt(value.toString()));
		} else if(controlValue instanceof DoubleProperty) {
			controlValue.setValue(Double.parseDouble(value.toString()));
		} else if(controlValue instanceof LongProperty) {
			controlValue.setValue(Long.parseLong(value.toString()));
		} else if(controlValue instanceof FloatProperty) {
			controlValue.setValue(Float.parseFloat(value.toString()));
		} else if(controlValue instanceof StringProperty) {
			controlValue.setValue(value.toString());
		} else if(controlValue instanceof BooleanProperty) {
			controlValue.setValue(Boolean.valueOf(value.toString()));
		} else if(controlValue instanceof ObjectProperty) {
			controlValue.setValue(value);
		}
		logger.debug(operation+": set control value: "+controlValue.getValue().toString());
		logger.debug(controlValue.getValue().getClass().toString());
	}
	
	public Property<?> controlValueProperty() {
		return controlValue;
	}
	
	public boolean getHasDelete() {
		return hasDeleteButton.get();
	}
	
	public void setHasDelete(boolean hasDelete) {
		hasDeleteButton.set(hasDelete);
	}
	
	public SimpleBooleanProperty hasDeleteProperty() {
		return hasDeleteButton;
	}
	
	public String getInputResultantType() {
		return inputResultantType.get();
	}
	
	public void setInputResultantType(String inputType) {
		if (!acceptableResultantInputs.contains(inputType)) {
			setIsBroken(true, String.format("Input resultant type [%s] is not valid for this operation [%s]", inputType, operation));
		} else {
			setIsBroken(false, "No errors detected.");
		}
		inputResultantType.set(inputType);;
	}
	
	public String getOutputResultantType() {
		return outputResultantType.get();
	}
	
	public void setOutputResultantType(String outputType) {
		outputResultantType.set(outputType);;
	}
	
	public SimpleStringProperty outputResultantTypeProperty() {
		return outputResultantType;
	}
	
	//Can override this for each, but this is in general what is desired
	public void validateOperation() {
		//Check control value
		if(controlValue.getValue()==null) {
			setIsBroken(true, "Control value is null.");
		}
		//Check input/output
		else if(!acceptableResultantInputs.contains(inputResultantType.get())) {
			setIsBroken(true, String.format("Input resultant type [%s] is not valid for this operation [%s]", inputResultantType, operation));
		}
		//Check dependencies?
		//binding loops/circular references
		else if(!resultantDependencies.isEmpty()) {
			
		}
		
		else {
			setIsBroken(false, "No errors detected.");
		}
	}
	
	public boolean getIsBrokenBool() {
		return isBroken.get();
	}
	
	public String getIsBrokenText() {
		return brokenErrorText.get();
	}
	
	public String getToolTipText() {
		return toolTipText;
	}
	
	public void setIsBroken(boolean brokenBool, String brokenText) {
		isBroken.set(brokenBool);
		brokenErrorText.set(brokenText);
		logger.debug(brokenText);
	}
	
	//Override this depending on the children for the node
	public Collection<? extends Operation> getChildren() {
		return Collections.emptyList();
	}
	
	//Override this for each operation class depending on the code to execute
	public List<String> getExec() {
		this.exec = generateExec();
		return exec;
	}
	
	//Override this for each operation class depending on the code to execute
	List<String> generateExec() {
		return exec;
	}
	
	public Map<String, Object> getControlParameters() {
		return controlParameters;
	}
	
	public void setComboValue(String comboValue, Integer selectedIndex) {
		logger.debug(operation + " : Set combovalue = " + comboValue + ", index = " + selectedIndex);
		if(comboValue==null || selectedIndex==-1) {
			return;
		}
		if(controlParameters.containsKey("observableMap")) {
			@SuppressWarnings("unchecked")
			ObservableMap<?, String> obsMap = (ObservableMap<?, String>) controlParameters.get("observableMap");
			logger.debug(obsMap.toString());
			//Could be Integer or String Property... 
			Property<?> obsKey = (Property<?>) controlParameters.get("controlKey");
//			logger.debug(obsKey.getBean().toString());
//			logger.debug(obsKey.getName());
			//Selected index should correspond to the appropriate key in observableMap
			List<?> keys = new ArrayList(obsMap.keySet());
			//Check that selected index key corresponds with obsMap(key) == comboValue
			//What if selected index was outside of keys.size()? 
			//Perhaps obsList for combobox did not update when mask was deleted... does this happen?
			if(comboValue.equals(obsMap.get(keys.get(selectedIndex)))) {
				if (obsKey instanceof SimpleIntegerProperty) {
					((SimpleIntegerProperty) obsKey).set((Integer) keys.get(selectedIndex));
				} else if (obsKey instanceof SimpleStringProperty) {
					((SimpleStringProperty) obsKey).set((String) keys.get(selectedIndex));
				}
				
//				if(obsKey.getBean() instanceof CombineOperation) {
//					logger.debug(String.valueOf(((CombineOperation) obsKey.getBean()).getMaskIDChoice()));
//					logger.debug(((CombineOperation) obsKey.getBean()).getMaskNameChoice());
//				}
				//Check that binding between controlKey and controlValue has updated...
				logger.debug("New controlValue : " + controlValue.getValue().toString());
				logger.debug("Output resultant type : " + outputResultantType.get());
			} else {
				//Scan for first instance that matches comboValue in keySet and update controlKey
				logger.debug("Selected index did not correspond with controlKey/Value");
				logger.debug("Scanning for first instance of controlKey that matches comboValue...");
				//Could use i to compare with selected index, however if it didn't match the first time maybe the item was not selected according to javafx...
				int i = 0;
				for (Object key : obsMap.keySet()) {
					if(comboValue.equals(obsMap.get(key))) {
						if (obsKey instanceof IntegerProperty) {
							((IntegerProperty) obsKey).setValue((Integer) key);
						} else if (obsKey instanceof StringProperty) {
							((StringProperty) obsKey).setValue((String) key);
						}
						//Check that binding between controlKey and controlValue has updated...
						logger.debug("New controlValue : " + controlValue.getValue().toString());
					}
					i++;
				}
			}
		} else {
			//Instead the comboChoices come from an observableList and there is not controlKey needing to be set...
			//Set the controlValue directly based on choice
			setControlValue(comboValue);
		}
	}
	
	@SuppressWarnings("unchecked")
	public ObservableList<String> getComboChoices(){
		logger.debug(operation + " getting combo choices...");
		if(controlParameters.containsKey("observableMap")) {
			//Need to return the observableMap values as the combobox choices
			ObservableMap<?, String> obsMap = (ObservableMap<?, String>) controlParameters.get("observableMap");
			logger.debug(obsMap.toString());
			ObservableList<String> obsList = FXCollections.observableArrayList(obsMap.values());
			logger.debug(obsList.toString());
			//Have to bind the map to the list with a MapChangeListener because there is no easy property binding or synchronization option for map.values() to list.....
			obsMap.addListener((MapChangeListener<? super Object, ? super String>) new MapChangeListener<Object, String>(){
				@Override
				public void onChanged (javafx.collections.MapChangeListener.Change<? extends Object, ? extends String> change) {
					if (change.toString().contains(" replaced by ")) {
						logger.debug("Replaced mask name...");
						logger.debug(change.getValueAdded());
						logger.debug(change.getValueRemoved());
						logger.debug(change.getKey().toString());
						//Find the index of the valueRemoved and then replace with valueAdded
						//The mask name is the critical element as all duplicate mask names will be combined into the same path class at the end.
						//MaskID is mainly for the UI element IDs...
						List<?> keys = new ArrayList(obsMap.keySet());
						obsList.set(keys.indexOf(change.getKey()), change.getValueAdded());
						//This breaks the comboBox if it is set since the value becomes null and a commitEdit() is triggered...
//						obsList.clear();
//						obsList.addAll(obsMap.values());
					} else if(change.wasAdded()) {
						obsList.add(change.getValueAdded());
					} else if(change.wasRemoved()) {
						obsList.remove(change.getValueRemoved());
					}
				}		
			});
			
			return obsList;
		} else {
			if(!(controlParameters.get("comboChoices") instanceof ObservableList)) {
				ObservableList<String> comboChoices = FXCollections.observableArrayList((List<String>) controlParameters.get("comboChoices"));
				return comboChoices;
			}
			return (ObservableList<String>) controlParameters.get("comboChoices");
		}	
	}
}
