package qupath.extension.aqua.operations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class ManipulateOperation extends Operation {

	protected ArrayList<OperationParameter> opParameterList;
	protected SimpleDoubleProperty pxDistance = new SimpleDoubleProperty();
	
	private void initParameters() {
//		//Setup for customizing the controls for each type of operation...
		switch(operation) {
		case "expand":
			this.displayText.set("Expand (px): ");
			this.controlParameters = new HashMap<String, Object>(Map.ofEntries(
					Map.entry("controlValue", pxDistance)
					));
			this.inputControlType = "text_posNumber";
			this.opParameterList = new ArrayList<OperationParameter>();
			this.acceptableResultantInputs = new ArrayList<>(List.of("mask"));
			this.outputResultantType="mask";
			break;
		case "shrink":
			this.displayText.set("Shrink (px): ");
			this.controlParameters = new HashMap<String, Object>(Map.ofEntries(
					Map.entry("controlValue", pxDistance)
					));
			this.inputControlType = "text_posNumber";
			this.opParameterList = new ArrayList<OperationParameter>();
			this.acceptableResultantInputs = new ArrayList<>(List.of("mask"));
			this.outputResultantType="mask";
			break;
		case "fill_holes":
			this.displayText.set("Fill holes: ");
			this.controlParameters = new HashMap<String, Object>(Map.ofEntries(
					Map.entry("controlValue", thresholdValue)
					));
			this.inputControlType = "text_posNumber";
			this.opParameterList = new ArrayList<OperationParameter>(List.of(
					new OperationParameter("downsample", "Downsample:", "text_number", 
							downsample),
					new OperationParameter("blurSigma", "Blur Sigma:", "text_number",
							blurSigma)
					));
			this.acceptableResultantInputs = new ArrayList<>(List.of("continuous"));
			this.outputResultantType="mask";
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
			this.outputResultantType="mask";
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
			this.outputResultantType="mask";
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
			this.outputResultantType="mask";
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
