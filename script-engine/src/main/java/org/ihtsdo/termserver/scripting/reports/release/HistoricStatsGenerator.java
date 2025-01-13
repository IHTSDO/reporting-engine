package org.ihtsdo.termserver.scripting.reports.release;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TransitiveClosure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoricStatsGenerator extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(HistoricStatsGenerator.class);

	private static int MAX_HIERARCHY_DEPTH = 150;
	
	private boolean splitOutDisease = false;  //If you change this to true, don't check it in! See ISRS-1392.
	
	private static final String dataDir = "historic-data/";
	private static int ACTIVE = 1;
	private static int INACTIVE = 0;

	private static int OUT_OF_SCOPE = 2;
	private Map<String, String> semTagHierarchyMap;
	private List<String> moduleFilter;
	
	public HistoricStatsGenerator() {
	}
	
	public HistoricStatsGenerator(TermServerScript ts) {
		project = ts.getProject();
	}

	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(HistoricStatsGenerator.class, args, params);
	}
	
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
	
	public void runJob() throws TermServerScriptException {
		FileWriter fw = null;
		try {
			TransitiveClosure tc = gl.generateTransativeClosure();
			
			LOGGER.info("Creating map of semantic tag hierarchies");
			populateSemTagHierarchyMap(tc);
			
			//Create the historic-data directory if required
			File dataDirFile = new File(dataDir);
			if (!dataDirFile.exists()) {
				LOGGER.info("Creating directory to store historic data analysis: " + dataDir);
				boolean success = dataDirFile.mkdir();
				if (!success) {
					throw new TermServerScriptException("Failed to create " + dataDirFile.getAbsolutePath());
				}
			}
			
			File f = new File(dataDir + project.getKey() + ".tsv");
			LOGGER.info("Creating dataFile: " + f.getAbsolutePath());
			Files.createDirectories(f.toPath().getParent());
			f.createNewFile();
			fw = new FileWriter(f);
			
			LOGGER.debug ("Determining all IPs");
			Set<Concept> IPs = identifyIntermediatePrimitives(gl.getAllConcepts(), CharacteristicType.INFERRED_RELATIONSHIP);
		
			LOGGER.debug ("Outputting Data to " + f.getAbsolutePath());
			for (Concept c : gl.getAllConcepts()) {
				/*if (c.getId().equals("16711071000119107")) {
					LOGGER.debug("here");
				}*/
				String active = c.isActive() ? "Y" : "N";
				String defStatus = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
				String hierarchy = getHierarchy(tc, c, new Stack<Concept>());
				String IP = IPs.contains(c) ? "Y" : "N";
				String hasAttributes = SnomedUtils.countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) > 0 ? "Y" : "N";
				String sdDescendant = hasSdDescendant(tc, c);
				String sdAncestor = hasSdAncestor(tc, c);
				String histAssocTargets = getHistAssocTargets(c);
				String[] relIds = getRelIds(c);
				String[] descIds = getDescIds(c);
				String[] axiomIds = getAxiomIds(c);
				String[] langRefSetIds = getLangRefsetIds(c);
				String[] inactivationIds = getInactivationIds(c);
				String[] descInactivationIds = getDescInactivationIds(c);
				String[] histAssocIds = getHistAssocIds(c);
				String[] descHistAssocIds = getDescHistAssocIds(c);
				ouput(fw, c.getConceptId(), c.getFsn(), c.getModuleId(), active, defStatus, hierarchy, IP, hasAttributes, sdDescendant, sdAncestor,	histAssocTargets,
						relIds[ACTIVE], relIds[INACTIVE], relIds[OUT_OF_SCOPE],
						descIds[ACTIVE], descIds[INACTIVE], descIds[OUT_OF_SCOPE],
						axiomIds[ACTIVE], axiomIds[INACTIVE], axiomIds[OUT_OF_SCOPE],
						langRefSetIds[ACTIVE], langRefSetIds[INACTIVE], langRefSetIds[OUT_OF_SCOPE],
						inactivationIds[ACTIVE], inactivationIds[INACTIVE], inactivationIds[OUT_OF_SCOPE],
						histAssocIds[ACTIVE], histAssocIds[INACTIVE], histAssocIds[OUT_OF_SCOPE],
						descHistAssocIds[ACTIVE], descHistAssocIds[INACTIVE], descHistAssocIds[OUT_OF_SCOPE],
						descInactivationIds[ACTIVE], descInactivationIds[INACTIVE], descInactivationIds[OUT_OF_SCOPE]);
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		} finally {
			try {
				if (fw != null) {
					fw.close();
				}
			} catch (IOException e) {
				LOGGER.error("Exception encountered",e);
			}
		}
	}

	private String getHistAssocTargets(Concept c) {
		return c.getAssociationEntries(ActiveState.ACTIVE, true)
				.stream()
				.map(a -> a.getTargetComponentId())
				.collect(Collectors.joining(","));
	}

	private void populateSemTagHierarchyMap(TransitiveClosure tc) throws TermServerScriptException {
		semTagHierarchyMap = new HashMap<>();
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				String[] parts = SnomedUtils.deconstructFSN(c.getFsnSafely(), true);
				if (parts.length == 2 && 
						!StringUtils.isEmpty(parts[1]) && 
						!semTagHierarchyMap.containsKey(parts[1])) {
					String hierarchySCTID = getHierarchy(tc, c, new Stack<Concept>());
					if (!StringUtils.isEmpty(hierarchySCTID)) {
						semTagHierarchyMap.put(parts[1], hierarchySCTID);
					}
				}	
			}
		}
	}

	private String[] getRelIds(Concept c) {
		String[] results = new String[3];
		
		results[ACTIVE] = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)
		.stream()
		.filter(r -> inScope(r))
		.map(r -> r.getId())
		.collect(Collectors.joining(","));
	
		results[INACTIVE] = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.INACTIVE)
		.stream()
		.filter(r -> inScope(r))
		.map(r -> r.getId())
		.collect(Collectors.joining(","));

		results[OUT_OF_SCOPE] = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)
				.stream()
				.filter(r -> !inScope(r))
				.map(r -> r.getId())
				.collect(Collectors.joining(","));

		return results;
	}
	
	private String[] getDescIds(Concept c) {
		String[] results = new String[3];
		
		results[ACTIVE] = c.getDescriptions(ActiveState.ACTIVE)
		.stream()
		.filter(d -> inScope(d))
		.map(d -> d.getId())
		.collect(Collectors.joining(","));
		
		results[INACTIVE] = c.getDescriptions(ActiveState.INACTIVE)
			.stream()
			.filter(d -> inScope(d))
			.map(d -> d.getId())
			.collect(Collectors.joining(","));

		results[OUT_OF_SCOPE] = c.getDescriptions()
				.stream()
				.filter(d -> !inScope(d))
				.map(d -> d.getId())
				.collect(Collectors.joining(","));
		
		return results;
	}
	
	private String[] getAxiomIds(Concept c) {
		String[] results = new String[3];
		
		results[ACTIVE] = c.getAxiomEntries().stream()
		.filter(a -> a.isActive())
		.filter(a -> inScope(a))
		.map(a -> a.getId())
		.collect(Collectors.joining(","));
		
		results[INACTIVE] = c.getAxiomEntries().stream()
		.filter(a -> a.isActive() == false)
		.filter(a -> inScope(a))
		.map(a -> a.getId())
		.collect(Collectors.joining(","));

		results[OUT_OF_SCOPE] = c.getAxiomEntries().stream()
				.filter(a -> !inScope(a))
				.map(a -> a.getId())
				.collect(Collectors.joining(","));
		
		return results;
	}
	
	private String[] getLangRefsetIds(Concept c) {
		String[] results = new String[3];

		results[ACTIVE] = getLangRefsetEntries(c, ActiveState.ACTIVE)
				.stream()
				.filter(l -> inScope(l))
				.map(l -> l.getId())
				.collect(Collectors.joining(","));

		results[INACTIVE] = getLangRefsetEntries(c, ActiveState.INACTIVE)
				.stream()
				.filter(l -> inScope(l))
				.map(l -> l.getId())
				.collect(Collectors.joining(","));

		results[OUT_OF_SCOPE] = getLangRefsetEntries(c, ActiveState.BOTH)
				.stream()
				.filter(l -> !inScope(l))
				.map(l -> l.getId())
				.collect(Collectors.joining(","));

		return results;
	}
	
	private String[] getDescHistAssocIds(Concept c) {
		String[] results = new String[3];

		results[ACTIVE] = getAssociationEntries(c, ActiveState.ACTIVE)
				.stream()
				.filter(a -> inScope(a))
				.map(a -> a.getId())
				.collect(Collectors.joining(","));

		results[INACTIVE] = getAssociationEntries(c, ActiveState.INACTIVE)
				.stream()
				.filter(a -> inScope(a))
				.map(a -> a.getId())
				.collect(Collectors.joining(","));

		results[OUT_OF_SCOPE] = getAssociationEntries(c, ActiveState.BOTH)
				.stream()
				.filter(a -> !inScope(a))
				.map(a -> a.getId())
				.collect(Collectors.joining(","));

		return results;
	}
	
	private String[] getInactivationIds(Concept c) {
		String[] results = new String[3];
		
		results[ACTIVE] = c.getInactivationIndicatorEntries(ActiveState.ACTIVE)
		.stream()
		.filter(i -> inScope(i))
		.map(i -> i.getId())
		.collect(Collectors.joining(","));

		results[INACTIVE] = c.getInactivationIndicatorEntries(ActiveState.INACTIVE)
		.stream()
		.filter(i -> inScope(i))
		.map(i -> i.getId())
		.collect(Collectors.joining(","));

		results[OUT_OF_SCOPE] = c.getInactivationIndicatorEntries()
				.stream()
				.filter(i -> !inScope(i))
				.map(i -> i.getId())
				.collect(Collectors.joining(","));
		
		return results;
	}
	
	private String[] getDescInactivationIds(Concept c) {
		String[] results = new String[3];
		
		results[ACTIVE] = getDescriptionInactivationIndicatorEntries(c, ActiveState.ACTIVE)
		.stream()
		.filter(i -> inScope(i))
		.map(i -> i.getId())
		.collect(Collectors.joining(","));

		results[INACTIVE] = getDescriptionInactivationIndicatorEntries(c, ActiveState.INACTIVE)
		.stream()
		.filter(i -> inScope(i))
		.map(i -> i.getId())
		.collect(Collectors.joining(","));

		results[OUT_OF_SCOPE] = getDescriptionInactivationIndicatorEntries(c, ActiveState.BOTH)
				.stream()
				.filter(i -> !inScope(i))
				.map(i -> i.getId())
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

	private Collection<AssociationEntry> getAssociationEntries(Concept c, ActiveState activeState) {
		List<AssociationEntry> entries = new ArrayList<>();
		for (Description d : c.getDescriptions()) {
			entries.addAll(d.getAssociationEntries(activeState));
		}
		return entries;
	}

	private Collection<LangRefsetEntry> getLangRefsetEntries(Concept c, ActiveState activeState) {
		List<LangRefsetEntry> entries = new ArrayList<>();
		for (Description d : c.getDescriptions()) {
			entries.addAll(d.getLangRefsetEntries(activeState));
		}
		return entries;
	}

	private String[] getHistAssocIds(Concept c) {
		String[] results = new String[3];
		
		results[ACTIVE] = c.getAssociationEntries(ActiveState.ACTIVE)
		.stream()
		.filter(h -> inScope(h))
		.map(h -> h.getId())
		.collect(Collectors.joining(","));
		
		results[INACTIVE] = c.getAssociationEntries(ActiveState.INACTIVE)
		.stream()
		.filter(h -> inScope(h))
		.map(h -> h.getId())
		.collect(Collectors.joining(","));

		results[OUT_OF_SCOPE] = c.getAssociationEntries()
				.stream()
				.filter(h -> !inScope(h))
				.map(h -> h.getId())
				.collect(Collectors.joining(","));
		
		return results;
	}

	private String getHierarchy(TransitiveClosure tc, Concept c, Stack<Concept> stack) throws TermServerScriptException {

		if (c.equals(ROOT_CONCEPT)) {
			return ROOT_CONCEPT.getConceptId();
		}
		
		if (c.isActive() && c.getDepth() == 1) {
			return c.getConceptId();
		}

		if (!c.isActive() || c.getDepth() == NOT_SET) {
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
			String[] parts = SnomedUtils.deconstructFSN(c.getFsnSafely(), true);
			if (parts.length == 2 && 
					!StringUtils.isEmpty(parts[1]) && 
					semTagHierarchyMap.containsKey(parts[1])) {
				return semTagHierarchyMap.get(parts[1]);
			}
			return "";  //Hopefully the previous release will know
		}
		
		Set<Long> allAncestors = tc.getAncestors(c);
		
		if (splitOutDisease) {
			//We're going to separate out Diseases from Clinical Findings
			if (allAncestors.contains(64572001L)) {
				return DISEASE.getConceptId();
			}
		}

		for (Long sctId : tc.getAncestors(c)) {
			Concept a = gl.getConcept(sctId);
			if (a.getDepth() == 1) {
				return a.getConceptId();
			}
		}
		throw new TermServerScriptException("Unable to determine hierarchy for " + c);
	}

	private void reportStackOverflow(Stack<Concept> stack) throws TermServerScriptException {
		String stackStr = stack.stream()
				.map(c -> c.getId())
				.collect(Collectors.joining(", "));
		LOGGER.error("Recursive loop encountered in hierarchy of: " + stackStr, (Exception)null);
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
					if ((!parent.isActive() && nextParent.isActive())) {
						parents.remove(nextParent);
						parents.push(nextParent);
					} else if (parent.isActive() && nextParent.isActive() || !parent.isActive() && !nextParent.isActive()) {
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
		StringBuffer sb = new StringBuffer();
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
	
	protected boolean inScope (Component c) {
		if (moduleFilter == null || moduleFilter.size() == 0) {
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
