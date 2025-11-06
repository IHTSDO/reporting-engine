package org.ihtsdo.termserver.scripting.reports.drugs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DoseFormHelper;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * RP-1048 List all dose forms and say which are acceptable, and which are used in modelling.
 */
public class ListDoseForms extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(ListDoseForms.class);

	private final Concept[] doseFormTypes = new Concept[] {HAS_MANUFACTURED_DOSE_FORM};

	public static void main(String[] args) throws TermServerScriptException {
		ListDoseForms report = new ListDoseForms();
		try {
			report.additionalReportColumns = "FSN, SemTag, Acceptability Specified, Acceptable for MPF, Acceptable for CD, Used in Modelling, Example";
			report.init(args);
			report.loadProjectSnapshot(true);
			report.postInit();
			report.listDoseForms();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report",e);
		} finally {
			report.finish();
		}
	}

	private void listDoseForms() throws TermServerScriptException {
		DoseFormHelper doseFormHelper = new DoseFormHelper();
		doseFormHelper.initialise(gl);
		Set<Concept> doseFormsAvailableUnsorted = PHARM_DOSE_FORM.getDescendants(NOT_SET);
		List<Concept> doseFormsAvailable = SnomedUtils.sort(doseFormsAvailableUnsorted);
		Map<Concept, Concept> doseFormsUsedInModelling = getDoseFormsUsedInModelling();

		for (Concept doseForm : doseFormsAvailable) {
			report( doseForm,
					doseFormHelper.isListed(doseForm)? "Y" : "N",
					acceptableOrNA(doseForm, doseFormHelper,ConceptType.MEDICINAL_PRODUCT_FORM),
					acceptableOrNA(doseForm, doseFormHelper,ConceptType.CLINICAL_DRUG),
					doseFormsUsedInModelling.containsKey(doseForm)? "Y" : "N",
					doseFormsUsedInModelling.containsKey(doseForm) ? doseFormsUsedInModelling.get(doseForm) : "N/A");
		}
	}

	private String acceptableOrNA(Concept doseForm, DoseFormHelper doseFormHelper, ConceptType conceptType) {
		if (!doseFormHelper.isListed(doseForm)) {
			return "N/A";
		}
		return doseFormHelper.isAcceptableDoseFormForConceptType(doseForm, conceptType)? "Y" : "N";
	}

	private Map<Concept,Concept> getDoseFormsUsedInModelling() throws TermServerScriptException {
		Map<Concept, Concept> doseFormsUsedInModelling = new HashMap<>();
		Set<Concept> allDrugs = MEDICINAL_PRODUCT.getDescendants(NOT_SET);
		for (Concept c : allDrugs) {
			Concept doseForm = SnomedUtils.getTarget(c, doseFormTypes, UNGROUPED, CharacteristicType.INFERRED_RELATIONSHIP);
			if (doseForm != null) {
				doseFormsUsedInModelling.put(doseForm, c);
			}
		}
		return doseFormsUsedInModelling;
	}

}
