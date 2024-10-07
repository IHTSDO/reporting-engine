package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

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
	private AtomicLongMap<String> boSSPaiMDF = AtomicLongMap.create();
	
	Map<String, Concept> examples = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		TermServerScript.run(UniqueAttributePairs.class, args, new HashMap<>());
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1wtB15Soo-qdvb0GHZke9o_SjFSL_fxL3"); //Drugs Validation
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"SCTID, Dose Form, SCTID, Denom Unit, SCTID,Unit Pres, Count, Example",
				"SCTID,DoseForm, SCTID, Conc DenomUnit, Count, Example",
				"SCTID,Pres Num Unit, SCTID, Pres Den Unit, Count, Example",
				"SCTID,Conc Num Unit, SCTID, Conc Den Unit, Count, Example",
				"SCTID,BoSS, SCTID, PAI, Count, Example",
				"SCTID,BoSS, SCTID, PAI, SCTID, MDF, Count, Example"};
		String[] tabNames = new String[] {	
				"Pres Form/Unit",
				"Conc Form/Unit",
				"Pres Num/Denom Units",
				"Conc Num/Denom Units",
				"BoSS/PAI",
				"BoSS/PAI/MDF"
				};
		super.postInit(tabNames, columnHeadings);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DRUGS))
				.withName("Unique Attribute Pairs")
				.withDescription("This report lists combinations of dose forms and units along with usage counts and examples.  Also BoSS/PAI/MDF combinations.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		for (Concept c : MEDICINAL_PRODUCT.getDescendants(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP)) {
			analyzeConcept(c);
		}
		
		reportData(PRIMARY_REPORT, presFormUnit);
		reportData(SECONDARY_REPORT,concFormUnit);
		reportData(TERTIARY_REPORT, presNumDenomUnits);
		reportData(QUATERNARY_REPORT,concNumDenomUnits );
		reportData(QUINARY_REPORT,boSSPai);
		reportData(SENARY_REPORT,boSSPaiMDF);
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
				if (boSS != null && pAI != null) {
					countInstance(boSSPaiMDF, c, boSS, pAI, doseForm);
					if (!boSS.equals(pAI)) {
						countInstance(boSSPai, c, boSS, pAI);
					}
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
			report(tabIdx, splitEntry(entry.getKey()), entry.getValue(), examples.get(entry.getKey()));
			countIssue(null);
		}
	}
	
	private String[] splitEntry(String entry) throws TermServerScriptException {
		List<String> columns = new ArrayList<>();
		for (String conceptStr : entry.split("~")) {
			if (conceptStr.equals("null")) {
				columns.add("");
				columns.add("");
			} else {
				int cutIdx = conceptStr.indexOf(PIPE_CHAR);
				if (cutIdx == NOT_FOUND) {
					throw new TermServerScriptException("Unable to separate FSN in " + entry);
				}
				columns.add(conceptStr.substring(0, cutIdx));
				columns.add(conceptStr.substring(cutIdx +1).replace("|", ""));
			}
		}
		return columns.toArray(new String[] {});
	}
}
