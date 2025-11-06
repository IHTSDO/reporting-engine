package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * ISP-20 List properties of Pharmaceutical Dose forms
 * SCTid, FSN, PT, Parents (id, PT), 
 * Defined/Primitive?, 
 * Has basic dose form (id, PT), 
 * Has dose form administration method (id, PT), 
 * Has dose form intended site (id, PT), 
 * Has dose form release characteristic (id, PT), 
 * Has dose form transformation (id, PT)
 */
public class DoseFormProperties extends TermServerReport implements ReportClass {

	public static final String THIS_RELEASE = "This Release";
	
	public static void main(String[] args) throws TermServerScriptException {
		TermServerScript.run(DoseFormProperties.class, args, new HashMap<>());
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1H7T_dqmvQ-zaaRtfrd-T3QCUMD7_K8st"); //Drugs Analysis
		additionalReportColumns = "Dose Form FSN,SemTag,Dose Form PT,ParentIds,Parents,Used in Int,DefnStat,BDFID, Basic Dose Form, AMID, Administration Method, ISID, Intended Site, RCID, Release Characteristic, TransId, Transformation";
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DRUGS))
				.withName("Dose Form Properties")
				.withDescription("This report lists dose form properties. Use 'This Release' optionally, to run against an S3 published package.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		Concept pharmDoseForm = gl.getConcept("736542009 |Pharmaceutical dose form (dose form)|");
		List<Concept> pharmDoseForms = new ArrayList<>(pharmDoseForm.getDescendants(NOT_SET));
		pharmDoseForms.sort(Comparator.comparing(Concept::getFsn));
		Set<Concept> usedInInternationalEdition = findDoseFormsUsed();
		for (Concept c : pharmDoseForms) {
			countIssue(c);
			report(c, 
					c.getPreferredSynonym(),
					reportValue(c, IS_A),
					usedInInternationalEdition.contains(c) ? "Y" : "N",
					SnomedUtils.translateDefnStatus(c.getDefinitionStatus()),
					reportValue(c, gl.getConcept("736476002 |Has basic dose form (attribute)|")),
					reportValue(c, gl.getConcept("736472000 |Has dose form administration method (attribute)|")),
					reportValue(c, gl.getConcept("736474004 |Has dose form intended site (attribute)|")),
					reportValue(c, gl.getConcept("736475003 |Has dose form release characteristic (attribute)|")),
					reportValue(c, gl.getConcept("736473005 |Has dose form transformation (attribute)|")));
		}

	}

	private Set<Concept> findDoseFormsUsed() throws TermServerScriptException {
		Set<Concept> doseFormsUsed = new HashSet<>();
		Concept[] types = new Concept[] { gl.getConcept("411116001 |Has manufactured dose form (attribute)|")};
		for (Concept drug : PHARM_BIO_PRODUCT.getDescendants(NOT_SET)) {
			doseFormsUsed.addAll(SnomedUtils.getTargets(drug, types, CharacteristicType.INFERRED_RELATIONSHIP));
		}
		return doseFormsUsed;
	}

	private String[] reportValue(Concept c, Concept attributeType) {
		String[] idsPTs = new String[] {"",""};
		boolean isFirst = true;
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE)) {
			idsPTs[0] += (isFirst?"":",\n") + r.getTarget().getId();
			idsPTs[1] += (isFirst?"":",\n") + r.getTarget().getPreferredSynonym();
			isFirst = false;
		}
		return idsPTs;
	}

}
