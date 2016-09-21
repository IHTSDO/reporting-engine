package org.ihtsdo.termserver.scripting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class IdGenerator implements RF2Constants{
	private String fileName;
	private BufferedReader availableSctIds;
	
	public static IdGenerator initiateIdGenerator(String sctidFilename) throws TermServerScriptException {
		File sctIdFile = new File (sctidFilename);
		try {
			if (sctIdFile.canRead()) {
				return new IdGenerator(sctIdFile);
			}
		} catch (Exception e) {}
		
		throw new TermServerScriptException("Unable to read sctids from " + sctidFilename);
	}
	private IdGenerator(File sctidFile) throws FileNotFoundException {
		fileName = sctidFile.getAbsolutePath();
		availableSctIds = new BufferedReader(new FileReader(sctidFile));
	}
	
	public String getSCTID(PartionIdentifier partitionIdentifier) throws IOException, TermServerScriptException {
		String sctId;
		try {
			sctId = availableSctIds.readLine();
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to recover SCTID from file");
		}
		//Check the SCTID is valid, and belongs to the correct partition
		SnomedUtils.isValid(sctId, partitionIdentifier, true);  //throw exception if not valid
		return sctId;
	}
}
