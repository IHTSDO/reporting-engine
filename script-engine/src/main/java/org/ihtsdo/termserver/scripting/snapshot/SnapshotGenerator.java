package org.ihtsdo.termserver.scripting.snapshot;

import java.io.*;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapshotGenerator implements RF2Constants{

	private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotGenerator.class);

	protected static boolean runAsynchronously = true;
	protected static boolean skipSave = false;

	protected boolean newIdsRequired = true;
	protected String moduleId = SCTID_CORE_MODULE;
	private TermServerScript ts;
	private File outputDirFile;

	public SnapshotGenerator (TermServerScript ts) {
		this.ts = ts;
	}

	public static void setRunAsynchronously(boolean runAsynchronously) {
		SnapshotGenerator.runAsynchronously = runAsynchronously;
	}

	public static void setSkipSave(boolean skipSave) {
		SnapshotGenerator.skipSave = skipSave;
	}

	private void init(File outputDirFile) {
		//Make sure the Graph Loader is clean
		LOGGER.info("Snapshot Generator ensuring Graph Loader is clean");
		GraphLoader.getGraphLoader().reset();
		this.outputDirFile = outputDirFile;
	}
	
	public void generateSnapshot(File dependencySnapshot, File previousSnapshot, File delta, File newLocation) throws TermServerScriptException {
		ts.setQuiet(true);
		init(newLocation);
		if (dependencySnapshot != null) {
			LOGGER.info("Loading dependency snapshot {}", dependencySnapshot);
			ts.getArchiveManager().loadArchive(dependencySnapshot, false, "Snapshot", true);
		}
		
		LOGGER.info("Loading previous snapshot {}", previousSnapshot);
		ts.getArchiveManager().loadArchive(previousSnapshot, false, "Snapshot", true);
		
		LOGGER.info("Loading delta {}", delta);
		ts.getArchiveManager().loadArchive(delta, false, "Delta", false);
		ts.getGraphLoader().finalizeMRCM();
		ts.setQuiet(false);
	}

	public void writeSnapshotToCache(TermServerScript ts, String defaultModuleId) throws TermServerScriptException {
		//Writing to disk can be done asynchronously and complete at any time.  We have the in-memory copy to work with.
		//The disk copy will save time when we run again for the same project

		//Ah, well that's not completely true because sometimes we want to be really careful we've not modified the data
		//in some process.
		if (!skipSave) {
			ArchiveWriter as = new ArchiveWriter(ts, outputDirFile);
			as.init(defaultModuleId);

			if (runAsynchronously) {
				new Thread(as).start();
			} else {
				as.run();
			}
		}
	}

}
