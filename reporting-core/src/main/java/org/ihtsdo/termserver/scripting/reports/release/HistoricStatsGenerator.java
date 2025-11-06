package org.ihtsdo.termserver.scripting.reports.release;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TransitiveClosure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Generates a file of data based on a release so that we can do investigations 
 * between one release and the next using: TBC
 * We want to know a selection of information
 * 1.  Active / Inactive for all concepts
 * 2.  Is the concept an Intermediate Primitive?
 * 3.  Definition Status
 * 4.  Does the concept have SD descendants (inferred)
 * 5.  Does the concept have SD ancestors
 * 6.  For QI, does the concept have attributes?
 * See HistoricStatsAnalyzer for analysis.
 * NB Used by Summary Component Stats as well as HistoricStatsAnalyzer
 * */
public class HistoricStatsGenerator extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(HistoricStatsGenerator.class);

	public static final String FAILED_TO_CREATE = "Failed to create ";

	private static final int MAX_HIERARCHY_DEPTH = 150;
	
	private boolean splitOutDisease = false;  //If you change this to true, don't check it in! See ISRS-1392.
	
	private static final String DATA_DIR = "historic-data/";
	private static final int ACTIVE = 1;
	private static final int INACTIVE = 0;
	private Map<String, String> semTagHierarchyMap;

	public HistoricStatsGenerator(TermServerScript ts) {
		project = ts.getProject();
	}

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(HistoricStatsGenerator.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		suppressOutput = true;
		super.init(run);
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Historic Stats Generator")
				.withDescription("Generates a selection of information about all concepts for a given release")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		FileWriter fw = null;
		try {
			//Create the historic-data directory if required
			ensureHistoricDataDirExistsOrThrow();
			
			File f = new File(DATA_DIR + project.getKey() + ".tsv");
			//Since the package is published, if this file already exists, we can just reuse it
			if (!f.exists()) {
				fw = initialiseHistoricDataFile(f);
				LOGGER.info("Outputting Data to {}", f.getAbsolutePath());
				TransitiveClosure tc = gl.generateTransitiveClosure();

				LOGGER.info("Creating map of semantic tag hierarchies");
				populateSemTagHierarchyMap(tc);

				generateHistoricData(fw, tc);
			} else {
				LOGGER.info("Reusing existing dataFile: {}", f.getAbsolutePath());
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		} finally {
			try {
				if (fw != null) {
					fw.close();
				}
			} catch (IOException e) {
				LOGGER.error("Exception encountered during tidy-up. Ignoring.",e);
			}
		}
	}

	private void ensureHistoricDataDirExistsOrThrow() throws TermServerScriptException {
		File dataDirFile = new File(DATA_DIR);
		if (!dataDirFile.exists()) {
			LOGGER.info("Creating directory to store historic data analysis: {}", DATA_DIR);
			boolean success = dataDirFile.mkdir();
			if (!success) {
				throw new TermServerScriptException(FAILED_TO_CREATE + dataDirFile.getAbsolutePath());
			}
		}
	}

	private FileWriter initialiseHistoricDataFile(File f) throws TermServerScriptException {
		try {
			LOGGER.info("Creating dataFile: {}", f.getAbsolutePath());
			Files.createDirectories(f.toPath().getParent());
			if (!f.createNewFile()) {
				throw new TermServerScriptException(FAILED_TO_CREATE + f.getAbsolutePath());
			}
			LOGGER.debug("Outputting Data to {}", f.getAbsolutePath());
			return new FileWriter(f);
		} catch (IOException e) {
			throw new TermServerScriptException(FAILED_TO_CREATE + f.getAbsolutePath(), e);
		}
	}

	private void generateHistoricData(FileWriter fw, TransitiveClosure tc) throws TermServerScriptException, IOException {
		LOGGER.debug("Determining all IPs");
		Set<Concept> intermediatePrimitives = identifyIntermediatePrimitives(gl.getAllConcepts(), CharacteristicType.INFERRED_RELATIONSHIP);

		for (Concept c : gl.getAllConcepts()) {
			String active = c.isActiveSafely() ? "Y" : "N";
			String defStatus = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
			String hierarchy = getHierarchy(tc, c, new LinkedList<>());
			String intermediatePrimitiveIndicator = intermediatePrimitives.contains(c) ? "Y" : "N";
			String sdDescendant = hasSdDescendant(tc, c);
			String sdAncestor = hasSdAncestor(tc, c);
			String[] relIds = getRelIds(c);
			String[] descIds = getDescIds(c);
			String[] axiomIds = getAxiomIds(c);
			String[] langRefSetIds = getLangRefsetIds(c);
			String[] inactivationIds = getInactivationIds(c);
			String[] descInactivationIds = getDescInactivationIds(c);
			String[] histAssocIds = getHistAssocIds(c);
			String[] descHistAssocIds = getDescHistAssocIds(c);
			String hasAttributes = SnomedUtils.countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) > 0 ? "Y" : "N";
			String histAssocTargets = getHistAssocTargets(c);
			ouput(fw, c.getConceptId(), c.getFsn(), active, defStatus, hierarchy, intermediatePrimitiveIndicator,
					sdDescendant, sdAncestor,
					relIds[ACTIVE], relIds[INACTIVE], descIds[ACTIVE], descIds[INACTIVE],
					axiomIds[ACTIVE], axiomIds[INACTIVE], langRefSetIds[ACTIVE], langRefSetIds[INACTIVE],
					inactivationIds[ACTIVE], inactivationIds[INACTIVE], histAssocIds[ACTIVE], histAssocIds[INACTIVE],
					c.getModuleId(), hasAttributes, descHistAssocIds[ACTIVE], descHistAssocIds[INACTIVE],
					descInactivationIds[ACTIVE], descInactivationIds[INACTIVE], histAssocTargets);
		}
	}

	private String getHistAssocTargets(Concept c) {
		return c.getAssociationEntries(ActiveState.ACTIVE, true)
				.stream()
				.map(AssociationEntry::getTargetComponentId)
				.collect(Collectors.joining(","));
	}

	private void populateSemTagHierarchyMap(TransitiveClosure tc) throws TermServerScriptException {
		semTagHierarchyMap = new HashMap<>();
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActiveSafely()) {
				String[] parts = SnomedUtilsBase.deconstructFSN(c.getFsnSafely(), true);
				if (parts.length == 2 && 
						!StringUtils.isEmpty(parts[1]) && 
						!semTagHierarchyMap.containsKey(parts[1])) {
					String hierarchySCTID = getHierarchy(tc, c, new LinkedList<>());
					if (!StringUtils.isEmpty(hierarchySCTID)) {
						semTagHierarchyMap.put(parts[1], hierarchySCTID);
					}
				}	
			}
		}
	}

	private String[] getRelIds(Concept c) {
		String[] results = new String[2];
		
		results[ACTIVE] = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)
		.stream()
		.filter(this::inScope)
		.map(Relationship::getId)
		.collect(Collectors.joining(","));
	
		results[INACTIVE] = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.INACTIVE)
		.stream()
		.filter(this::inScope)
		.map(Relationship::getId)
		.collect(Collectors.joining(","));
		return results;
	}
	
	private String[] getDescIds(Concept c) {
		String[] results = new String[2];
		
		results[ACTIVE] = c.getDescriptions(ActiveState.ACTIVE)
		.stream()
		.filter(this::inScope)
		.map(Description::getId)
		.collect(Collectors.joining(","));
		
		results[INACTIVE] = c.getDescriptions(ActiveState.INACTIVE)
			.stream()
			.filter(this::inScope)
			.map(Description::getId)
			.collect(Collectors.joining(","));
		
		return results;
	}
	
	private String[] getAxiomIds(Concept c) {
		String[] results = new String[2];
		
		results[ACTIVE] = c.getAxiomEntries().stream()
		.filter(Component::isActive)
		.filter(this::inScope)
		.map(RefsetMember::getId)
		.collect(Collectors.joining(","));
		
		results[INACTIVE] = c.getAxiomEntries().stream()
		.filter(a -> !a.isActiveSafely())
		.filter(this::inScope)
		.map(RefsetMember::getId)
		.collect(Collectors.joining(","));
		
		return results;
	}
	
	private String[] getLangRefsetIds(Concept c) {
		String[] results = new String[2];
		
		List<String> langRefsetIds = new ArrayList<>();
		for (Description d : c.getDescriptions()) {
			//An international scope description might have langrefset entries in another module
			//Check the scope of the refset entry rather than the description.
			for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
				if (inScope(l)) {
					langRefsetIds.add(l.getId());
				}
			}
		}
		results[ACTIVE] = String.join(",", langRefsetIds);
		
		langRefsetIds.clear();
		for (Description d : c.getDescriptions()) {
			for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.INACTIVE)) {
				if (inScope(l)) {
					langRefsetIds.add(l.getId());
				}
			}
		}
		results[INACTIVE] = String.join(",", langRefsetIds);
		return results;
	}
	
	private String[] getDescHistAssocIds(Concept c) {
		String[] results = new String[2];
		
		List<String> histAssocIds = new ArrayList<>();
		for (Description d : c.getDescriptions()) {
			for (AssociationEntry a : d.getAssociationEntries(ActiveState.ACTIVE)) {
				if (inScope(a)) {
					histAssocIds.add(a.getId());
				}
			}
		}
		results[ACTIVE] = String.join(",", histAssocIds);
		
		histAssocIds.clear();
		for (Description d : c.getDescriptions()) {
			for (AssociationEntry a : d.getAssociationEntries(ActiveState.INACTIVE)) {
				if (inScope(a)) {
					histAssocIds.add(a.getId());
				}
			}
		}
		results[INACTIVE] = String.join(",", histAssocIds);
		return results;
	}
	
	private String[] getInactivationIds(Concept c) {
		String[] results = new String[2];
		
		results[ACTIVE] = c.getInactivationIndicatorEntries(ActiveState.ACTIVE)
		.stream()
		.filter(this::inScope)
		.map(RefsetMember::getId)
		.collect(Collectors.joining(","));

		results[INACTIVE] = c.getInactivationIndicatorEntries(ActiveState.INACTIVE)
		.stream()
		.filter(this::inScope)
		.map(RefsetMember::getId)
		.collect(Collectors.joining(","));
		
		return results;
	}
	
	private String[] getDescInactivationIds(Concept c) {
		String[] results = new String[2];
		
		results[ACTIVE] = getDescriptionInactivationIndicatorEntries(c, ActiveState.ACTIVE)
		.stream()
		.filter(this::inScope)
		.map(RefsetMember::getId)
		.collect(Collectors.joining(","));

		results[INACTIVE] = getDescriptionInactivationIndicatorEntries(c, ActiveState.INACTIVE)
		.stream()
		.filter(this::inScope)
		.map(RefsetMember::getId)
		.collect(Collectors.joining(","));
		
		return results;
	}

	private Collection<InactivationIndicatorEntry> getDescriptionInactivationIndicatorEntries(Concept c, ActiveState activeState) {
		List<InactivationIndicatorEntry> indicators = new ArrayList<>();
		for (Description d : c.getDescriptions()) {
			indicators.addAll(d.getInactivationIndicatorEntries(activeState));
		}
		return indicators;
	}

	private String[] getHistAssocIds(Concept c) {
		String[] results = new String[2];
		
		results[ACTIVE] = c.getAssociationEntries(ActiveState.ACTIVE)
		.stream()
		.filter(this::inScope)
		.map(RefsetMember::getId)
		.collect(Collectors.joining(","));
		
		results[INACTIVE] = c.getAssociationEntries(ActiveState.INACTIVE)
		.stream()
		.filter(this::inScope)
		.map(RefsetMember::getId)
		.collect(Collectors.joining(","));
		
		return results;
	}

	private String getHierarchy(TransitiveClosure tc, Concept c, Deque<Concept> stack) throws TermServerScriptException {

		if (c.equals(ROOT_CONCEPT)) {
			return ROOT_CONCEPT.getConceptId();
		}
		
		if (c.isActiveSafely() && c.getDepth() == 1) {
			return c.getConceptId();
		}

		if (!c.isActiveSafely() || c.getDepth() == NOT_SET) {
			stack.push(c);
			if (stack.size() > MAX_HIERARCHY_DEPTH) {
				reportStackOverflow(stack);
			} else {
				Deque<Concept> parents = getParentsByISARelationships(c);
				if (!parents.isEmpty()) {
					Concept parent = parents.pop();
					//Now if we've already looked at this parent, then that's not a safe to recurse on 
					while (stack.contains(parent)) {
						if (parents.isEmpty()) {
							reportStackOverflow(stack);
							parent = null;
						} else {
							parent = parents.pop();
						}
					}
					
					if (parent != null) {
						return getHierarchy(tc, parent, stack);
					}
				}
			}
			
			//Attempt to determine hierarchy from the semantic tag
			String[] parts = SnomedUtilsBase.deconstructFSN(c.getFsnSafely(), true);
			if (parts.length == 2 && 
					!StringUtils.isEmpty(parts[1]) && 
					semTagHierarchyMap.containsKey(parts[1])) {
				return semTagHierarchyMap.get(parts[1]);
			}
			return "";  //Hopefully the previous release will know
		}
		
		Set<Long> allAncestors = tc.getAncestors(c);

		//Are we going to separate out Diseases from Clinical Findings
		if (splitOutDisease && allAncestors.contains(64572001L)) {
			return DISEASE.getConceptId();
		}

		for (Long sctId : tc.getAncestors(c)) {
			Concept a = gl.getConcept(sctId);
			if (a.getDepth() == 1) {
				return a.getConceptId();
			}
		}
		throw new TermServerScriptException("Unable to determine hierarchy for " + c);
	}

	private void reportStackOverflow(Deque<Concept> stack) {
		String stackStr = stack.stream()
				.map(Concept::getId)
				.collect(Collectors.joining(", "));
		LOGGER.error("Recursive loop encountered in hierarchy of: {}", stackStr, (Exception)null);
	}

	private Deque<Concept> getParentsByISARelationships(Concept concept) throws TermServerScriptException {
		Deque<Concept> parents = new ArrayDeque<>();

		Set<Relationship> relationships = gl.getConcept(concept.getId()).getRelationships();

		// look for the ISA relationships
		for (Relationship relationship : relationships) {
			Concept relationshipType = relationship.getType();
			if (relationshipType.equals(IS_A)) {
				if (parents.isEmpty()) {
					parents.push(relationship.getTarget());
				} else {
					Concept parent = parents.peekFirst();
					Concept nextParent = relationship.getTarget();
					
					// Prefer active before considering the effective date
					if ((!parent.isActiveSafely() && nextParent.isActiveSafely())) {
						parents.remove(nextParent);
						parents.push(nextParent);
					} else if (parent.isActiveSafely() && nextParent.isActiveSafely() || !parent.isActiveSafely() && !nextParent.isActiveSafely()) {
						Integer comparison = SnomedUtils.compareEffectiveDate(parent.getEffectiveTime(),
								nextParent.getEffectiveTime());
						if (comparison != null && comparison == -1) {
							//Might see this parent more than one, take our best position
							parents.remove(nextParent);
							parents.push(nextParent);
						}
					} else {
						//Otherwise, add to the back of the queue
						parents.add(nextParent);
					}
				}
			}
		}
		return parents;
	}

	private void ouput(FileWriter fw, String... fields) throws IOException {
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (String field : fields) {
			if (!isFirst) {
				sb.append(TAB);
			} else {
				isFirst = false;
			}
			sb.append(field);
		}
		sb.append("\n");
		fw.write(sb.toString());
	}

	private String hasSdDescendant(TransitiveClosure tc, Concept c) throws TermServerScriptException {
		for (Long sctId : tc.getDescendants(c)) {
			Concept d = gl.getConcept(sctId);
			if (d.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return "Y";
			}
		}
		return "N";
	}

	private String hasSdAncestor(TransitiveClosure tc, Concept c) throws TermServerScriptException {
		for (Long sctId : tc.getAncestors(c)) {
			Concept a = gl.getConcept(sctId);
			if (a.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return "Y";
			}
		}
		return "N";
	}

	public void setModuleFilter(List<String> moduleFilter) {
		this.moduleFilter = moduleFilter;
	}

	@Override
	protected boolean inScope (Component c) {
		if (moduleFilter == null || moduleFilter.isEmpty()) {
			return true;
		}
		
		for (String module : moduleFilter) {
			if (c.getModuleId().equals(module)) {
				return true;
			}
		}
		return false;
	}
	
	public void splitOutDisease(boolean split) {
		splitOutDisease = split;
	}
}
