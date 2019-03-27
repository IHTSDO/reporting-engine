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
import org.ihtsdo.termserver.scripting.client.TermServerClient.*;
import org.ihtsdo.termserver.scripting.dao.ReportManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.snapshot.SnapshotGenerator;
import org.ihtsdo.termserver.scripting.util.StringUtils;


public class ArchiveManager implements RF2Constants {
	
	static ArchiveManager singleton;
	
	protected String dataStoreRoot = "";
	protected GraphLoader gl;
	protected TermServerScript ts;
	public boolean allowStaleData = false;
	public boolean populateHierarchyDepth = true;  //Term contains X needs this
	private Project currentlyHeldInMemory;
	
	public static ArchiveManager getArchiveManager(TermServerScript ts) {
		if (singleton == null) {
			singleton = new ArchiveManager();
		}
		singleton.ts = ts;
		singleton.gl = ts.getGraphLoader();
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
		String server = "uknown";
		try {
			debug ("Checking TS branch metadata: " + branchPath);
			server = ts.getTSClient().getUrl();
			Branch branch = ts.getTSClient().getBranch(branchPath);
			//If empty, recover parent
			if (branch.getMetadata() == null || branch.getMetadata().getPreviousRelease() == null) {
				Branch parent = loadBranch(new Project().withBranchPath("MAIN"));
				branch.setMetadata(parent.getMetadata());
			}
			return branch;
		} catch (Exception e) {
			if (e.getMessage().contains("[404] Not Found")) {
				debug ("Unable to find branch " + branchPath);
				return null;
			}
			throw new TermServerScriptException("Failed to recover " + project + " from TS (" + server + ") due to " + e.getMessage(),e);
		}
	}

	public void loadProjectSnapshot(boolean fsnOnly) throws TermServerClientException, TermServerScriptException, InterruptedException, IOException {
		//Look for an expanded directory by preference
		File snapshot = getSnapshotPath();
		if (!snapshot.exists()) {
			//Otherwise, do we have a zip file to play with?
			snapshot = new File (snapshot.getPath() + ".zip");
		}
		
		//If we're loading a particular release, it will be stale
		if (StringUtils.isNumeric(ts.getProject().getKey())) {
			allowStaleData = true;
		}
		
		Branch branch = null;
		//Are we lacking data, or is our data out of date?  
		boolean isStale = false;
		if (snapshot.exists() && !allowStaleData) {
			branch = loadBranch(ts.getProject());
			Date branchHeadTime = new Date(branch.getHeadTimestamp());
			BasicFileAttributes attr = java.nio.file.Files.readAttributes(snapshot.toPath(), BasicFileAttributes.class);
			Date snapshotCreation = new Date(attr.creationTime().toMillis());
			isStale = branchHeadTime.after(snapshotCreation);
			if (isStale) {
				TermServerScript.warn(ts.getProject() + " snapshot held locally is stale.  Requesting delta to rebuild...");
			}
		}
		
		if (!snapshot.exists() || ( isStale && !allowStaleData)) {
			gl.reset();
			snapshot = generateSnapshot (ts.getProject(), branch);
			//We don't need to load the snapshot if we've just generated it
		} else {
			//We might already have this project in memory
			if (currentlyHeldInMemory != null && currentlyHeldInMemory.equals(ts.getProject())) {
				info (ts.getProject() + " already held in memory, no need to reload.  Resetting any issues held against components...");
				gl.makeReady();
			} else {
				if (currentlyHeldInMemory != null) {
					//Make sure the Graph Loader is clean if we're loading a different project
					info (currentlyHeldInMemory.getKey() + " being wiped to make room for " + ts.getProject());
					gl.reset();
					System.gc();
				}
				info ("Loading snapshot archive contents into memory...");
				try {
					loadArchive(snapshot, fsnOnly, "Snapshot");
				} catch (Exception e) {
					TermServerScript.error ("Non-viable snapshot encountered (Exception: " + e.getMessage()  +").  Deleting " + snapshot + "...", e);
					try {
						if (snapshot.isFile()) {
							snapshot.delete();
						} else if (snapshot.isDirectory()) {
							FileUtils.deleteDirectory(snapshot);
						} else {
							throw new TermServerScriptException (snapshot + " is neither file nor directory.");
						}
					} catch (Exception e2) {
						TermServerScript.warn("Failed to delete snapshot " + snapshot + " due to " + e2);
					}
					throw new TermServerScriptException("Non-viable snapshot detected",e);
				}
			}
		}
		currentlyHeldInMemory = ts.getProject();
	}
	
