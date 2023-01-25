package org.ihtsdo.termserver.scripting.reports.loinc;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.exception.TermServerScriptException;
/**
 * 
 * @author peter
 * Until I find a better place for it, here's out mapping of LOINC Part Types to Attribute Types
 * COMPONENT  --> 246093002 |Component (attribute)|
 * ADJUSTMENT --> 246501002 |Technique (attribute)|
 * DIVISORS   --> 246514001 |Units (attribute)|
 * CHALLENGE  --> 704326004 |Precondition (attribute)|
 * SCALE      --> 370132008 |Scale type (attribute)|
 * COMPONENT  --> 704318007 |Property type (attribute)|
 * SYSTEM     --> 704319004 |Inheres in (attribute)|
 * COMPONENT  --> 704321009 |Characterizes (attribute)|
 * COMPONENT  --> 704322002 |Process agent (attribute)|
 * TIME       --> 704323007 |Process duration (attribute)|
 * COMPONENT  --> 704324001 |Process output (attribute)|
 * DIVISORS   --> 704325000 |Relative to (attribute)|
 * SYSTEM     --> 704327008 |Direct site (attribute)|
 * SYSTEM     --> 718497002 |Inherent location (attribute)|
 * 
 */
public class LoincPart {
	
	public enum LoincStatus {ACTIVE, DEPRECATED};

	private String partNumber;	
	private String partTypeName;
	private String partName;
	//private String PartDisplayName;
	private LoincStatus status;
	
	private LoincPart() {
	}
	
	public LoincPart(String partNumber, String partTypeName, String partName) {
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
	public LoincStatus getStatus() {
		return status;
	}
	public void setStatus(LoincStatus status) {
		this.status = status;
	}
	
	public static LoincStatus getLoincStatus(String loincStatusStr) throws TermServerScriptException {
		switch(loincStatusStr) {
			case "ACTIVE" : return LoincStatus.ACTIVE;
			case "DEPRECATED" : return LoincStatus.DEPRECATED;
			default: throw new TermServerScriptException("Status '" + loincStatusStr + "' not recognized");
		}
	}
	
	public static LoincPart parse(CSVRecord csv) throws TermServerScriptException {
		LoincPart loincPart = new LoincPart();
		loincPart.setPartNumber(csv.get(0));
		loincPart.setPartTypeName(csv.get(1));
		loincPart.setPartName(csv.get(2));
		loincPart.setStatus(getLoincStatus(csv.get(4)));
		return loincPart;
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
		if (other instanceof LoincPart) {
			return getPartNumber().equals(((LoincPart)other).getPartNumber());
		}
		return false;
	}
}
