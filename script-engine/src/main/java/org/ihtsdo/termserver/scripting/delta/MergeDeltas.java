package org.ihtsdo.termserver.scripting.delta;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.FlatFileLoader;
import org.ihtsdo.termserver.scripting.domain.Rf2File;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
/**
 *ISRS-317
 *Script to compare a current delta with some earlier fix delta
 *to determine which fields have been changed and produce a merged view.
 *With the current delta taking "trump" if a change conflicts.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergeDeltas extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(MergeDeltas.class);

	FlatFileLoader currentDelta = new FlatFileLoader();
	File fixDeltaFile;
	Long publishedEffectiveTime = 20180131L;
	
	public static void main(String[] args) throws TermServerScriptException {
		MergeDeltas app = new MergeDeltas();
		try {
			app.newIdsRequired = false;
			app.runStandAlone = true;
			app.additionalReportColumns="ComponentType, ComponentId, Info, Data";
			app.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			app.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			app.startTimer();
			app.doMerge();
			app.flushFiles(false); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(app.outputDirName));
		} finally {
			app.finish();
		}
	}
	
	@Override
	public String getReportName() {
		return "MergedDeltaFixes";
	}

	@Override
	protected void init (String[] args) throws TermServerScriptException {
		super.init(args);
		
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-f2")) {
				fixDeltaFile = new File(args[++x]);
			}
		}
	}
	
	private void doMerge() throws TermServerScriptException {
		//First load in the current state
		currentDelta.loadArchive(getInputFile());
		
		//Now work through the fix Delta and compare each line
		processFixDelta();
		
	}

	private void processFixDelta() throws TermServerScriptException{
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(fixDeltaFile));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.getFileName().toString();
						ComponentType componentType = Rf2File.getComponentType(fileName, FileType.DELTA);
						if (componentType != null && !fileName.startsWith("._")) {
							LOGGER.info("Processing " + fileName);
							processFixDeltaFile(zis, componentType);
						} else {
							LOGGER.info("Skipping unrecognised file: " + fileName);
						}
					}
					ze = zis.getNextEntry();
				}
			} finally {
				zis.closeEntry();
				zis.close();
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to process fix delta " + fixDeltaFile.getName(), e);
		}
	}

	private void processFixDeltaFile(InputStream is, ComponentType componentType) throws Exception {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeader = true;
		while ((line = br.readLine()) != null) {
			if (!isHeader) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				String[] output = processFixDeltaLine(componentType, lineItems);
				outputRF2(componentType, output);
			} else {
				isHeader = false;
			}
		}
	}

	private String[] processFixDeltaLine(ComponentType componentType, String[] fixLineItems) throws TermServerScriptException {
		String id = fixLineItems[IDX_ID];
		String fixEffectiveTime = fixLineItems[IDX_EFFECTIVETIME];
		String[] alphaFields;
		String[] currentFields = currentDelta.get(id);
		String[] output = new String[fixLineItems.length];
		Concept relevantComponent = gl.getComponentOwner(id);
		
		//If the current delta does not know about this component, then it's not changed at all since release, so we should use the fix
		if (currentFields==null) {
			String msg = "Fixed component has not been changed since versioning.  Using fix version";
			report(relevantComponent, null, Severity.LOW, ReportActionType.INFO,componentType.toString(), id, msg, StringUtils.join(fixLineItems, "|"));
			return fixLineItems;			
		}
		String currentEffectiveTime = currentFields[IDX_EFFECTIVETIME];
		
		//Is this fix a reversion?  We'll either take the whole thing, or ignore the whole thing.
		if (!StringUtils.isEmpty(fixEffectiveTime) && Long.parseLong(fixEffectiveTime) < publishedEffectiveTime) {
			//If the current version has not changed since we versioned, then we'll apply the fix.
			//Otherwise, apply the current state
			if (currentEffectiveTime == "") {
				String msg = "Current state has changed since versioning.  Ignoring reversion.";
				report(relevantComponent, null, Severity.HIGH, ReportActionType.INFO, componentType.toString(), id, msg, StringUtils.join(fixLineItems, "|"));
				return null;
			}
		}
		
		//Otherwise, we'll work on a field by field basis to form merge of the two.
		
		//Get the alpha fields for this component
		Component alphaComponent = gl.getComponent(id);
		if (alphaComponent == null) {
			//This is a new component since the release
			alphaFields = new String[fixLineItems.length];
		} else {
			try {
				alphaFields = alphaComponent.toRF2();
			} catch (Exception e) {
				throw new TermServerScriptException("Unable to express alphaComponent in RF2",e);
			}
		}
		
		//Check each field to see if it has changed since versioning.
		String fieldsChanged = "";
		String dataComparisons = "";		
		for (int i=0; i < alphaFields.length; i++ ) {
			//If the current has changed, that takes priority.  Otherwise, take the fix
			if (!alphaFields[i].equals(currentFields[i])) {
				fieldsChanged += fieldsChanged.isEmpty()?i:"," +i;
				dataComparisons += dataComparisons.isEmpty()?"":", ";
				dataComparisons += "current '" + currentFields[i] + "' vs fix '" + fixLineItems[i] + "'";
				output[i] = currentFields[i];
			} else {
				output[i] = fixLineItems[i];
				if (!alphaFields[i].equals(fixLineItems[i])) {
					dataComparisons += dataComparisons.isEmpty()?"":", ";
					dataComparisons += " taking fix '" + fixLineItems[i] + "'";
				}
			}
		}
		//HOWEVER, if the ONLY field to be different to the fix is the effective date, then we actually want to reset that component back to being alpha, ie use the fix line
		if (differsOnlyInEffectiveTime(output, fixLineItems)) {
			String msg = "Current rows shows as unpublished, but is otherwise the same as the published fix.  Resetting to fix row to prevent no-change delta in next release.";
			report(relevantComponent, null, Severity.HIGH, ReportActionType.INFO,componentType.toString(), id, msg,  StringUtils.join(fixLineItems, "|"));
			output = fixLineItems;
		} else {
			String msg = "Using fields " + fieldsChanged + ", modified since versioning: " + dataComparisons;
			report(relevantComponent, null, Severity.MEDIUM, ReportActionType.INFO,componentType.toString(), id, msg);
		}
		return output;
	}

	private boolean differsOnlyInEffectiveTime(String[] a, String[] b) {
		//First check the effectiveTime is different
		boolean differsOnlyInEffectiveTime = true;
		if (!a[IDX_EFFECTIVETIME].equals(b[IDX_EFFECTIVETIME])) {
			//Now check all the other fields are the same
			for (int i=0; i<a.length; i++) {
				if (i != IDX_EFFECTIVETIME && !a[i].equals(b[i])) {
					differsOnlyInEffectiveTime = false;
					break;
				}
			}
		} else {
			differsOnlyInEffectiveTime = false;
		}
		return differsOnlyInEffectiveTime;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null;
	}
}
