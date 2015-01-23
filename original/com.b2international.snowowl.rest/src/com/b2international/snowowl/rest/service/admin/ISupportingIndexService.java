/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.service.admin;

import java.util.List;

import com.b2international.snowowl.rest.exception.admin.SnapshotCreationException;
import com.b2international.snowowl.rest.exception.admin.SnapshotListingException;
import com.b2international.snowowl.rest.exception.admin.SnapshotReleaseException;
import com.b2international.snowowl.rest.exception.admin.SupportingIndexNotFoundException;
import com.b2international.snowowl.rest.exception.admin.SupportingIndexSnapshotNotFoundException;

/**
 * An interface definition for the Supporting Index Service.
 * <p>
 * The following operations are supported:
 * <ul>
 * <li>{@link #getSupportingIndexIds() <em>Retrieve all supporting index identifiers</em>}
 * <li>{@link #getSupportingIndexSnapshotIds(String) <em>Retrieve all snapshot identifiers for an index</em>}
 * <li>{@link #createSupportingIndexSnapshot(String) <em>Create snapshot for an index</em>}
 * <li>{@link #getSupportingIndexFiles(String, String) <em>List contents of an index snapshot</em>}
 * <li>{@link #releaseSupportingIndexSnapshot(String, String) <em>Release resources associated with an index snapshot</em>}
 * </ul>
 * 
 * @author Andras Peteri
 */
public interface ISupportingIndexService {

	/**
	 * Retrieves a list of identifiers for indexes which are storing supplementary information (eg. task state, bookmarks, etc.)
	 * 
	 * @return a list of supporting index service identifiers, in alphabetical order (never {@code null})
	 */
	List<String> getSupportingIndexIds();

	/**
	 * Retrieves a list of snapshot identifiers for the specified supporting index.
	 * 
	 * @param indexId the identifier of the supporting index service (may not be {@code null})
	 * @return a list of snapshot identifiers, from newest to oldest (never {@code null})
	 * @throws SupportingIndexNotFoundException if the specified service identifier does not correspond to any registered index service
	 */
	List<String> getSupportingIndexSnapshotIds(String indexId);

	/**
	 * Creates a new, consistent snapshot for the specified supporting index.
	 * 
	 * @param indexId the identifier of the supporting index service (may not be {@code null})
	 * @return the identifier of the created snapshot
	 * @throws SupportingIndexNotFoundException if the specified service identifier does not correspond to any registered index service
	 * @throws SnapshotCreationException if snapshot creation fails for some reason
	 */
	String createSupportingIndexSnapshot(String indexId);

	/**
	 * Retrieves a list of relative paths to files which make up the given consistent snapshot of a supporting index.
	 * 
	 * @param indexId the identifier of the supporting index service (may not be {@code null})
	 * @param snapshotId the identifier of the snapshot (may not be {@code null})
	 * @return a list of relative paths to files which make up the index snapshot, in alphabetical order (never {@code null})
	 * @throws SupportingIndexNotFoundException if the specified service identifier does not correspond to any registered index service
	 * @throws SupportingIndexSnapshotNotFoundException if the specified snapshot identifier does not correspond to any currently present snapshot
	 * @throws SnapshotListingException if listing snapshot contents fails for some reason
	 */
	List<String> getSupportingIndexFiles(String indexId, String snapshotId);

	/**
	 * Releases an existing, consistent snapshot of the specified supporting index.
	 * 
	 * @param indexId the identifier of the supporting index service (may not be {@code null})
	 * @param snapshotId the identifier of the snapshot to be removed (may not be {@code null})
	 * @throws SupportingIndexNotFoundException if the specified service identifier does not correspond to any registered index service
	 * @throws SupportingIndexSnapshotNotFoundException if the specified snapshot identifier does not correspond to any currently present snapshot
	 * @throws SnapshotReleaseException if releasing the snapshot fails for some reason
	 */
	void releaseSupportingIndexSnapshot(String indexId, String snapshotId);
}
