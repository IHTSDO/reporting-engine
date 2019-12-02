package org.ihtsdo.termserver.scripting.creation;

import java.util.List;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;

public class DummyScript extends TermServerScript {

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}

}
