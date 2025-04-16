package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.util.*;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.CaseSensitivityUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * DRUGS-269, SUBST-130, MAINT-77, INFRA-2723, MAINT-345
 * Lists all case-sensitive terms that do not have capital letters after the first letter
 * UPDATE: We'll also load in the existing cs_words.txt file instead of hardcoding a list of proper nouns.
 */
public class CaseSensitivity extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(CaseSensitivity.class);
	private static final String INCLUDE_SUB_ORG = "Include Substances and Organisms";
	private static final String RECENT_CHANGES_ONLY = "Recent Changes Only";

	private static final List<String> eponymPatterns = List.of("disorder", "syndrome", "disease", "anomaly");

	private List<Concept> hierarchiesKnownLikelyToBeCS;

	enum Strictness {
		LAX, PICKY
	}

	private Set<Concept> allExclusions = new HashSet<>();
	private Set<String> whiteList = new HashSet<>();  //Note this can be both descriptions and concept SCTIDs
	private boolean recentChangesOnly = true;
	private CaseSensitivityUtils csUtils;
	private boolean includeProductionWhiteList = true;

	private Strictness strictness = Strictness.LAX;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, Object> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, "N");
		params.put(RECENT_CHANGES_ONLY, "N");
		params.put(INCLUDE_SUB_ORG, "N");
		TermServerScript.run(CaseSensitivity.class, params, args);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		super.init(run);
		ReportSheetManager.setTargetFolderId(GFOLDER_RELEASE_QA);
		recentChangesOnly = run.getParameters().getMandatoryBoolean(RECENT_CHANGES_ONLY);
		additionalReportColumns = "FSN, Semtag, Description, isPreferred, CaseSignificance, Issue";
		inputFiles.add(0, new File("resources/cs_words.tsv"));
	}

	@Override
	public void postInit() throws TermServerScriptException {
		super.postInit();

		//If we include substances and organisms for cs checking, then we're not treating them as sources of truth
		boolean substancesAndOrganismsAreSourcesOfTruth = !getJobRun().getParameters().getMandatoryBoolean(INCLUDE_SUB_ORG);
		csUtils = CaseSensitivityUtils.get(substancesAndOrganismsAreSourcesOfTruth);
		
		whiteList.add("3722547016");
		whiteList.add("3722542010");
		whiteList.add("3737657014");

		if (includeProductionWhiteList) {
			includeProductionWhiteList();
		}

		hierarchiesKnownLikelyToBeCS = List.of(
				gl.getConcept("767524001 |Unit of measure|"),
				gl.getConcept("272396007 |Ranked categories|"));
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(UNPROMOTED_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.add(INCLUDE_SUB_ORG).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.add(RECENT_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Case Significance")
				.withDescription("This report validates the case significance of new and modified descriptions.  Note that the Substances and Organism hierarchies are normally excluded as they are taken to be a 'source of truth' and since most Organisms start with proper nouns we see a lot of false positives, but this setting can be overridden. " +
									"The 'Issues' count here reflects the number of rows in the report.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		initialiseSummaryInformation(ISSUE_COUNT);
		//Work through all active descriptions of all hierarchies
		for (Concept targetHierarchy : ROOT_CONCEPT.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//We won't process any hierarchies that are a source of truth
			if (!csUtils.isSourceOfTruthHierarchy(targetHierarchy)) {
				List<Concept> hierarchyDescendants = new ArrayList<>(targetHierarchy.getDescendants(NOT_SET));
				LOGGER.info("Checking case significance in target hierarchy: {}", targetHierarchy);
				checkCaseSignificanceOfHierarchy(hierarchyDescendants);
				LOGGER.info("Completed hierarchy: {}", targetHierarchy);
			}
		}
	}

	private void checkCaseSignificanceOfHierarchy(List<Concept> hierarchyDescendants) throws TermServerScriptException {
		for (Concept c : hierarchyDescendants) {
			if (c.getId().equals("104606001")) {
				LOGGER.info("Checking case significance in concept: {}", c);
			}
			if (inScopeForCsChecking(c)) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (!d.getType().equals(DescriptionType.TEXT_DEFINITION)
							&& checkCaseSignificance(c, d)) {
						break;
					} else if (d.getType().equals(DescriptionType.TEXT_DEFINITION)
							&& !d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
						report(c, d, "Y", "CS", "Text Definitions must be CS");
						countIssue(c);
					}
				}
			}
		}
	}

	/*
	*@return - true if we've reported an issue on this concept
	 */
	private boolean checkCaseSignificance(Concept c, Description d) throws TermServerScriptException {
		if (!inScopeForCsChecking(d)) {
			return false;
		}

		String term = d.getTerm().replace("-", " ");
		String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
		String chopped = term.substring(1);
		String preferred = d.isPreferred() ? "Y" : "N";

		if (reportedForNumberStartRuleOrAccepted(c, d, caseSig, preferred)) {
			return true;
		}

		if (reportedForTextDefinitionRuleOrAccepted(c, d, caseSig, preferred)) {
			return true;
		}

		if (reportedForFirstLetterInfractionOrAccepted(c, d,term, caseSig, preferred)) {
			return true;
		}
		
		if (reportedForStartingWithAcronymInfraction(c, d, caseSig, preferred)) {
			return true;
		}

		if (caseSig.equals(CS) || caseSig.equals(cI)) {
			if (checkCaseSignicanceOfCaseSensitiveTerm(c, d, chopped, preferred, caseSig, term)) {
				return true;
			}
		} else {
			if (checkCaseSignificanceOfCaseInsensitiveTerm(c, d, chopped, preferred, caseSig)) {
				return true;
			}
		}
		return false;
	}

	private boolean reportedForNumberStartRuleOrAccepted(Concept c, Description d, String caseSig, String preferred) throws TermServerScriptException {
		if (csUtils.startsWithNumberOrSymbol(d.getTerm())) {
			if (caseSig.equals(CS)) {
				//Now if we're not being picky, we'll ignore numeric acronyms and terms that consist of a single word eg 30mm
				if (strictness.equals(Strictness.LAX)
						&& ( csUtils.startsWithAcronym(d.getTerm()) || csUtils.termIsSingleWord(d.getTerm()))) {
					return false;
				}
				report(c, d, preferred, caseSig, "Terms starting with numbers cannot be CS");
				countIssue(c);
				return true;
			}
			//Does this term contain a capital letter after the first letter?
			if (caseSig.equals(ci) && d.getTerm().length() > 1) {
				String chopped = d.getTerm().substring(1);
				if (!chopped.equals(chopped.toLowerCase())) {
					report(c, d, preferred, caseSig, "Terms featuring a capital letter after the first letter cannot be ci");
					countIssue(c);
					return true;
				}
			}
		}
		return false;
	}

	private boolean reportedForStartingWithAcronymInfraction(Concept c, Description d, String caseSig, String preferred) throws TermServerScriptException {
		//Now, although we might _have_ an acronym, if it starts with a number, then we'd want cI instead
		if (!caseSig.equals(CS) && csUtils.startsWithAcronym(d.getTerm())
			&& !csUtils.startsWithNumberOrSymbol(d.getTerm())
			&& !csUtils.startsWithCaseInsensitivePrefix(d.getTerm())) {
			report(c, d, preferred, caseSig, "Terms starting with acronyms must be CS");
			countIssue(c);
			return true;
		}
		return false;
	}

	private boolean reportedForFirstLetterInfractionOrAccepted(Concept c, Description d, String term, String caseSig, String preferred) throws TermServerScriptException {
		String firstLetter = term.substring(0, 1);
		if (Character.isLetter(firstLetter.charAt(0))
				&& firstLetter.equals(firstLetter.toLowerCase())
				&& !caseSig.equals(CS)) {
			//Lower case first letters must be entire term case-sensitive
			report(c, d, preferred, caseSig, "Terms starting with lower case letter must be CS");
			countIssue(c);
			return true;
		}
		return false;
	}

	private boolean reportedForTextDefinitionRuleOrAccepted(Concept c, Description d, String caseSig, String preferred) throws TermServerScriptException {
		//Text Definitions must be CS
		if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
			if (!caseSig.equals(CS)) {
				report(c, d, preferred, caseSig, "Text Definitions must be CS");
				countIssue(c);
			}
			return true;
		}
		return false;
	}

	private boolean inScopeForCsChecking(Concept c) {
		if (whiteListedConceptIds.contains(c.getId())) {
			incrementSummaryInformation(WHITE_LISTED_COUNT);
			return false;
		}

		return !(allExclusions.contains(c) || whiteList.contains(c.getId()));
	}

	private boolean inScopeForCsChecking(Description d) {
		//Are we checking only unpromoted changes?
		if (unpromotedChangesOnly && !unpromotedChangesHelper.hasUnpromotedChange(d)) {
			return false;
		}

		if (whiteList.contains(d.getDescriptionId())) {
			return false;
		}

		if (recentChangesOnly && d.isReleasedSafely()) {
			return false;
		}
		incrementSummaryInformation("Descriptions checked");
		return true;
	}

	private boolean checkCaseSignificanceOfCaseInsensitiveTerm(Concept c, Description d, String chopped, String preferred, String caseSig) throws TermServerScriptException {
		//For case-insensitive terms, we're on the lookout for capital letters after the first letter
		if (!chopped.equals(chopped.toLowerCase())) {
			report(c, d, preferred, caseSig, "Case insensitive term has a capital after first letter");
			countIssue(c);
			return true;
		}

		//Or if one of our sources of truth?
		String firstWord = d.getTerm().split(" ")[0];
		if (csUtils.startsWithKnownCsWordInContext(c, firstWord, null)) {
			report(c, d, preferred, caseSig, "Case insensitive term should be CS as per " +  csUtils.explainCsWordInContext(c, firstWord));
			countIssue(c);
			return true;
		}
		return false;
	}

	private boolean checkCaseSignicanceOfCaseSensitiveTerm(Concept c, Description d, String chopped, String preferred, String caseSig, String term) throws TermServerScriptException {
		if (chopped.equals(chopped.toLowerCase()) &&
				!csUtils.singleLetterCombo(term) &&
				!csUtils.startsWithLowerCaseLetter(term) &&
				!csUtils.startsWithKnownCaseSensitiveTerm(c, term) &&
				!csUtils.containsKnownLowerCaseWord(term)) {
			if ((caseSig.equals(CS) && csUtils.startsWithSingleLetter(d.getTerm()))
			|| (strictness.equals(Strictness.LAX) && (isInHierarchyKnownToBeLikelyCS(c) || isProbableEponym(d) || checkKnownSpecialCases(d)))) {
				//Probably OK
			} else {
				report(c, d, preferred, caseSig, "Case sensitive term does not have capital after first letter");
				countIssue(c);
				return true;
			}
		}
		return false;
	}

	private boolean checkKnownSpecialCases(Description d) {
		//diagnostic allergen extract as a CS usually uses a specific organism, but is then modelled using the substance,
		//so we cannot check for a source of truth using the correct context.  Calling this function in 'LAX' strictness,
		//we'll assume it's OK
		return d.getTerm().contains("diagnostic allergen extract");
	}

	private boolean isProbableEponym(Description d) {
		//If this is a 2 word term like X disorder, then (assuming we're saying CS) this is probably an eponym
		String term = d.getTerm();
		if (d.getType().equals(DescriptionType.FSN)) {
			term = SnomedUtilsBase.deconstructFSN(term)[0];
		}
		String[] words = term.split(" ");
		if (words.length == 2) {
			for (String eponymWord : eponymPatterns) {
				if (words[1].equals(eponymWord)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isInHierarchyKnownToBeLikelyCS (Concept c) throws TermServerScriptException {
		Set<Concept> ancestors = c.getAncestors(RF2Constants.NOT_SET);
		for (Concept hierarchyKnownToBeLikelyCS : hierarchiesKnownLikelyToBeCS) {
			if (ancestors.contains(hierarchyKnownToBeLikelyCS)) {
				return true;
			}
		}
		return false;
	}

	//Temporary measure to allow like-for-like testing with the existing case sensitivity report in production
	private void includeProductionWhiteList() {
		whiteList.addAll(List.of("109431006","133932002","13464007","196609006","1991000221106","2001000221108","205480005","232436000","234362006","234969005","235597001"));
		whiteList.addAll(List.of("236979009","236981006","253017000","253781004","253828000","254803001","263241008","280664008","294280001","369625009","389237009"));
		whiteList.addAll(List.of("389239007","397568004","420338000","62294009","6492006","702447002","702625009","707742001","722893002","72922008","777919007"));
		whiteList.addAll(List.of("782670003","782690007","782721009","782940006","783096008","783097004","783157004","783159001","783166000","783553008","783737007"));
		whiteList.addAll(List.of("784008009","784373007","784391002","785389002","785684000","785685004","785809005","786089001","787407003","787963008","787964002"));
		whiteList.addAll(List.of("788417006","789160000","836385002","836386001","836403007","422653006","424399000","860785004","860780009","425106001","863901004"));
		whiteList.addAll(List.of("860767006","424575004","866006002","423331005","866005003","424877001","415354003","865899005","865988009","423937004","865995000"));
		whiteList.addAll(List.of("423185002","863902006","860782001","863898000","863899008","237770005","870448005","340671000119102","335061000119105","680291000119102","870537001"));
		whiteList.addAll(List.of("911000221103","724332002","717953009","734152003","921000221108","871767000","767634004","737081007","871919004","722215002","836495005"));
		whiteList.addAll(List.of("871719005","871587002","866121005","876850004","77121009","896791000","787484007","1010666007","1010668008","1010664005","1010610007"));
		whiteList.addAll(List.of("1119301001","297602009","73736004","1142206004","1142046003","62999006","1144919009","1144914004","1148488008","1148740009","239072003"));
		whiteList.addAll(List.of("277807007","1149131009","1149099005","1156209001","1156212003","1156211005","1156385007","1156478007","408539000","1156913001","1156760001"));
		whiteList.addAll(List.of("1156914007","445104009","24559001","389214003","257751006","182405008","398958000","447111007","1172898008","109436001","239030004"));
		whiteList.addAll(List.of("1177081002","1179093004","1179456002","399050001","1186710001","1179075000","1187120008","1187132007","156246008","95738005","399114005"));
		whiteList.addAll(List.of("398049005","297836000","1197589000","1197732001","1197733006","1197705006","1197707003","1197714001","223356001","223550008","1204493003"));
		whiteList.addAll(List.of("1204486002","1204478002","1208615009","1208747005","1208934006","1208849004","1077861000119105","253855721000119107","515623061000119105","315051000119100","314941000119102"));
		whiteList.addAll(List.of("314961000119103","1077851000119108","1217041008","1217654000","1220594007","312351000119105","1222676004","1228875006","1229872004","1229895008","190687004"));
		whiteList.addAll(List.of("15663521000119106","15663481000119106","1231140009","65720003","337661000119105","343431000119105","332051000119100","1231437004","1231544006","278900006","89718004"));
		whiteList.addAll(List.of("1234919008","788613004","1237346001","1231642002","1251449006","1254809008","1254792004","1251395000","193410003","1255278004","1251367000"));
		whiteList.addAll(List.of("1255799000","1255801002","1255800001","105941000119105","1255964009","1255983002","1254781000","1259219009","1259218001","1259106002","1259108001"));
		whiteList.addAll(List.of("1259939000","1259068001","1259896006","1260412008","1263394008","1264005000","42312000","1264278006","37709009","1268554008","310286009"));
		whiteList.addAll(List.of("1268846004","310287000","1268838000","1269399005","1284851009","70694009","1285519007","1285518004","206138281000119100","1287347006","1287338003"));
		whiteList.addAll(List.of("1287387001","123912007","123888000","123874004","82913001","123873005","123889008","123928002","123887005","123914008","781550002"));
		whiteList.addAll(List.of("123877006","123876002","123900008","123902000","123941003","123913002","123915009","123927007","123916005","123904004","123917001"));
		whiteList.addAll(List.of("123918006","123942005","123890004","123929005","123903005","123940002","123943000","123901007","123894008","123930000","123931001"));
		whiteList.addAll(List.of("123906002","123919003","123905003","53901006","123908001","123933003","123944006","123932008","123907006","123893002","44290007"));
		whiteList.addAll(List.of("123880007","123921008","111785002","123896005","123882004","73177007","123895009","123920009","123909009","123884003","123934009"));
		whiteList.addAll(List.of("85959007","123881006","123898006","123923006","31272009","123922001","123911000","123936006","123899003","123886001","123910004"));
		whiteList.addAll(List.of("123937002","123897001","123926003","123938007","123885002","123939004","123925004","123924000","1287729003","1287726005","56044004"));
		whiteList.addAll(List.of("1287876003","23127007","51311000087100","312381000119103","688271000119100","50351000087108","1295529002","12313004","810003","37029000","15628691000119105"));
		whiteList.addAll(List.of("15628651000119100","15628731000119103","1303772009","1303973005","1304277005","1306860003","1306859008","1331912004","1332075009","1332074008","1332076005"));
		whiteList.addAll(List.of("1332077001","15719361000119100","55166000","81854007","238836000","1335905003","1335861005","1332344005","1222710008","1342414005","1343302008"));
		whiteList.addAll(List.of("864990181000119100","1345047003","1345114009","1345135006","1336200007","1344944003","1348308009","1351326006","1351335004","65880007","238926009","248680001"));
	}

}
