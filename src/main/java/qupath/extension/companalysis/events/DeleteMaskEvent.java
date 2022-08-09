package qupath.extension.companalysis.events;

public class DeleteMaskEvent {
	
	private final Integer maskID;
	
	public DeleteMaskEvent(String maskID) {
		this.maskID = Integer.parseInt(maskID);
	}
	
	public DeleteMaskEvent(Integer maskID) {
		this.maskID = maskID;
	}
	
	public Integer getMaskID() {
		return this.maskID;
	}

}
