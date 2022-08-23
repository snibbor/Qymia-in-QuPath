package qupath.extension.aqua.operations;

import java.util.LinkedHashMap;

import org.hildan.fxgson.FxGson;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import qupath.extension.qiimia.operations.CombineOperation;

class CombineOperationTest {
	private String maskID = "1";
	private String resultantID = "1";
	private ObservableMap<String, String> obsMaskNameMap = FXCollections.observableMap(
			new LinkedHashMap<String, String>(){{
				put("1", "Mask1");
				put("2", "Mask2");
				put("3", "Mask3");
				}}
			);
	private ObservableMap<String, String> obsDatatypeMap = FXCollections.observableMap(
			new LinkedHashMap<String, String>(){{
				put("1", "mask");
				put("2", "mask");
				put("3", "mask");
			}}
	);
	private CombineOperation combOp = new CombineOperation("intersection", maskID, resultantID, obsMaskNameMap, obsDatatypeMap);
	Gson fxGsonWithExtras = FxGson.createWithExtras();
	
	@Test
	void testSerialization() {
		String json = fxGsonWithExtras.toJson(combOp);
		System.out.println(json);
		assert true;
	}
	@Test
	void testChangingKey() {
		System.out.println(combOp.getMaskIDChoice() + " : " + combOp.getMaskNameChoice());
		combOp.setMaskIDChoice("3");
		System.out.println(combOp.getMaskIDChoice() + " : " + combOp.getMaskNameChoice());
		assert combOp.getMaskNameChoice().equals("Mask3");
	}
	
	@Test
	void testChangingKeyAfterRename() {
		obsMaskNameMap.replace("1", "Tumor");
		obsMaskNameMap.replace("3", "Stroma");
		System.out.println(combOp.getMaskIDChoice() + " : " + combOp.getMaskNameChoice());
		assert combOp.getMaskNameChoice().equals("Tumor");
		combOp.setMaskIDChoice("3");
		System.out.println(combOp.getMaskIDChoice() + " : " + combOp.getMaskNameChoice());
		assert combOp.getMaskNameChoice().equals("Stroma");
	}
	
	@Test
	void testChangingKeyDelete() {
		obsMaskNameMap.remove("1");
		System.out.println(combOp.getMaskIDChoice() + " : " + combOp.getMaskNameChoice());
		combOp.setMaskIDChoice("3");
		System.out.println(combOp.getMaskIDChoice() + " : " + combOp.getMaskNameChoice());
		assert combOp.getMaskNameChoice().equals("Mask3");
	}
	
	

}
