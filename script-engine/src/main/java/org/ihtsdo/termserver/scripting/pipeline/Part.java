package org.ihtsdo.termserver.scripting.pipeline;

public class Part {

	public enum PartStatus {ACTIVE, DEPRECATED};

	private String partNumber;	
	private String partTypeName;
	private String partName;
	private PartStatus partStatus;
	
	public Part(String partNumber, String partTypeName, String partName) {
		this.partNumber = partNumber;
		this.partTypeName = partTypeName;
		this.partName = partName;
	}
	
	public String getPartNumber() {
		return partNumber;
	}
	
	public void setPartNumber(String partNumber) {
		this.partNumber = partNumber;
	}
	
	public String getPartTypeName() {
		return partTypeName;
	}
	
	public void setPartTypeName(String partTypeName) {
		this.partTypeName = partTypeName;
	}
	
	public String getPartName() {
		return partName;
	}
	
	public void setPartName(String partName) {
		this.partName = partName;
	}
	
	public PartStatus getPartStatus() {
		return partStatus;
	}
	
	public void setPartStatus(PartStatus status) {
		this.partStatus = status;
	}
	
	public String toString() {
		return getPartNumber() + "|" + getPartTypeName() + "|" + getPartName();
	}
	
	@Override
	public int hashCode() {
		return getPartNumber().hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		if (other instanceof Part part) {
			return getPartNumber().equals(part.getPartNumber());
		}
		return false;
	}
}
