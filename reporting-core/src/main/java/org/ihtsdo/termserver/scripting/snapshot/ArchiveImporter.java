package org.ihtsdo.termserver.scripting.snapshot;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ArchiveImporter implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveImporter.class);

	private static boolean runAsynchronously = true;
	private static boolean skipSave = false;

	private boolean useWindowsZipEncoding = false;
	private GraphLoader gl;
	private SnapshotConfiguration config;
	private File cacheSnapshotLocation;

	ArchiveImporter(GraphLoader gl, SnapshotConfiguration config) {
		this.gl = gl;
		this.config = config;
	}

	public static void setRunAsynchronously(boolean runAsynchronously) {
		ArchiveImporter.runAsynchronously = runAsynchronously;
	}

	public static void setSkipSave(boolean skipSave) {
		ArchiveImporter.skipSave = skipSave;
	}

	public void generateSnapshot(File dependencySnapshot, File previousSnapshot, File delta, File newLocation) throws TermServerScriptException {
		cacheSnapshotLocation = newLocation;
		if (dependencySnapshot != null) {
			LOGGER.info("Loading dependency snapshot {}", dependencySnapshot);
			loadArchive(dependencySnapshot, false, "Snapshot", true);
		}

		LOGGER.info("Loading previous snapshot {}", previousSnapshot);
		loadArchive(previousSnapshot, false, "Snapshot", true);

		double sizeMb = delta.length() / (1024d * 1024d);
		String sizeMbStr = String.format("%.2f", sizeMb);
		LOGGER.info("Loading delta {} of size {}Mb", delta, sizeMbStr);
		loadArchive(delta, false, "Delta", false);
		gl.finalizeMRCM();
	}

	public void loadArchive(File archive, boolean fsnOnly, String fileType, Boolean isReleased) throws TermServerScriptException {
		try {
			boolean isDelta = (fileType.equals(DELTA));
			//Are we loading an expanded or compressed archive?
			if (archive.isDirectory()) {
				loadArchiveDirectory(archive, fsnOnly, fileType, isReleased);
			} else if (archive.getPath().endsWith(".zip")) {
				LOGGER.debug("Loading archive file: {}", archive);
				loadArchiveZip(archive, fsnOnly, fileType, isReleased);
			} else {
				throw new TermServerScriptException("Unrecognised archive : " + archive);
			}

			//Are we generating the transitive closure?
			if (fileType.equals(SNAPSHOT) && config.isPopulatePreviousTransitiveClosure()) {
				gl.populatePreviousTransitiveClosure();
			}

			if(!isDelta && gl.isPopulateOriginalModuleMap()) {
				gl.populateOriginalModuleMap();
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to extract project state from archive " + archive.getName(), e);
		} catch (IllegalArgumentException e) {
			if (!useWindowsZipEncoding) {
				LOGGER.error("Failed to extract project state from archive {} due to {}", archive.getName(), e.getMessage());
				LOGGER.error("Second attempt to load archive with Windows zip encoding enabled");
				useWindowsZipEncoding = true;
				loadArchive(archive, fsnOnly, fileType, isReleased);
			} else {
				throw e; //If we've already tried with Windows zip encoding, then we really can't load this archive
			}
		} finally {
			useWindowsZipEncoding = false; //Reset this so that we don't use it inappropriately in the future
		}
	}

	private void loadArchiveZip(File archive, boolean fsnOnly, String fileType, Boolean isReleased) throws IOException {
		Charset encoding = useWindowsZipEncoding ? Charset.forName("windows-1252") : StandardCharsets.UTF_8;
		ZipInputStream zis = new ZipInputStream(new FileInputStream(archive), encoding);
		ZipEntry ze = zis.getNextEntry();
		try {
			while (ze != null) {
				if (!ze.isDirectory()) {
					Path path = Paths.get(ze.getName());
					loadFile(path, zis, fileType, fsnOnly, isReleased);
				}
				ze = zis.getNextEntry();
			}
		} finally {
			try{
				zis.closeEntry();
				zis.close();
			} catch (Exception e){
				//Well, we tried.
			}
		}
	}

	private void loadArchiveDirectory(File dir, boolean fsnOnly, String fileType, Boolean isReleased) throws IOException {
		try (Stream<Path> paths = Files.walk(dir.toPath())) {
			paths.filter(Files::isRegularFile)
					.forEach( path ->  {
						try {
							InputStream is = toInputStream(path);
							loadFile(path, is , fileType, fsnOnly, isReleased);
							is.close();
						} catch (Exception e) {
							throw new IllegalStateException("Failed to load " + path + " due to " + e.getMessage(),e);
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

	private void loadFile(Path path, InputStream is, String fileType, boolean fsnOnly, Boolean isReleased)  {
		try {
			String fileName = path.getFileName().toString();
			//Skip zip file artifacts
			if (fileName.contains("._")) {
				return;
			}

			if (fileName.contains(fileType)
					&& !loadContentFile(is, fileName, fileType, isReleased, fsnOnly)) {
				loadReferenceSetFile(is, fileName, fileType, isReleased, fsnOnly);
			}
		} catch (TermServerScriptException | IOException e) {
			throw new IllegalStateException("Unable to load " + path + " due to " + e.getMessage(), e);
		}
	}

	private void loadReferenceSetFile(InputStream is, String fileName, String fileType, Boolean isReleased,
	                                  boolean fsnOnly) throws TermServerScriptException, IOException {
		boolean loadTheReferenceSet = false;
		if (loadMRCMFile(is, fileName, fileType, isReleased)) {
			return;
		}

		if (fileName.contains("der2_cRefset_ConceptInactivationIndicatorReferenceSet" )) {
			LOGGER.info("Loading Concept Inactivation Indicator {} file: {}", fileType, fileName);
			gl.loadInactivationIndicatorFile(is, isReleased);
		} else if (fileName.contains("der2_cRefset_DescriptionInactivationIndicatorReferenceSet" )) {
			LOGGER.info("Loading Description Inactivation Indicator {} file: {}", fileType, fileName);
			gl.loadInactivationIndicatorFile(is, isReleased);
		} else if (fileName.contains("der2_cRefset_AttributeValue" )) {
			LOGGER.info("Loading Concept/Description Inactivation Indicators {} file: {}", fileType, fileName);
			gl.loadInactivationIndicatorFile(is, isReleased);
		} else if (fileName.contains("Association" ) || fileName.contains("AssociationReferenceSet" )) {
			LOGGER.info("Loading Historical Association File: {} file: {}", fileType, fileName);
			gl.loadHistoricalAssociationFile(is, isReleased);
		} else if (fileName.contains("ComponentAnnotationStringValue")) {
			LOGGER.info("Loading ComponentAnnotationStringValue File: {} file: {}", fileType, fileName);
			gl.loadComponentAnnotationFile(is, isReleased);
		}  else if (fileName.contains("ssRefset_ModuleDependency")) {
			LOGGER.info("Loading Module Dependency File: {} file: {}", fileType, fileName);
			gl.loadModuleDependencyFile(is, isReleased);
		} else if (config.isLoadOtherReferenceSets() && fileName.contains("Refset")) {
			loadTheReferenceSet = true;
		}

		//If we're loading all terms, load the language refset as well
		if (!fsnOnly && (fileName.contains("English" ) || fileName.contains("Language"))) {
			LOGGER.info("Loading {} Language Reference Set File - {}", fileType, fileName);
			gl.loadLanguageFile(is, isReleased);
		} else if (loadTheReferenceSet) {
			gl.loadReferenceSets(is, fileName, isReleased);
		}
	}

	private boolean loadMRCMFile(InputStream is, String fileName, String fileType, Boolean isReleased) throws TermServerScriptException, IOException {
		if (fileName.contains("MRCMModuleScope")) {
			LOGGER.info("Loading MRCM Module Scope File: {} file: {}", fileType, fileName);
			gl.loadMRCMModuleScopeFile(is, isReleased);
		} else if (fileName.contains("MRCMDomain")) {
			LOGGER.info("Loading MRCM Domain File: {} file: {}", fileType, fileName);
			gl.loadMRCMDomainFile(is, isReleased);
		} else if (fileName.contains("MRCMAttributeRange")) {
			LOGGER.info("Loading MRCM AttributeRange File: {} file: {}", fileType, fileName);
			gl.loadMRCMAttributeRangeFile(is, isReleased);
		} else if (fileName.contains("MRCMAttributeDomain")) {
			LOGGER.info("Loading MRCM AttributeDomain File: {} file: {}", fileType, fileName);
			gl.loadMRCMAttributeDomainFile(is, isReleased);
		} else {
			return false;
		}
		return true;
	}

	private boolean loadContentFile(InputStream is, String fileName, String fileType, Boolean isReleased, boolean fsnOnly) throws TermServerScriptException, IOException {
		if (fileName.contains("sct2_Concept_" )) {
			LOGGER.info("Loading Concept {} file: {}", fileType, fileName);
			gl.loadConceptFile(is, isReleased);
		} else if (fileName.contains("Identifier" )) {
			LOGGER.info("Loading Alternate Identifier {} file: {}", fileType, fileName);
			gl.loadAlternateIdentifierFile(is, isReleased);
		} else if (fileName.contains("sct2_Relationship_" )) {
			LOGGER.info("Loading Relationship {} file: {}", fileType, fileName);
			gl.loadRelationships(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, is, true, isReleased);
			if (config.isPopulateHierarchyDepth()) {
				LOGGER.info("Calculating concept depth...");
				gl.populateHierarchyDepth(ROOT_CONCEPT, 0);
			}
		} else if (fileName.contains("sct2_StatedRelationship_" )) {
			LOGGER.info("Skipping StatedRelationship {} file: {}", fileType, fileName);
		} else if (fileName.contains("sct2_RelationshipConcrete" )) {
			LOGGER.info("Loading Concrete Relationship {} file: {}", fileType, fileName);
			gl.loadRelationships(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, is, true, isReleased);
		} else if (fileName.contains("sct2_sRefset_OWLExpression" ) ||
				fileName.contains("sct2_sRefset_OWLAxiom" )) {
			LOGGER.info("Loading Axiom {} file: {}", fileType, fileName);
			gl.loadAxioms(is, isReleased);
		} else if (fileName.contains("sct2_Description_" )) {
			LOGGER.info("Loading Description {} file: {}", fileType, fileName);
			int count = gl.loadDescriptionFile(is, fsnOnly, isReleased);
			LOGGER.info("Loaded {} descriptions.", count);
		} else if (fileName.contains("sct2_TextDefinition_" )) {
			LOGGER.info("Loading Text Definition {} file: {}", fileType, fileName);
			gl.loadDescriptionFile(is, fsnOnly, isReleased);
		} else {
			return false;
		}
		return true;
	}

	public void writeSnapshotToCache(TermServerScript ts, String defaultModuleId) throws TermServerScriptException {
		//Writing to disk can be done asynchronously and complete at any time.  We have the in-memory copy to work with.
		//The disk copy will save time when we run again for the same project

		//Ah, well that's not completely true because sometimes we want to be really careful we've not modified the data
		//in some process.
		if (!skipSave) {
			ArchiveWriter as = new ArchiveWriter(ts, cacheSnapshotLocation);
			as.init(defaultModuleId);

			if (runAsynchronously) {
				new Thread(as).start();
			} else {
				as.run();
			}
		}
	}
}
