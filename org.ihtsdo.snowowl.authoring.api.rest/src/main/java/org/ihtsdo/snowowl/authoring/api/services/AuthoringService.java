package org.ihtsdo.snowowl.authoring.api.services;

import com.b2international.snowowl.api.domain.IComponentRef;
import com.b2international.snowowl.api.impl.domain.InternalStorageRef;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.datastore.index.IndexQueryBuilder;
import com.b2international.snowowl.datastore.index.IndexUtils;
import com.b2international.snowowl.snomed.datastore.SnomedConceptIndexEntry;
import com.b2international.snowowl.snomed.datastore.browser.SnomedIndexBrowserConstants;
import com.b2international.snowowl.snomed.datastore.index.SnomedConceptIndexQueryAdapter;
import com.b2international.snowowl.snomed.datastore.index.SnomedIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.util.BytesRef;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AuthoringService {

	private final SnomedIndexService snomedIndexService;

	private final File logicalModelsDirectory;

	@Autowired
	private ObjectMapper jsonMapper;

	public AuthoringService() {
		snomedIndexService = ApplicationContext.getServiceForClass(SnomedIndexService.class);
		logicalModelsDirectory = new File("resources/org.ihtsdo.snowowl.authoring/logical-models");
		logicalModelsDirectory.mkdirs();
	}

	public List<String> getDescendantIds(final IComponentRef ref) {
		InternalStorageRef internalRef = (InternalStorageRef) ref;
		internalRef.checkStorageExists();

		IBranchPath branchPath = internalRef.getBranchPath();
		IndexQueryAdapter indexQueryAdapter = new IndexQueryAdapter(ref.getComponentId());
		List<String> conceptIds = new ArrayList<>();
		List<SnomedConceptIndexEntry> entries = snomedIndexService.search(branchPath, indexQueryAdapter);
		for (SnomedConceptIndexEntry entry : entries) {
			conceptIds.add(entry.getId());
		}
		return conceptIds;
	}

	public void saveLogicalModel(String name, LogicalModel logicalModel) throws IOException {
		Assert.notNull(name, "Logical model name can not be null.");
		try (FileWriter writer = new FileWriter(getLogicalModelFile(name))) {
			jsonMapper.writeValue(writer, logicalModel);
		}
	}

	public LogicalModel loadLogicalModel(String name) throws IOException {
		Assert.notNull(name, "Logical model name can not be null.");
		File logicalModelFile = getLogicalModelFile(name);
		if (logicalModelFile.isFile()) {
			try (FileReader src = new FileReader(logicalModelFile)) {
				return jsonMapper.readValue(src, LogicalModel.class);
			}
		} else {
			throw new LogicalModelNotFoundException(name);
		}
	}

	private File getLogicalModelFile(String name) {
		return new File(logicalModelsDirectory, name + ".json");
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
