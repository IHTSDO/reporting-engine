package org.ihtsdo.termserver.scripting.delta.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class INFRA13580_UICCCopyAJCC_Groupers extends INFRA13580_UICCCopyAJCC implements ScriptConstants{

	private List<Concept> sourceConcepts;

	public static void main(String[] args) throws TermServerScriptException {
		INFRA13580_UICCCopyAJCC_Groupers delta = new INFRA13580_UICCCopyAJCC_Groupers();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.init(args);
			delta.loadProjectSnapshot(false); //Need all descriptions loaded.
			delta.postInit();
			delta.process();
			delta.createOutputArchive(false, delta.sourceConcepts.size());
		} finally {
			delta.finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		// 1222593009 |American Joint Committee on Cancer pathological stage group allowable value (qualifier value)|
		// 1222592004 |American Joint Committee on Cancer clinical stage group allowable value (qualifier value)|"
		// 1281795007 |American Joint Committee on Cancer rpM category allowable value|"
		sourceConcepts = List.of(gl.getConcept("1222599008 |American Joint Committee on Cancer pathological grade allowable value|"),
				gl.getConcept("1222598000 |American Joint Committee on Cancer clinical grade allowable value|"));
		super.postInit();
	}

	@Override
	protected void process() throws TermServerScriptException {
		for (Concept c : sourceConcepts) {
			cloneConceptAsUICC(c, newRootConcept);
		}
	}

}
