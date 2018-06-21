package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * QI
 * Report changes made in this authoring cycle for specific sub-hierarchies.
 */
public class GenerateWorkDoneStats extends TermServerReport {
	
	List<Concept> subHierarchies;
	InitialAnalysis ipReport = new InitialAnalysis();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		GenerateWorkDoneStats report = new GenerateWorkDoneStats();
		try {
			report.additionalReportColumns = "FSN, Subhierarchy size, concepts modified - stated, concepts modified - inferred, concepts modified - purely stated, concepts modified - purely inferred, Intermediate Primitive count ";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.generateWorkDoneStats();
		} catch (Exception e) {
			info("Failed to produce MissingAttributeReport due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subHierarchies = new ArrayList<>();
		subHierarchies.add(gl.getConcept("46866001")); // |Fracture of lower limb (disorder)|
		subHierarchies.add(gl.getConcept("125605004")); // |Fracture of bone (disorder)|
		subHierarchies.add(gl.getConcept("34014006")); // |Viral disease (disorder)|
	}

	private void generateWorkDoneStats() throws TermServerScriptException {
		ipReport.setQuiet(true);
		for (Concept subHierarchy : subHierarchies) {
			debug ("Analysing subHierarchy: " + subHierarchy);
			int total = descendantsCache.getDescendentsOrSelf(subHierarchy).size();
			
			//How many of these concepts have been modified?
			Set<Concept> modifiedStated = getModified(subHierarchy, CharacteristicType.STATED_RELATIONSHIP);
			Set<Concept> modifiedInferred = getModified(subHierarchy, CharacteristicType.INFERRED_RELATIONSHIP);
			
			Set<Concept> purelyStated = new HashSet<>(modifiedStated);
			purelyStated.removeAll(modifiedInferred);
			
			Set<Concept> purelyInferred = new HashSet<>(modifiedInferred);
			purelyInferred.removeAll(modifiedStated);
			
			//How many Intermediate Primitives affect this subHierarchy?
			ipReport.setSubHierarchy(subHierarchy.getConceptId());
			ipReport.reportConceptsAffectedByIntermediatePrimitives();
			int ipCount = ipReport.intermediatePrimitives.keySet().size();
			
			report (subHierarchy, total, modifiedStated.size(), modifiedInferred.size(), purelyStated.size(), purelyInferred.size(), ipCount);
		}
	}

	private Set<Concept> getModified(Concept subHierarchy, CharacteristicType charType) throws TermServerScriptException {
		//find any concepts in this hierarchy which have unreleased relationships
		//TODO Add check for specific effectiveTime so we can also check post release.
		Set<Concept> modified = new HashSet<>();
		for (Concept c : descendantsCache.getDescendentsOrSelf(subHierarchy.getConceptId())) {
			if (c.getEffectiveTime() == null) {
				modified.add(c);
			} else {
				for (Relationship r : c.getRelationships(charType, ActiveState.BOTH)) {
					if (r.getEffectiveTime() == null) {
						modified.add(c);
						break;
					}
				}
			}
		}
		return modified;
	}
	
}
