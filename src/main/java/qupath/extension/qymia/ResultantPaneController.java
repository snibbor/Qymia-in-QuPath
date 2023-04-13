package qupath.extension.qymia;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableColumn.CellEditEvent;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import qupath.extension.qymia.operations.Operation;
import qupath.extension.qymia.operations.OperationTreeItem;
import qupath.extension.qymia.operations.ThresholdOperation;
import qupath.extension.qymia.operations.control_display.ControlCellFactory;
import qupath.extension.qymia.operations.CombineOperation;
import qupath.extension.qymia.operations.FromOperation;

public class ResultantPaneController implements Initializable{
	
	protected StringProperty resultantNameString = new SimpleStringProperty();
	protected Integer resultantID;
	protected ObservableMap<Integer, String> obsChannelMap = FXCollections.observableMap(new LinkedHashMap<Integer, String>());
	protected ObservableMap<Integer, String> obsResultantNameMap = FXCollections.observableMap(new LinkedHashMap<Integer, String>());
	protected ObservableMap<String, String> obsCombinedNameMap = FXCollections.observableMap(new LinkedHashMap<String, String>());
	protected ObservableMap<String, String> obsCombinedDatatypeMap = FXCollections.observableMap(new LinkedHashMap<String, String>());
	// Array to determine root node, where to start operations
	// {option; specific value (channelID, resultantID, or file path/directory); displayName}
	protected ArrayList<String> fromOption;
	protected Object operationsData;
	protected SimpleIntegerProperty estimatedRowHeight = new SimpleIntegerProperty(30); 
	
	private static final Logger logger = LoggerFactory.getLogger(ResultantPaneController.class);
	//May want to alter this depending on operation, but for now keeping it static final
	private static final Function<Operation, Operation> dataFunction = c -> c;
	private static final Function<Operation, Collection<? extends Operation>> childFunction = c -> c.getChildren();
	
	protected EventBus appEventBus;
	protected String currentResultantDatatype;
	protected Boolean isResultantAMask;
	
	/**
     * resultantID (String or Integer) to specify the ID for the resultantPane object(s)
     * Accepts the resultantName to set textField for resultantPane
     * Uses fromOption ("channel", "mask", "file") to specify the root node of the tree table view
     * operationsData is an object interpreted to populate the operations list and parameters for the MaskPane 
     * If operationsData is not provided, then a blank tree table view is created  
     * 
     * @param maskID
     * @param maskName
     * @param fromOption
     * @param appEventBus
     * @param operationsData
     * 
     * Overloaded constructor to require a set of these parameters
     */
	public ResultantPaneController(String resultantID, String resultantName, ArrayList<String> fromOption, EventBus appEventBus, 
			Map<String, ObservableMap<?,?>> obsMaps, Object operationsData) {
		this.resultantID = Integer.parseInt(resultantID);
		resultantNameString.set(resultantName);
		this.fromOption = fromOption;
		this.appEventBus = appEventBus;
		//store operations for tree table view to be used in initialize function
		this.operationsData = operationsData;
		this.obsResultantNameMap = (ObservableMap<Integer, String>) obsMaps.get("resultant_name");
		this.obsChannelMap = (ObservableMap<Integer, String>) obsMaps.get("channel_name");
		this.obsCombinedNameMap = (ObservableMap<String, String>) obsMaps.get("combined_name");
		this.obsCombinedDatatypeMap = (ObservableMap<String, String>) obsMaps.get("combined_datatype");
	}
	
	public ResultantPaneController(String resultantID, String resultantName, ArrayList<String> fromOption, EventBus appEventBus,
			Map<String, ObservableMap<?,?>> obsMaps) {
		this.resultantID = Integer.parseInt(resultantID);
		resultantNameString.set(resultantName);
		this.fromOption = fromOption;
		this.appEventBus = appEventBus;
		//blank tree table view, null or empty operations data object
		this.operationsData = null;
		this.obsResultantNameMap = (ObservableMap<Integer, String>) obsMaps.get("resultant_name");
		this.obsChannelMap = (ObservableMap<Integer, String>) obsMaps.get("channel_name");
		this.obsCombinedNameMap = (ObservableMap<String, String>) obsMaps.get("combined_name");
		this.obsCombinedDatatypeMap = (ObservableMap<String, String>) obsMaps.get("combined_datatype");
	}
	
