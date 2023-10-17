package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.DrugUtils;

/**
 * SUBST-246 list all substances with parents, modifications, dispositions and say if it's used 
 * as an ingredient in a product, in which case we'll say it's already been reviewed.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListSubstancesWithModificationsAndDispositions extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(ListSubstancesWithModificationsAndDispositions.class);

	Set<Concept> substancesUsedInProducts;
	int maxParents = 0;
	int maxModifications = 0;
	int maxDispositions = 0;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		ListSubstancesWithModificationsAndDispositions report = new ListSubstancesWithModificationsAndDispositions();
		try {
			report.additionalReportColumns = "FSN, Used in Product, Some Direct Stated Children Flattened, All Direct Stated Children Flattened, Some Direct Inferred Children Flattened, All Direct Inferred Children Flattened, Parents, Modifications, Dispositions";
			report.init(args);
			report.loadProjectSnapshot(true);  
			report.findBaseWithModifications();
		} catch (Exception e) {
			LOGGER.info("Failed to produce MissingAttributeReport due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void findBaseWithModifications() throws TermServerScriptException {
		substancesUsedInProducts = DrugUtils.getSubstancesUsedInProducts();
		for (Concept c : SUBSTANCE.getDescendants(NOT_SET)) {
			//Get a list of parents
			Set<Concept> statedParents = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
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
			
			String someDirectStatedChildrenFlattened, allDirectStatedChildrenFlattened;
			int childrenCount = c.getChildren(CharacteristicType.STATED_RELATIONSHIP).size();
			if (childrenCount == 0) {
				someDirectStatedChildrenFlattened = "-";
				allDirectStatedChildrenFlattened = "-";
			} else {
				int flattenedChildCount = c.getChildren(CharacteristicType.STATED_RELATIONSHIP)
										.stream()
										.filter(child -> child.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE).size() > 0)
										.collect(Collectors.toSet()).size();
				someDirectStatedChildrenFlattened = flattenedChildCount > 0 ? "Y":"N";
				allDirectStatedChildrenFlattened = flattenedChildCount ==  childrenCount ? "Y":"N";
			}
			
			String someDirectInferredChildrenFlattened, allDirectInferredChildrenFlattened;
			childrenCount = c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).size();
			if (childrenCount == 0) {
				someDirectInferredChildrenFlattened = "-";
				allDirectInferredChildrenFlattened = "-";
			} else {
				int flattenedChildCount = c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)
										.stream()
										.filter(child -> child.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE).size() > 0)
										.collect(Collectors.toSet()).size();
				someDirectInferredChildrenFlattened = flattenedChildCount > 0 ? "Y":"N";
				allDirectInferredChildrenFlattened = flattenedChildCount ==  childrenCount ? "Y":"N";
			}
			
			//Is this substance used in a product?
			String usedInProduct = substancesUsedInProducts.contains(c) ? "Y":"N";
			report (c, usedInProduct, someDirectStatedChildrenFlattened, allDirectStatedChildrenFlattened, 
					someDirectInferredChildrenFlattened, allDirectInferredChildrenFlattened, 
					statedParents.stream().map(p->p.toString()).collect(Collectors.joining(",\n")),
					modifications.stream().map(m->m.toString()).collect(Collectors.joining(",\n")),
					dispositions.stream().map(d->d.toString()).collect(Collectors.joining(",\n")));
			incrementSummaryInformation("Substances reported");
		}
		
		LOGGER.warn ("Max Parents = " + maxParents);
		LOGGER.warn ("Max Modifications = " + maxModifications);
		LOGGER.warn ("Max Dispositions = " + maxDispositions);
	}

}
