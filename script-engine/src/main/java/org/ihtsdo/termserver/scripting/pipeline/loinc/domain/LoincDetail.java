package org.ihtsdo.termserver.scripting.pipeline.loinc.domain;

import org.ihtsdo.otf.exception.TermServerScriptException;

import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.ihtsdo.termserver.scripting.pipeline.loinc.LoincScriptConstants;

public class LoincDetail extends Part implements LoincScriptConstants {

	private String loincNum;
	private String LDTColumnName;
	private Integer hashCode;
	
	private LoincDetail() {
		super();
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

	public String getLoincNum() {
		return loincNum;
	}

	public void setLoincNum(String loincNum) {
		this.loincNum = loincNum;
	}

	public String getLDTColumnName() {
		return LDTColumnName;
	}

	public void setLDTColumnName(String lDTColumnName) {
		LDTColumnName = lDTColumnName;
	}

	public static LoincDetail parse(String[] columns) throws TermServerScriptException {
		LoincDetail loincDetail = new LoincDetail();
		loincDetail.setLoincNum(columns[0]);
		loincDetail.setPartNumber(columns[1]);
		loincDetail.setLDTColumnName(columns[2]);
		loincDetail.setPartName(columns[3]);
		loincDetail.setPartTypeName(columns[5]);
		return loincDetail;
	}
	
	public String toString() {
		return getLoincNum() + "|" + getPartNumber() + "|" + getPartName() + "|" + getPartTypeName() + " (" + getLDTColumnName() + ")";
	}
	
	@Override
	public int hashCode() {
		if (hashCode == null) {
			hashCode = toString().hashCode();
		}
		return hashCode;
	}
	
	@Override
	public boolean equals(Object other) {
		if (other instanceof LoincDetail) {
			return hashCode() == ((LoincDetail)other).hashCode();
		}
		return false;
	}
}
