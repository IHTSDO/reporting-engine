package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class RollbackBranch extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(RollbackBranch.class);

	public static void main(String[] args) throws TermServerScriptException {
		RollbackBranch importer = new RollbackBranch();
		try {
			ReportSheetManager.setTargetFolderId("13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe"); //Technical Specialist Kung Foo
			importer.init(args);
			importer.postInit(new String[] {"Actions"}, new String[] {"Branch, HeadTimestamp, BaseTimestamp, Status, Action"});
			importer.rollbackBranch();
		} finally {
			importer.finish();
		}
	}

	private void rollbackBranch() throws TermServerScriptException {
		String branchPath = getProject().getBranchPath();
		String msg = "Rolling back " + branchPath;
		LOGGER.info(msg);
		report(PRIMARY_REPORT, msg);
		boolean userQuit = false;
		boolean rollBackToBaseline = false;
		while (!userQuit) {
			Branch b = tsClient.getBranch(branchPath);
			LocalDateTime head = LocalDateTime.ofInstant(Instant.ofEpochMilli(b.getHeadTimestamp()), ZoneOffset.UTC);
			LocalDateTime base = LocalDateTime.ofInstant(Instant.ofEpochMilli(b.getBaseTimestamp()), ZoneOffset.UTC);
			println( "\n" + b.getName() + " Head: " + head + " Base: " + base + " state: " + b.getState());
			msg = "Rolled-Back";

			if (b.getHeadTimestamp() <= b.getBaseTimestamp() || b.getState().equals("BEHIND")) {
				userQuit = true;
				msg = "Final state - base timestamp reached / branch is behind parent";
			} else if (!rollBackToBaseline) {
				print("Rollback (R), Rollback to Baseline (B) or Quit (Q): ");
				String choice = STDIN.nextLine().trim().toUpperCase();
				if (choice.equals("B")) {
					rollBackToBaseline = true;
				} else if (choice.equals("Q")) {
					userQuit = true;
					msg = "Final state";
				} else if (choice.equals("R")) {
					tsClient.adminRollbackCommit(b);
				}
			}

			if (!userQuit && rollBackToBaseline) {
				tsClient.adminRollbackCommit(b);
			}
			report(PRIMARY_REPORT, "", head, base, b.getState(), msg);
		}
	}


}
