package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * Check for duplicate terms across all active concepts
 */
public class DuplicateTermsCrossHierarchy extends TermServerReport implements ReportClass {

	private static Set<String> sameHierarchyWhitelist;
	private Map<Concept, Concept> hierarchyMap = new HashMap<>();
	
	List<DescriptionType> typesOfInterest = List.of(DescriptionType.FSN, DescriptionType.SYNONYM);
	
	public static void main(String[] args) throws TermServerScriptException {
		TermServerScript.run(DuplicateTermsCrossHierarchy.class, args, new HashMap<>());
	}
	
	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"); //Release QA
		super.init(run);
		additionalReportColumns = "Term, Concepts, Descriptions, , ";
		
		//Are we in an extension?  Confirm expectedExtensionModules metadata is present if so, or refuse to run.
		//This will be used after we've formed the snapshot in TermServerScript.inScope(component c)
		if (project.getBranchPath().contains("SNOMEDCT-") && project.getMetadata().getExpectedExtensionModules() == null) {
			throw new TermServerScriptException("Extension does not have expectedExtensionModules metadata populated.  Cannot continue.");
		}
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Duplicate Terms Cross Hierarchy")
				.withParameters(new JobParameters())
				.withTag(INT).withTag(MS)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		//Create a map of all terms and check for one already known
		Map<String, Set<Description>> termMap = collectAllTerms();
		populateHierarchyMap();
		populateWhiteList();
		for (Map.Entry<String, Set<Description>> entry : termMap.entrySet()) {
			Set<Description> descriptions = entry.getValue();
			removeWhitelistedSameHiearchy(descriptions);
			Set<Concept> concepts = determineConcepts(descriptions);
			
			//Do we have anything left to report.  Must be at least 2 to be duplicate
			if (descriptions.size() > 1) {
				String conceptStr = concepts.stream().map(Object::toString).collect(Collectors.joining(",\n"));
				String descriptionStr = descriptions.stream().map(Object::toString).collect(Collectors.joining(",\n"));
				String firstDesc = descriptions.stream().filter(d -> !d.getType().equals(DescriptionType.FSN)).findFirst().get().getTerm();
				report(PRIMARY_REPORT, firstDesc, conceptStr, descriptionStr);
			}
		}
	}

	private Set<Concept> determineConcepts(Set<Description> descriptions) {
		return descriptions.stream()
				.map(d -> gl.getConceptSafely(d.getConceptId()))
				.collect(Collectors.toSet());
	}

	private void removeWhitelistedSameHiearchy(Set<Description> descriptions) {
		Set<Description> descriptionsToRemove = new HashSet<>();
		for (Description thisDescription : descriptions) {
			if (descriptionsToRemove.contains(thisDescription)) {
				continue;
			}
			Concept thisConcept = gl.getConceptSafely(thisDescription.getConceptId());
			for (Description thatDescription : descriptions) {
				if (thisDescription.equals(thatDescription) 
						|| descriptionsToRemove.contains(thatDescription)) {
					continue;
				}
				if (!sameHierarchyWhitelist.contains(thisDescription.getId())
						&& !sameHierarchyWhitelist.contains(thatDescription.getId())) {
					continue;
				}
				Concept thatConcept = gl.getConceptSafely(thatDescription.getConceptId());
				if (isSameHierarchy(thisConcept, thatConcept)) {
					descriptionsToRemove.add(thatDescription);
				}
			}
		}
		descriptions.removeAll(descriptionsToRemove);
	}

	private boolean isSameHierarchy(Concept thisConcept, Concept thatConcept) {
		return hierarchyMap.get(thisConcept).equals(hierarchyMap.get(thatConcept));
	}

	private void populateHierarchyMap() {
		hierarchyMap =  gl.getAllConcepts().stream()
				.filter(c -> c.isActiveSafely())
				.collect(Collectors.toMap(
							Function.identity(),
							c -> SnomedUtils.getHierarchySafely(gl, c),
							(a, b) -> a, 
							HashMap::new));
	}

	private static void populateWhiteList() {
		sameHierarchyWhitelist = new HashSet<>(Arrays.asList(
				"3498527018","3498503015","3498593012","3498504014","1479437014","3498539014","3498561016","2617770016","900000000001183011","2578855013","3535041013","2618109010","2578774010","1479326014","3012753012","1479457013","3445947019","2695026010",
				"2577177012","5073443018","1479457013","3534535018","3498553014","380431015","3521550018","2696017015","2695026010","3498510014","339260018","2616651013","2618113015","3031387013","2693994018","2616633012","2764782019","388805010","2578772014",
				"2764782019","3445947019","2773667013","3498551011","900000000001185016","3534543011","2774373012","5072959015","3534539012","3534539012","3534539012","2695780014","3534604015","2552410019","3534533013","3498590010","3498590010",
				"3534604015","3498538018","3498538018","2618125017","3534544017","3498560015","3534571017","2694273017","2694273017","1479310013","2535128013","2535127015","3534571017","2621433017","1777785010","450351012","2621433017","3534558015","2578773016",
				"3650039017","3541503017","2674989018","3498514017","2616623016","2616623016","1479349011","1479349011","2618121014","3633867018","3498577015","450353010","3498556018","3498505010","3498572014","3769685013","3498556018","3498577015","3498584011",
				"3498584011","3498533010","3498547014","3498519010","3534378010","3031166010","3498541010","3498541010","2616622014","2616622014","3031166010","3534540014","3498582010","3498582010","900000000001031019",
				"3498545018","3498541010","2616624010","3498574010","3541426010","2472324010","3521546013","3498574010","3498545018","3031315013","3031315013","388087015","2616625011","2536646010","2537473014","2692355017","3758654012","3498399010","2578771019",
				"1777790013","2534626015","3647391016","2675418015","2692413013","2692413013","3498399010","3498587016","2577173011","3498598015","2535322018","1479311012","2674995017","1479311012","3498397012","3498397012","2695405017","2695405017",
				"3621079016","3621079016","2617933019","2618111018","3498518019","2695405017","3873861010","3534537014","3873861010","2551613014","3644121010","392081010","2156115013","2577161011","2577161011","392193015","3510669010","3534563016","2694184016",
				"2694184016","1479317011","3498535015","1479317011","2618112013","3534563016","2618112013"));
	}

	private Map<String, Set<Description>> collectAllTerms() {
		Map<String, Set<Description>> termMap = new TreeMap<>();
		for (Concept c : gl.getAllConcepts()) {
			if (!c.isActiveSafely()) {
				continue;
			}
			for (Description d : c.getDescriptions(ActiveState.ACTIVE, typesOfInterest)) {
				String termLowerCase = d.getTerm().toLowerCase();
				if (d.getType().equals(DescriptionType.FSN)) {
					termLowerCase = SnomedUtilsBase.deconstructFSN(termLowerCase)[0];
				}
				Set<Description> descriptions = termMap.computeIfAbsent(termLowerCase, t -> new HashSet<>());
				recordBestDescription(descriptions, d);
			}
		}
		return termMap;
	}

	private void recordBestDescription(Set<Description> descriptions, Description d) {
		//If we already have a matching synonym for this concept, don't also add the FSN
		//If we already have the FSN, replace it with this synonym
		String thisConceptId = d.getConceptId();
		for (Description existing : new ArrayList<>(descriptions)) {
			if (existing.getConceptId().equals(thisConceptId)) {
				if (existing.getType().equals(DescriptionType.FSN)) {
					descriptions.remove(existing);
					break;
				} else if (d.getType().equals(DescriptionType.FSN)) {
					return;
				}
			}
		}
		descriptions.add(d);
	}

}
