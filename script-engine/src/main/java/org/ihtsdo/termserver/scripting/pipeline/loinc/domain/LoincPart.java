package org.ihtsdo.termserver.scripting.pipeline.loinc.domain;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.exception.TermServerScriptException;
/**
 * @author pwi@snomed.org
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
 */
import org.ihtsdo.termserver.scripting.pipeline.Part;

public class LoincPart extends Part {

	public LoincPart(String partNumber, String partTypeName, String partName) {
		super(partNumber, partTypeName, partName);
	}

	public static LoincPart parse(CSVRecord csv) throws TermServerScriptException {
		LoincPart loincPart = new LoincPart(csv.get(0), csv.get(1), csv.get(2));
		loincPart.setPartStatus(getLoincStatus(csv.get(4)));
		return loincPart;
	}
	
	public static PartStatus getLoincStatus(String loincStatusStr) throws TermServerScriptException {
		switch(loincStatusStr) {
			case "ACTIVE" : return PartStatus.ACTIVE;
			case "DEPRECATED" : return PartStatus.DEPRECATED;
			default: throw new TermServerScriptException("Status '" + loincStatusStr + "' not recognized");
		}
	}
	
}
