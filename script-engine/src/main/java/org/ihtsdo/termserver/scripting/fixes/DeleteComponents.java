package org.ihtsdo.termserver.scripting.fixes;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DeleteComponents extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(DeleteComponents.class);

	private String[] componentsToDelete = new String[] {
			"bd14e5fb-478f-4de8-9977-5d37f5575785","9001d8e1-463a-445e-95cc-2bcaf37db18e","94dc0c97-c45b-42f1-9003-993ff2353904",
			"992ec59e-ce60-4ac6-ab85-be1cbfafdbc8","ea288158-8198-4097-af02-25f4af5954de","bcb02d40-f9f8-4393-a96d-bced68729095",
			"59fd684c-544c-4fd6-a960-69b8f878c7d9","85c0787e-7746-4e6a-ad28-cc7d2d8b763f","b02c7dee-1e17-4aed-a226-6efa575d8164",
			"bd1121bf-1ff2-46ab-ab1e-dd91a51ac987","951a3276-9945-4a89-b578-57de0c0ca534","5926923a-91f7-474b-9e86-0be6b14f65be",
			"adcf6572-fe11-4049-8b18-5c016918c79e","44dff1ce-431e-4548-8e64-b08968d9e3eb","05f45084-ba3b-405d-83cb-9f7b9f14edd6",
			"ab26dadd-e426-425e-b8d8-b83fabb3de75","9e4eaa44-0aae-4c3a-8da5-8c4e6b75577e","284e98d2-47fe-473a-bd73-316d92404e78",
			"3636e8c7-94d4-4c53-8341-757e953ea17d","8c6c1455-473e-42fa-bd36-02a42440f4c4","0c2dac1c-de31-4286-b403-945947355584",
			"ccd6b6f8-e457-4032-b215-f7fe33ab7c0a","658b5485-2a0a-499c-900e-93984b1197d9","68f44366-50d5-41e5-8aad-3080dfbd610b",
			"17e23b7c-47cc-4d54-b463-abdd0dc63b5f","e8c5cc16-a923-4a78-ab11-1a681104adc1","fc0f8fa1-bad5-4ffc-b3e1-8d8f4f3a123a",
			"7a867650-c54e-4bc1-99f4-a905f2129c11","9239a886-13ff-41dd-89af-785bcc3836bf","29f0241f-30dc-4dde-8cc9-883aa0634f1d",
			"1c118fc8-7e2b-4f28-b7c8-0c48953040dc","20a30ed1-5bc6-4714-a106-937227619491","62a18088-b378-45e9-8296-49d4598b96a8",
			"1c51b3f8-ff8d-4f01-bc12-8b6c59da1b5f","433d3a25-34e2-45b0-86be-beb08a5a7580","6c95892a-bab9-43a4-9dd1-88639a1d4995",
			"328a9b96-721e-462b-8eeb-d220e7de40e8","f8f5e465-c7de-4972-a56a-7e6d10d16b7e","34b08a77-3580-4206-a5a8-2b3359d2ef1a",
			"daa29599-7910-4f15-85c2-9138580b3679","1449f983-4bb7-448b-a2d1-60988313b6b6","783977f8-2562-4602-95f7-b7f326ae109b",
			"9c0a668b-dee8-46f4-b60c-5d18e428b72b","157e61d4-7dc7-40ea-96c4-4ec5bb08a03b","1a9c15ed-20a8-4b80-9e7c-a09685a878ed"
	};

	protected DeleteComponents(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		DeleteComponents fix = new DeleteComponents(null);
		try {
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.runStandAlone = false;
			fix.worksWithConcepts = false; //Ensures doFix is called with Component
			fix.getArchiveManager().setPopulateReleasedFlag(true);
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Component c, String info) throws TermServerScriptException {
		Component fullComponent = gl.getComponent(c.getId());
		if (fullComponent == null || fullComponent instanceof RefsetMember) {
			if (fullComponent == null && c.getId().contains("-")) {
				LOGGER.warn("Suspected orphan {}, attempting refset deletion", c.getId());
			} else {
				throw new TermServerScriptException("Further work needed to delete oprhan component: " + c.getId());
			}
			deleteRefsetMember(t, c.getId());
		} else if (fullComponent instanceof Concept) {
			Concept concept = gl.getConcept(c.getId());
			deleteConcept(t, concept);
		} else if (fullComponent instanceof Description) {
			Description d = gl.getDescription(c.getId());
			deleteDescription(t, d);
		} else {
			throw new TermServerScriptException("Unable to delete component of type " + c.getClass().getSimpleName());
		}

		if (fullComponent == null) {
			report(t, (Concept)null, Severity.LOW, ReportActionType.COMPONENT_DELETED, c.getId());
		} else {
			Concept concept = gl.getComponentOwner(c.getId());
			report(t, concept, Severity.LOW, ReportActionType.COMPONENT_DELETED, c);
		}
			return CHANGE_MADE;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return Arrays.stream(componentsToDelete)
				.map(ComponentFactory::create)
				.collect(Collectors.toList());
	}
	
}
