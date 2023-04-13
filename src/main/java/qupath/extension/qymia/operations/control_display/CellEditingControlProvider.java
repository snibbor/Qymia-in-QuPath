package qupath.extension.qymia.operations.control_display;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import javafx.event.EventHandler;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextFormatter.Change;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import qupath.extension.qymia.operations.Operation;

public class CellEditingControlProvider implements EditingControlProvider {
	
	private Map<String, EditingControlProvider> providers;
	private EditingControlProvider selectedProvider;

    public CellEditingControlProvider() {
        providers = new HashMap<>();
        providers.put("check", new CheckProvider());
        providers.put("combobox", new ComboProvider());
        providers.put("text", new TextProvider());
        providers.put("text_percent", new TextProvider("percent"));
        providers.put("text_0-1", new TextProvider("0-1"));
        providers.put("text_number", new TextProvider("number"));
    }

    @Override
    public Control getControl(ControlCell cell) {
    	Operation field = (Operation) cell.getTableRow().getItem();
        if (field == null || field.getInputControlType() == null) {
            return null;
        } else {
        	selectedProvider = providers.get(field.getInputControlType());
            return selectedProvider.getControl(cell);
        }
    }

    @Override
    public void updateFromControl(Operation field) {
        selectedProvider.updateFromControl(field);
    }
    
    
    public class CheckProvider implements EditingControlProvider {

    	@Override
    	public Control getControl(ControlCell cell) {
    		// TODO Auto-generated method stub
    		return null;
    	}

    	@Override
    	public void updateFromControl(Operation field) {
    		// TODO Auto-generated method stub

    	}

    }
    
    public class ComboProvider implements EditingControlProvider {
        private ComboBox<String> comboBox;

        @Override
        public Control getControl(ControlCell cell) {
        	if (comboBox == null) {
                createComboBox(cell);
            }
            return comboBox;
        }

        private void createComboBox(ControlCell cell) {
        	Operation field = (Operation) cell.getTableRow().getItem();
            comboBox = new ComboBox<String>();
            comboBox.setEditable(false);
            comboBox.setMinSize(50, comboBox.USE_PREF_SIZE);
            comboBox.setMaxSize(110, 24);
            comboBox.setPrefSize(110, 24);
//            comboBox.setStyle("-fx-padding: 0 0 0 0; -fx-border-insets: 0 0 0 0;");
            comboBox.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
            	cell.commitEdit(cell.getTableRow().getItem());
			});
//            comboBox.setOnAction(e -> {
//            	cell.commitEdit();
//            	cell.getTableColumn().editCommitEvent();
//            });
            resetBox(field);

        }

        private void resetBox(Operation field) {
            comboBox.getItems().clear();
            comboBox.setItems(field.getComboChoices());
            comboBox.setValue((String) field.getControlValue());
        }

        @Override
        public void updateFromControl(Operation field) {
        	if(field!=null) {
        		field.setComboValue(comboBox.getValue(), comboBox.getSelectionModel().getSelectedIndex());
        	}
        }

    }
    
    public class TextProvider implements EditingControlProvider {
    	private TextField textField;
    	private String valueType;
    	
    	public TextProvider(String valueType){
    		this.valueType = valueType;
    	}
    	public TextProvider(){
    		this.valueType = "string";
    	}

        @Override
        public Control getControl(ControlCell cell) {
            if (textField == null) {
                createTextField(cell);
            }
            return textField;
        }

        private void createTextField(ControlCell cell) {
        	Operation field = (Operation) cell.getTableRow().getItem();
            textField = new TextField(field.getControlValue().toString());
            textField.setMinSize(50, textField.USE_PREF_SIZE);
            textField.setPrefSize(110, 20);
            textField.setMaxSize(110, 20);
            
            textField.setOnKeyPressed(new EventHandler<KeyEvent>() {
    		    @Override
    		    public void handle(KeyEvent ke) {
    		        if (ke.getCode().equals(KeyCode.ENTER)) {
    		            cell.commitEdit(cell.getTableRow().getItem());
    		        }
    		    }
    		});
    		textField.focusedProperty().addListener((ov, oldV, newV) -> {
    	           if (!newV) { // focus lost
    	        	   cell.commitEdit(cell.getTableRow().getItem());
    	           }
    	        });
    		
    		switch(valueType) {
    		
    			case "string":
    			{
    				break;
    			}
    			case "number":
    			{
//    				UnaryOperator<Change> filter = change -> {
//    		            String newText = change.getControlNewText();
//    		            if (newText.matches("")) { 
//    		                return change;
//    		            } 
//    		            return null;
//    		        };
    //
//    		        StringConverter<Double> converter = new DoubleStringConverter() {
//    		            @Override
//    		            public Double fromString(String s) {
//    		                if (s.isEmpty()) return 0.0 ;
//    		                else if(Double.parseDouble(s) == 0) return 0.0;
//    		                return super.fromString(s);
//    		            }
//    		        };
    //
//    		        TextFormatter<Double> textFormatter = 
//    		                new TextFormatter<Double>(converter, 0.0, filter);
//    		        textField.setTextFormatter(textFormatter);
    				break;

    			}
    				
    			case "percent":
    			{
    				UnaryOperator<Change> filter = change -> {
    		            String newText = change.getControlNewText();
    		            if (newText.matches("^100(\\.0{0,2})?$|^\\d{0,2}(\\.\\d{0,2})?$")) { 
    		                return change;
    		            } 
    		            return null;
    		        };

    		        StringConverter<Double> converter = new DoubleStringConverter() {
    		            @Override
    		            public Double fromString(String s) {
    		                if (s.isEmpty()) return 0.0 ;
//    		                else if(Double.parseDouble(s) == 0) return 0.0;
    		                return super.fromString(s);
    		            }
    		        };

    		        TextFormatter<Double> textFormatter = 
    		                new TextFormatter<Double>(converter, 0.0, filter);
    		        textField.setTextFormatter(textFormatter);
    				break;
    			}
    			case "0-1":
    			{
    				UnaryOperator<Change> filter = change -> {
    		            String newText = change.getControlNewText();
    		            if (newText.matches("^0{0,1}(\\.\\d{0,3})?$|^1(\\.0{0,3})?$")) { 
    		                return change;
    		            } 
    		            return null;
    		        };

    		        StringConverter<Double> converter = new DoubleStringConverter() {
    		            @Override
    		            public Double fromString(String s) {
    		                if (s.isEmpty()) return 0.0 ;
//    		                else if(Double.parseDouble(s) == 0) return 0.0;
    		                return super.fromString(s);
    		            }
    		        };

    		        TextFormatter<Double> textFormatter = 
    		                new TextFormatter<Double>(converter, 0.0, filter);
    		        textField.setTextFormatter(textFormatter);
    				break;
    			}
    		}
    		
        }

        @Override
        public void updateFromControl(Operation field) {
        	if(field!=null) {
        		field.setControlValue(textField.getText());
        	}
        }


    }
}
