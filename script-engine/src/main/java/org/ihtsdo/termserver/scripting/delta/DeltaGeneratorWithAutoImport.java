package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.util.MultiArchiveImporter;

import java.io.File;

public class DeltaGeneratorWithAutoImport extends DeltaGenerator {

	protected String taskPrefix;
	private MultiArchiveImporter importer;
	private File archive;

	protected void importArchiveToNewTask(File archive) throws TermServerScriptException {
		this.archive = archive;
		importer = new MultiArchiveImporter(this);
		boolean proceed = reviewSettingsWithUser();

		if (proceed) {
			importer.recoverProjectFromProjectName(projectName);
			importer.importArchive(this.archive, taskPrefix);
		}
	}

	private boolean reviewSettingsWithUser() throws TermServerScriptException {
		//Check if we're going to rename the file to be the task prefix
		print("Rename " + archive.getName() + " to " + taskPrefix + ".zip ? Y/N [Y]: ");
		String response = STDIN.nextLine().trim();
		if (response.isEmpty() || !response.equalsIgnoreCase("N")) {
			File oldFile = archive;
			archive = new File(archive.getParentFile(), taskPrefix + ".zip");
			if (!oldFile.renameTo(archive)) {
				throw new TermServerScriptException("Failed to rename " + oldFile + " to " + archive);
			}
		}

		print("Import onto which project? : ");
		projectName = STDIN.nextLine().trim();
		String authorDisplay = importer.getAuthors() == null ? "" : importer.getAuthors().get(0);
		print("Assign to author [" + authorDisplay + "]: ");
		importer.setAuthors(STDIN.nextLine().trim());

		print("Ready to import into a new task in " + projectName + "? Y/N [Y]: ");
		response = STDIN.nextLine().trim();
		return response.isEmpty() || !response.equalsIgnoreCase("N");
	}
}