	private File generateSnapshot(Project project, Branch branch) throws TermServerScriptException, IOException, TermServerClientException {
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
		TermServerScript.info("Building snapshot release based on previous: " + previous);
		//Now we need a recent delta to add to it
		File delta = File.createTempFile("delta_export-", ".zip");
		delta.deleteOnExit();
		ts.getTSClient().export(project.getBranchPath(), null, ExportType.UNPUBLISHED, ExtractType.DELTA, delta);
		
		SnapshotGenerator snapshotGenerator = new SnapshotGenerator();
		
		//Lets have a separate output report for generation of the snapshot - local only
		ReportManager rm = ReportManager.create(ts);
		rm.setFileOnly();
		rm.initialiseReportFiles(new String[] {ReportManager.STANDARD_HEADERS});
		snapshotGenerator.setReportManager(rm);
		snapshotGenerator.setProject(ts.getProject());
		snapshotGenerator.leaveArchiveUncompressed();
		snapshotGenerator.setOutputDirName(snapshot.getPath());
		snapshot = snapshotGenerator.generateSnapshot(previous, delta, snapshot);
		return snapshot;
	}

	private File getSnapshotPath() {
		//Do we have a release effective time as a project?  Or a branch release
		String releaseBranch = detectReleaseBranch(ts.getProject().getKey());
		if (releaseBranch != null) {
			return new File (dataStoreRoot + "releases/" + releaseBranch + ".zip");
		} else if (StringUtils.isNumeric(ts.getProject().getKey())) {
			return new File (dataStoreRoot + "releases/" + ts.getProject() + ".zip");
		} else {
			return new File (dataStoreRoot + "snapshots/" + ts.getProject() + "_" + ts.getEnv());
		}
	}

	public String detectReleaseBranch(String projectKey) {
		String releaseBranch = projectKey.replace("MAIN/", "").replace("-", "");
		return StringUtils.isNumeric(releaseBranch) ? releaseBranch : null;
	}

	protected void loadArchive(File archive, boolean fsnOnly, String fileType) throws TermServerScriptException, TermServerClientException {
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
				//a malformed, or classification style archive.
				List<Description> first100Descriptions = gl.getAllConcepts()
						.stream()
						.limit(100)
						.flatMap(c -> c.getDescriptions().stream())
						.collect(Collectors.toList());
				if (first100Descriptions.size() < 100) {
					throw new TermServerScriptException("Failed to find sufficient number of descriptions - classification archive used? Deleting snapshot, please retry.");
				}
			}
				
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to extract project state from archive " + archive.getName(), e);
		}
	}

	private void loadArchiveZip(File archive, boolean fsnOnly, String fileType, boolean isDelta) throws IOException, TermServerScriptException, TermServerClientException {
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
			} else if (fileName.contains("sct2_StatedRelationship_" + fileType )) {
				info("Loading StatedRelationship " + fileType + " file.");
				gl.loadRelationships(CharacteristicType.STATED_RELATIONSHIP, is, true, isDelta);
			} else if (fileName.contains("sct2_sRefset_OWLExpression" + fileType ) ||
					   fileName.contains("sct2_sRefset_OWLAxiom" + fileType )) {
				info("Loading Axiom " + fileType + " refset file.");
				gl.loadAxioms(is, isDelta);
			} else if (fileName.contains("sct2_Description_" + fileType )) {
				info("Loading Description " + fileType + " file.");
				gl.loadDescriptionFile(is, fsnOnly);
			} else if (fileName.contains("sct2_TextDefinition_" + fileType )) {
				info("Loading Text Definition " + fileType + " file.");
				gl.loadDescriptionFile(is, fsnOnly);
			} else if (fileName.contains("der2_cRefset_ConceptInactivationIndicatorReferenceSet" + fileType )) {
				info("Loading Concept Inactivation Indicator " + fileType + " file.");
				gl.loadInactivationIndicatorFile(is);
			} else if (fileName.contains("der2_cRefset_DescriptionInactivationIndicatorReferenceSet" + fileType )) {
				info("Loading Description Inactivation Indicator " + fileType + " file.");
				gl.loadInactivationIndicatorFile(is);
			} else if (fileName.contains("der2_cRefset_AttributeValue" + fileType )) {
				info("Loading Concept/Description Inactivation Indicators " + fileType + " file.");
				gl.loadInactivationIndicatorFile(is);
			} else if (fileName.contains("Association" + fileType ) || fileName.contains("AssociationReferenceSet" + fileType )) {
				info("Loading Historical Association File: " + fileName);
				gl.loadHistoricalAssociationFile(is);
			}
			//If we're loading all terms, load the language refset as well
			if (!fsnOnly && (fileName.contains("English" + fileType ) || fileName.contains("Language" + fileType))) {
				info("Loading Language Reference Set File - " + fileName);
				gl.loadLanguageFile(is);
			}
		} catch (TermServerScriptException | IOException | TermServerClientException e) {
			throw new IllegalStateException("Unable to load " + path + " due to " + e.getMessage(), e);
		}
	}
}
