package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * SUBST-246 list all substances with parents, modifications, dispositions and say if it's used 
 * as an ingredient in a product, in which case we'll say it's already been reviewed.
 */
public class ListSubstancesWithModificationsAndDispositions extends TermServerReport {
	
	Set<Concept> substancesUsedInProducts = new HashSet<>();
	int maxParents = 0;
	int maxModifications = 0;
	int maxDispositions = 0;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ListSubstancesWithModificationsAndDispositions report = new ListSubstancesWithModificationsAndDispositions();
		try {
			report.additionalReportColumns = "FSN, Used in Product, Some Direct Children Flattened, All Direct Children Flattened, Parents, Modifications, Dispositions";
			report.init(args);
			report.loadProjectSnapshot(true);  
			report.postLoadInit();
			report.findBaseWithModifications();
		} catch (Exception e) {
			info("Failed to produce MissingAttributeReport due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		for (Concept product : PHARM_BIO_PRODUCT.getDescendents(NOT_SET)) {
			for (Relationship r : product.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE)) {
				substancesUsedInProducts.add(r.getTarget());
			}
			for (Relationship r : product.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE)) {
				substancesUsedInProducts.add(r.getTarget());
			}
		}
	}

	private void findBaseWithModifications() throws TermServerScriptException {
		for (Concept c : SUBSTANCE.getDescendents(NOT_SET)) {
			//Get a list of parents
			List<Concept> statedParents = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
			if (statedParents.size() > maxParents) {
				maxParents = statedParents.size();
			}
			
			//Get a list of modification attribute values
			Set<Concept> modifications = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE)
										.stream()
										.map(r -> r.getTarget())
										.collect(Collectors.toSet());
			if (modifications.size() > maxModifications) {
				maxModifications = modifications.size();
			}
			
			//Get a list of dispositions
			Set<Concept> dispositions = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_DISPOSITION, ActiveState.ACTIVE)
					.stream()
					.map(r -> r.getTarget())
					.collect(Collectors.toSet());
			if (dispositions.size() > maxDispositions) {
				maxDispositions = dispositions.size();
			}
			
			String someDirectChildrenFlattened, allDirectChildrenFlattened;
			int childrenCount = c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).size();
			if (childrenCount == 0) {
				someDirectChildrenFlattened = "-";
				allDirectChildrenFlattened = "-";
			} else {
				int flattenedChildCount = c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)
										.stream()
										.filter(child -> child.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE).size() > 0)
										.collect(Collectors.toSet()).size();
				someDirectChildrenFlattened = flattenedChildCount > 0 ? "Y":"N";
				allDirectChildrenFlattened = flattenedChildCount ==  childrenCount ? "Y":"N";
			}
			
			//Is this substance used in a product?
			String usedInProduct = substancesUsedInProducts.contains(c) ? "Y":"N";
			report (c, usedInProduct, someDirectChildrenFlattened, allDirectChildrenFlattened, 
					statedParents.stream().map(p->p.toString()).collect(Collectors.joining(",\n")),
					modifications.stream().map(m->m.toString()).collect(Collectors.joining(",\n")),
					dispositions.stream().map(d->d.toString()).collect(Collectors.joining(",\n")));
			incrementSummaryInformation("Substances reported");
		}
		
		warn ("Max Parents = " + maxParents);
		warn ("Max Modifications = " + maxModifications);
		warn ("Max Dispositions = " + maxDispositions);
	}

}
