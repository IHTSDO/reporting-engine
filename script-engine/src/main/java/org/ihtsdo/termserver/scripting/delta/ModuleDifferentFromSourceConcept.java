package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Metadata;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Set the moduleId of Relationships and Axioms to the same as the source/owning Concept.
 */
public class ModuleDifferentFromSourceConcept extends DeltaGenerator {
    /**
     * The branch path to process.
     */
    private static final String BRANCH_PATH = "";

    public static void main(String[] args) throws Exception {
        ModuleDifferentFromSourceConcept app = new ModuleDifferentFromSourceConcept();
        try {
            String now = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            app.getArchiveManager().setPopulateReleasedFlag(true);
            app.newIdsRequired = false;
            app.packageDir = "output" + File.separator + "Delta_" + now + File.separator;
            app.init(args);
            app.loadProjectSnapshot(false);
            app.postInit();
            app.process();
            app.flushFiles(false);
            SnomedUtils.createArchive(new File(app.outputDirName));
        } finally {
            app.finish();
        }
    }


    @Override
    public void postInit() throws TermServerScriptException {
        String[] columnHeadings = new String[]{
                "Concept Id, Concept FSN, Member Id, Active, Details",
                "Concept Id, Concept FSN, Relationship Id, Active, Details",
                "Item, Count"
        };

        String[] tabNames = new String[]{
                "Stated",
                "Inferred",
                "Summary"
        };

        super.postInit(tabNames, columnHeadings, false);
    }

    @Override
    protected void process() throws TermServerScriptException {
        // Gather required data
        List<String> moduleIds = getModuleIdsOrThrow();
        Collection<Concept> concepts = gl.getAllConcepts();
        int updatedAxioms = 0;
        int updatedRelationships = 0;

        // Remove concepts not belonging to Extension
        concepts.removeIf(concept -> !moduleIds.contains(concept.getModuleId()));

        // Process concepts
        for (Concept concept : concepts) {
            String conceptModuleId = concept.getModuleId();

            // Change Axioms
            for (AxiomEntry axiomEntry : concept.getAxiomEntries()) {
                String message = String.format("Axiom's moduleId changed from %s to %s", axiomEntry.getModuleId(), conceptModuleId);
                boolean sameModuleId = Objects.equals(axiomEntry.getModuleId(), conceptModuleId);
                if (!sameModuleId) {
                    // Write change to RF2
                    axiomEntry.setModuleId(conceptModuleId);
                    axiomEntry.setEffectiveTime(null);
                    writeToRF2File(owlDeltaFilename, axiomEntry.toRF2());

                    // Report change
                    report(0, concept.getId(), concept.getFsn(), axiomEntry.getId(), axiomEntry.isActive(), message);
                    updatedAxioms = updatedAxioms + 1;
                }
            }

            // Change Relationships
            Set<Relationship> relationships = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
            for (Relationship relationship : relationships) {
                String message = String.format("Relationship's moduleId from %s to %s", relationship.getModuleId(), conceptModuleId);
                boolean sameModuleId = Objects.equals(relationship.getModuleId(), conceptModuleId);
                if (!sameModuleId) {
                    // Write change to RF2
                    relationship.setModuleId(conceptModuleId);
                    relationship.setEffectiveTime(null);
                    writeToRF2File(relDeltaFilename, relationship.toRF2());

                    // Report change
                    report(1, concept.getId(), concept.getFsn(), relationship.getId(), relationship.isActive(), message);
                    updatedRelationships = updatedRelationships + 1;
                }
            }
        }

        // Write summary
        report(2, "Updated Axioms", updatedAxioms);
        report(2, "Updated Relationships", updatedRelationships);
    }

    private List<String> getModuleIdsOrThrow() throws TermServerScriptException {
        if (BRANCH_PATH.isBlank()) {
            throw new TermServerScriptException("Change the default branch before running");
        }

        Branch branch = tsClient.getBranch(BRANCH_PATH);
        if (branch == null) {
            throw new TermServerScriptException(String.format("Cannot find branch with path '%s'.", BRANCH_PATH));
        }

        Metadata metadata = branch.getMetadata();
        if (metadata == null) {
            throw new TermServerScriptException(String.format("Cannot find metadata for path '%s'.", BRANCH_PATH));
        }

        List<String> expectedExtensionModules = metadata.getExpectedExtensionModules();
        if (expectedExtensionModules == null || expectedExtensionModules.isEmpty()) {
            throw new TermServerScriptException(String.format("Cannot find modules for path '%s'.", BRANCH_PATH));
        }

        return expectedExtensionModules;
    }
}
