package org.ihtsdo.termserver.scripting;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.*;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.*;
import org.ihtsdo.termserver.scripting.dao.ReportManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.snapshot.SnapshotGenerator;
import org.ihtsdo.termserver.scripting.util.StringUtils;


public class ArchiveManager implements RF2Constants {
	
	protected String dataStoreRoot = "";
	private Project project;
	private String env;
	protected GraphLoader gl;
	private SnowOwlClient tsClient;
	public boolean allowStaleData = false;
	public boolean populateHierarchyDepth = false;
	static ArchiveManager singleton;
	private Project currentlyHeldInMemory;
	
	public static ArchiveManager getArchiveManager(TermServerScript ts) {
		if (singleton == null) {
			singleton = new ArchiveManager();
		}
		singleton.project = ts.getProject();
		singleton.env = ts.getEnv();
		singleton.gl = ts.getGraphLoader();
		singleton.tsClient = ts.getTSClient();
		return singleton;
	}
	
	private ArchiveManager () {
		//Only access via singleton above
	}
	
	protected void info(String msg) {
		TermServerScript.info(msg);
	}
	
	protected void debug(String msg) {
		TermServerScript.debug(msg);
	}
	
	public static void print (Object msg) {
		System.out.print (msg.toString());
	}
	
	protected Branch loadBranch(Project project) throws TermServerScriptException {
		String branchPath = project.getBranchPath();
		try {
			debug ("Checking TS branch metadata: " + branchPath);
			Branch branch = tsClient.getBranch(branchPath);
			//TODO Merge metadata from parent branches recursively, but for now, if empty, recover parent
			if (branch.getMetadata().getPreviousRelease() == null) {
				Branch parent = loadBranch(new Project().withBranchPath("MAIN"));
				branch.setMetadata(parent.getMetadata());
			}
			return branch;
		} catch (Exception e) {
			if (e.getMessage().contains("[404] Not Found")) {
				debug ("Unable to find branch " + branchPath);
				return null;
			}
			throw new TermServerScriptException("Failed to recover " + project + " from TS due to " + e.getMessage(),e);
		}
	}

	public void loadProjectSnapshot(boolean fsnOnly) throws SnowOwlClientException, TermServerScriptException, InterruptedException, IOException {
		//Look for an expanded directory by preference
		File snapshot = getSnapshotPath();
		if (!snapshot.exists()) {
			//Otherwise, do we have a zip file to play with?
			snapshot = new File (snapshot.getPath() + ".zip");
		}
		
		//If we're loading a particular release, it will be stale
		if (StringUtils.isNumeric(project.getKey())) {
			allowStaleData = true;
		}
		
		Branch branch = null;
		//Are we lacking data, or is our data out of date?  
		boolean isStale = false;
		if (snapshot.exists() && !allowStaleData) {
			branch = loadBranch(project);
			Date branchHeadTime = new Date(branch.getHeadTimestamp());
			BasicFileAttributes attr = java.nio.file.Files.readAttributes(snapshot.toPath(), BasicFileAttributes.class);
			Date snapshotCreation = new Date(attr.creationTime().toMillis());
			isStale = branchHeadTime.after(snapshotCreation);
			if (isStale) {
				TermServerScript.warn(project + " snapshot held locally is stale.  Requesting delta to rebuild...");
			}
		}
		
		if (!snapshot.exists() || ( isStale && !allowStaleData)) {
			snapshot = generateSnapshot (project, branch);
			//We don't need to load the snapshot if we've just generated it
		} else {
			//We might already have this project in memory
			if (currentlyHeldInMemory != null && currentlyHeldInMemory.equals(this.project)) {
				info (project + " already held in memory, no need to reload");
			} else {
				if (currentlyHeldInMemory != null) {
					//Make sure the Graph Loader is clean
					gl.reset();
					System.gc();
				}
				info ("Loading snapshot archive contents into memory...");
				loadArchive(snapshot, fsnOnly, "Snapshot");
			}
		}
		currentlyHeldInMemory = project;
	}
	
	private File generateSnapshot(Project project, Branch branch) throws TermServerScriptException, IOException, SnowOwlClientException {
		//We need to know the previous release to base our snapshot on
		if (branch == null) {
			branch = loadBranch(project);
		}
		
		File snapshot = getSnapshotPath();
		//Delete the current snapshot if it exists - will be stale
		if (snapshot.isDirectory()) {
			FileUtils.deleteDirectory(snapshot);
		} else {
			java.nio.file.Files.deleteIfExists(snapshot.toPath());
		}
	
		File previous = new File (dataStoreRoot + "releases/"  + branch.getMetadata().getPreviousRelease() + ".zip");
		if (!previous.exists()) {
			throw new TermServerScriptException("Previous release not available for snapshot creation: " + previous);
		}
		
		//Now we need a recent delta to add to it
		File delta = File.createTempFile("delta_export-", ".zip");
		delta.deleteOnExit();
		tsClient.export(project.getBranchPath(), null, ExportType.UNPUBLISHED, ExtractType.DELTA, delta);
		
		SnapshotGenerator snapshotGenerator = new SnapshotGenerator();
		//Lets have a separate output report for generation of the snapshot - local only
		ReportManager rm = ReportManager.create(env, "SnapshotGeneration_" + project.getKey());
		rm.setFileOnly();
		rm.initialiseReportFiles(new String[] {ReportManager.STANDARD_HEADERS});
		snapshotGenerator.setReportManager(rm);
		snapshotGenerator.leaveArchiveUncompressed();
		snapshotGenerator.setOutputDirName(snapshot.getPath());
		snapshot = snapshotGenerator.generateSnapshot(previous, delta, snapshot);
		return snapshot;
	}