	public ResultantPaneController(Integer resultantID, String resultantName, ArrayList<String> fromOption, EventBus appEventBus,
			Map<String, ObservableMap<?,?>> obsMaps, Object operationsData) {
		this.resultantID = resultantID;
		resultantNameString.set(resultantName);
		this.fromOption = fromOption;
		this.appEventBus = appEventBus;
		//store operations for tree table view to be used in initialize function
		this.operationsData = operationsData;
		this.obsResultantNameMap = (ObservableMap<Integer, String>) obsMaps.get("resultant_name");
		this.obsChannelMap = (ObservableMap<Integer, String>) obsMaps.get("channel_name");
		this.obsCombinedNameMap = (ObservableMap<String, String>) obsMaps.get("combined_name");
		this.obsCombinedDatatypeMap = (ObservableMap<String, String>) obsMaps.get("combined_datatype");
	}
	
	public ResultantPaneController(Integer resultantID, String resultantName, ArrayList<String> fromOption, EventBus appEventBus,
			Map<String, ObservableMap<?,?>> obsMaps) {
		this.resultantID = resultantID;
		resultantNameString.set(resultantName);
		this.fromOption = fromOption;
		this.appEventBus = appEventBus;
		//blank tree table view, null or empty operations data object
		this.operationsData = null;
		this.obsResultantNameMap = (ObservableMap<Integer, String>) obsMaps.get("resultant_name");
		this.obsChannelMap = (ObservableMap<Integer, String>) obsMaps.get("channel_name");
		this.obsCombinedNameMap = (ObservableMap<String, String>) obsMaps.get("combined_name");
		this.obsCombinedDatatypeMap = (ObservableMap<String, String>) obsMaps.get("combined_datatype");
	}
	
	@FXML
	TitledPane resultantProtocolPane;
	
	@FXML
	AnchorPane resultantAnchorPane;

	@FXML
	TextField resultantNameTextField;
	
	@FXML
	GridPane titleGridPane;
	
	@FXML
	Menu combineMenu;
	
	@FXML
	Menu manipulateMenu;
	
	@FXML
	Menu thresholdMenu;
	
	@FXML
	Menu miscMenu;
	@FXML
	MenuItem invertMenuItem;
	@FXML
	MenuItem blurChannelMenuItem;
	@FXML
	MenuItem addChannelsMenuItem;
	@FXML
	MenuItem subtractChannelsMenuItem;
	@FXML
	MenuItem multiplyChannelsMenuItem;
	@FXML
	MenuItem divideChannelsMenuItem;
	
	@FXML
	public TreeTableView<Operation> operationsTTV;
	
	@FXML
	TreeTableColumn<Operation, String> opTTVCol1;
	
	@FXML
	TreeTableColumn<Operation, Object> opTTVCol2;
	
	@FXML
	TreeTableColumn<Operation, Boolean> opTTVCol3;
	
	protected OperationTreeItem<Operation, Operation> rootOpTI;
	
