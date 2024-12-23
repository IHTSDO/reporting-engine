package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Class to fix ALL Case Significance issues.
 * ISRS-302
 * 
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaseSignificanceFixAll extends DeltaGenerator implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(CaseSignificanceFixAll.class);

	public enum Mode{ REPORT_ONLY, SILENT_CHANGE, REPORT_CHANGE };
	public Mode mode;

	private List<String> exceptions = new ArrayList<>();
	boolean padReport = false;
	
	public CaseSignificanceFixAll (TermServerScript client, Mode mode) {
		if (client != null) {
			setReportManager(client.getReportManager());
		}
		padReport = true;  //Add two blank columns to make output compatible with a BatchFix
		this.mode = mode;
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		CaseSignificanceFixAll delta = new CaseSignificanceFixAll(null, Mode.REPORT_CHANGE);
		try {
			delta.newIdsRequired = false; // We'll only be modifying existing descriptions\
			delta.additionalReportColumns = "Old, New, EffectiveTime, Notes";
			delta.inputFileHasHeaderRow = false;
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false); 
			//We won't include the project export in our timings
			delta.startTimer();
			delta.process();
			delta.flushFiles(false); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}

	@Override
	protected void process() throws TermServerScriptException {
		LOGGER.info("Processing...");
		for (Concept c : GraphLoader.getGraphLoader().getAllConcepts()) {
			if (c.isActive()) {
				normalizeCaseSignificance(c, false);
			}
			if (c.isModified()) {
				incrementSummaryInformation("Concepts modified", 1);
				outputRF2(c);  //Will only output dirty fields.
			}
		}
	}

	public int normalizeCaseSignificance(Concept c, boolean aggressive) throws TermServerScriptException {
		int changesMade = 0;
		if (exceptions.contains(c.getId())) {
			if (!mode.equals(Mode.SILENT_CHANGE)) {
				report (c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "", "","","","Concept manually listed as an exception");
			}
		} else {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (exceptions.contains(c.getId())) {
					if (!mode.equals(Mode.SILENT_CHANGE)) {
						report (c, Severity.MEDIUM, ReportActionType.NO_CHANGE, d, "","","","Description manually listed as an exception");
					}
				} else {
					switch (d.getCaseSignificance()) {
					case INITIAL_CHARACTER_CASE_INSENSITIVE : changesMade += normalizeCaseSignificance_cI(c, d);
																break;
					case CASE_INSENSITIVE : changesMade += normalizeCaseSignificance_ci(c, d);
																break;
					case ENTIRE_TERM_CASE_SENSITIVE:  //Have to assume author is correct here, unless we're being aggressive
												if (aggressive) {
													changesMade += normalizeCaseSignificance_CS(c, d);
												}
												break;
					}
				}
			}
		}
		return changesMade;
	}

	private int normalizeCaseSignificance_cI(Concept c, Description d) throws TermServerScriptException {
		int changesMade = 0;
		String term = d.getTerm();
		if (d.getType().equals(DescriptionType.FSN)) {
			term = SnomedUtils.deconstructFSN(term)[0];
		}
		//First letter lower case should always be CS
		if (startsLower(term)) {
			if (!mode.equals(Mode.REPORT_ONLY)) {
				d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
				d.setEffectiveTime(null);
				d.setDirty();
				changesMade++;
				c.setModified();  //Indicates concept contains changes, without necessarily needing a concept RF2 line output
			}
			if (!mode.equals(Mode.SILENT_CHANGE)) {
				report(c, Severity.LOW,ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, "cI", "CS", d.getEffectiveTimeSafely());
				incrementSummaryInformation("Descriptions modified", 1);
			}
		} else if (!StringUtils.isCaseSensitive(term)) {
			report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, d, "", "", d.getEffectiveTimeSafely(), "Confirm that term contains lower case, case significant letters");
		}
		return changesMade;
	}
	
	private int normalizeCaseSignificance_ci(Concept c, Description d) throws TermServerScriptException {
		String term = d.getTerm();
		if (d.getType().equals(DescriptionType.FSN)) {
			term = SnomedUtils.deconstructFSN(term)[0];
		}
		boolean changeMade = false;
		//First letter lower case should always be CS
		if (!mode.equals(Mode.REPORT_ONLY)) {
			if (startsLower(term)) {
				d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
				changeMade = true;
			} else if (StringUtils.isCaseSensitive(term)) {
				d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
				changeMade = true;	
			}
		}
		
		if (changeMade) {
			String newValue = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
			d.setEffectiveTime(null);
			d.setDirty();
			c.setModified();  //Indicates concept contains changes, without necessarily needing a concept RF2 line output
			if (!mode.equals(Mode.SILENT_CHANGE)) {
				report(c,Severity.LOW,ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, "ci", newValue, d.getEffectiveTimeSafely());
				incrementSummaryInformation("Descriptions modified", 1);
			}
		}
		return changeMade ? 1 : 0;
	}
	
	/**
	 * Here we're going to say that if we ONLY start with an upper case letter then there's been 
	 * a mistake and we can make the entire term case insensitive
	 * @param c
	 * @param d
	 * @param silent 
	 * @throws TermServerScriptException 
	 */
	private int normalizeCaseSignificance_CS(Concept c, Description d) throws TermServerScriptException {
		int changesMade = 0;
		String term = d.getTerm();
		if (d.getType().equals(DescriptionType.FSN)) {
			term = SnomedUtils.deconstructFSN(term)[0];
		}
		//First letter lower case should always be CS, so no change.  
		//Otherwise if we've no further upper case letters, we *could* be ok to say this
		//is "ci", but only if we're being aggressive about it.
		if (!startsLower(term) && !StringUtils.isCaseSensitive(term)) {
			if (!mode.equals(Mode.REPORT_ONLY)) {
				d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
				d.setEffectiveTime(null);
				d.setDirty();
				changesMade++;
				c.setModified();  //Indicates concept contains changes, without necessarily needing a concept RF2 line output
			}
			
			if (mode.equals(Mode.REPORT_ONLY)) {
				report(c,Severity.MEDIUM,ReportActionType.VALIDATION_CHECK, d, "Algorithm would suggest change from CS to ci");
			} else if (mode.equals(Mode.REPORT_CHANGE)) {
				report(c,Severity.MEDIUM,ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, "CS", "ci", d.getEffectiveTimeSafely());
				incrementSummaryInformation("Descriptions modified", 1);
			}
		}
		return changesMade;
	}

	private boolean startsLower(String term) {
		/*Chop off any leading non-numeric characters - ignore
		int nonNumericCount = 0;
		while (!Character.isLetter(term.charAt(nonNumericCount))) {
			nonNumericCount++;
			//Is there nothing left?
			if (nonNumericCount == term.length()) {
				return false;
			}
		}
		term = term.substring(nonNumericCount, term.length());
		*/
		
		String firstLetter = term.substring(0, 1);
		//If the first character is not a letter, then there is no significance to the case of the subsequent character
		if (!Character.isLetter(term.charAt(0))) {
			return false;
		}
		return firstLetter.equals(firstLetter.toLowerCase());
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
	
	protected void report (Concept c, Severity severity,  ReportActionType actionType, Object...details) throws TermServerScriptException {
		if (padReport) {
			super.report (null, c, severity, actionType, details);
		} else {
			super.report(c, c.getFSNDescription(), details);
		}
	}

	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		exceptions.add("10692681000119108");
		exceptions.add("108796006");
		exceptions.add("108797002");
		exceptions.add("108798007");
		exceptions.add("10997931000119101");
		exceptions.add("1228899010");
		exceptions.add("129477009");
		exceptions.add("137421000119106");
		exceptions.add("1493566011");
		exceptions.add("1493669014");
		exceptions.add("1493671014");
		exceptions.add("1495349011");
		exceptions.add("1783615017");
		exceptions.add("1784004015");
		exceptions.add("195614019");
		exceptions.add("199692013");
		exceptions.add("202924015");
		exceptions.add("203022011");
		exceptions.add("203023018");
		exceptions.add("203032016");
		exceptions.add("2161348018");
		exceptions.add("2161349014");
		exceptions.add("2161491010");
		exceptions.add("2161492015");
		exceptions.add("2475963019");
		exceptions.add("2476011018");
		exceptions.add("2476480018");
		exceptions.add("2536185016");
		exceptions.add("2536209018");
		exceptions.add("2536351011");
		exceptions.add("2537349017");
		exceptions.add("2538029015");
		exceptions.add("2550013017");
		exceptions.add("2551879017");
		exceptions.add("2551880019");
		exceptions.add("2551903016");
		exceptions.add("2553866017");
		exceptions.add("2553886018");
		exceptions.add("2579054016");
		exceptions.add("2579055015");
		exceptions.add("2579379010");
		exceptions.add("2642148011");
		exceptions.add("2915670010");
		exceptions.add("3005477016");
		exceptions.add("3078863019");
		exceptions.add("3084391017");
		exceptions.add("3297637015");
		exceptions.add("3438142014");
		exceptions.add("3467349017");
		exceptions.add("3467350017");
		exceptions.add("3472936011");
		exceptions.add("3514389013");
		exceptions.add("3516254012");
		exceptions.add("3516255013");
		exceptions.add("386914007");
		exceptions.add("386915008");
		exceptions.add("398841009");
		exceptions.add("419598008");
		exceptions.add("421559001");
		exceptions.add("623096015");
		exceptions.add("718688008");
		exceptions.add("724096007");
		exceptions.add("725026008");
		exceptions.add("725027004");
		exceptions.add("725028009");
		exceptions.add("725078006");
		exceptions.add("725079003");
		exceptions.add("725390002");
		exceptions.add("725391003");
		exceptions.add("725582001");
		exceptions.add("725930008");
		exceptions.add("726705007");
		exceptions.add("726706008");
		exceptions.add("726707004");
		exceptions.add("732252005");
		exceptions.add("732259001");
		exceptions.add("733083006");
		exceptions.add("733084000");
		exceptions.add("733085004");
		exceptions.add("733111000");
		exceptions.add("733112007");
		exceptions.add("733115009");
		exceptions.add("733473000");
		exceptions.add("733518000");
		exceptions.add("733519008");
		exceptions.add("733520002");
		exceptions.add("733521003");
		exceptions.add("733598001");
		exceptions.add("733843002");
		exceptions.add("734016004");
		exceptions.add("734029004");
		exceptions.add("734030009");
		exceptions.add("734937009");
		exceptions.add("734977001");
		exceptions.add("734978006");
		exceptions.add("735238003");
		exceptions.add("735239006");
		exceptions.add("737545003");
		exceptions.add("737546002");
		exceptions.add("737547006");
		exceptions.add("77860012");
		exceptions.add("3444259016");
		exceptions.add("3444260014");
		exceptions.add("3450282018");
		exceptions.add("3452124015");
		exceptions.add("3464334019");
		exceptions.add("3464453011");
		exceptions.add("3491133013");
		exceptions.add("3491689017");
		exceptions.add("3509526012");
		exceptions.add("3509527015");
		exceptions.add("3525383017");
		exceptions.add("3526666012");
		exceptions.add("3526671017");
		exceptions.add("3533581011");
		exceptions.add("3542839015");
		exceptions.add("3438142014");
		exceptions.add("3467349017");
		exceptions.add("3467350017");
		exceptions.add("3514389013");
		exceptions.add("3516254012");
		exceptions.add("3516255013");
	}	
	
}
