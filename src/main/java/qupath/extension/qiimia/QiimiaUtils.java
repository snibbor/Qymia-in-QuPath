package qupath.extension.qiimia;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;

import java.util.function.UnaryOperator;

public class QiimiaUtils {


    public QiimiaUtils(){};

    public static TextField formatTextFields(TextField textField, String format, String defaultValue) {
        switch(format.toLowerCase()) {
            case "string": {
                break;
            }
            case "pos_double":{
                UnaryOperator<TextFormatter.Change> filter = change -> {
                    String newText = change.getControlNewText();
                    if (newText.matches("^(([1-9][0-9]*)|0)?(\\.[0-9]*)?$|^$")) {
                        return change;
                    }
                    return null;
                };

                StringConverter<Double> converter = new DoubleStringConverter() {
                    @Override
                    public Double fromString(String s) {
                        if (s.isEmpty()) return null;
                        else if (Double.parseDouble(s) == 0.0) return 0.0;
                        return super.fromString(s);
                    }
                };

                TextFormatter<Double> textFormatter;
                if(defaultValue!=null) {
                    textFormatter = new TextFormatter<Double>(converter, Double.parseDouble(defaultValue), filter);
                } else{
                    textFormatter = new TextFormatter<Double>(converter, null, filter);
                }

                textField.setTextFormatter(textFormatter);
                break;

            }
            case "integer": {
                UnaryOperator<TextFormatter.Change> filter = change -> {
                    String newText = change.getControlNewText();
                    if (newText.matches("^\\d{0,4}$|^$")) {
                        return change;
                    }
                    return null;
                };

                StringConverter<Integer> converter = new IntegerStringConverter() {
                    @Override
                    public Integer fromString(String s) {
                        if (s.isEmpty()) return null;
                        else if (Integer.parseInt(s) == 0.0) return 0;
                        return super.fromString(s);
                    }
                };

                TextFormatter<Integer> textFormatter;
                if(defaultValue!=null) {
                    textFormatter = new TextFormatter<Integer>(converter, Integer.parseInt(defaultValue), filter);
                } else{
                    textFormatter = new TextFormatter<Integer>(converter, null, filter);
                }

                textField.setTextFormatter(textFormatter);
                break;
            }
            case "percent": {
                UnaryOperator<TextFormatter.Change> filter = change -> {
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

                TextFormatter<Double> textFormatter;
                if(defaultValue!=null) {
                    textFormatter = new TextFormatter<Double>(converter, Double.parseDouble(defaultValue), filter);
                } else{
                    textFormatter = new TextFormatter<Double>(converter, null, filter);
                }

                textField.setTextFormatter(textFormatter);
                break;
            }
            case "0-1": {
                UnaryOperator<TextFormatter.Change> filter = change -> {
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

                TextFormatter<Double> textFormatter;
                if(defaultValue!=null) {
                    textFormatter = new TextFormatter<Double>(converter, Double.parseDouble(defaultValue), filter);
                } else{
                    textFormatter = new TextFormatter<Double>(converter, null, filter);
                }

                textField.setTextFormatter(textFormatter);
                break;
            }
        }
        return textField;
    }

}
