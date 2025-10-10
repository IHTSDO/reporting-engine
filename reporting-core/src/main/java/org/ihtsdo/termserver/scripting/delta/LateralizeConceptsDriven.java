package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.ConceptLateralizer;
import org.ihtsdo.termserver.scripting.util.TermGenerationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class LateralizeConceptsDriven extends DeltaGenerator implements ScriptConstants, TermGenerationStrategy {

	private static final Logger LOGGER = LoggerFactory.getLogger(LateralizeConceptsDriven.class);
 	private ConceptLateralizer conceptLateralizer;
	private Map<Concept, LateralizeInstruction> lateralizedInstructionMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		new LateralizeConceptsDriven().standardExecutionWithIds(args);
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		conceptLateralizer = ConceptLateralizer.get(this, true, this);
		super.postInit(googleFolder);
	}

	@Override
	protected void process() throws TermServerScriptException {
		populateLateralizedInstructionMap();
		for (LateralizeInstruction li : lateralizedInstructionMap.values()) {

			conceptLateralizer.createLateralizedConceptIfRequired(li.concept, LEFT, null);
			conceptLateralizer.createLateralizedConceptIfRequired(li.concept, RIGHT, null);
			conceptLateralizer.createLateralizedConceptIfRequired(li.concept, BILATERAL, null);
		}
	}

	private void populateLateralizedInstructionMap() throws TermServerScriptException {
		try {
			for (String line : Files.readAllLines(getInputFile().toPath(), Charset.defaultCharset())) {
				parseLateralizedInstructionMapLine(line);
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	private void parseLateralizedInstructionMapLine(String line) {
		try {
			String[] items = line.split(TAB);
			LateralizeInstruction li = parseLateralityInstruction(gl, items);
			lateralizedInstructionMap.put(li.concept, li);
		} catch (Exception e) {
			LOGGER.warn("Failed to parse line: {}", line);
		}
	}

	@Override
	public boolean applyTermViaOverride(Concept original, Concept clone) throws TermServerScriptException {
		//Do we have an override for this concept?
		LateralizeInstruction li = lateralizedInstructionMap.get(original);
		if (li != null && li.pt != null) {
			conceptLateralizer.applyTermAsPtAndFsn(original, clone, li.pt);
			return true;
		}
		return false;
	}

	@Override
	public String suggestTerm(Concept concept) {
		return "";
	}

	class LateralizeInstruction {
		Concept concept;
		String pt;

		public LateralizeInstruction(Concept concept, String pt) {
			this.concept = concept;
			this.pt = pt;
		}
	}

	public LateralizeInstruction parseLateralityInstruction(GraphLoader gl, String[] items) throws TermServerScriptException {
		String conceptId = items[0];
		String pt = null;
		if (items.length > 2) {
			pt = items[2];
		}

		Concept concept = gl.getConcept(conceptId);
		if (concept == null) {
			throw new TermServerScriptException("Concept not found: " + conceptId);
		}
		return new LateralizeInstruction(concept, pt);
	}

}
