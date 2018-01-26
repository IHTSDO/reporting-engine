package org.ihtsdo.termserver.scripting.domain;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ComponentType;

public interface Component {

	String getId();
	
	String getReportedName();
	
	String getReportedType();
	
	ComponentType getComponentType();
	
	String[] toRF2() throws TermServerScriptException;
	
}
