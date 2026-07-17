package org.ihtsdo.termserver.scripting.dao;

import org.ihtsdo.otf.exception.TermServerScriptException;

import java.nio.file.Path;

/**
 * Base for DataLoaders that fetch a single named archive from S3 to a local file.
 * See AbstractS3Component for the shared S3 wiring this builds on.
 */
public abstract class AbstractDataLoader extends AbstractS3Component implements DataLoader {

	protected void downloadFromS3(Path sourcePath, Path targetPath) throws TermServerScriptException {
		copyFromS3(sourcePath, targetPath, false);
	}
}
