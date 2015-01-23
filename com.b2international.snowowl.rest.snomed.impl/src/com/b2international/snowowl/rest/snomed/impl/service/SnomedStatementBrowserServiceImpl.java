/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.impl.service;

import java.util.List;

import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;

import com.b2international.commons.ClassUtils;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.api.index.CommonIndexConstants;
import com.b2international.snowowl.rest.domain.IComponentList;
import com.b2international.snowowl.rest.domain.IComponentRef;
import com.b2international.snowowl.rest.impl.domain.InternalComponentRef;
import com.b2international.snowowl.rest.snomed.domain.ISnomedRelationship;
import com.b2international.snowowl.rest.snomed.impl.domain.SnomedRelationshipList;
import com.b2international.snowowl.rest.snomed.service.ISnomedStatementBrowserService;
import com.b2international.snowowl.snomed.datastore.SnomedRelationshipIndexEntry;
import com.b2international.snowowl.snomed.datastore.index.SnomedIndexService;
import com.b2international.snowowl.snomed.datastore.index.SnomedRelationshipIndexQueryAdapter;
import com.b2international.snowowl.snomed.datastore.services.SnomedBranchRefSetMembershipLookupService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * @author apeteri
 */
public class SnomedStatementBrowserServiceImpl implements ISnomedStatementBrowserService {

	private static final class SortedRelationshipAdapter extends SnomedRelationshipIndexQueryAdapter {

		private static final long serialVersionUID = 1L;

		private SortedRelationshipAdapter(final String conceptId, final int searchFlags) {
			super(conceptId, searchFlags);
		}

		@Override
		protected Sort createSort() {
			return new Sort(new SortField(CommonIndexConstants.COMPONENT_ID, Type.LONG));
		}
	}

	private static SnomedIndexService getIndexService() {
		return ApplicationContext.getServiceForClass(SnomedIndexService.class);
	}

	@Override
	public IComponentList<ISnomedRelationship> getInboundEdges(final IComponentRef nodeRef, final int offset, final int limit) {
		final InternalComponentRef internalRef = ClassUtils.checkAndCast(nodeRef, InternalComponentRef.class);
		final SnomedRelationshipIndexQueryAdapter queryAdapter = new SortedRelationshipAdapter(nodeRef.getComponentId(), SnomedRelationshipIndexQueryAdapter.SEARCH_DESTINATION_ID);
		return toEdgeList(internalRef, offset, limit, queryAdapter);
	}

	@Override
	public IComponentList<ISnomedRelationship> getOutboundEdges(final IComponentRef nodeRef, final int offset, final int limit) {
		final InternalComponentRef internalRef = ClassUtils.checkAndCast(nodeRef, InternalComponentRef.class);
		final SnomedRelationshipIndexQueryAdapter queryAdapter = new SortedRelationshipAdapter(nodeRef.getComponentId(), SnomedRelationshipIndexQueryAdapter.SEARCH_SOURCE_ID);
		return toEdgeList(internalRef, offset, limit, queryAdapter);
	}

	private IComponentList<ISnomedRelationship> toEdgeList(final InternalComponentRef internalRef, final int offset, final int limit, final SnomedRelationshipIndexQueryAdapter queryAdapter) {
		final SnomedRelationshipList result = new SnomedRelationshipList();
		final IBranchPath branchPath = internalRef.getBranchPath();
		result.setTotalMembers(getIndexService().getHitCount(branchPath, queryAdapter));

		final List<SnomedRelationshipIndexEntry> indexEntries = getIndexService().search(branchPath, queryAdapter, offset, limit);
		final List<ISnomedRelationship> relationships = Lists.transform(indexEntries, createConverter(branchPath));
		result.setMembers(ImmutableList.copyOf(relationships));

		return result;
	}

	private SnomedRelationshipConverter createConverter(final IBranchPath branchPath) {
		return new SnomedRelationshipConverter(new SnomedBranchRefSetMembershipLookupService(branchPath));
	}
}
