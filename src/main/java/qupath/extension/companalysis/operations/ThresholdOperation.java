package qupath.extension.companalysis.operations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;

public class ThresholdOperation extends Operation {
	
	protected ArrayList<OperationParameter> opParameterList;
	protected SimpleStringProperty thresholdValue = new SimpleStringProperty();
	protected SimpleDoubleProperty downsample = new SimpleDoubleProperty(2.0);
	protected SimpleDoubleProperty blurSigma = new SimpleDoubleProperty(0);
	
	private void initParameters() {
//		//Setup for customizing the controls for each type of operation...
		switch(operation) {
//		case "thresholder":
//			this.displayText.set("Thresholder:");
//			this.opParameterList = new ArrayList<OperationParameter>(List.of(
//					new OperationParameter("resolution","Resolution",),
//					));
		case "lower_bound":
			this.displayText.set("Lower bound: ");
			this.controlParameters = new HashMap<String, Object>(Map.ofEntries(
					Map.entry("controlValue", thresholdValue)
					));
			this.inputControlType = "text_percent";
			this.opParameterList = new ArrayList<OperationParameter>();
			this.acceptableResultantInputs = new ArrayList<>(List.of("continuous"));
			this.outputResultantType.set("continuous");
			break;
		case "upper_bound":
			this.displayText.set("Upper bound: ");
			this.controlParameters = new HashMap<String, Object>(Map.ofEntries(
					Map.entry("controlValue", thresholdValue)
					));
			this.inputControlType = "text_percent";
			this.opParameterList = new ArrayList<OperationParameter>();
			this.acceptableResultantInputs = new ArrayList<>(List.of("continuous"));
			this.outputResultantType.set("continuous");
			break;
//		case "percent_threshold":
//			displayText.set("Percent threshold: ");
//			this.controlParameters = new HashMap<String, Object>(Map.ofEntries(
//					Map.entry("controlValue", thresholdValue)
//					));
//			this.controlValue = thresholdValue;
//			this.inputControlType = "text_percent";
//			this.opParameterList = new ArrayList<OperationParameter>();
//			break;
		case "absolute_threshold":
			this.displayText.set("Absolute threshold: ");
			this.controlParameters = new HashMap<String, Object>(Map.ofEntries(
					Map.entry("controlValue", thresholdValue)
					));
			this.inputControlType = "text_0-1";
			this.opParameterList = new ArrayList<OperationParameter>(List.of(
					new OperationParameter("downsample", "Downsample:", "text_number", 
							downsample),
					new OperationParameter("blurSigma", "Blur Sigma:", "text_number",
							blurSigma)
					));
			this.acceptableResultantInputs = new ArrayList<>(List.of("continuous"));
			this.outputResultantType.set("mask");
			break;
		case "histogram_threshold":
			this.displayText.set("Histogram threshold: ");
			this.controlParameters = new HashMap<String, Object>(Map.ofEntries(
					Map.entry("controlValue", thresholdValue)
					));
			this.inputControlType = "text_percent";
			this.opParameterList = new ArrayList<OperationParameter>(List.of(
					new OperationParameter("downsample", "Downsample:", "text_number", 
							downsample),
					new OperationParameter("blurSigma", "Blur Sigma:", "text_number",
							blurSigma)
					));
			this.acceptableResultantInputs = new ArrayList<>(List.of("continuous"));
			this.outputResultantType.set("mask");
			break;
		case "auto_threshold":
			this.displayText.set("Auto threshold: ");
			this.controlParameters = new HashMap<String, Object>(Map.ofEntries(
					Map.entry("controlValue", thresholdValue),
					Map.entry("comboChoices", 
							List.of(
									"Default",
									"Huang",
									"IJ_IsoData",
									"Intermodes",
									"IsoData",
									"Li",
									"MaxEntropy",
									"Mean",
									"MinError",
									"Minimum",
									"Moments",
									"Otsu",
									"Percentile",
									"RenyiEntropy",
									"Shanbhag",
									"Triangle",
									"Yen")
							)
					));
			this.inputControlType = "combobox";
			this.opParameterList = new ArrayList<OperationParameter>(List.of(
					new OperationParameter("downsample", "Downsample:", "text_number", 
							downsample),
					new OperationParameter("blurSigma", "Blur Sigma:", "text_number",
							blurSigma)
					));
			this.acceptableResultantInputs = new ArrayList<>(List.of("continuous"));
			this.outputResultantType.set("mask");
			break;
		case "mean_threshold":
			this.displayText.set("Mean threshold: ");
			this.controlParameters = new HashMap<String, Object>(Map.ofEntries(
					Map.entry("controlValue", thresholdValue)
					));
			this.inputControlType = "text_number";
			this.opParameterList = new ArrayList<OperationParameter>(List.of(
					new OperationParameter("downsample", "Downsample:", "text_number", 
							downsample),
					new OperationParameter("blurSigma", "Blur Sigma:", "text_number",
							blurSigma)
					));
			this.acceptableResultantInputs = new ArrayList<>(List.of("continuous"));
			this.outputResultantType.set("mask");
			break;
		}
	}
	
	public ThresholdOperation(String operation, String thresholdValue) {
		this.operation = operation;
		this.thresholdValue.set(thresholdValue);
		this.controlValue = this.thresholdValue;
		//Has a delete button
		this.hasDeleteButton = new SimpleBooleanProperty(true);
		initParameters();
	}
	
	@Override
	public Collection<? extends Operation> getChildren() {
		List<OperationParameter> parameterRows = opParameterList;
		return parameterRows;
	}
}

