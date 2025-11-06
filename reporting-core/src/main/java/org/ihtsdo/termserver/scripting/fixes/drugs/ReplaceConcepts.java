package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
For DRUGS-482
Add ingredient counts where required.  Algorithm described in DRUGS-476.

The file of previous locations is being generated from the full file history using this SQL:

select r.* from relationship_f r, transclos t, concept_s c
where typeid = 116680003
and r.active = 0
and r.sourceid = c.id
and c.active = 1
and r.sourceid = t.`sourceid`
and t.destinationid = 373873005 -- |Pharmaceutical / biologic product (product)|
and r.effectiveTime = (
	select max(effectiveTime) from stated_relationship_f r2
	where r.id = r2.id
	and r2.active = 0
);

*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplaceConcepts extends DrugBatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceConcepts.class);

	Concept subHierarchy;
	Map<Concept, List<Concept>> originalParents;
	String orignalParentsFileName = "/Users/Peter/GDrive/017_Drugs/2018/DRUGS-522/previous_is_a.txt";
	String replaceConceptsFileName = "/Users/Peter/GDrive/017_Drugs/2018/DRUGS-522/replace.txt";
	public static final String MOVE = "MOVE";
	public static final String INACTIVATE = "INACTIVATE";
	public static final InactivationIndicator inactivationIndicator = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
	
	protected ReplaceConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceConcepts fix = new ReplaceConcepts(null);
		try {
			ReportSheetManager.setTargetFolderId("1yKJYSWaXm1_mQqEEEpK8cbYqnTk0DUer"); //Drugs/Bulk Concept Inactivations
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.populateEditPanel = false;
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
	public void postInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("770654000 |TEMPORARY parent for CDs that are not updated (product)|");
	
		//Load in our IS_A relationship from the before time of the long long ago
		LOGGER.info("Loading IS_A relationships from some previous release");
		originalParents = new HashMap<>();
		try {
			for (String line : FileUtils.readLines(new File(orignalParentsFileName), "UTF-8")) {
				String[] items = line.split(TAB);
				Concept child = gl.getConcept(items[REL_IDX_SOURCEID]);
				Concept parent = gl.getConcept(items[REL_IDX_DESTINATIONID]);
				//Have we seen this concept before?
				List<Concept> parents = originalParents.get(child);
				if (parents == null) {
					parents = new ArrayList<>();
					originalParents.put(child, parents);
				}
				parents.add(parent);
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read from " + orignalParentsFileName, e);
		}
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		try {
			int changes = inactivateOrReplaceConcept(task, loadedConcept);
			if (changes > 0) {
				updateConcept(task, loadedConcept, info);
			}
			return changes;
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return NO_CHANGES_MADE;
	}

	public int inactivateOrReplaceConcept(Task t, Concept c) throws TermServerScriptException {
		//Are we moving or inactivating this concept?
		if (!c.isActive()) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept is inactive, no action being taken");
			return NO_CHANGES_MADE;
		} else if (c.getIssueList().contains(MOVE)) {
			return moveConcept(t, c);
		} else if (c.getIssueList().contains(INACTIVATE)) {
			return inactivateConcept(t,c);
		} else {
			throw new TermServerScriptException("Unexpected action for concept:  " + c.getIssues(", "));
		}
	}

	private int moveConcept(Task t, Concept c) throws TermServerScriptException {
		//What should our new parent be?
		ParentFind parentFind = new ParentFind();
		findReturnedParent(gl.getConcept(c.getId()),parentFind);  //use cached concept so we can walk the transitive closure
		report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, parentFind);
		int changesMade = replaceParents(t, c, MEDICINAL_PRODUCT);
		
		//Now we want to take all the attributes from our parents and make them stated
		//but only if the parents have been properly modelled - filter out any "(product)" semtags
		Set<RelationshipGroup> allInheritedGroups = parentFind.parents
				.stream()
				.filter(p -> !p.getFsn().contains("(product)"))
				.flatMap(p -> p.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP).stream())
				.collect(Collectors.toSet());
		
		changesMade += applyRemodelledGroups(t, c, new ArrayList<>(allInheritedGroups));
		report(t, c, Severity.MEDIUM, ReportActionType.INFO, c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
		return changesMade;
	}

	private void findReturnedParent(Concept c, ParentFind parentFind) throws TermServerScriptException {
		//Do I have an immediate parent and is it active?
		List<Concept> parents = originalParents.get(c);
		if (parents == null || parents.size() == 0) {
			//Are the current parents still useful?
			Set<Concept> currentParents = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
			if (currentParents.size() > 0) {
				parentFind.parents.addAll(currentParents);
			} else {
				throw new ValidationFailure(c, "Can't find original or current parents of " + c);
			} 
		} else {
			parentFind.stepsTaken = parentFind.stepsTaken + 1;
			for (Concept parent : parents) {
				if (parent.isActive()) {
					parentFind.parents.add(parent);
				} else {
					//Can we find a historical association to replace it?
					List<AssociationEntry> assocs = parent.getAssociationEntries(ActiveState.ACTIVE);
					if (assocs!= null && assocs.size() > 1) {
						String assocStr = assocs
								.stream()
								.map(AssociationEntry::toString)
								.collect(Collectors.joining(",\n"));
						LOGGER.warn ("Multiple associations for " + c + ": " + assocStr);
						List<Concept> assocTargets = assocs.stream()
								.map(a -> gl.getConceptSafely(a.getTargetComponentId()))
								.collect(Collectors.toList());
						parentFind.histAssocsFollowed += assocs.size();
						parentFind.parents.addAll(assocTargets);
					} else if (assocs.size() == 1) {
						//We're assuming the association will point to a live concept
						parentFind.histAssocsFollowed++;
						parentFind.parents.add(gl.getConcept(assocs.get(0).getTargetComponentId()));
					} else {
						findReturnedParent(parent, parentFind);  //Recurse
					}
				}
			}
		}
	}

	private int inactivateConcept(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		//First we must - recursively - inactivate all the children, in the same task
		for (Concept child : c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			LOGGER.info("Adding " + child + " into task for inactivation as child of " + c);
			t.addAfter(child, c);
			changesMade += inactivateConcept(t, child);
		}
		//And now this concept
		changesMade += inactivateConcept(t, c, null, inactivationIndicator); //Not offering a replacement
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//First we'll do the concepts to move as priority components, and then we'll
		//inactivate all the remainder.
		try {
			for (String line : FileUtils.readLines(new File(replaceConceptsFileName), "UTF-8")) {
				String[] items = line.split(TAB);
				Concept c = gl.getConcept(items[0]);
				c.addIssue(MOVE);  //This ensures we get a new batch for moves vs inactivations
				priorityComponents.add(c);
			}
			
			List<Concept>processMe = new ArrayList<>(asConcepts(priorityComponents));
			processMe.sort(Comparator.comparing(c -> ((Concept)c).getFsn()));
			
			//Now all of our subHierarchy will be removed except for the ones we're moving
			List<Concept> inactivateMe = new ArrayList<>(subHierarchy.getDescendants(NOT_SET));
			inactivateMe.removeAll(processMe);
			
			//As well as sorting, we also need to remove any children, since they'll have to 
			//be processed in the same task as their parent
			for (Concept c : new ArrayList<>(inactivateMe)) {
				for (Concept p : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
					if (inactivateMe.contains(p)) {
						inactivateMe.remove(c);
					}
				}
			}
			inactivateMe.stream()
				.sorted((c1, c2) -> c1.getFsn().compareTo(c2.getFsn()))
				.forEach(c -> c.addIssue(INACTIVATE));
			processMe.addAll(inactivateMe);
			return asComponents(processMe);
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to identify components to process", e);
		}
	}
	
	class ParentFind {
		int stepsTaken;
		int histAssocsFollowed;
		Set<Concept> parents = new HashSet<>();
		
		@Override
		public String toString() {
			String parentStr = "\n" + parents.stream().map(Concept::toString).collect(Collectors.joining(",\n"));
			return "Found " + parents.size() + " parents taking " 
					+ stepsTaken + " steps and following " 
					+ histAssocsFollowed + " historical associations: " 
					+ parentStr;
		}
	}
}
