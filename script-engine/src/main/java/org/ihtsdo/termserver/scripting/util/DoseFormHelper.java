package org.ihtsdo.termserver.scripting.util;

import com.google.common.io.Files;
import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DoseFormHelper implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(DoseFormHelper.class);
	private boolean initialised = false;

	private Map<Concept, Boolean> acceptableMpfDoseForms = new HashMap<>();
	private Map<Concept, Boolean> acceptableCdDoseForms = new HashMap<>();

	public void initialise(GraphLoader gl) throws TermServerScriptException {
		String fileName = "resources/acceptable_dose_forms.tsv";
		LOGGER.debug("Loading {}", fileName);
		try {
			List<String> lines = Files.readLines(new File(fileName), StandardCharsets.UTF_8);
			boolean isHeader = true;
			for (String line : lines) {
				String[] items = line.split(TAB);
				if (!isHeader) {
					Concept c = gl.getConcept(items[0]);
					acceptableMpfDoseForms.put(c, items[2].equals("yes"));
					acceptableCdDoseForms.put(c, items[3].equals("yes"));
				} else {
					isHeader = false;
				}
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + fileName, e);
		}
		initialised = true;
	}

	private boolean isMPF(Concept c) {
		return isMPF(c.getConceptType());
	}

	private boolean isMPF(ConceptType conceptType) {
		return conceptType.equals(RF2Constants.ConceptType.MEDICINAL_PRODUCT_FORM) ||
				conceptType.equals(RF2Constants.ConceptType.MEDICINAL_PRODUCT_FORM_ONLY);
	}

	private boolean isCD(Concept c) {
		return isCD(c.getConceptType());
	}

	private boolean isCD(ConceptType conceptType) {
		return conceptType.equals(RF2Constants.ConceptType.CLINICAL_DRUG);
	}

	public boolean isListed(Concept doseForm) {
		//Dose forms are specified for both CD and MPF, so we could respond from either map.
		ensureInitialised();
		return acceptableMpfDoseForms.containsKey(doseForm);
	}

	private void ensureInitialised() {
		if (!initialised) {
			throw new IllegalStateException("DoseFormHelper has not been initialised");
		}
	}

	public boolean inScope(Concept c) {
		return isCD(c) || isMPF(c);
	}

	public boolean usesListedDoseForm(Concept c, Concept thisDoseForm) {
		return getAcceptableMapForConceptType(c).containsKey(thisDoseForm);
	}

	public boolean usesAcceptableDoseForm(Concept c, Concept thisDoseForm) {
		return getAcceptableMapForConceptType(c).get(thisDoseForm);
	}

	public boolean isAcceptableDoseFormForConceptType(Concept doseForm, ConceptType ct) {
		return getAcceptableMapForConceptType(ct).get(doseForm);
	}

	private Map<Concept, Boolean> getAcceptableMapForConceptType(Concept c) {
		if (isMPF(c)) {
			return acceptableMpfDoseForms;
		} else if (isCD(c)) {
			return acceptableCdDoseForms;
		} else {
			throw new IllegalArgumentException("Concept " + c + " is not a CD or MPF");
		}
	}

	private Map<Concept, Boolean> getAcceptableMapForConceptType(ConceptType ct) {
		if (isMPF(ct)) {
			return acceptableMpfDoseForms;
		} else if (isCD(ct)) {
			return acceptableCdDoseForms;
		} else {
			throw new IllegalArgumentException("Concept Type" + ct + " is not a CD or MPF");
		}
	}
}
