package qupath.extension.qiima.operations;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

public class OperationParameter extends Operation {

//	protected Map<String, Object> controlParameters = new HashMap<String, Object>();
	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(OperationParameter.class);
	
	public OperationParameter(String operation, String displayText, String inputControlType, Property<?> controlValue) {
		this.operation = String.format("parameter:%s", operation);
		//OperationParameter specific properties
		this.displayText = new SimpleStringProperty(displayText);
		this.inputControlType = inputControlType;
		this.hasDeleteButton = new SimpleBooleanProperty(false);
		//Property that depends on parent operation data
		this.controlValue = controlValue;
		//Extra parameters or observables helpful for setting up the control object
		this.controlParameters = Collections.emptyMap();
	}
	
	//For multiple arguments/kwargs
	public OperationParameter(String operation, String displayText, String inputControlType, Map<String, Object> controlParameters) {
		this.operation = String.format("parameter:%s", operation);
		this.displayText = new SimpleStringProperty(displayText);
		this.inputControlType = inputControlType;
		this.hasDeleteButton = new SimpleBooleanProperty(false);
		this.controlValue = (Property) controlParameters.get("controlValue");
		this.controlParameters = controlParameters;
	}
	
	@Override
	public Collection<? extends Operation> getChildren() {
		return Collections.emptyList();
	}
	
//	public Map<String, Object> getControlParameters() {
//		return controlParameters;
//	}
//	
//	public void setComboValue(String comboValue, Integer selectedIndex) {
//		logger.debug(operation + " : Set combovalue = " + comboValue + ", index = " + selectedIndex);
//		if(comboValue==null || selectedIndex==-1) {
//			return;
//		}
//		if(controlParameters.containsKey("observableMap")) {
//			@SuppressWarnings("unchecked")
//			ObservableMap<?, String> obsMap = (ObservableMap<?, String>) controlParameters.get("observableMap");
//			logger.debug(obsMap.toString());
//			//Could be Integer or String Property... 
//			Property<?> obsKey = (Property<?>) controlParameters.get("controlKey");
////			logger.debug(obsKey.getBean().toString());
////			logger.debug(obsKey.getName());
//			//Selected index should correspond to the appropriate key in observableMap
//			List<?> keys = new ArrayList(obsMap.keySet());
//			//Check that selected index key corresponds with obsMap(key) == comboValue
//			//What if selected index was outside of keys.size()? 
//			//Perhaps obsList for combobox did not update when mask was deleted... does this happen?
//			if(comboValue.equals(obsMap.get(keys.get(selectedIndex)))) {
//				if (obsKey instanceof SimpleIntegerProperty) {
//					((SimpleIntegerProperty) obsKey).set((Integer) keys.get(selectedIndex));
//				} else if (obsKey instanceof SimpleStringProperty) {
//					((SimpleStringProperty) obsKey).set((String) keys.get(selectedIndex));
//				}
//				
////				if(obsKey.getBean() instanceof CombineOperation) {
////					logger.debug(String.valueOf(((CombineOperation) obsKey.getBean()).getMaskIDChoice()));
////					logger.debug(((CombineOperation) obsKey.getBean()).getMaskNameChoice());
////				}
//				//Check that binding between controlKey and controlValue has updated...
//				logger.debug("New controlValue : " + controlValue.get());
//			} else {s
//				//Scan for first instance that matches comboValue in keySet and update controlKey
//				logger.debug("Selected index did not correspond with controlKey/Value");
//				logger.debug("Scanning for first instance of controlKey that matches comboValue...");
//				//Could use i to compare with selected index, however if it didn't match the first time maybe the item was not selected according to javafx...
//				int i = 0;
//				for (Object key : obsMap.keySet()) {
//					if(comboValue.equals(obsMap.get(key))) {
//						if (obsKey instanceof IntegerProperty) {
//							((IntegerProperty) obsKey).setValue((Integer) key);
//						} else if (obsKey instanceof StringProperty) {
//							((StringProperty) obsKey).setValue((String) key);
//						}
//						//Check that binding between controlKey and controlValue has updated...
//						logger.debug("New controlValue : " + controlValue.get());
//					}
//					i++;
//				}
//			}
//		} else {
//			//Instead the comboChoices come from an observableList and there is not controlKey needing to be set...
//			//Set the controlValue directly based on choice
//			controlValue.set(comboValue);
//		}
//	}
//	
//	@SuppressWarnings("unchecked")
//	public ObservableList<String> getComboChoices(){
//		logger.debug(operation + " getting combo choices...");
//		if(controlParameters.containsKey("observableMap")) {
//			//Need to return the observableMap values as the combobox choices
//			ObservableMap<?, String> obsMap = (ObservableMap<?, String>) controlParameters.get("observableMap");
//			logger.debug(obsMap.toString());
//			ObservableList<String> obsList = FXCollections.observableArrayList(obsMap.values());
//			logger.debug(obsList.toString());
//			//Have to bind the map to the list with a MapChangeListener because there is no easy property binding or synchronization option for map.values() to list.....
//			obsMap.addListener((MapChangeListener<? super Object, ? super String>) new MapChangeListener<Object, String>(){
//				@Override
//				public void onChanged (javafx.collections.MapChangeListener.Change<? extends Object, ? extends String> change) {
//					if (change.toString().contains(" replaced by ")) {
//						logger.debug("Replaced mask name...");
//						logger.debug(change.getValueAdded());
//						logger.debug(change.getValueRemoved());
//						logger.debug(change.getKey().toString());
//						//Find the index of the valueRemoved and then replace with valueAdded
//						//The mask name is the critical element as all duplicate mask names will be combined into the same path class at the end.
//						//MaskID is mainly for the UI element IDs...
//						List<?> keys = new ArrayList(obsMap.keySet());
//						obsList.set(keys.indexOf(change.getKey()), change.getValueAdded());
//						//This breaks the comboBox if it is set since the value becomes null and a commitEdit() is triggered...
////						obsList.clear();
////						obsList.addAll(obsMap.values());
//					} else if(change.wasAdded()) {
//						obsList.add(change.getValueAdded());
//					} else if(change.wasRemoved()) {
//						obsList.remove(change.getValueRemoved());
//					}
//				}		
//			});
//			
//			return obsList;
//		} else {
//			return (ObservableList<String>) controlParameters.get("comboChoices");
//		}	
//	}
	
}
