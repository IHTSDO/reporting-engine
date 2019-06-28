package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.AntigenTermGenerator;
import org.ihtsdo.termserver.scripting.util.TermGenerator;

/*
 * SUBST-17 for Antigen reterming, but other TermGenerators can be swapped in.
 */
public class NormalizeTerms extends BatchFix implements RF2Constants{
	
	TermGenerator termGenerator = new AntigenTermGenerator(this);
	
	protected NormalizeTerms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException, InterruptedException {
		NormalizeTerms fix = new NormalizeTerms(null);
		try {
			ReportSheetManager.targetFolderId="1E6kDgFExNA9CRd25yZk_Y7l-KWRf8k6B"; //Drugs/Normalize Terming
			fix.inputFileHasHeaderRow = true;
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		String X = ((ConceptChange)c).getNewTerm();  //Actually just the 'X' part of the new term eg Organism Name
		if (X == null || X.isEmpty()) {
			throw new IllegalArgumentException("Terming expects to be told 'X'");
		}
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		int changesMade = termGenerator.ensureTermsConform(t, loadedConcept, X, CharacteristicType.INFERRED_RELATIONSHIP);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		ConceptChange c = new ConceptChange(lineItems[0], lineItems[1], lineItems[2]);
		return Collections.singletonList(c);
	}
}