	//Can't setup size properties until the maskPane has been added to the scene....
	public void setupSizeProperties() {
		//Setup property binding for resultantProtocolPane width with ScrollPane
		Scene scene = resultantProtocolPane.getScene();
		ScrollPane resultantScrollPane = (ScrollPane) scene.lookup("#resultantScrollPane");
		ReadOnlyDoubleProperty parentPaneWidth = resultantScrollPane.widthProperty();
		//Strange gap... subtract so that horizontal scroll bar is not displayed for protocol
		resultantProtocolPane.prefWidthProperty().bind(parentPaneWidth.subtract(37));
		//Bind titleGridPane width to treeTableView width
		titleGridPane.prefWidthProperty().bind(parentPaneWidth.subtract(37));

//		operationsTTV.prefHeightProperty().bind(estimatedRowHeight
//				.multiply(operationsTTV.expandedItemCountProperty().add(1.1)));
//		
		operationsTTV.expandedItemCountProperty().addListener((obs, oldV, newV) -> {
		    Platform.runLater(() -> {
		        operationsTTV.setPrefHeight((newV.intValue() + 1) * estimatedRowHeight.get());
		    });
		});
	}
		
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		appEventBus.register(this);
		//Setup resultantNameTextField
		resultantNameTextField.setText(resultantNameString.get());
		resultantNameTextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
		    @Override
		    public void handle(KeyEvent ke) {
		        if (ke.getCode().equals(KeyCode.ENTER)) {
		            setNameText();
		        }
		    }
		});
		resultantNameTextField.focusedProperty().addListener((ov, oldV, newV) -> {
	           if (!newV) { // focus lost
	              setNameText();
	           }
	        });

		//Create first node of operationsTTV
		Integer rootOptionID = Integer.parseInt(fromOption.get(1));
		String rootDisplayName = fromOption.get(2);
		Operation fromOp = null;
		logger.debug(fromOption.get(0));
		if (fromOption.get(0).equals("channel")) {
			fromOp = new FromOperation("from_channel", "Start from Channel:", rootOptionID, resultantID, obsChannelMap, obsCombinedDatatypeMap);
			this.currentResultantDatatype = "continuous";
			//Disable mask combine or manipulations initially, until a mask is made by thresholding
			setupOperationButtons();
		} else if (fromOption.get(0).equals("resultant")) {
			fromOp = new FromOperation("from_resultant", "Start from Resultant:", rootOptionID, resultantID, obsResultantNameMap, obsCombinedDatatypeMap);
			this.currentResultantDatatype = obsCombinedDatatypeMap.get("resultant"+rootOptionID.toString());
			//Depending on resultant.... disable options
			setupOperationButtons();
		} else if (fromOption.get(0).equals("file")) {
			fromOp = new FromOperation("from_file", "Start from File:", rootDisplayName);
			this.currentResultantDatatype = "mask";
			//Treat loaded file as mask, therefore disable channel/thresholding options
			setupOperationButtons();
		}
		rootOpTI = new OperationTreeItem<Operation, Operation>(fromOp, dataFunction, childFunction);
		operationsTTV.setRoot(rootOpTI);
		rootOpTI.setExpanded(true);
		
		
		operationsTTV.setRowFactory(row -> new TreeTableRow<Operation>() {
			@Override
			public void updateItem(Operation item, boolean empty) {
				super.updateItem(item, empty);
	            if(item != null && !empty) {
		            if(item.getIsBrokenBool()) {
		            	setStyle("-fx-background-color: rgba(255,148,148,1);");
		            	setTooltip(new Tooltip(item.getIsBrokenText()));
		            } else {
		            	setStyle("");
		            	setTooltip(new Tooltip(item.getToolTipText()));
		            }
	            } else {
	            	setStyle("");
	            	setTooltip(null);
            	}
			}
		});
		
		//Setup column values and cell value factories...
		//col1: displayText
		//col2: inputControlType
		//col2: hasDeleteButton
		opTTVCol1.setCellValueFactory(new TreeItemPropertyValueFactory<Operation, String>("displayText"));
		opTTVCol2.setCellValueFactory(new TreeItemPropertyValueFactory<Operation, Object>("controlValue"));
		opTTVCol3.setCellValueFactory(new TreeItemPropertyValueFactory<Operation, Boolean>("hasDelete"));
		
		opTTVCol2.setCellFactory(new ControlCellFactory());
		opTTVCol2.setOnEditCommit(new EventHandler<CellEditEvent<Operation, Object>>(){
			@Override
			public void handle(CellEditEvent<Operation, Object> event) {
				logger.debug("Committing new edit, validating protocol, and updating classification preview...");
//				logger.debug(event.getSource().toString());
				validateAndUpdateOps();
			}
			
		});
		
