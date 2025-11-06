package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.util.MultiArchiveImporter;

import java.io.File;

public class DeltaGeneratorWithAutoImport extends DeltaGenerator {

	protected String taskPrefix;
	private MultiArchiveImporter importer;
	private File archive;

	protected void importArchiveToNewTask(File archive) throws TermServerScriptException {
		this.archive = archive;
		importer = new MultiArchiveImporter(this);
		importer.setTaskPrefix(taskPrefix);
		boolean proceed = reviewSettingsWithUser();

		if (proceed) {
			importer.recoverProjectFromProjectName(projectName);
			importer.importArchive(this.archive);
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
		importer.recoverProjectFromProjectName(projectName);

		//Do we want to import onto an existing task?
		print("Import onto an existing task? Y/N [N]: ");
		response = STDIN.nextLine().trim();
		if (response.equalsIgnoreCase("Y")) {
			print ("Please enter the task ID to import onto: ");
			response = STDIN.nextLine().trim();
			if (!StringUtils.isEmpty(response)) {
				importer.setLastTaskCreated(STDIN.nextLine().trim());
				importer.setMode(MultiArchiveImporter.MODE.ALL_ARCHIVES_IN_ONE_TASK);
			}
		}

		String authorDisplay = importer.getAuthors() == null ? "" : importer.getAuthors().get(0);
		print("Assign to author [" + authorDisplay + "]: ");
		importer.setAuthors(STDIN.nextLine().trim());

		print("Ready to import into a new task in " + projectName + "? Y/N [Y]: ");
		response = STDIN.nextLine().trim();
		return response.isEmpty() || !response.equalsIgnoreCase("N");
	}
}
