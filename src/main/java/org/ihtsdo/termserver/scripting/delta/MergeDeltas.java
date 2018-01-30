package org.ihtsdo.termserver.scripting.delta;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.FlatFileLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Rf2File;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
/**
 *ISRS-317
 *Script to compare a current delta with some earlier fix delta
 *to determine which fields have been changed and produce a merged view.
 *With the current delta taking "trump" if a change conflicts.
 */
public class MergeDeltas extends DeltaGenerator {
	
	FlatFileLoader currentDelta = new FlatFileLoader();
	File fixDeltaFile;
	Long publishedEffectiveTime = 20180131L;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		MergeDeltas app = new MergeDeltas();
		try {
			app.newIdsRequired = false;
			app.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			app.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			app.startTimer();
			app.doMerge();
			app.flushFiles(false);
			SnomedUtils.createArchive(new File(app.outputDirName));
		} finally {
			app.finish();
		}
	}
	
	protected void init (String[] args) throws IOException, TermServerScriptException, SnowOwlClientException, SnowOwlClientException {
		super.init(args);
		
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-f2")) {
				fixDeltaFile = new File(args[++x]);
			}
		}
	}
	
	private void doMerge() throws TermServerScriptException, SnowOwlClientException, FileNotFoundException, IOException {
		//First load in the current state
		currentDelta.loadArchive(inputFile);
		
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
						ComponentType compoonentType = Rf2File.getComponentType(fileName, FileType.DELTA);
						if (compoonentType != null) {
							println ("Processing " + fileName);
							processFixDeltaFile(zis, compoonentType);
						} else {
							println ("Skipping unrecognised file: " + fileName);
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

	private void processFixDeltaFile(InputStream is, ComponentType compoonentType) throws Exception {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeader = true;
		while ((line = br.readLine()) != null) {
			if (!isHeader) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				String[] output = processFixDeltaLine(compoonentType, lineItems);
				outputRF2(compoonentType, output);
			} else {
				isHeader = false;
			}
		}
	}

	private String[] processFixDeltaLine(ComponentType compoonentType, String[] fixLineItems) throws TermServerScriptException {
		String id = fixLineItems[IDX_ID];
		String fixEffectiveTime = fixLineItems[IDX_EFFECTIVETIME];
		String[] releasedFields;
		String[] currentFields = currentDelta.get(id);
		String[] output = new String[fixLineItems.length];
		Concept relevantComponent = gl.getComponentOwner(id);
		
		//If the current delta does not know about this component, then it's not changed at all since release
		if (currentFields==null) {
			String msg = "Fixed component has not been changed since versioning.  Using fix version";
			report (relevantComponent, Severity.HIGH, ReportActionType.INFO, msg, compoonentType.toString(), StringUtils.join(fixLineItems, "\t"));
			return fixLineItems;			
		}
		String currentEffectiveTime = currentFields[IDX_EFFECTIVETIME];
		
		//Is this fix a reversion?  We'll either take the whole thing, or ignore the whole thing.
		if (fixEffectiveTime != "" && Long.parseLong(fixEffectiveTime) < publishedEffectiveTime) {
			//If the current version has not changed since we versioned, then we'll apply the fix.
			//Otherwise, apply the current state
			if (currentEffectiveTime == "") {
				String msg = "Current state has changed since versioning.  Ignoring reversion.";
				report (relevantComponent, Severity.HIGH, ReportActionType.INFO, msg, compoonentType.toString(), StringUtils.join(fixLineItems, "\t"));
				return null;
			}
		}
		
		//Get the released fields for this component
		Component releasedComponent = gl.getComponent(id);
		if (releasedComponent == null) {
			//This is a new component since the release
			releasedFields = new String[fixLineItems.length];
		} else {
			releasedFields = releasedComponent.toRF2();
		}
		
		//Check each field to see if it has changed since the release
		for (int i=0; i < releasedFields.length; i++ ) {
			//If the current has changed, that takes priority.  Otherwise, take the fix
			if (!releasedFields[i].equals(currentFields[i])) {
				output[i] = currentFields[i];
				String msg = "Using field modified since versioning " + i + ": " + currentFields[i] + " compared to fix " + fixLineItems[i];
				report (relevantComponent, Severity.MEDIUM, ReportActionType.INFO,compoonentType.toString(), msg);
			} else {
				output[i] = fixLineItems[i];
			}
		}
		return output;
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		return null;
	}
}
