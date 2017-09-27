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
	int idsAssigned = 0;
	private String namespace = "";
	private boolean isExtension = false;
	private PartitionIdentifier partitionIdentifier;
	
	public static IdGenerator initiateIdGenerator(String sctidFilename, PartitionIdentifier p) throws TermServerScriptException {
		if (sctidFilename.toLowerCase().equals("dummy")) {
			return new IdGenerator(p);
		}
		
		File sctIdFile = new File (sctidFilename);
		try {
			if (sctIdFile.canRead()) {
				return new IdGenerator(sctIdFile, p);
			}
		} catch (Exception e) {}
		
		throw new TermServerScriptException("Unable to read sctids from " + sctidFilename);
	}
	private IdGenerator(File sctidFile, PartitionIdentifier p) throws FileNotFoundException {
		fileName = sctidFile.getAbsolutePath();
		availableSctIds = new BufferedReader(new FileReader(sctidFile));
		partitionIdentifier = p;
	}
	private IdGenerator(PartitionIdentifier p) {
		partitionIdentifier = p;
		useDummySequence = true;
	}
	
	public String getSCTID() throws TermServerScriptException {
		if (useDummySequence) {
			idsAssigned++;
			return getDummySCTID();
		}
		
		String sctId;
		try {
			sctId = availableSctIds.readLine();
		} catch (IOException e) {
			throw new RuntimeException("Unable to recover SCTID from file " + fileName, e);
		}
		
		if (sctId == null || sctId.isEmpty()) {
			//Use RuntimeException to ensure we bomb all the way out.
			throw new RuntimeException("No more SCTIDs in file " + fileName + " need more than " + idsAssigned);
		}
		//Check the SCTID is valid, and belongs to the correct partition
		SnomedUtils.isValid(sctId, partitionIdentifier, true);  //throw exception if not valid
		idsAssigned++;
		return sctId;
	}
	
	private String getDummySCTID() throws TermServerScriptException  {
		try {
			String sctIdBase = ++dummySequence + namespace + (isExtension?"1":"0") + partitionIdentifier.ordinal();
			String checkDigit = new VerhoeffCheckDigit().calculate(sctIdBase);
			return sctIdBase + checkDigit;
		} catch (CheckDigitException e) {
			throw new TermServerScriptException ("Failed to generate dummy sctid",e);
		}
	}
	
	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}	
	
	public String finish() {
		try {
			if (!useDummySequence) {
				availableSctIds.close();
			}
		} catch (Exception e){}
		return "IdGenerator supplied " + idsAssigned + " " + partitionIdentifier + " sctids.";
	}
	
	public void isExtension(boolean b) {
		isExtension = b;
	}
}
