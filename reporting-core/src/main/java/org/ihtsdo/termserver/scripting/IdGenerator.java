package org.ihtsdo.termserver.scripting;

import java.io.*;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.apache.commons.validator.routines.checkdigit.VerhoeffCheckDigit;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class IdGenerator implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(IdGenerator.class);

	private String fileName;
	private BufferedReader availableSctIds;
	private int dummySequence = 100;
	private boolean useDummySequence = false;
	private int idsAssigned = 0;
	private String namespace = "";
	private boolean isExtension = false;
	private final PartitionIdentifier partitionIdentifier;
	private int runForwardCount = 0;
	
	private boolean useValidSequence = false;
	private long validSequence = 0;
	
	private static final String ID_CONFIG_ROOT = "running_id_config_";
	private static final String ID_CONFIG_EXT = ".txt";
	private static String generatorProgressTrackerName;
	private static boolean configFileReset = false;

	public static IdGenerator initiateIdGenerator(String sctidFilename, PartitionIdentifier p, String runId) throws TermServerScriptException {
		generatorProgressTrackerName = ID_CONFIG_ROOT + runId + ID_CONFIG_EXT;
		if (isDummyFile(sctidFilename)) {
			return new IdGenerator(p);
		}
		
		if (StringUtils.isNumeric(sctidFilename)) {
			return new IdGenerator(p, Long.parseLong(sctidFilename));
		}
		
		File sctIdFile = new File (sctidFilename);
		try {
			if (sctIdFile.canRead()) {
				IdGenerator idGen = new IdGenerator(sctIdFile, p);
				//Does the config say we have to run this forward?
				runForward(idGen);
				return idGen;
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failure while reading reading sctids from " + sctidFilename, e);
		}
		
		throw new TermServerScriptException("Unable to read sctids from " + sctidFilename);
	}

	private static boolean isDummyFile(String sctidFilename) {
        //remove the extension
		int dotIndex = sctidFilename.lastIndexOf('.');
		sctidFilename = (dotIndex == -1) ? sctidFilename : sctidFilename.substring(0, dotIndex);
		return sctidFilename.toLowerCase().endsWith("dummy");
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
	
	private IdGenerator(PartitionIdentifier p, long sequence) {
		partitionIdentifier = p;
		useValidSequence = true;
		validSequence = sequence;
	}
	
	private static void runForward (IdGenerator idGen) throws NumberFormatException, IOException {
		//Is there a config file to consider? If not, do nothing.
		File idConfigFile = new File (generatorProgressTrackerName);
		if (idConfigFile.canRead()) {
			for (String line : FileUtils.readLines(idConfigFile, StandardCharsets.UTF_8)) {
				String[] lineItems = line.split(TAB);
				if (lineItems[0].equals(idGen.partitionIdentifier.toString())) {
					idGen.runForwardCount = Integer.parseInt(lineItems[1]);
					LOGGER.warn("{} running forward by {} as per {}", idGen.partitionIdentifier, idGen.runForwardCount, generatorProgressTrackerName);
					for (int i=0; i<idGen.runForwardCount; i++) {
						idGen.availableSctIds.readLine();
					}
				}
			}
		} else {
			LOGGER.info("No 'runFoward' file (expected name {}) found for {}", generatorProgressTrackerName, idGen.partitionIdentifier);
		}
	}
	
	public String getSCTID() throws TermServerScriptException {
		if (useDummySequence) {
			idsAssigned++;
			return getDummySCTID();
		}
		
		if (useValidSequence) {
			idsAssigned++;
			return getGeneratedValidSCTID();
		}
		
		String sctId;
		try {
			sctId = availableSctIds.readLine();
		} catch (IOException e) {
			throw new IllegalStateException("Unable to recover SCTID from file " + fileName, e);
		}
		
		if (sctId == null || sctId.isEmpty()) {
			//Report switch to use dummy strategy
			useDummySequence = true;
			LOGGER.warn("Ran out of ids for partition {} at {}, switching to dummy...", partitionIdentifier, idsAssigned);
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
	
	private String getGeneratedValidSCTID() throws TermServerScriptException  {
		try {
			String sctIdBase = ++validSequence + namespace + (isExtension?"1":"0") + partitionIdentifier.ordinal();
			String checkDigit = new VerhoeffCheckDigit().calculate(sctIdBase);
			return sctIdBase + checkDigit;
		} catch (CheckDigitException e) {
			throw new TermServerScriptException ("Failed to generate valid sctid",e);
		}
	}
	
	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}	
	
	public void finish() throws FileNotFoundException {
		try {
			if (!useDummySequence && !useValidSequence) {
				availableSctIds.close();
			}
		} catch (Exception e){
			//It's OK if we don't manage to close the file, the program is ending now anyway.
		}
		//Are we the first generator to write out the final state?  Blitz the file if so, otherwise append.
		OutputStreamWriter osw; 
		String dataLine = partitionIdentifier + TAB + (runForwardCount + idsAssigned);
 		if (!IdGenerator.configFileReset) {
 			LOGGER.debug("Writing {} to {}", dataLine, generatorProgressTrackerName);
 			osw = new OutputStreamWriter(new FileOutputStream(new File(generatorProgressTrackerName), false), StandardCharsets.UTF_8);
			IdGenerator.configFileReset = true;
 		} else {
			LOGGER.debug("Appending {} to {}", dataLine, generatorProgressTrackerName);
			osw = new OutputStreamWriter(new FileOutputStream(new File(generatorProgressTrackerName), true), StandardCharsets.UTF_8);
		}
		PrintWriter pw = new PrintWriter(new BufferedWriter(osw));
		pw.write(dataLine + LINE_DELIMITER);
		pw.close();
		String ofWhich = ".";
		if (dummySequence > 100) {
			ofWhich = " of which " + (dummySequence - 100) + " were dummy.";
		}
		LOGGER.info("IdGenerator supplied {} {} sctids in namespace {}{}", idsAssigned, partitionIdentifier, namespace, ofWhich) ;
	}
	
	public void isExtension(boolean b) {
		isExtension = b;
	}
}
