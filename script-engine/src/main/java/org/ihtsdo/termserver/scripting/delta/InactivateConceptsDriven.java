package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InactivateConceptsDriven extends DeltaGenerator implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivateConceptsDriven.class);

	private Set<Concept> conceptsToInactivate;
	
	public static void main(String[] args) throws TermServerScriptException {
		InactivateConceptsDriven delta = new InactivateConceptsDriven();
		try {
			delta.targetModuleId = "57101000202106";  //NO
			delta.newIdsRequired = false; 
			delta.init(args);
			delta.loadProjectSnapshot(false); 
			delta.postInit(GFOLDER_ADHOC_UPDATES);
			delta.process();
			delta.flushFiles(false); //Need to flush files before zipping
			if (!delta.dryRun) {
				SnomedUtils.createArchive(new File(delta.outputDirName));
			} else {
				LOGGER.info("Dry run mode.  Skipping creation of archive");
			}
		} finally {
			delta.finish();
		}
	}

	@Override
	protected void process() throws TermServerScriptException {
		getConceptsToInactivate();
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			//Is this a concept we've been told to replace the associations on?
			if (conceptsToInactivate.contains(c)) {
				if (c.isActiveSafely()) {
					inactivateConcept(c);
					outputRF2(c);
					countIssue(c);
					report(c, Severity.LOW, ReportActionType.CONCEPT_INACTIVATED);
				} else {
					report(c, Severity.MEDIUM, ReportActionType.VALIDATION_ERROR, "Concept is already inactive");
				}
			}
		}
	}

	private void inactivateConcept(Concept c) {
		//Inactivate concept, and ensure that it's now primitive
		inactivateComponent(c);
		c.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
		InactivationIndicatorEntry cii = InactivationIndicatorEntry.withDefaults(c, SCTID_INACT_CLASS_DERIVED_COMPONENT);
		cii.setModuleId(targetModuleId);
		c.addInactivationIndicator(cii);
		
		//Inactivate Axioms
		for (AxiomEntry a : c.getAxiomEntries()) {
			inactivateComponent(a);
		}
		
		//Inactivate Inferred Relationships
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			inactivateComponent(r);
		}
		
		//Create CNC indicators for all active descriptions
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			InactivationIndicatorEntry dii = InactivationIndicatorEntry.withDefaults(d,SCTID_INACT_CONCEPT_NON_CURRENT);
			d.addInactivationIndicator(dii);
		}
		
	}

	private void inactivateComponent(Component c) {
		c.setActive(false);
		c.setEffectiveTime(null);
		c.setDirty();
	}

	private void getConceptsToInactivate() throws TermServerScriptException {
		conceptsToInactivate = new HashSet<>();
		try {
			for (String line : Files.readAllLines(getInputFile().toPath(), Charset.defaultCharset())) {
				try {
					String[] items = line.split(TAB);
					conceptsToInactivate.add(gl.getConcept(items[0]));
				} catch (Exception e) {
					LOGGER.warn("Failed to parse line: {}", line);
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}

		//Validate the list
		for (Concept c : conceptsToInactivate) {
			//Check that for any concepts that have children that we're not also going to inactivate
			for (Concept child : c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (!conceptsToInactivate.contains(child)) {
					throw new TermServerScriptException(c + " has children not also being inactivated");
				}
			}
			
			//Also check for incoming historical associations
			if (gl.isUsedAsHistoricalAssociationTarget(c)) {
				throw new TermServerScriptException(c + " is used as the target of a historical association. " + gl.listAssociationParticipation(c));
			}
		}
	}

}
