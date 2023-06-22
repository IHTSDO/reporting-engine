package org.ihtsdo.termserver.scripting.domain;

import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;

public class BatchEndMarker extends Component {
	
	public static String NEW_TASK = "NEW_TASK";
	
	public BatchEndMarker() {
		this.moduleId = getReportedName();
		this.released = true;
	}

	@Override
	public String getReportedName() {
		return "BatchEndMarker";
	}

	@Override
	public String getReportedType() {
		return "BatchEndMarker";
	}

	@Override
	public String[] toRF2() throws Exception {
		return null;
	}

	@Override
	public List<String> fieldComparison(Component other, boolean ignoreEffectiveTime) throws TermServerScriptException {
		return null;
	}

}