	private File getSnapshotPath() {
		//Do we have a release effective time as a project?
		if (StringUtils.isNumeric(project.getKey())) {
			return new File (dataStoreRoot + "releases/" + project + ".zip");
		} else {
			return new File (dataStoreRoot + "snapshots/" + project + "_" + env);
		}
	}

	protected void loadArchive(File archive, boolean fsnOnly, String fileType) throws TermServerScriptException, SnowOwlClientException {
		try {
			boolean isDelta = (fileType.equals(DELTA));
			//Are we loading an expanded or compressed archive?
			if (archive.isDirectory()) {
				loadArchiveDirectory(archive, fsnOnly, fileType, isDelta);
			} else if (archive.getPath().endsWith(".zip")) {
				loadArchiveZip(archive, fsnOnly, fileType, isDelta);
			} else {
				throw new TermServerScriptException("Unrecognised archive : " + archive);
			}
			
			if (!fsnOnly) {  
				//Check that we've got some descriptions to be sure we've not been given
				//a classification style archive.
				List<Description> first50Descriptions = gl.getAllConcepts()
						.stream()
						.limit(50)
						.flatMap(c -> c.getDescriptions().stream())
						.collect(Collectors.toList());
				if (first50Descriptions.size() < 20) {
					throw new TermServerScriptException("Failed to find sufficient number of descriptions - classification archive used?");
				}
			}
				
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to extract project state from archive " + archive.getName(), e);
		}
	}

	private void loadArchiveZip(File archive, boolean fsnOnly, String fileType, boolean isDelta) throws IOException, TermServerScriptException, SnowOwlClientException {
		ZipInputStream zis = new ZipInputStream(new FileInputStream(archive));
		ZipEntry ze = zis.getNextEntry();
		try {
			while (ze != null) {
				if (!ze.isDirectory()) {
					Path path = Paths.get(ze.getName());
					loadFile(path, zis, fileType, isDelta, fsnOnly);
				}
				ze = zis.getNextEntry();
			}
		}  finally {
			try{
				zis.closeEntry();
				zis.close();
			} catch (Exception e){} //Well, we tried.
		}
	}
	
	private void loadArchiveDirectory(File dir, boolean fsnOnly, String fileType, boolean isDelta) throws IOException {
		try (Stream<Path> paths = Files.walk(dir.toPath())) {
			paths.filter(Files::isRegularFile)
			.forEach( path ->  {
				try {
					InputStream is =  toInputStream(path);
					loadFile(path, is , fileType, isDelta, fsnOnly);
					is.close();
				} catch (Exception e) {
					throw new RuntimeException ("Faied to load " + path + " due to " + e.getMessage());
				}
			});
		} 
	}
	
	private InputStream toInputStream(Path path) {
		InputStream is;
		try {
			is = new BufferedInputStream(Files.newInputStream(path));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to load " + path, e);
		}
		return is;
	}

	private void loadFile(Path path, InputStream is, String fileType, boolean isDelta, boolean fsnOnly)  {
		try {
			String fileName = path.getFileName().toString();
			if (fileName.contains("sct2_Concept_" + fileType )) {
				info("Loading Concept " + fileType + " file.");
				gl.loadConceptFile(is);
			} else if (fileName.contains("sct2_Relationship_" + fileType )) {
				info("Loading Relationship " + fileType + " file.");
				gl.loadRelationships(CharacteristicType.INFERRED_RELATIONSHIP, is, true, isDelta);
				if (populateHierarchyDepth) {
					info("Calculating concept depth...");
					gl.populateHierarchyDepth(ROOT_CONCEPT, 0);
				}
			} else if (fileName.contains("sct2_StatedRelationship_" + fileType )) {
				info("Loading StatedRelationship " + fileType + " file.");
				gl.loadRelationships(CharacteristicType.STATED_RELATIONSHIP, is, true, isDelta);
			} else if (fileName.contains("sct2_Description_" + fileType )) {
				info("Loading Description " + fileType + " file.");
				gl.loadDescriptionFile(is, fsnOnly);
			} else if (fileName.contains("sct2_TextDefinition_" + fileType )) {
				info("Loading Text Definition " + fileType + " file.");
				gl.loadDescriptionFile(is, fsnOnly);
			} else if (fileName.contains("der2_cRefset_ConceptInactivationIndicatorReferenceSet" + fileType )) {
				info("Loading Concept Inactivation Indicator " + fileType + " file.");
				gl.loadInactivationIndicatorFile(is);
			}  else if (fileName.contains("der2_cRefset_DescriptionInactivationIndicatorReferenceSet" + fileType )) {
				info("Loading Description Inactivation Indicator " + fileType + " file.");
				gl.loadInactivationIndicatorFile(is);
			} else if (fileName.contains("der2_cRefset_AttributeValue" + fileType )) {
				info("Loading Concept/Description Inactivation Indicators " + fileType + " file.");
				gl.loadInactivationIndicatorFile(is);
			}  else if (fileName.contains("Association" + fileType ) || fileName.contains("AssociationReferenceSet" + fileType )) {
				info("Loading Historical Association File: " + fileName);
				gl.loadHistoricalAssociationFile(is);
			}
			//If we're loading all terms, load the language refset as well
			if (!fsnOnly && (fileName.contains("English" + fileType ) || fileName.contains("Language" + fileType))) {
				info("Loading Language Reference Set File - " + fileName);
				gl.loadLanguageFile(is);
			}
		} catch (TermServerScriptException | IOException | SnowOwlClientException e) {
			throw new IllegalArgumentException("Unable to load " + path, e);
		}
	}
}
