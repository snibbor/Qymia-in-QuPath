package qupath.extension.aqua.operations;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;

import org.hildan.fxgson.FxGson;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import qupath.extension.aqua.AQUAPanelController;

class CombineOperationTest {
	private String maskID = "1";
	private ObservableMap<String, String> obsMaskNameMap = FXCollections.observableMap(
			new LinkedHashMap<String, String>(){{
				put("1", "Mask1");
				put("2", "Mask2");
				put("3", "Mask3");
				}}
			);
	private CombineOperation combOp = new CombineOperation("intersection", maskID, obsMaskNameMap);;
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
