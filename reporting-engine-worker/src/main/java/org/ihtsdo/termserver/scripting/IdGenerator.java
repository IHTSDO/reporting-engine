package org.ihtsdo.termserver.scripting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
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
	private int runForwardCount = 0;
	
	static private String ID_CONFIG = "running_id_config.txt";
	static private boolean configFileReset = false;
	
	public static IdGenerator initiateIdGenerator(String sctidFilename, PartitionIdentifier p) throws TermServerScriptException {
		if (sctidFilename.toLowerCase().equals("dummy")) {
			return new IdGenerator(p);
		}
		
		File sctIdFile = new File (sctidFilename);
		try {
			if (sctIdFile.canRead()) {
				IdGenerator idGen = new IdGenerator(sctIdFile, p);
				//Does the config say we have to run this forward?
				runForward(idGen);
				return idGen;
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
	
	private static void runForward (IdGenerator idGen) throws NumberFormatException, IOException {
		//Is there a config file to consider? If not, do nothing.
		File idConfigFile = new File (ID_CONFIG);
		if (idConfigFile.canRead()) {
			for (String line : FileUtils.readLines(idConfigFile)) {
				String[] lineItems = line.split(TAB);
				if (lineItems[0].equals(idGen.partitionIdentifier.toString())) {
					idGen.runForwardCount = Integer.parseInt(lineItems[1]);
					System.out.println(idGen.partitionIdentifier +" running forward by " + idGen.runForwardCount);
					for (int i=0; i<idGen.runForwardCount; i++) {
						idGen.availableSctIds.readLine();
					}
				}
			}
		}
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
			//Report switch to use dummy strategy
			useDummySequence = true;
			System.out.println("Ran out of ids for partition " + partitionIdentifier  + " at " + idsAssigned + " switching to dummy...");
			return getSCTID(); 
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
	
	public String finish() throws FileNotFoundException {
		try {
			if (!useDummySequence) {
				availableSctIds.close();
			}
		} catch (Exception e){}
		//Are we the first generator to write out the final state?  Blitz the file if so, otherwise append.
		OutputStreamWriter osw; 
		String dataLine = partitionIdentifier + TAB + (runForwardCount + idsAssigned);
 		if (!IdGenerator.configFileReset) {
 			System.out.println("Writing " + dataLine + " to " + ID_CONFIG);
 			osw = new OutputStreamWriter(new FileOutputStream(new File(ID_CONFIG), false), StandardCharsets.UTF_8);
			IdGenerator.configFileReset = true;
 		} else {
			System.out.println("Appending " + dataLine + " to " + ID_CONFIG);
			osw = new OutputStreamWriter(new FileOutputStream(new File(ID_CONFIG), true), StandardCharsets.UTF_8);
		}
		PrintWriter pw = new PrintWriter(new BufferedWriter(osw));
		pw.write(dataLine + LINE_DELIMITER);
		pw.close();
		String ofWhich = ".";
		if (dummySequence > 100) {
			ofWhich = " of which " + (dummySequence - 100) + " were dummy.";
		}
		return "IdGenerator supplied " + idsAssigned + " " + partitionIdentifier + " sctids" + ofWhich;
	}
	
	public void isExtension(boolean b) {
		isExtension = b;
	}
}
