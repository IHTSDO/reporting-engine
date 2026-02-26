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
import org.ihtsdo.termserver.scripting.util.DrugUtils;
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

	private static final boolean USE_ORGANISM_TAXONOMY_WORD_TRIGGERS_CS_RULE = true;

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
		additionalReportColumns = "FSN, Semtag, Description, EffectiveTime, isPreferred, CaseSignificance, Issue";
		inputFiles.add(0, new File("resources/cs_words.tsv"));

		//Don't allow substances and organisms to be checked unless we're only working with unpromoted changes
		if (getJobRun().getParameters().getMandatoryBoolean(INCLUDE_SUB_ORG)
			&& !getJobRun().getParameters().getMandatoryBoolean(UNPROMOTED_CHANGES_ONLY)) {
			throw new TermServerScriptException("Substances and Organisms can only be checked in combination with 'Unpromoted Changes Only'");
		}
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
				checkCaseSignificanceOfHierarchy(targetHierarchy, hierarchyDescendants);
				LOGGER.info("Completed hierarchy: {}", targetHierarchy);
			}
		}
	}

	private void checkCaseSignificanceOfHierarchy(Concept targetHierarchy, List<Concept> hierarchyDescendants) throws TermServerScriptException {
		for (Concept c : hierarchyDescendants) {
			if (inScopeForCsChecking(c)) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (!d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
						checkCaseSignificance(targetHierarchy, c, d);
					} else if (!d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
						report(c, d, d.getEffectiveTime(), "Y", "CS", "Text Definitions must be CS");
						countIssue(c);
					}
				}
			}
		}
	}

	/*
	*@return - true if we've reported an issue on this concept
	 */
	private boolean checkCaseSignificance(Concept targetHierarchy, Concept c, Description d) throws TermServerScriptException {
		if (!inScopeForCsChecking(d)) {
			return false;
		}

		String term = d.getTerm();
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

		if (targetHierarchy.equals(PHARM_BIO_PRODUCT) && reportedForDrugUnitInfraction(c, d, caseSig, preferred)) {
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
				report(c, d, d.getEffectiveTime(),  preferred, caseSig, "Terms starting with numbers cannot be CS");
				countIssue(c);
				return true;
			}
			//Does this term contain a capital letter after the first letter?
			if (caseSig.equals(ci) && d.getTerm().length() > 1) {
				String chopped = d.getTerm().substring(1);
				if (!chopped.equals(chopped.toLowerCase())) {
					report(c, d, d.getEffectiveTime(),  preferred, caseSig, "Terms featuring a capital letter after the first letter cannot be ci");
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
			report(c, d, d.getEffectiveTime(),  preferred, caseSig, "Terms starting with acronyms must be CS");
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
			report(c, d, d.getEffectiveTime(),  preferred, caseSig, "Terms starting with lower case letter must be CS");
			countIssue(c);
			return true;
		}
		return false;
	}


	private boolean reportedForDrugUnitInfraction(Concept c, Description d, String caseSig, String preferred) throws TermServerScriptException {
		//Now if we're ci that's certainly wrong
		//BUT if it's CS then it might be that we start with a known case-sensitive word, so let's check that
		if (DrugUtils.containsKnownCaseSensitiveDrugUnit(d)
			&& (caseSig.equals(ci)
					|| (caseSig.equals(CS) && !csUtils.startsWithKnownCaseSensitiveTerm(c, d.getTerm())))) {
				report(c, d, d.getEffectiveTime(),  preferred, caseSig, "Terms containing drug units should be cI");
				countIssue(c);
				return true;
		}
		return false;
	}


	private boolean reportedForTextDefinitionRuleOrAccepted(Concept c, Description d, String caseSig, String preferred) throws TermServerScriptException {
		//Text Definitions must be CS
		if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
			if (!caseSig.equals(CS)) {
				report(c, d, d.getEffectiveTime(),  preferred, caseSig, "Text Definitions must be CS");
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
			report(c, d, d.getEffectiveTime(),  preferred, caseSig, "Case insensitive term has a capital after first letter");
			countIssue(c);
			return true;
		}

		//Or if one of our sources of truth?
		String firstWord = d.getTerm().split(" ")[0];
		if (csUtils.startsWithKnownCsWordInContext(c, firstWord, null)) {
			report(c, d, d.getEffectiveTime(),  preferred, caseSig, "Case insensitive term should be CS as per " +  csUtils.explainCsWordInContext(c, firstWord));
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
			|| (strictness.equals(Strictness.LAX) && (isInHierarchyKnownToBeLikelyCS(c) || isProbableEponym(d) || checkKnownSpecialCases(c,d)))) {
				//Probably OK
			} else {
				//Now it might be that our term does not feature a capital, but it has a word that's all LOWER case, which is nevertheless case-sensitive
				//like p-tert-butylphenol
				if (!csUtils.containsKnownLowerCaseWord(term)
						&& !isDrugWithCaseSensitiveUnit(c, d)
						&& !(USE_ORGANISM_TAXONOMY_WORD_TRIGGERS_CS_RULE && organismExistsWithTaxonomicTerm(c, term))) {
					report(c, d, d.getEffectiveTime(), preferred, caseSig, "Case sensitive term does not have capital after first letter");
					countIssue(c);
					return true;
				}
			}
		}
		return false;
	}

	private boolean isDrugWithCaseSensitiveUnit(Concept c, Description d) {
		String semTag = SnomedUtilsBase.deconstructFSN(c.getFSNDescription().getTerm())[1];
		return semTag.equals("(clinical drug)") && DrugUtils.containsKnownCaseSensitiveDrugUnit(d);
	}

	private boolean organismExistsWithTaxonomicTerm(Concept c, String term) throws TermServerScriptException {
		if (c.getFSNDescription().getTerm().contains("organism")) {
			//Does the first word of this term exist as the 2nd term in some taxonomic way with a capital?
			String firstWord = term.split(" ")[0];
			Collection<Concept> ancestors = c.getAncestors(RF2Constants.NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, true);
			if (wordContainedInTaxonomicContext(firstWord, ancestors)) {
				return true;
			}
		}
		return false;
	}

	private boolean wordContainedInTaxonomicContext(String word, Collection<Concept> ancestors) {
		for (Concept ancestor : ancestors) {
			String fsnTerm = ancestor.getFSNDescription().getTerm();
			String firstWordInFSN = fsnTerm.split(" ")[0];
			if (csUtils.isTaxonomicWord(firstWordInFSN)
				&& fsnTerm.contains(word)) {
				return true;
			}
		}
		return false;
	}

	private boolean checkKnownSpecialCases(Concept c, Description d) {
		//diagnostic allergen extract as a CS usually uses a specific organism, but is then modelled using the substance,
		//so we cannot check for a source of truth using the correct context.  Calling this function in 'LAX' strictness,
		//we'll assume it's OK
		return c.getFSNDescription().getTerm().contains("diagnostic allergen extract")
				|| d.getTerm().contains("Universal designation");
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
		whiteList.addAll(List.of("123888000","122413000","1367729005","24294007","1231642002","1010666007","123932008","389214003","1077861000119105","1220594007"));
		whiteList.addAll(List.of("310286009","863902006","15719361000119100","43166006","1269399005","16908321000119106","1144919009","1365861003","403876006","24559001"));
		whiteList.addAll(List.of("772297009","123901007","55166000","767165007","240865005","1366600009","1172898008","15628731000119103","1259106002","123939004"));
		whiteList.addAll(List.of("77121009","122324002","232436000","44001008","717953009","871717007","123874004","399050001","781550002","1295529002"));
		whiteList.addAll(List.of("415354003","408539000","1259219009","1204493003","122414006","12246008","238069004","1363284002","810003","81854007"));
		whiteList.addAll(List.of("389239007","123906002","1363365002","870448005","863899008","1186710001","15736921000119108","423185002","1209197008","783159001"));
		whiteList.addAll(List.of("1351326006","123907006","782690007","123931001","1156212003","736959331000119108","1363424003","788613004","239072003","1343302008"));
		whiteList.addAll(List.of("187039009","52219002","871919004","398958000","1351784005","397568004","82913001","35494007","238926009","1367743000"));
		whiteList.addAll(List.of("1287729003","1031000221108","787964002","870537001","722215002","1255983002","123900008","123889008","601000221108","413916006"));
		whiteList.addAll(List.of("424575004","123873005","1363382000","718221007","70694009","118107006","390011000221106","1255800001","1367745007","123915009"));
		whiteList.addAll(List.of("123930000","782940006","412589009","863901004","1142206004","1187132007","1208849004","44290007","447111007","836401009"));
		whiteList.addAll(List.of("297602009","123890004","238424004","6412007","1367797000","108723008","2001000221108","896791000","123909009","1331912004"));
		whiteList.addAll(List.of("707742001","711158005","354034000","1268554008","771748003","95738005","1222676004","84469002","308271000087104","253855721000119107"));
		whiteList.addAll(List.of("865951006","419029001","398381009","515623061000119105","1287876003","37029000","1362039001","72922008","860780009","53901006"));
		whiteList.addAll(List.of("122398007","280664008","860767006","236877007","1367841008","394928002","787484007","715381005","239030004","117966002"));
		whiteList.addAll(List.of("340671000119102","369625009","412270008","1351782009","721800006","871719005","109431006","1229872004","350061008","1367696000"));
		whiteList.addAll(List.of("315051000119100","42335002","54605008","711190000","1156913001","15663521000119106","1003756003","403873003","122312009","1254781000"));
		whiteList.addAll(List.of("783157004","1285519007","1348308009","1363381007","123908001","864990181000119100","1367948003","420338000","1156760001","51311000087100"));
		whiteList.addAll(List.of("785389002","123918006","1363425002","1156478007","763387005","786089001","423937004","111785002","1344944003","17582006"));
		whiteList.addAll(List.of("238836000","784373007","1363383005","1382214006","95333004","425226008","85959007","1229895008","1237346001","73736004"));
		whiteList.addAll(List.of("190687004","1332077001","123943000","411275001","236979009","1260412008","1304277005","782721009","1217041008","346327000"));
		whiteList.addAll(List.of("122098004","713774003","403880001","1367754005","85278001","1335905003","836397001","121999007","1208747005","737081007"));
		whiteList.addAll(List.of("1285518004","123922001","876850004","337661000119105","82319005","871587002","1263394008","31272009","123886001","871723002"));
		whiteList.addAll(List.of("863898000","1255799000","1363411003","122376003","237770005","123887005","1148488008","1335861005","13464007","123944006"));
		whiteList.addAll(List.of("123938007","234969005","1179075000","1259218001","1367704004","123916005","303401008","774180009","314961000119103","123923006"));
		whiteList.addAll(List.of("121899006","1268838000","419395007","1156914007","66045001","702625009","403875005","397522002","1156211005","23127007"));
		whiteList.addAll(List.of("253828000","1010664005","73177007","123937002","921000221108","5881000124102","1149131009","1179456002","768871001","1217654000"));
		whiteList.addAll(List.of("104466005","1367733003","725046003","21019005","1255964009","1306859008","15736961000119103","1367725004","1363427005","403879004"));
		whiteList.addAll(List.of("15628691000119105","233167002","783737007","123885002","314941000119102","235597001","424399000","1332344005","871767000","1234919008"));
		whiteList.addAll(List.of("1363426001","123912007","1197714001","1179093004","123894008","193410003","779566008","1268846004","160563000","425106001"));
		whiteList.addAll(List.of("343431000119105","1363264001","123911000","1363412005","429211003","722893002","123904004","335061000119105","1367718006","865899005"));
		whiteList.addAll(List.of("865988009","702447002","123919003","1351788008","1303973005","22150007","1231437004","1367789007","1254809008","1287347006"));
		whiteList.addAll(List.of("866121005","254803001","1259068001","1284851009","123920009","76999009","787407003","310287000","122370009","133932002"));
		whiteList.addAll(List.of("1255278004","223356001","1342414005","123893002","1367717001","784391002","1287338003","123895009","787963008","109436001"));
		whiteList.addAll(List.of("699076007","1362038009","297836000","12313004","1303772009","789160000","783097004","31435000","1197707003","1367720009"));
		whiteList.addAll(List.of("1367698004","411422003","123884003","782670003","62999006","123903005","860785004","122064008","1363380008","784008009"));
		whiteList.addAll(List.of("1336200007","277537008","1351787003","182405008","1259939000","123917001","1367740002","776350009","1332076005","223550008"));
		whiteList.addAll(List.of("62309008","19044004","1254792004","117835009","196609006","1363272004","80702002","1306860003","65880007","123899003"));
		whiteList.addAll(List.of("42312000","312381000119103","15628651000119100","1367699007","186138007","251137005","1144914004","768869001","263241008","123880007"));
		whiteList.addAll(List.of("1208934006","1287726005","788417006","315106001","123942005","123902000","253017000","1156385007","123936006","1231140009"));
		whiteList.addAll(List.of("1177081002","1148740009","715380006","16908281000119101","388667004","836385002","403874009","720963003","123921008","865995000"));
		whiteList.addAll(List.of("122371008","420071006","389237009","1255801002","680291000119102","65720003","1231544006","1187120008","403878007","234362006"));
		whiteList.addAll(List.of("205480005","123913002","1197705006","43490004","123941003","253781004","123933003","1251395000","777919007","1163058004"));
		whiteList.addAll(List.of("370454004","1259896006","428506007","836386001","123934009","71462001","123929005","123910004","123928002","785685004"));
		whiteList.addAll(List.of("774213008","248680001","121980003","1363428000","785684000","403877002","412269007","239069005","1061000221102","1251367000"));
		whiteList.addAll(List.of("121979001","123924000","123925004","1367771006","866006002","420921006","122401005","724332002","1119301001","254124008"));
		whiteList.addAll(List.of("1197732001","62294009","332051000119100","1287387001","1367763007","1162893000","1010610007","767634004","1345047003","1371799006"));
		whiteList.addAll(List.of("123914008","50351000087108","783553008","123940002","1351785006","123881006","10135005","1197733006","123896005","840551008"));
		whiteList.addAll(List.of("1251449006","1010668008","294280001","6492006","1363379005","1209198003","688271000119100","1197589000","105941000119105","1156209001"));
		whiteList.addAll(List.of("236981006","1259108001","156246008","1264005000","445104009","123876002","1371798003","257751006","56044004","422653006"));
		whiteList.addAll(List.of("67817003","1204478002","717761005","1204486002","722067005","230437002","123877006","1228875006","123897001","423331005"));
		whiteList.addAll(List.of("123926003","1264278006","1222710008","783096008","277807007","123898006","836495005","1345135006","192659004","866005003"));
		whiteList.addAll(List.of("911000221103","1332075009","1351786007","1388297006","315104003","123927007","1149099005","89718004","1363423009","1345114009"));
		whiteList.addAll(List.of("37709009","21986005","734152003","1077851000119108","104320001","1367791004","278900006","785809005","426423008","123882004"));
		whiteList.addAll(List.of("398049005","1351335004","1991000221106","206138281000119100","1363271006","860782001","1208615009","15663481000119106","1367792006","122318008"));
		whiteList.addAll(List.of("836403007","1363422004","419514006","1388813003","1332074008","424877001","123905003","783166000","1142046003","312351000119105"));
		whiteList.addAll(List.of("7220001", "399114005"));
	}

}
