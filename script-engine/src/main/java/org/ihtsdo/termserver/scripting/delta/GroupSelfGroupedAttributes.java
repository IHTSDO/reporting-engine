package org.ihtsdo.termserver.scripting.delta;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.AssociationEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GroupSelfGroupedAttributes extends DeltaGenerator implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(GroupSelfGroupedAttributes.class);

	private List<Concept> hierarchies = new ArrayList<>();

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		GroupSelfGroupedAttributes delta = new GroupSelfGroupedAttributes();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.newIdsRequired = false; // We'll only be modifying existing components
			delta.init(args);
			delta.loadProjectSnapshot();
			delta.postInit();
			delta.process();
			delta.createOutputArchive();
		} finally {
			delta.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		hierarchies.add(OBSERVABLE_ENTITY);
		hierarchies.add(gl.getConcept("386053000 |Evaluation procedure|"));
		super.postInit();
	}

	private void process() throws ValidationFailure, TermServerScriptException, IOException {
		for (Concept hierarchy : hierarchies) {
			for (Concept c :  SnomedUtils.sort(hierarchy.getDescendants(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP))) {
				if (inScope(c)) {
					groupSelfGroupedAttributes(c);
				}
			}
		}
	}

	private void groupSelfGroupedAttributes(Concept c) throws TermServerScriptException {
		String before = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		boolean changesMade = false;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getType().equals(IS_A) || r.getType().equals(PART_OF)) {
				continue;
			}
			if (r.getGroupId() > 1) {
				//report(c, Severity.LOW, ReportActionType.RELATIONSHIP_GROUP_REMOVED, r.getGroupId() + "--> 1", r);
				r.setGroupId(1);
				r.setDirty();
				changesMade = true;
			}
		}
		if (changesMade) {
			String after = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
			report(c, Severity.LOW, ReportActionType.INFO, before, after);
		}
	}

}
