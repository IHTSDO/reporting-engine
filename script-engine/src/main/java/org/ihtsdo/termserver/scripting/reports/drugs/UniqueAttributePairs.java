package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import com.google.common.util.concurrent.AtomicLongMap;

/**
 * RP-200 
 */
public class UniqueAttributePairs extends TermServerReport implements ReportClass {
	
	private AtomicLongMap<String> presFormUnit = AtomicLongMap.create();
	private AtomicLongMap<String> concFormUnit = AtomicLongMap.create();
	private AtomicLongMap<String> presNumDenomUnits = AtomicLongMap.create();
	private AtomicLongMap<String> concNumDenomUnits = AtomicLongMap.create();
	private AtomicLongMap<String> boSSPai = AtomicLongMap.create();
	
	Map<String, Concept> examples = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		TermServerReport.run(UniqueAttributePairs.class, args, new HashMap<>());
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1wtB15Soo-qdvb0GHZke9o_SjFSL_fxL3"; //Drugs Validation
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Dose Form, Denom Unit, Unit Pres, Count, Example",
				"DoseForm, Conc DenomUnit, Count, Example",
				"Pres Num Unit, Pres Den Unit, Count, Example",
				"Conc Num Unit, Conc Den Unit, Count, Example",
				"BoSS, PAI, Count, Example"
				};
		String[] tabNames = new String[] {	
				"Pres Form/Unit",
				"Conc Form/Unit",
				"Pres Num/Denom Units",
				"Conc Num/Denom Units",
				"Boss/PAI"
				};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DRUGS))
				.withName("Unique Attribute Pairs")
				.withDescription("This report lists combinations of dose forms and quantities along with usage counts and examples.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		for (Concept c : MEDICINAL_PRODUCT.getDescendents(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP)) {
			analyzeConcept(c);
		}
		
		reportData(PRIMARY_REPORT, presFormUnit);
		reportData(SECONDARY_REPORT,concFormUnit);
		reportData(TERTIARY_REPORT, presNumDenomUnits);
		reportData(QUATERNARY_REPORT,concNumDenomUnits );
		reportData(QUINARY_REPORT,boSSPai);
	}

	private void analyzeConcept(Concept c) throws TermServerScriptException {
		Concept doseForm = getRelationshipValue(c, HAS_MANUFACTURED_DOSE_FORM, UNGROUPED);
		Concept unitPres = getRelationshipValue(c, HAS_UNIT_OF_PRESENTATION, UNGROUPED);
		if (doseForm != null) {
			for (RelationshipGroup group : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
				Concept denomUnit = getRelationshipValue(c, HAS_PRES_STRENGTH_DENOM_UNIT, group.getGroupId());
				if (denomUnit != null) {
					countInstance(presFormUnit, c, doseForm, denomUnit, unitPres);
				}
				
				Concept concDenomUnit = getRelationshipValue(c, HAS_CONC_STRENGTH_DENOM_UNIT, group.getGroupId());
				if (concDenomUnit != null) {
					countInstance(concFormUnit, c, doseForm, concDenomUnit);
				}
				
				Concept presNumUnit = getRelationshipValue(c, HAS_PRES_STRENGTH_UNIT, group.getGroupId());
				Concept presDenUnit = getRelationshipValue(c, HAS_PRES_STRENGTH_DENOM_UNIT, group.getGroupId());
				if (presNumUnit != null && presDenUnit != null) {
					countInstance(presNumDenomUnits, c, presNumUnit, presDenUnit);
				}
				
				Concept concNumUnit = getRelationshipValue(c, HAS_CONC_STRENGTH_UNIT, group.getGroupId());
				Concept concDenUnit = getRelationshipValue(c, HAS_CONC_STRENGTH_DENOM_UNIT, group.getGroupId());
				if (concNumUnit != null && concDenUnit != null) {
					countInstance(concNumDenomUnits, c, concNumUnit, concDenUnit);
				}
				
				Concept boSS = getRelationshipValue(c, HAS_BOSS, group.getGroupId());
				Concept pAI = getRelationshipValue(c, HAS_PRECISE_INGRED, group.getGroupId());
				if (boSS != null && pAI != null && !boSS.equals(pAI)) {
					countInstance(boSSPai, c, boSS, pAI);
				}
			}
		}
		
	}

	private void countInstance(AtomicLongMap<String> countMap, Concept c, Concept... values) {
		//Build a key to store the data in
		String key = "";
		boolean isFirst = true;
		for (Concept value : values) {
			if (!isFirst) {
				key += "~";
			} else {
				isFirst = false;
			}
			key += value;
		}
		countMap.getAndIncrement(key);
		examples.put(key, c);
	}

	private Concept getRelationshipValue(Concept c, Concept type, int groupId) throws TermServerScriptException {
		Set<Relationship> matches = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, type, groupId);
		if (matches.size() > 1) {
			throw new TermServerScriptException("Unexpected " + matches.size() + " relationships of type " + type + " in group " + groupId + " in concept " + c);
		} else if (matches.size() == 1) {
			return matches.iterator().next().getTarget();
		}
		return null;
	}
	

	private void reportData(int tabIdx, AtomicLongMap<String> data) throws TermServerScriptException {
		for (Map.Entry<String, Long> entry : data.asMap().entrySet()) {
			report (tabIdx, entry.getKey().split("~"), entry.getValue(), examples.get(entry.getKey()));
			countIssue(null);
		}
	}
}
