package org.ihtsdo.termserver.scripting.delta;

import java.io.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
/**
 * INFRA-5000 We have rows appearing in the delta which do not represent actual changes
 * since the last release.   We're going to work through the delta and re-assert
 * the snapshot effective time where this occurs so as to produce a delta that can
 * be imported to reset the delta back to its previous state
 * 
 * INFRA-5156
 * 
 * TO DO Check refset components also.
 * TO DO Create a report of the "dirty" components output
 */
public class RevertNoChangeDelta extends DeltaGenerator {

	public static void main(String[] args) throws TermServerScriptException {
		RevertNoChangeDelta app = new RevertNoChangeDelta();
		try {
			app.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			app.newIdsRequired = false;
			app.runStandAlone = true;
			app.additionalReportColumns="ComponentType, ComponentId, Info, Data";
			app.init(args);
			app.getGraphLoader().setDetectNoChangeDelta(true);
			app.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			app.postInit(GFOLDER_ADHOC_UPDATES);
			app.reportRevertedComponents();
			app.outputModifiedComponents(true);
			app.flushFiles(false); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(app.outputDirName));
		} finally {
			app.finish();
		}
	}

	private void reportRevertedComponents() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (c.isDirty()) {
				report(c, "Concept", null, null);
			}
			
			for (Description d : c.getDescriptions()) {
				if (d.isDirty()) {
					report(c, "Description", d, null);
				}
				for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
					if (i.isDirty()) {
						report(c, "D_Inactivator", d, i);
					}
				}
				for (AssociationEntry a : d.getAssociationEntries()) {
					if (a.isDirty()) {
						report(c, "D_Association", d, a);
					}
				}
			}
			
			for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries()) {
				if (i.isDirty()) {
					report(c, "C_Inactivator", null, i);
				}
			}
			for (AssociationEntry a : c.getAssociationEntries()) {
				if (a.isDirty()) {
					report(c, "C_Association", null, a);
				}
			}
			for (Relationship r : c.getRelationships()) {
				if (r.isDirty() && !r.fromAxiom()) {
					report(c, "Relationship", null, r);
				}
			}
			for (AxiomEntry a : c.getAxiomEntries()) {
				if (a.isDirty()) {
					report(c, "Axiom", null, a);
				}
			}
		}
		
	}
	
}
