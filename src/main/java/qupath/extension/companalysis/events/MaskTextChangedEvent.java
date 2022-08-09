package qupath.extension.companalysis.events;

public class MaskTextChangedEvent {
	
	private final Integer maskID;
	private final String maskName;
	
	public MaskTextChangedEvent(String maskID, String maskName) {
		this.maskID = Integer.parseInt(maskID);
		this.maskName = maskName;
	}
	
	public MaskTextChangedEvent(Integer maskID, String maskName) {
		this.maskID = maskID;
		this.maskName = maskName;
	}
	
	public Integer getMaskID() {
		return this.maskID;
	}
	
	public String getMaskName() {
		return this.maskName;
	}

}
