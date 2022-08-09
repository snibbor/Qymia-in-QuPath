package qupath.extension.companalysis;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

public class CompMakerPanelController implements Initializable{
	
	// Every class need a logger...
	private static final Logger logger = LoggerFactory.getLogger(CompMakerPanelController.class);
	
	// this bus is used application wide 
	private EventBus appEventBus = new EventBus();
	
	//updated in the setup AQUA or inferred by QuPath image data....
	//customize the naming of these channels in the setup...
//	private LinkedHashMap<Integer, String> channelMap = new LinkedHashMap<Integer, String>(
//			Map.ofEntries(
//					Map.entry(1, "Channel1"), 
//					Map.entry(2, "Channel2"), 
//					Map.entry(3, "Channel3")
//					)
//			);
	private LinkedHashMap<Integer, String> channelMap = new LinkedHashMap<Integer, String>() {{
		put(1, "Channel1");
		put(2, "Channel2");
		put(3, "Channel3");
	}};
	private ObservableMap<Integer, String> obsChannelMap = FXCollections.observableMap(channelMap);
	
	//	Holds resultantID and resultantName
	//	private Map<Integer, String> resultantNameMap = new LinkedHashMap<Integer, String>();
	private ObservableMap<Integer, String> obsResultantNameMap = FXCollections.observableMap(new LinkedHashMap<Integer, String>());
	private ObservableMap<String, String> obsCombinedNameMap = FXCollections.observableMap(new LinkedHashMap<String, String>());
	private ObservableMap<String, String> obsCombinedDatatypeMap = FXCollections.observableMap(new LinkedHashMap<String, String>());
	
	//Holds resultantID and resultantPane Controllers upon initialization
	private Map<Integer, ResultantPaneController> resultantPaneControllerMap = new LinkedHashMap<Integer, ResultantPaneController>();
	
	@FXML
	MenuBar aquaPanelMenu;
	
	@FXML
	AnchorPane aquaMainPane;
	
	@FXML
	ScrollPane resultantScrollPane;
	
	@FXML
	VBox resultantVBox;
	
	//Observable maps to automatically update menu options
	//Lots of repeated code because of 2 menu options to create masks from channel or resultant mask....
	@FXML
	Menu fromChannelMenu1;
	
	@FXML
	Menu fromChannelMenu2;
	
	@FXML
	Menu fromResultantMenu1;
	
	@FXML
	Menu fromResultantMenu2;
	
