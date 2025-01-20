package org.ihtsdo.termserver.scripting.delta;

import java.io.File;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.AssociationEntry;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * DRUGS-452
 * Generic class to recreate some component and form that into a delta for import
 * @author Peter
 *
 */
public class RecreateComponent extends DeltaGenerator {

	public static void main(String[] args) throws TermServerScriptException {
		RecreateComponent delta = new RecreateComponent();
		try {
			delta.runStandAlone=true;
			delta.newIdsRequired=false;
			delta.init(args);
			delta.recreateComponent();
			delta.flushFiles(false); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}
	
	private void recreateComponent() throws TermServerScriptException {
		AssociationEntry h = new AssociationEntry();
		h.setId("475d74f4-587e-5c33-987b-72f993436c38");
		h.setRefsetId("900000000000523009");
		h.setActive(false);
		h.setEffectiveTime(null);
		h.setModuleId(targetModuleId);
		h.setReferencedComponentId("376166005");
		h.setTargetComponentId("370769002");
		h.setDirty();
		
		Concept c = gl.getConcept("376166005");
		c.getAssociationEntries().add(h);
		outputRF2(c);
	}

}