//		operationsTTV.getRoot().childrenModificationEvent()
		operationsTTV.getRoot().addEventHandler(operationsTTV.getRoot().childrenModificationEvent(), event -> {
			logger.debug("Children have been modified...");
			validateAndUpdateOps();
		});
		
		
		opTTVCol3.setCellFactory(col -> new TreeTableCell<Operation, Boolean>(){
			private HBox container;
			private Button removeButton;
			
			{
	            removeButton = new Button("-");
	            removeButton.setMinHeight(removeButton.USE_PREF_SIZE);
	            removeButton.setPrefHeight(20);
	            removeButton.setMaxHeight(20);

	            removeButton.setOnAction(new EventHandler<ActionEvent>() {
	                @Override
	                public void handle(ActionEvent event) {
	                    Operation data = (Operation) getTableRow().getItem();
	                    Boolean hasDelete = data.getHasDelete();
	                    if(hasDelete){
	                    	logger.debug("Deleting row: " + getTableRow().getTreeItem());
	                    	TreeItem c = getTableRow().getTreeItem();
	                    	boolean wasRemoved = c.getParent().getChildren().remove(c);
	                    	logger.debug("Row was removed: " + wasRemoved);
//	                    	validateAndUpdateOps();
	                    	//Request focus to parent titled pane because it doesn't work properly after button press...
	                    	resultantProtocolPane.requestFocus();
	                    }
	                }
	            });
	            container = new HBox(0, removeButton);
	            container.setAlignment(Pos.CENTER);
//	            container.setMinHeight(container.USE_PREF_SIZE);
//	            container.setPrefHeight(20);
//	            container.setMaxHeight(20);
	            container.setFillHeight(false);
	        }
			
			@Override
	        public void updateItem(Boolean item, boolean empty) {
//	            removeButton.disableProperty().unbind();
	            super.updateItem(item, empty);
	            if (empty||!item) {
	                setGraphic(null);
	            } else {
//	                removeButton.disableProperty().bind(
//	                		getTableRow().getItem().hasDeleteProperty().not()
//	                		);
	                setGraphic(container);
	                setStyle("-fx-padding: 0;");
	            }
	        }
			
		});
		
		operationsTTV.setOnKeyPressed( new EventHandler<KeyEvent>() {
		  @Override
		  public void handle( final KeyEvent keyEvent ) {
		    final TreeItem<Operation> selectedItem = operationsTTV.getSelectionModel().getSelectedItem();

		    if ( selectedItem != null ) {
		      if ( keyEvent.getCode().equals( KeyCode.DELETE ) ) {
		    	if(((Operation) selectedItem.getValue()).getHasDelete()) {
		    		logger.debug("Delete item: " + selectedItem);
                	boolean wasRemoved = selectedItem.getParent().getChildren().remove(selectedItem);
                	logger.debug("Row was removed: " + wasRemoved);
//                	validateAndUpdateOps();
		    	} else {
		    		logger.debug("Cannot delete item: " + selectedItem);
		    	}
		      }
		    }
		  }
		});
	}
	
	//Utility functions
	protected void setupOperationButtons() {
		if(this.currentResultantDatatype!=null) {
			if(this.currentResultantDatatype.equals("mask")) {
				thresholdMenu.setDisable(true);
				blurChannelMenuItem.setDisable(true);
				addChannelsMenuItem.setDisable(true);
				subtractChannelsMenuItem.setDisable(true);
				multiplyChannelsMenuItem.setDisable(true);
				divideChannelsMenuItem.setDisable(true);
	//			combineMenu.setDisable(false);
				manipulateMenu.setDisable(false);
				invertMenuItem.setDisable(false);
			} else {
	//			combineMenu.setDisable(true);
				manipulateMenu.setDisable(true);
				invertMenuItem.setDisable(true);
				thresholdMenu.setDisable(false);
				blurChannelMenuItem.setDisable(false);
				addChannelsMenuItem.setDisable(false);
				subtractChannelsMenuItem.setDisable(false);
				multiplyChannelsMenuItem.setDisable(false);
				divideChannelsMenuItem.setDisable(false);
			}
		} else {
			
		}
	}
	
	public void validateAndUpdateOps() {
		//Code may be refactored into a dedicated class (ResultantManager or ResultantCalculator)
		
		//Validate operations
		int brokenTally = 0;
		//Validate root?
		Operation rootOp = operationsTTV.getRoot().getValue();
		rootOp.validateOperation();
		if(rootOp.getIsBrokenBool()) {
			brokenTally++;
		}
		
		//Get current list of tree item operations
		List<TreeItem<Operation>> opTreeItems = operationsTTV.getRoot().getChildren();
		
		//Check input/output relationships
		String lastOutputType = operationsTTV.getRoot().getValue().getOutputResultantType();
		for(TreeItem<Operation> ti : opTreeItems) {
			Operation op = ti.getValue();
			op.setInputResultantType(lastOutputType);
			//The op will validate itself internally
			op.validateOperation();
			if(op.getIsBrokenBool()) {
				//record this and do not proceed to update Ops step
				brokenTally++;
			}
			lastOutputType = op.getOutputResultantType();
		}
		//Check dependencies
		//binding loops/circular references?
		currentResultantDatatype = lastOutputType;
		obsCombinedDatatypeMap.put("resultant"+resultantID, lastOutputType);
		setupOperationButtons();
		if (brokenTally==0) {
			
		} else {
			logger.debug(String.format("Protocol contains %d broken operations... cannot calculate resultant/mask...", brokenTally));
		}
	}
	
	public String getResultantDatatype() {
		return currentResultantDatatype;
	}
	
	//Mask pane specific commands
	public void showMaskDetection(ActionEvent e) {
		logger.info("Showing mask detection: " + resultantNameString.get());
	}
	
	public void showMaskAnnotation(ActionEvent e) {
		logger.info("Showing mask annotation: " + resultantNameString.get());
	}
	
	public void showMaskAll(ActionEvent e) {
		logger.info("Showing all mask PathObjects (annotations and detections): " + resultantNameString.get());
	}
	