	@FXML
	Menu deleteResultantMenu;
	
	
	public CompMakerPanelController() {
		
	}
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		appEventBus.register(this);
		//Initialize basic channel map based on stored values (or image server..?)
		for (Map.Entry<Integer, String> entry : obsChannelMap.entrySet()) {
			Integer key = entry.getKey();
			String value = entry.getValue();
			
			MenuItem addItem1 = new MenuItem(value);
			addItem1.setId(String.format("fromChannelMI1_%s", key.toString()));
			addItem1.setOnAction((EventHandler<ActionEvent>) new EventHandler<ActionEvent>() {
				@Override public void handle(ActionEvent e) {
					try {
						newResultantFromChannel(e);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			});
			
			MenuItem addItem2 = new MenuItem(value);
			addItem2.setId(String.format("fromChannelMI2_%s", key.toString()));
			addItem2.setOnAction((EventHandler<ActionEvent>) new EventHandler<ActionEvent>() {
				@Override public void handle(ActionEvent e) {
					try {
						newResultantFromChannel(e);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			});
			
			fromChannelMenu1.getItems().add(addItem1);
			fromChannelMenu2.getItems().add(addItem2);
			
			obsCombinedNameMap.put("channel"+key, value);
			obsCombinedDatatypeMap.put("channel"+key, "continuous");
		}
		
		initObservables();
	}
	
	public Map<String, ObservableMap<?,?>> getObsMaps(){
		Map<String, ObservableMap<?,?>> obsMaps = new HashMap<String, ObservableMap<?,?>>(
				Map.ofEntries(
						Map.entry("channel_name", obsChannelMap),
						Map.entry("resultant_name", obsResultantNameMap),
						Map.entry("combined_name", obsCombinedNameMap),
						Map.entry("combined_datatype", obsCombinedDatatypeMap)
						)
				);
		return obsMaps;
	}
	
	private void initObservables() {
		
		this.obsChannelMap.addListener((MapChangeListener<? super Integer, ? super String>) new MapChangeListener<Integer, String>(){
			@Override
			public void onChanged (javafx.collections.MapChangeListener.Change<? extends Integer, ? extends String> change) {
				if (change.toString().contains(" replaced by ")) {
					//lookup MenuItem by resultantID and change name if not blank
					String idItem1 = String.format("fromChannelMI1_%s", change.getKey().toString());
					String idItem2 = String.format("fromChannelMI2_%s", change.getKey().toString());
					String newChannelName;
					
					if(!change.getValueAdded().isEmpty() && change.getValueAdded() != null) {
						newChannelName = change.getValueAdded();
					} else {
						newChannelName = String.format("Channel%s", change.getKey().toString());
					}
					
					//iterate through children items, check if id matches, rename item that matches
					renameMenuItem(idItem1, fromChannelMenu1, newChannelName);
					renameMenuItem(idItem2, fromChannelMenu2, newChannelName);
					obsCombinedNameMap.replace("channel"+change.getKey().toString(), newChannelName);
//					Still a channel, no need to replace/update datatype
//					obsCombinedDatatypeMap.put("channel"+change.getKey(), "continuous");
					
				} else if(change.wasAdded()) {
					MenuItem addItem1 = new MenuItem(change.getValueAdded());
					addItem1.setId(String.format("fromChannelMI1_%s", change.getKey().toString()));
					addItem1.setOnAction((EventHandler<ActionEvent>) new EventHandler<ActionEvent>() {
						@Override public void handle(ActionEvent e) {
							try {
								newResultantFromChannel(e);
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						}
					});
					MenuItem addItem2 = new MenuItem(change.getValueAdded());
					addItem2.setId(String.format("fromChannelMI2_%s", change.getKey().toString()));
					addItem2.setOnAction((EventHandler<ActionEvent>) new EventHandler<ActionEvent>() {
						@Override public void handle(ActionEvent e) {
							try {
								newResultantFromChannel(e);
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						}
					});
					
					fromChannelMenu1.getItems().add(addItem1);
					fromChannelMenu2.getItems().add(addItem2);
					obsCombinedNameMap.put("channel"+change.getKey().toString(), change.getValueAdded());
					obsCombinedDatatypeMap.put("channel"+change.getKey().toString(), "continuous");
					
				} else if(change.wasRemoved()) {
					//Very hard to lookup or use menu item ID to remove object..... Relying that key
//					Scene scene = (Scene) aquaPanelMenu.getScene();
//					String itemID = String.format("#fromChannelMI_%s",change.getKey().toString());
//					MenuItem removeItem = (MenuItem) scene.lookup(itemID);
					
					String idItem1 = String.format("fromChannelMI1_%s", change.getKey().toString());
					String idItem2 = String.format("fromChannelMI2_%s", change.getKey().toString());
					
					//iterate through children items, check if id matches, remove item that matches
					deleteMenuItem(idItem1, fromChannelMenu1);
					deleteMenuItem(idItem2, fromChannelMenu2);
					obsCombinedNameMap.remove("channel"+change.getKey().toString());
					obsCombinedDatatypeMap.remove("channel"+change.getKey());
				}
					
	        }
	    });
		
		this.obsResultantNameMap.addListener((MapChangeListener<? super Integer, ? super String>) new MapChangeListener<Integer, String>(){
			@Override
			public void onChanged (javafx.collections.MapChangeListener.Change<? extends Integer, ? extends String> change) {
				logger.debug("obsResultantNameMap changed: " + change.toString());
				if (change.toString().contains(" replaced by ")) {
					//lookup MenuItem by resultantID and change name if not blank
					String idItem1 = String.format("fromResultantMI1_%s", change.getKey().toString());
					String idItem2 = String.format("fromResultantMI2_%s", change.getKey().toString());
					String idItem3 = String.format("deleteResultantMI_%s", change.getKey().toString());
					String newResultantName;
					
					if(!change.getValueAdded().isEmpty() && change.getValueAdded() != null) {
						newResultantName = change.getValueAdded();
					} else {
						newResultantName = String.format("Resultant%s", change.getKey().toString());
					}
					
					//iterate through children items, check if id matches, rename item that matches
					renameMenuItem(idItem1, fromResultantMenu1, newResultantName);
					renameMenuItem(idItem2, fromResultantMenu2, newResultantName);
					renameMenuItem(idItem3, deleteResultantMenu, newResultantName);
					obsCombinedNameMap.replace("resultant"+change.getKey().toString(), newResultantName);
					//Need to get the datatype from the resultant...
					//Use controllerMap?
					String dataType = resultantPaneControllerMap.get(change.getKey()).getResultantDatatype();
					obsCombinedDatatypeMap.replace("resultant"+change.getKey().toString(), dataType);
					
				} else if(change.wasAdded()) {
					
					String newResultantName;
					
					if(!change.getValueAdded().isEmpty() && change.getValueAdded() != null) {
						newResultantName = change.getValueAdded();
					} else {
						newResultantName = String.format("Resultant%s", change.getKey().toString());
					}
					
					MenuItem addItem1 = new MenuItem(newResultantName);
					MenuItem addItem2 = new MenuItem(newResultantName);
					MenuItem addItem3 = new MenuItem(newResultantName);
					
					addItem1.setId(String.format("fromResultantMI1_%s", change.getKey().toString()));
					addItem1.setOnAction((EventHandler<ActionEvent>) new EventHandler<ActionEvent>() {
						@Override public void handle(ActionEvent e) {
							try {
								newResultantFromResultant(e);
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						}
					});
					
					addItem2.setId(String.format("fromResultantMI2_%s", change.getKey().toString()));
					addItem2.setOnAction((EventHandler<ActionEvent>) new EventHandler<ActionEvent>() {
						@Override public void handle(ActionEvent e) {
							try {
								newResultantFromResultant(e);
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						}
					});
					
					addItem3.setId(String.format("deleteResultantMI_%s", change.getKey().toString()));
					addItem3.setOnAction((EventHandler<ActionEvent>) new EventHandler<ActionEvent>() {
						@Override public void handle(ActionEvent e) {
							try {
								deleteResultantFromMenu(e);
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						}
					});
					
					fromResultantMenu1.getItems().add(addItem1);
					fromResultantMenu2.getItems().add(addItem2);
					deleteResultantMenu.getItems().add(addItem3);
					obsCombinedNameMap.put("resultant"+change.getKey().toString(), newResultantName);
					String dataType = resultantPaneControllerMap.get(change.getKey()).getResultantDatatype();
					obsCombinedDatatypeMap.put("resultant"+change.getKey().toString(), dataType);
					
				} else if(change.wasRemoved()) {
					
					String idItem1 = String.format("fromResultantMI1_%s", change.getKey().toString());
					String idItem2 = String.format("fromResultantMI2_%s", change.getKey().toString());
					String idItem3 = String.format("deleteResultantMI_%s", change.getKey().toString());
					
					//iterate through children items, check if id matches, remove item that matches
					deleteMenuItem(idItem1, fromResultantMenu1);
					deleteMenuItem(idItem2, fromResultantMenu2);
					deleteMenuItem(idItem3, deleteResultantMenu);
					
					obsCombinedNameMap.remove("resultant"+change.getKey().toString());
					obsCombinedDatatypeMap.remove("resultant"+change.getKey().toString());
					
					//Remove resultantPaneController associated with resultantID deleted
					resultantPaneControllerMap.remove(change.getKey());
//					logger.debug(resultantPaneControllerMap.toString());
	        	}
	        }
	    });
		
	}
	
	//Utility methods
	
	//Generate a next resultantID based on current largest resultantID inside of resultantVBox or obsResultantNameMap
	public Integer getNextResultantID() {
		logger.debug("Checking if there are ResultantPane(s) in VBox or observable map...");
		if(resultantVBox.getChildren().size() == 0 || obsResultantNameMap.size() == 0) {
			logger.debug("No ResultantPane(s) yet, returning 1 for next resultant ID...");
			return 1;
		} else {
			//Get last element of ordered map by keys
			ArrayList<Integer> sortedResultantIDs = new ArrayList<Integer>(obsResultantNameMap.keySet());
			Collections.sort(sortedResultantIDs);
			//Increment largestID by 1 and use as next resultant ID
			Integer nextResultantID = sortedResultantIDs.get(sortedResultantIDs.size()-1) + 1;
			logger.debug(String.format("%d ResultantPane(s) exist, returning resultantID: %d", sortedResultantIDs.size(), nextResultantID));
			return nextResultantID;
		}
	}
	
	//iterate through children items, check if id matches, rename item that matches
	public void renameMenuItem(String menuItemID, Menu menu, String newMenuItemName) {
		logger.debug("Renaming MenuItem requested...");
		for(MenuItem m : menu.getItems()) {
//			logger.debug(menuItemID + " =?= " + m.getId());
			if(menuItemID.equals(m.getId())) {
				m.setText(newMenuItemName);
				break;
			}
		}
	}
	
	//iterate through children items, check if id matches, remove item that matches
	public void deleteMenuItem(String menuItemID, Menu menu) {
		int i = 0;
		logger.debug("Deleting MenuItem requested...");
		for(MenuItem m : menu.getItems()) {
//			logger.debug(menuItemID + " =?= " + m.getId());
			if(menuItemID.equals(m.getId())) {
				menu.getItems().remove(i);
				break;
			}
			i++;
		}
	}
	
	
	//Events
	//Triggered when a ResultantPane receives a deleteResultant() from button press (not from menu).
	//Remove resultantID from obsResultantNameMap which will update MenuItems
	//Remove resultantID from resultantPaneControllerMap...
//	@Subscribe
//	public void onDeleteResultantEvent(DeleteResultantEvent event) {
//		logger.debug("Received DeleteResultantEvent for: " +  event.getResultantID());
//		Integer resultantID = event.getResultantID();
//		obsResultantNameMap.remove(resultantID);
//		resultantPaneControllerMap.remove(resultantID);
//		//Check that resultant was actually deleted?
//		//Save ResultantPane data for undo/redo option....
//	}
	
//	@Subscribe
//	public void onResultantTextChangedEvent(ResultantTextChangedEvent event) {
//		logger.debug("Received ResultantTextChangedEvent for: " +  event.getResultantID());
//		Integer resultantID = event.getResultantID();
//		String ResultantName = event.getResultantName();
//		if(obsResultantNameMap.containsKey(resultantID)) {
//	//		obsResultantNameMap.remove(ResultantID);
//	//		obsResultantNameMap.put(ResultantID, ResultantName);
//			obsResultantNameMap.replace(resultantID, ResultantName);
//		}
//	}
	
	//Main panel and button commands
	public void undo(ActionEvent e) {
		//IDK how to save a history of edits and undo or redo them....
	}
	
	public void redo(ActionEvent e) {
		//IDK how to save a history of edits and undo or redo them....
	}
	
	public void setupAnalysis(ActionEvent e) {
		logger.info("Opening setup panel for analysis...");
	}
	
	public void advancedSettings(ActionEvent e) {
		logger.info("Opening advanced settings panel...");
	}
	
	public void aboutButton(ActionEvent e) {
		logger.info("Opening about dialog...");
	}
	
	public void closeButton(ActionEvent e) {
		logger.info("Close requested... Checking if protocolState is not empty...");
		logger.info("Are you sure you want to close dialog");
	}
	
	//Resultant Name == resultantID?
	//If resultant name unknown, assign incrementing resultantIDs.... resultant1, resultant2, resultant3, etc...
	public void newResultantFromChannel(ActionEvent e) throws IOException {
		String idChannelMI = ((MenuItem) e.getSource()).getId();
		Integer channelID = Integer.parseInt(idChannelMI.split("_")[1]);
		String channelName = obsChannelMap.get(channelID);
		logger.info("Creating new resultant starting from channel: " + channelName + " [channelID: " + channelID.toString() + "]");
		//build fromOption array
		ArrayList<String> fromOption = new ArrayList<String>(List.of("channel", channelID.toString(), channelName));
		//getResultantIDs sorted to determine next id to use
		Integer newResultantID = getNextResultantID();
		String tempResultantName = String.format("Resultant%s", newResultantID.toString());
		//Create new titled pane with root node in tree populated with channel number/name
		FXMLLoader resultantPaneLoader = new FXMLLoader(getClass().getResource("/resultant-pane.fxml"));
		resultantPaneLoader.setControllerFactory(controllerClass -> new ResultantPaneController(newResultantID, tempResultantName, fromOption, appEventBus, getObsMaps()));
		TitledPane resultantPane = resultantPaneLoader.load();
		resultantVBox.getChildren().add(resultantPane);
		//Add MaskPaneController to map for accessing later
		ResultantPaneController resultantPaneControl = resultantPaneLoader.getController();
		resultantPaneControllerMap.put(newResultantID, resultantPaneControl);
		resultantPaneControl.setupSizeProperties();
		//Add to ObservableMap
		obsResultantNameMap.put(newResultantID, tempResultantName);
	}
	
	public void newResultantFromResultant(ActionEvent e) throws IOException{
		String idResultantMI = ((MenuItem) e.getSource()).getId();
		Integer resultantID = Integer.parseInt(idResultantMI.split("_")[1]);
		String resultantName = obsResultantNameMap.get(resultantID);
		logger.info("Creating new resultant starting from resultant: " + resultantName + " [resultantID: " + resultantID.toString() + "]");
		//build fromOption array
		ArrayList<String> fromOption = new ArrayList<String>(List.of("resultant", resultantID.toString(), resultantName));
		//getResultantIDs sorted to determine next id to use
		Integer newResultantID = getNextResultantID();
		String tempResultantName = String.format("Resultant%s", newResultantID.toString());
		//Create new titled pane with root node in tree populated with resultant number/name
		FXMLLoader resultantPaneLoader = new FXMLLoader(getClass().getResource("/resultant-pane.fxml"));
		resultantPaneLoader.setControllerFactory(controllerClass -> new ResultantPaneController(newResultantID, tempResultantName, fromOption, appEventBus, getObsMaps()));
		TitledPane resultantPane = resultantPaneLoader.load();
		resultantVBox.getChildren().add(resultantPane);
		//Add ResultantPaneController to map for accessing later
		ResultantPaneController resultantPaneControl = resultantPaneLoader.getController();
		resultantPaneControllerMap.put(newResultantID, resultantPaneControl);
		resultantPaneControl.setupSizeProperties();
		//Add to ObservableMap
		obsResultantNameMap.put(newResultantID, tempResultantName);
	}
	
	public void deleteResultantFromMenu(ActionEvent e) throws IOException{
		//Fetch ResultantPane controller and then call deleteResultant on it
		String idResultantMI = ((MenuItem) e.getSource()).getId();
		Integer resultantID = Integer.parseInt(idResultantMI.split("_")[1]);
		String resultantName = obsResultantNameMap.get(resultantID);
		logger.info("Delete Resultant (from menu): " + resultantName + " [resultantID: " + resultantID.toString()+"]");
		
		ResultantPaneController resultantPaneControl = resultantPaneControllerMap.get(resultantID);
		resultantPaneControl.deleteResultant();
		//Make sure that the Resultant has been deleted from the obsResultantNameMap
//		obsResultantNameMap.remove(ResultantID);
//		ResultantPaneControllerMap.remove(ResultantID);
//		logger.debug(obsResultantNameMap.toString());
	}
	
	public void previewMasks(ActionEvent e) {
		logger.info("Calculating mask preview for current image...");
	}
	
	public void runCurrent(ActionEvent e) {
		logger.info("Performing quantitative analysis of targets for current image...");
	}
	
	public void runForProject(ActionEvent e) {
		logger.info("Opening dialog to run quantitative analysis of targets for project...");
	}
	
	public void exportData(ActionEvent e) {
		logger.info("Opening dialog to export data (measurements and/or masks) for project...");
	}
	
	public void exportMeasurementsButton(ActionEvent e) {
		logger.info("Opening dialog to export measurments for project...");
	}
	//Overload these methods depending on input arguments. Export data dialog may just run these commands in isolation
	public void exportMeasurements(String outputFilePath) {
		
	}
	
	public void exportMasksButton(ActionEvent e) {
		logger.info("Opening dialog to export masks for project...");
	}
	
	//Overload these methods depending on input arguments. Export data dialog may just run these commands in isolation
	public void exportMasks(String outputFileDirectory) {
		
	}
	
	public void clearProtocolPanel() {
		
	}
	
	public void newProtocol(ActionEvent e) {
		logger.info("Checking if there are operations/masks defined in current panel...");
		logger.info("Opening dialog to confirm to start new protocol...");
		//How to handle any masks/detections that were created? Clear all detections before AQUA? Only keep annotations...
	}
	
	public void writeProtocolToFile(String protocolFilename, Object protocolState) {
		logger.info("Writing protocolState to " + String.valueOf(protocolFilename) + ".json file...");
		//Interpret protocolState from VBOX>Titled Pane>TableTreeView heirarchy......
		//Store parameters as .json file....
	}
	
	
	//How to store if current protocol has already been saved before and it's name?
	public void saveProtocol(ActionEvent e, Object protocolFilename, Object protocolState) {
		logger.info("Checking if there already exists a protocol file with the filename: " + String.valueOf(protocolFilename));
		//if yes, save using writeProtocol interpreter...
		//if no or protocolFilename is null, trigger saveAsProtocol code
	}
	
	public void saveAsProtocol(ActionEvent e, Object protocolState) {
		//Open file viewer and receive input on protocolFileName...
		logger.info("Checking if there already exists a protocol file with the filename: ");
		//if yes, save using writeProtocol interpreter...
	}
	
	public void readProtocolUpdatePanel(String protocolFilePath) {
		//interpret protocolState from file
		//clear panel if protocolState is valid
		//send protocolState to populate/update panel
	}
	
	public void openProtocol(ActionEvent e) {
		//Open file viewer and receive input on selected protocolFileName...
		logger.info("Attempting to open protocol file with the filename: ");
		//readProtocolUpdatePanel()
	}
	
}
