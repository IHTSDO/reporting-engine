package org.ihtsdo.termserver.scripting.reports.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoincDetail {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincDetail.class);

	public static String COMPONENT_PN = "COMPONENT_PN";
	public static String COMPONENTCORE_PN = "COMPONENTCORE_PN";
	public static String COMPSUBPART1_PN = "COMPSUBPART1_PN";
	public static String COMPSUBPART2_PN = "COMPSUBPART2_PN";
	public static String COMPSUBPART3_PN = "COMPSUBPART3_PN";
	public static String COMPSUBPART4_PN = "COMPSUBPART4_PN";
	public static String COMPNUM_PN = "COMPNUM_PN";
	public static String COMPDENOM_PN = "COMPDENOM_PN";
	public static String PROPERTY = "PROPERTYMIXEDCASE_PN";
	
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
		return getLoincNum() + "|" + getPartNumber() + "|" + getPartName() + "|" + getPartTypeName();
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