//	public void setMaskText(ActionEvent e) {
//		maskNameString.set(maskNameTextField.getText());
//		MaskTextChangedEvent event = new MaskTextChangedEvent(this.maskID, maskNameString.get());
//		appEventBus.post(event);
//	}
	
	public void setNameText() {
		resultantNameString.set(resultantNameTextField.getText());
//		MaskTextChangedEvent event = new MaskTextChangedEvent(this.maskID, maskNameString.get());
//		appEventBus.post(event);
		if(obsResultantNameMap.containsKey(resultantID)) {
		//		obsMaskNameMap.remove(maskID);
		//		obsMaskNameMap.put(maskID, maskName);
				obsResultantNameMap.replace(resultantID, resultantNameString.get());
		}
	}
	
	public void deleteResultant(ActionEvent e) {
		logger.info("Requesting to delete resultant: " + resultantNameString.get() + " [resultantID: " + resultantID + "]");
		//Dialog box to confirm and to select whether to clear DETECTIONS from entire project
		operationsTTV.getRoot().getChildren().clear();
		VBox root = (VBox) resultantProtocolPane.getParent();
        root.getChildren().remove(resultantProtocolPane);
        //Communicate with AQUAPanelController that this Resultant has been deleted
//        DeleteResultantEvent event = new DeleteResultantEvent(ResultantID);
//        appEventBus.post(event);
        obsResultantNameMap.remove(resultantID);
		logger.debug(obsResultantNameMap.toString());
	}
	
	public void deleteResultant() {
		logger.info("Requesting to delete resultant: " + resultantNameString.get() + " [resultantID: " + resultantID + "]");
		//Dialog box to confirm and to select whether to clear DETECTIONS from entire project
		operationsTTV.getRoot().getChildren().clear();
		VBox root = (VBox) resultantProtocolPane.getParent();
        root.getChildren().remove(resultantProtocolPane);
        //Communicate with AQUAPanelController that this Resultant has been deleted
        obsResultantNameMap.remove(resultantID);
		logger.debug(obsResultantNameMap.toString());
		
	}
	
	public void pressAddOperationButton(MouseEvent e) {
		//Check that Resultant pane protocol is expanded, if not expand
		logger.debug("Add operation button pressed...");
		if(!resultantProtocolPane.isExpanded()) {
			resultantProtocolPane.setExpanded(true);
		}
	}
	
	public void operationFinishedSelecting(ActionEvent e) {
		operationsTTV.requestFocus();
	}
	
	//Combine operations
	//Can only place combine operations after a segmentation mask is created by thresholding or if you are starting from a mask
	public void addIntersectOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + ": Adding intersect operation to tree table view");
		//Start combineOp with value of this resultantID, although this is not really valid.... Prevents null pointer exception and can be replaced with null?
		Operation interOp = new CombineOperation("intersection", "resultant"+resultantID.toString(), "resultant"+resultantID.toString(), obsCombinedNameMap, obsCombinedDatatypeMap);
		OperationTreeItem<Operation, Operation> interTI = new OperationTreeItem<Operation, Operation>(interOp, dataFunction, childFunction);
		interTI.setExpanded(true);
		
		operationsTTV.getRoot().getChildren().add(interTI);
