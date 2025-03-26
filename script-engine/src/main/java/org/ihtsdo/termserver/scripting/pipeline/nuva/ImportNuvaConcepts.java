package org.ihtsdo.termserver.scripting.pipeline.nuva;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.nuva.domain.NuvaVaccine;
import org.ihtsdo.termserver.scripting.pipeline.nuva.template.NuvaTemplatedVaccineConcept;
import org.ihtsdo.termserver.scripting.pipeline.nuva.template.NuvaTemplatedValenceConcept;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;

import java.util.*;
import java.util.stream.Collectors;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ImportNuvaConcepts extends ContentPipelineManager implements NuvaConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(ImportNuvaConcepts.class);

	protected String[] tabNames = new String[] {
			TAB_SUMMARY,
			TAB_MODELING_ISSUES,
			TAB_PROPOSED_MODEL_COMPARISON,
			TAB_IMPORT_STATUS,
			TAB_IOI,
			TAB_STATS};
	
	public static void main(String[] args) throws TermServerScriptException {
		new ImportNuvaConcepts().ingestExternalContent(args);
	}

	protected String[] getTabNames() {
		return tabNames;
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Item, Info, Details, Foo, Bar, What, Goes, Here?",
				"NUVANum, Item of Special Interest, NUVAName, Issues, details, details",
				"NUVANum, SCTID, This Iteration, Template, Differences, Proposed Descriptions, Previous Descriptions, Proposed Model, Previous Model, detail, detail, detail, detail",
				"PartNum, PartName, PartType, Needed for High Usage Mapping, Needed for Highest Usage Mapping, PriorityIndex, Usage Count,Top Priority Usage, Higest Rank, HighestUsageCount",
				"Concept, FSN, SemTag, Severity, Action, NUVANum, Descriptions, Expression, Status, , ",
				"Category, NUVANum, Detail, , , "
		};

		super.postInit(GFOLDER_NUVA, tabNames, columnHeadings, false);
		scheme = gl.getConcept(SCTID_NUVA_SCHEMA);
		externalContentModuleId = SCTID_NUVA_EXTENSION_MODULE;
		namespace = "1002000";
		includeShortNameDescription = false;
	}

	@Override
	protected String getContentType() {
		return "Vaccine";
	}

	@Override
	protected void loadSupportingInformation() throws TermServerScriptException {
		NuvaTemplatedVaccineConcept.initialise(this);
		NuvaTemplatedValenceConcept.initialise(this);
		NuvaOntologyLoader loader = new NuvaOntologyLoader();
		List<NuvaVaccine> vaccines = loader.asVaccines(getInputFile());
		externalConceptMap = vaccines.stream()
				.collect(Collectors.toMap(NuvaVaccine::getExternalIdentifier, c -> c));
	}

	@Override
	protected void importPartMap() throws TermServerScriptException {
		LOGGER.debug("NUVA does not use a part map");
	}

	@Override
	public TemplatedConcept getAppropriateTemplate(ExternalConcept externalConcept) throws TermServerScriptException {
		return NuvaTemplatedVaccineConcept.create(externalConcept);
	}

	@Override
	protected String inScope(String property) throws TermServerScriptException {
		//It's all in scope for NUVA
		return "Y";
	}

	@Override
	protected Set<String> getObjectionableWords() {
		return new HashSet<>();  //No objections here
	}

	@Override
	public List<String> getMappingsAllowedAbsent() {
		return new ArrayList<>();  //Not yet expecting to allow missing mappings
	}

	@Override
	protected String[] getHighUsageIndicators(Set<ExternalConcept> externalConcepts) {
		return new String[0];
	}

}
