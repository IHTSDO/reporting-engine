package org.ihtsdo.termserver.scripting.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class NormaliseConceptsDrivenPPPSpecified extends NormaliseConceptsDriven {

	public NormaliseConceptsDrivenPPPSpecified(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException {
		NormaliseConceptsDrivenPPPSpecified app = new NormaliseConceptsDrivenPPPSpecified(null);
		try {
			ReportSheetManager.setTargetFolderId(GFOLDER_QI_NORMALIZATION);
			app.classifyTasks = true;
			app.init(args);
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			app.processFile();
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to Normalise Template Compliant Concepts", e);
		} finally {
			app.finish();
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		//We'll store the PPP in the concept issue field
		c.addIssue(lineItems[2].trim());
		return Collections.singletonList(c);
	}
}
