package org.ihtsdo.termserver.scripting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.apache.commons.validator.routines.checkdigit.VerhoeffCheckDigit;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class IdGenerator implements RF2Constants{
	private String fileName;
	private BufferedReader availableSctIds;
	private int dummySequence = 100;
	private boolean useDummySequence = false;
	
	public static IdGenerator initiateIdGenerator(String sctidFilename) throws TermServerScriptException {
		if (sctidFilename.equals("dummy")) {
			return new IdGenerator();
		}
		
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
	private IdGenerator() {
		useDummySequence = true;
	}
	
	public String getSCTID(PartionIdentifier partitionIdentifier) throws IOException, TermServerScriptException {
		if (useDummySequence) {
			return getDummySCTID(partitionIdentifier);
		}
		
		String sctId;
		try {
			sctId = availableSctIds.readLine();
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to recover SCTID from file " + fileName);
		}
		//Check the SCTID is valid, and belongs to the correct partition
		SnomedUtils.isValid(sctId, partitionIdentifier, true);  //throw exception if not valid
		return sctId;
	}
	private String getDummySCTID(PartionIdentifier partitionIdentifier) throws TermServerScriptException  {
		try {
			String sctIdBase = ++dummySequence + "0" + partitionIdentifier.ordinal();
			String checkDigit = new VerhoeffCheckDigit().calculate(sctIdBase);
			return sctIdBase + checkDigit;
		} catch (CheckDigitException e) {
			throw new TermServerScriptException ("Failed to generate dummy sctid",e);
		}
	}
}
