package org.ihtsdo.snowowl.authoring.api.services;

import com.b2international.snowowl.api.domain.IComponentRef;
import com.b2international.snowowl.api.impl.domain.InternalStorageRef;
import com.b2international.snowowl.api.impl.task.domain.TaskInput;
import com.b2international.snowowl.api.task.domain.ITask;
import com.b2international.snowowl.api.task.exception.TaskNotFoundException;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.datastore.index.IndexQueryBuilder;
import com.b2international.snowowl.datastore.index.IndexUtils;
import com.b2international.snowowl.snomed.api.domain.ISnomedConcept;
import com.b2international.snowowl.snomed.api.impl.SnomedConceptServiceImpl;
import com.b2international.snowowl.snomed.api.impl.SnomedTaskServiceImpl;
import com.b2international.snowowl.snomed.api.rest.domain.ChangeRequest;
import com.b2international.snowowl.snomed.api.rest.domain.SnomedConceptRestInput;
import com.b2international.snowowl.snomed.datastore.SnomedConceptIndexEntry;
import com.b2international.snowowl.snomed.datastore.browser.SnomedIndexBrowserConstants;
import com.b2international.snowowl.snomed.datastore.index.SnomedConceptIndexQueryAdapter;
import com.b2international.snowowl.snomed.datastore.index.SnomedIndexService;
import org.apache.lucene.util.BytesRef;
import org.ihtsdo.snowowl.authoring.api.model.Template;
import org.ihtsdo.snowowl.authoring.api.model.lexical.LexicalModel;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModel;
import org.ihtsdo.snowowl.authoring.api.model.work.WorkingConcept;
import org.ihtsdo.snowowl.authoring.api.model.work.WorkingContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContentServiceImpl implements ContentService {

	public static final String MAIN_BRANCH = "MAIN";
	public static final String SNOWOWL_USER_ID = "snowowl";
	public static final String SNOMEDCT_CODE_SYSTEM = "SNOMEDCT";

	private final SnomedIndexService snomedIndexService;

	@Autowired
	private SnomedConceptServiceImpl snomedConceptService;

	@Autowired
	private SnomedTaskServiceImpl taskService;

	@Autowired
	private LogicalModelService logicalModelService;

	@Autowired
	private LexicalModelService lexicalModelService;

	@Autowired
	private ContentMapper contentMapper;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public ContentServiceImpl() {
		snomedIndexService = ApplicationContext.getServiceForClass(SnomedIndexService.class);
	}

	@Override
	public Set<String> getDescendantIds(final IComponentRef ref) {
		InternalStorageRef internalRef = (InternalStorageRef) ref;
		internalRef.checkStorageExists();

		IBranchPath branchPath = internalRef.getBranchPath();
		IndexQueryAdapter indexQueryAdapter = new IndexQueryAdapter(ref.getComponentId());
		Set<String> conceptIds = new HashSet<>();
		List<SnomedConceptIndexEntry> entries = snomedIndexService.search(branchPath, indexQueryAdapter);
		for (SnomedConceptIndexEntry entry : entries) {
			conceptIds.add(entry.getId());
		}
		return conceptIds;
	}

	@Override
	public void createConcepts(Template template, WorkingContent content, String taskId) throws IOException {
		getCreateTask(taskId);

		LogicalModel logicalModel = logicalModelService.loadLogicalModel(template.getLogicalModelName());
		Assert.isTrue(logicalModel != null, "Logical model not found.");

		LexicalModel lexicalModel = lexicalModelService.loadModel(template.getLexicalModelName());
		Assert.isTrue(lexicalModel != null, "Lexical model not found.");

		List<WorkingConcept> concepts = content.getConcepts();
		List<ChangeRequest<SnomedConceptRestInput>> changeRequests = new ArrayList<>();
		for (WorkingConcept concept : concepts) {
			ChangeRequest<SnomedConceptRestInput> changeRequest = contentMapper.getSnomedConceptRestInputChangeRequest(lexicalModel, concept);
			changeRequests.add(changeRequest);
		}
		for (int i = 0; i < changeRequests.size(); i++) {
			ChangeRequest<SnomedConceptRestInput> changeRequest = changeRequests.get(i);
			String commitComment = "Batch create from template '" + template.getName() + "', concept " + (i + 1) + " of " + changeRequests.size();
			logger.info("snomedConceptService {}", snomedConceptService);
			ISnomedConcept iSnomedConcept = snomedConceptService.create(changeRequest.getChange().toComponentInput(MAIN_BRANCH, taskId), SNOWOWL_USER_ID, commitComment);
			concepts.get(i).setId(iSnomedConcept.getId());
		}
	}

	private ITask getCreateTask(String taskId) {
		ITask task = null;
		try {
			task = taskService.getTaskByName(SNOMEDCT_CODE_SYSTEM, MAIN_BRANCH, taskId);
		} catch (TaskNotFoundException e) {
			// Gulp
		}

		// If not exists create
		if (task == null) {
			TaskInput input = new TaskInput();
			input.setDescription(taskId);
			input.setTaskId(taskId);
			task = taskService.createTask(SNOMEDCT_CODE_SYSTEM, MAIN_BRANCH, taskId, input, SNOWOWL_USER_ID);
		}

		return task;
	}

	private static final class IndexQueryAdapter extends SnomedConceptIndexQueryAdapter {

		public static final int SEARCH_PARENT = 1 << 8;
		public static final int SEARCH_ANCESTOR = 1 << 9;

		protected IndexQueryAdapter(String conceptId) {
			super(conceptId, SEARCH_PARENT | SEARCH_ANCESTOR | SEARCH_ACTIVE_CONCEPTS, null);
		}

		@Override
		protected IndexQueryBuilder createIndexQueryBuilder() {
			BytesRef conceptIdByteRef = IndexUtils.longToPrefixCoded(this.searchString);
			IndexQueryBuilder queryBuilder = new IndexQueryBuilder()
					.matchExactTerm(SnomedIndexBrowserConstants.CONCEPT_PARENT, conceptIdByteRef)
					.matchExactTerm(SnomedIndexBrowserConstants.CONCEPT_ANCESTOR, conceptIdByteRef);
			return super.createIndexQueryBuilder().require(queryBuilder);
		}
	}

}
