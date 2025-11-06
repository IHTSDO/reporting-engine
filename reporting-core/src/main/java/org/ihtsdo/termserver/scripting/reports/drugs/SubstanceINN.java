package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * SUBST-226 Checks substance terming against spreadsheet
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubstanceINN extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(SubstanceINN.class);

	List<INNSet> allINN = new ArrayList<>();

	public static void main(String[] args) throws TermServerScriptException {
		SubstanceINN report = new SubstanceINN();
		try {
			report.additionalReportColumns = "SemTag, Description, Affected, Issue, Spreadsheet Term";
			report.inputFileHasHeaderRow = true;
			report.expectNullConcepts = true;
			report.init(args);
			report.processFile(report.getInputFile()); //Load early so we know if there's a problem
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.runSubstanceINNReport();
		} catch (Exception e) {
			LOGGER.info("Failed to produce SubstanceINNReport due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
	}

	private void runSubstanceINNReport() throws TermServerScriptException {
		
		//For all substances, check if they have any match to an INN Set entry
		for (Concept subst : SUBSTANCE.getDescendants(NOT_SET)) {
			INNSet innSet = findINNSetMatch(subst);
			if (innSet != null) {
				if (subst.getConceptId().equals("58202007")) {
					LOGGER.debug("Debug Here");
				}
				validateAgainstInnSet(subst, innSet);
			} else {
				checkforSulphur(subst);
			}
		}
	}

	private INNSet findINNSetMatch(Concept subst) throws TermServerScriptException {
		//Do any of our descriptions match any of the INN set entries
		Set<INNSet> matches = new HashSet<>();
		for (Description d : subst.getDescriptions(ActiveState.ACTIVE)) {
			String term = d.getTerm().toLowerCase();
			if (d.getType().equals(DescriptionType.FSN)) {
				term = SnomedUtils.deconstructFSN(term)[0];
			}
			for (INNSet innSet : allINN) {
				if (innSet.matchAny(term)) {
					matches.add(innSet);
				}
			}
		}
		if (matches.size() == 0) {
			return null;
		} else if (matches.size() > 1) {
			throw new TermServerScriptException("Multiple INN matches " + matches.stream().map(m -> m.toString()).collect(Collectors.joining(",")));
		} else {
			return matches.iterator().next();
		}
	}
	
	
	private void validateAgainstInnSet(Concept subst, INNSet innSet) throws TermServerScriptException {
		if (!SnomedUtils.deconstructFSN(subst.getFsn())[0].toLowerCase().equals(innSet.fsn)) {
			report(subst, subst.getFSNDescription(), "FSN", "Mismatched", innSet.fsn);
		}
		Description usPT = subst.getPreferredSynonym(US_ENG_LANG_REFSET);
		Description gbPT = subst.getPreferredSynonym(GB_ENG_LANG_REFSET);
		boolean usGbAligned = usPT.equals(gbPT);
		
		if (!usPT.getTerm().toLowerCase().equals(innSet.usPt)) {
			report(subst, usPT, "US_PT", "Mismatched", innSet.usPt);
		}
		
		if (!gbPT.getTerm().toLowerCase().equals(innSet.gbPt)) {
			report(subst, gbPT, "GB_PT", "Mismatched", innSet.gbPt);
		}
		
		//If we have different us/gb terms then they should be acceptable in the other dialect
		if (!usGbAligned) {
			if (!usPT.hasAcceptability(Acceptability.ACCEPTABLE, GB_ENG_LANG_REFSET)) {
				report(subst, usPT, "US_PT", "Unexpected GB Acceptability", innSet.usPt);
			}
			
			if (!gbPT.hasAcceptability(Acceptability.ACCEPTABLE, US_ENG_LANG_REFSET)) {
				report(subst, gbPT, "GB_PT", "Unexpected US Acceptability", innSet.gbPt);
			}
		}
		
		//Do we have a us or gb synonym?  Should be acceptable in one and not the other
		if (innSet.gbSyn != null && !innSet.gbSyn.isEmpty()) {
			Description syn = subst.findTerm(innSet.gbSyn, null, true, false); //No language, Yes case insensitive, no don't include inactive terms
			if (syn == null) {
				report(subst, subst.getFsn(), "GB_SYN", "Expected SYN missing", innSet.gbSyn);
			} else if (syn.hasAcceptability(Acceptability.BOTH, US_ENG_LANG_REFSET)) {
				report(subst, syn, "GB_SYN", "Unexpected US Acceptability", innSet.gbSyn);
			}
		}
		
		if (innSet.usSyn != null && !innSet.usSyn.isEmpty()) {
			Description syn = subst.findTerm(innSet.usSyn, null, true, false);
			if (syn == null) {
				report(subst, subst.getFsn(), "US_SYN", "Expected SYN missing", innSet.usSyn);
			} else if (syn.hasAcceptability(Acceptability.BOTH, GB_ENG_LANG_REFSET)) {
				report(subst, syn, "GB_SYN", "Unexpected GB Acceptability", innSet.usSyn);
			}
		}
		
		for (String synStr : innSet.bothSyn) {
			Description syn = subst.findTerm(synStr, null, true, false);  //No language, Yes case insensitive, no don't include inactive terms
			if (syn == null) {
				report(subst, subst.getFsn(), "SYN", "Expected SYN missing", synStr);
			} else {
				if (!syn.hasAcceptability(Acceptability.ACCEPTABLE, GB_ENG_LANG_REFSET)) {
					report(subst, syn, "SYN", "Unexpected GB Acceptability", synStr);
				}
				if (!syn.hasAcceptability(Acceptability.ACCEPTABLE, US_ENG_LANG_REFSET)) {
					report(subst, syn, "SYN", "Unexpected US Acceptability", synStr);
				}
			}
		}
		
		for (String notAllowed : innSet.notAllowed) {
			Description syn = subst.findTerm(notAllowed, null, true, false);
			if (syn != null) {
				//We're saying "ph" is an acceptably not allowed
				if (notAllowed.contains("ph")) {	
					report(subst, syn, "Sort of ALLOWED", "Sort of allowed term present", notAllowed);
				} else {
					report(subst, syn, "NOT ALLOWED", "Not allowed term present", notAllowed);
				}
			}
		}
	}
	

	private void checkforSulphur(Concept c) throws TermServerScriptException {
		//It is acceptable to have Sulphur form in GB, but never in US
		for (Description d : c.getDescriptions(Acceptability.PREFERRED, null, ActiveState.ACTIVE)) {
			if (d.getTerm().contains("ulph")) {
				report(c, d, "PREF", "Not allowed as preferred term");
				return;
			}
		}
		
		for (Description d : c.getDescriptions(Acceptability.ACCEPTABLE, null, ActiveState.ACTIVE)) {
			if (d.getTerm().contains("ulph") && d.isAcceptable(US_ENG_LANG_REFSET)) {
				report(c, d, "US_A", "Not allowed at all - 'ulph'");
				return;
			}
		}
		
	}

	protected List<Component> loadLine(String[] items) {
		INNSet set = new INNSet();
		set.fsn = items[0].toLowerCase().trim();
		set.gbPt = items[1].toLowerCase().trim();
		set.usPt = items[2].toLowerCase().trim();
		if (items.length > 3 && !items[3].isEmpty()) {
			set.bothSyn = Arrays.asList(items[3].split(TAB)).stream().map(s -> s.toLowerCase().trim()).collect(Collectors.toList());
		}
		
		if (items.length > 4) {
			set.gbSyn = items[4].toLowerCase().trim();
		}
		
		if (items.length > 5) {
			set.usSyn = items[5].toLowerCase().trim();
		}
		if (items.length > 6 && !items[6].isEmpty()) {
			set.notAllowed = Arrays.asList(items[6].split(TAB)).stream().map(s -> s.toLowerCase().trim()).collect(Collectors.toList());
		}
		allINN.add(set);
		return null;
	}
	
	class INNSet {
		String fsn;
		String gbPt;
		String usPt;
		List<String> bothSyn = new ArrayList<>();
		String gbSyn;
		String usSyn;
		List<String> notAllowed = new ArrayList<>();
		
		boolean matchAny (String term) {
			term = term.toLowerCase();
			return fsn.equals(term) ||
					gbPt.equals(term) ||
					usPt.equals(term) ||
					bothSyn.contains(term) ||
					(gbSyn!= null && gbSyn.equals(term)) ||
					(usSyn!= null && usSyn.equals(term));
		}
		
		public String toString() {
			return fsn;
		}
		
		@Override
		public boolean equals(Object other) {
			return fsn.equals(((INNSet)other).fsn);
		}
	}
	
}
