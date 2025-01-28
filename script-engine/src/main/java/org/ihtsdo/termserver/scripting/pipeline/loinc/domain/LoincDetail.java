package org.ihtsdo.termserver.scripting.pipeline.loinc.domain;

import org.ihtsdo.otf.exception.TermServerScriptException;


import org.ihtsdo.termserver.scripting.pipeline.loinc.LoincScriptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoincDetail implements LoincScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincDetail.class);

	private String loincNum;
	private String partNumber;	
	private String LDTColumnName;
	private String partName;
	private int partType;
	private String partTypeName;
	
	private Integer hashCode;
	
	private LoincDetail() {
	}
	
	public LoincDetail(String partNumber, String partTypeName, String partName) {
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

	public int getPartType() {
		return partType;
	}

	public void setPartType(int partType) {
		this.partType = partType;
	}
	
	public static LoincDetail parse(String[] columns) throws TermServerScriptException {
		LoincDetail loincDetail = new LoincDetail();
		loincDetail.setLoincNum(columns[0]);
		loincDetail.setPartNumber(columns[1]);
		loincDetail.setLDTColumnName(columns[2]);
		loincDetail.setPartName(columns[3]);
		loincDetail.setPartType(Integer.parseInt(columns[4]));
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
