package org.ihtsdo.termserver.scripting.snapshot;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.TermServerLocation;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.client.TermServerClient;

import java.io.File;
import java.io.IOException;

public class TBCHelper {

	private TermServerScript ts;

	public TBCHelper(TermServerScript ts) {
		this.ts = ts;
	}

	public File getExportedDelta(TermServerLocation location) throws TermServerScriptException {
		return getExportedDelta(location, false);
	}

	public File getExportedDelta(TermServerLocation location, boolean unpromotedChangesOnly) throws TermServerScriptException {
		try {
			File delta = File.createTempFile("delta_export-", ".zip");
			delta.deleteOnExit();
			if (location.getBranchPath() == null) {
				throw new TermServerScriptException("Cannot generate delta for null branch path: " + location);
			}
			ts.getTSClient().export(location.getBranchPath(), null, TermServerClient.ExportType.UNPUBLISHED, TermServerClient.ExtractType.DELTA, delta, unpromotedChangesOnly);
			return delta;
		} catch (TermServerScriptException | IOException e) {
			throw new TermServerScriptException("Failed to generate delta from " + location, e);
		}
	}

	public File getPublishedArchive(SnapshotConfiguration config) throws TermServerScriptException {
		File archive = new File("releases/" + config.getSource());
		if (!archive.canRead()) {
			throw new TermServerScriptException("Unable to read " + archive);
		}
		return archive;
	}
}