//		rootOpTI.getChildren().add(interTI);
	}
	
	public void addUnionOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + ": Adding union operation to tree table view");
		//Start combineOp with value of this resultantID, although this is not really valid.... Prevents null pointer exception and can be replaced with null?
		Operation unionOp = new CombineOperation("union", "resultant"+resultantID.toString(), "resultant"+resultantID.toString(), obsCombinedNameMap, obsCombinedDatatypeMap);
		OperationTreeItem<Operation, Operation> unionTI = new OperationTreeItem<Operation, Operation>(unionOp, dataFunction, childFunction);
		unionTI.setExpanded(true);
		
		operationsTTV.getRoot().getChildren().add(unionTI);
	}
	
	public void addDifferenceOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding difference operation to tree table view");
		//Start combineOp with value of this resultantID, although this is not really valid.... Prevents null pointer exception and can be replaced with null?
		Operation diffOp = new CombineOperation("difference", "resultant"+resultantID.toString(), "resultant"+resultantID.toString(), obsCombinedNameMap, obsCombinedDatatypeMap);
		OperationTreeItem<Operation, Operation> diffTI = new OperationTreeItem<Operation, Operation>(diffOp, dataFunction, childFunction);
		diffTI.setExpanded(true);
		
		operationsTTV.getRoot().getChildren().add(diffTI);
	}
	
	public void addAddWithinOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding add within operation to tree table view");
		//Start combineOp with value of this resultantID, although this is not really valid.... Prevents null pointer exception and can be replaced with null?
		Operation addWinOp = new CombineOperation("add_within", "resultant"+resultantID.toString(), "resultant"+resultantID.toString(), obsCombinedNameMap, obsCombinedDatatypeMap);
		OperationTreeItem<Operation, Operation> addWinTI = new OperationTreeItem<Operation, Operation>(addWinOp, dataFunction, childFunction);
		addWinTI.setExpanded(true);
		
		operationsTTV.getRoot().getChildren().add(addWinTI);
	}
	
	
	//Manipulations
	public void addExpandOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding expand operation to tree table view");
	}
	
	public void addShrinkOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding shrink operation to tree table view");
	}
	
	public void addFillHolesOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding fill holes operation to tree table view");
	}
	
	public void addRemoveSmallObjectsOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding remove (small) objects operation to tree table view");
	}
	
	//Threshold operations
	public void addLowBoundOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding lower bound threshold operation to tree table view");
		Operation lowBoundOp = new ThresholdOperation("lower_bound", "0");
		OperationTreeItem<Operation, Operation> lowBoundTI = new OperationTreeItem<Operation, Operation>(lowBoundOp, dataFunction, childFunction);
		lowBoundTI.setExpanded(true);
		
		operationsTTV.getRoot().getChildren().add(lowBoundTI);
		
	}
	public void addUpBoundOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding upper bound threshold operation to tree table view");
		Operation upBoundOp = new ThresholdOperation("upper_bound", "100");
		OperationTreeItem<Operation, Operation> upBoundTI = new OperationTreeItem<Operation, Operation>(upBoundOp, dataFunction, childFunction);
		upBoundTI.setExpanded(true);
		
		operationsTTV.getRoot().getChildren().add(upBoundTI);
	}
	
//	public void addPercentThresholdOp(ActionEvent e) {
//		logger.info(obsResultantNameMap.get(resultantID) + " : Adding percent threshold operation to tree table view");
//	}
//	
	public void addAbsThresholdOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding absolute threshold operation to tree table view");
		Operation absThreshOp = new ThresholdOperation("absolute_threshold", "0");
		OperationTreeItem<Operation, Operation> absThreshTI = new OperationTreeItem<Operation, Operation>(absThreshOp, dataFunction, childFunction);
		absThreshTI.setExpanded(true);
		
		operationsTTV.getRoot().getChildren().add(absThreshTI);
	}
	
	public void addHistoThresholdOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding histogram threshold operation to tree table view");
		Operation histoThreshOp = new ThresholdOperation("histogram_threshold", "0");
		OperationTreeItem<Operation, Operation> histoThreshTI = new OperationTreeItem<Operation, Operation>(histoThreshOp, dataFunction, childFunction);
		histoThreshTI.setExpanded(true);
		
		operationsTTV.getRoot().getChildren().add(histoThreshTI);
	}
	
	public void addAutoThresholdOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding auto threshold operation to tree table view");
		Operation autoThreshOp = new ThresholdOperation("auto_threshold", "Default");
		OperationTreeItem<Operation, Operation> autoThreshTI = new OperationTreeItem<Operation, Operation>(autoThreshOp, dataFunction, childFunction);
		autoThreshTI.setExpanded(true);
		
		operationsTTV.getRoot().getChildren().add(autoThreshTI);
	}
	
	public void addMeanThresholdOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding mean threshold operation to tree table view");
		Operation meanThreshOp = new ThresholdOperation("mean_threshold", "0");
		OperationTreeItem<Operation, Operation> meanThreshTI = new OperationTreeItem<Operation, Operation>(meanThreshOp, dataFunction, childFunction);
		meanThreshTI.setExpanded(true);
		
		operationsTTV.getRoot().getChildren().add(meanThreshTI);
	}
	
	//Misc operations
	public void addInvertOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding invert resultant operation to tree table view");
	}
	
	public void addBlurChannelOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding blur channel operation to tree table view");
	}
	
	public void addMultiplyChannelsOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding multiply channels operation to tree table view");
	}
	
	public void addDivideChannelsOp(ActionEvent e) {
		logger.info(obsResultantNameMap.get(resultantID) + " : Adding divide channels operation to tree table view");
	}
			
}
