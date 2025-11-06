package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;

public class VaccineTermGenerator extends DrugTermGenerator {

    public VaccineTermGenerator(TermServerScript parent) {
        super(parent);
    }

    public String calculateTermFromIngredients(Concept c, boolean isFSN, boolean isPT, String langRefset, CharacteristicType charType) throws TermServerScriptException {
        return calculateTermFromIngredients(c, isFSN, isPT, langRefset, charType, true);
    }
}
