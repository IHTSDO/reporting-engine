package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.reports.release.ReleaseIssuesReport;
import org.ihtsdo.termserver.scripting.reports.release.ValidateInactivationsWithAssociations;

import java.util.HashMap;
import java.util.Map;

import static org.ihtsdo.termserver.scripting.TermServerScript.*;

/**
 * Test class to check the interaction between two or more reports run back to back on the same worker
 */
public class MultiReportRunner {

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "N");
		params.put(UNPROMOTED_CHANGES_ONLY, "N");
		TermServerScript.run(ReleaseIssuesReport.class, args, params);

		params.put(SUB_HIERARCHY, ROOT_CONCEPT.toString());
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "N");
		params.put(UNPROMOTED_CHANGES_ONLY, "N");
		TermServerScript.run(ValidateInactivationsWithAssociations.class, args, params);

		params.put(SUB_HIERARCHY, ROOT_CONCEPT.toString());
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "Y");
		params.put(UNPROMOTED_CHANGES_ONLY, "N");
		TermServerScript.run(ValidateInactivationsWithAssociations.class, args, params);

		params.put(INCLUDE_ALL_LEGACY_ISSUES, "Y");
		params.put(UNPROMOTED_CHANGES_ONLY, "N");
		TermServerScript.run(ReleaseIssuesReport.class, args, params);
	}
}