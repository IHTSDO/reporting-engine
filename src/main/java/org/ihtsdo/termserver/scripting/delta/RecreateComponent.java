package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.HistoricalAssociation;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * DRUGS-452
 * Generic class to recreate some component and form that into a delta for import
 * @author Peter
 *
 */
public class RecreateComponent extends DeltaGenerator {
	
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		RecreateComponent delta = new RecreateComponent();
		try {
			delta.runStandAlone=true;
			delta.newIdsRequired=false;
			delta.init(args);
			delta.recreateComponent();
			delta.flushFiles(true); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}
	
	private void recreateComponent() throws TermServerScriptException {
		HistoricalAssociation h = new HistoricalAssociation();
		h.setId("475d74f4-587e-5c33-987b-72f993436c38");
		h.setRefsetId("900000000000523009");
		h.setActive(false);
		h.setEffectiveTime(null);
		h.setModuleId(moduleId);
		h.setReferencedComponentId("376166005");
		h.setTargetComponentId("370769002");
		h.setDirty();
		
		Concept c = gl.getConcept("376166005");
		c.getHistorialAssociations().add(h);
		outputRF2(c);
	}

	@Override
	protected List<Concept> loadLine(String[] lineItems) throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}

}
