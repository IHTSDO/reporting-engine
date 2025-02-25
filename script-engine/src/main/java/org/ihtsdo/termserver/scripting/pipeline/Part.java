package org.ihtsdo.termserver.scripting.pipeline;

import org.jetbrains.annotations.NotNull;

public class Part implements Comparable<Part> {

	public enum PartStatus {ACTIVE, DEPRECATED}

	private String partNumber;	
	private String partTypeName;
	private String partName;
	private PartStatus partStatus;
	private String partCategory;
	
	public Part(String partNumber, String partTypeName, String partName) {
		this.partNumber = partNumber;
		this.partTypeName = partTypeName;
		this.partName = partName;
	}

	public Part(String partNumber, String partCategory) {
		this.partNumber = partNumber;
		this.partCategory = partCategory;
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


	public String getPartCategory() {
		return partCategory;
	}

	public void setPartCategory(String partCategory) {
		this.partCategory = partCategory;
	}

	private String getNumberCategoryCombo() {
		return getPartCategory() + "|" + getPartNumber();
	}

	@Override
	public int compareTo(@NotNull Part o) {
		return this.getNumberCategoryCombo().compareTo(o.getNumberCategoryCombo());
	}
}
