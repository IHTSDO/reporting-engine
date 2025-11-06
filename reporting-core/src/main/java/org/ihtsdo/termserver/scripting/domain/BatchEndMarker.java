package org.ihtsdo.termserver.scripting.domain;

import java.util.Collections;
import java.util.List;

import org.ihtsdo.otf.exception.ScriptException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentStore;

public class BatchEndMarker extends Component {
	
	public static String NEW_TASK = "NEW_TASK";

	private static int markerCount = 0;
	
	public BatchEndMarker() {
		this.id = NEW_TASK + "_" + ++markerCount;
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
	public String[] toRF2() throws ScriptException {
		return null;
	}

	@Override
	public List<String> fieldComparison(Component other, boolean ignoreEffectiveTime) throws TermServerScriptException {
		return null;
	}

	@Override
	public boolean matchesMutableFields(Component other) {
		throw new IllegalArgumentException("Wasn't expecting to compare BatchEndMarkers!");
	}

	@Override
	public List<Component> getReferencedComponents(ComponentStore cs) {
		return Collections.emptyList();
	}
}
