/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.service.admin;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.List;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.datastore.ISingleDirectoryIndexService;
import com.b2international.snowowl.datastore.server.index.ISingleDirectoryIndexServiceManager;
import com.b2international.snowowl.rest.exception.admin.SnapshotCreationException;
import com.b2international.snowowl.rest.exception.admin.SnapshotListingException;
import com.b2international.snowowl.rest.exception.admin.SnapshotReleaseException;
import com.b2international.snowowl.rest.exception.admin.SupportingIndexNotFoundException;
import com.b2international.snowowl.rest.exception.admin.SupportingIndexSnapshotNotFoundException;
import com.b2international.snowowl.rest.service.admin.ISupportingIndexService;
import com.google.common.collect.ImmutableList;

/**
 * @author apeteri
 */
public class SupportingIndexServiceImpl implements ISupportingIndexService {

	private static ISingleDirectoryIndexServiceManager getSingleDirectoryIndexManager() {
		return ApplicationContext.getServiceForClass(ISingleDirectoryIndexServiceManager.class);
	}

	@Override
	public List<String> getSupportingIndexIds() {
		final List<String> serviceIds = getSingleDirectoryIndexManager().getServiceIds();
		return ImmutableList.copyOf(serviceIds);
	}

	@Override
	public List<String> getSupportingIndexSnapshotIds(final String indexId) {
		checkValidIndexId(indexId);

		final ISingleDirectoryIndexService service = getSingleDirectoryIndexManager().getService(indexId);
		final List<String> snapshotIds = service.getSnapshotIds();
		return ImmutableList.copyOf(snapshotIds);
	}

	@Override
	public String createSupportingIndexSnapshot(final String indexId) {
		checkValidIndexId(indexId);

		try {
			final ISingleDirectoryIndexService service = getSingleDirectoryIndexManager().getService(indexId);
			return service.snapshot();
		} catch (final IOException e) {
			throw new SnapshotCreationException(e.getMessage());
		}
	}

	@Override
	public List<String> getSupportingIndexFiles(final String indexId, final String snapshotId) {
		checkValidIndexAndSnapshotId(indexId, snapshotId);

		try {
			final ISingleDirectoryIndexService service = getSingleDirectoryIndexManager().getService(indexId);
			final List<String> listFiles = service.listFiles(snapshotId);
			return ImmutableList.copyOf(listFiles);
		} catch (final IOException e) {
			throw new SnapshotListingException(e.getMessage());
		}
	}

	@Override
	public void releaseSupportingIndexSnapshot(final String indexId, final String snapshotId) {
		checkValidIndexAndSnapshotId(indexId, snapshotId);

		try {
			final ISingleDirectoryIndexService service = getSingleDirectoryIndexManager().getService(indexId);
			service.releaseSnapshot(snapshotId);
		} catch (final IOException e) {
			throw new SnapshotReleaseException(e.getMessage());
		}
	}

	private void checkValidIndexId(final String indexId) {
		checkNotNull(indexId, "Index identifier may not be null.");

		if (!getSupportingIndexIds().contains(indexId)) {
			throw new SupportingIndexNotFoundException(indexId);
		}
	}

	private void checkValidIndexAndSnapshotId(final String indexId, final String snapshotId) {
		checkNotNull(snapshotId, "Snapshot identifier may not be null.");

		// XXX: will invoke checkValidIndexId as well
		if (!getSupportingIndexSnapshotIds(indexId).contains(snapshotId)) {
			throw new SupportingIndexSnapshotNotFoundException(snapshotId);
		}
	}
}
