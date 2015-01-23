/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain;

import java.util.Collection;

import org.eclipse.emf.cdo.common.branch.CDOBranch;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.datastore.BranchPathUtils;
import com.b2international.snowowl.datastore.CodeSystemService;
import com.b2international.snowowl.datastore.IBranchPathMap;
import com.b2international.snowowl.datastore.ICodeSystem;
import com.b2international.snowowl.datastore.ICodeSystemVersion;
import com.b2international.snowowl.datastore.TerminologyRegistryService;
import com.b2international.snowowl.datastore.UserBranchPathMap;
import com.b2international.snowowl.datastore.cdo.ICDOConnectionManager;
import com.b2international.snowowl.rest.exception.NotFoundException;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemNotFoundException;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemVersionNotFoundException;
import com.b2international.snowowl.rest.exception.task.TaskNotFoundException;

/**
 * @author apeteri
 *
 */
public class StorageRef implements InternalStorageRef {

	private static final IBranchPathMap MAIN_BRANCH_PATH_MAP = new UserBranchPathMap();

	private static ICDOConnectionManager getConnectionManager() {
		return ApplicationContext.getServiceForClass(ICDOConnectionManager.class);
	}

	private static CodeSystemService getCodeSystemService() {
		return ApplicationContext.getServiceForClass(CodeSystemService.class);
	}

	private static TerminologyRegistryService getRegistryService() {
		return ApplicationContext.getServiceForClass(TerminologyRegistryService.class);
	}

	private String shortName;
	private String version;
	private String taskId;

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public String getTaskId() {
		return taskId;
	}

	public void setShortName(final String shortName) {
		this.shortName = shortName;
	}

	public void setVersion(final String version) {
		this.version = version;
	}

	public void setTaskId(final String taskId) {
		this.taskId = taskId;
	}

	@Override
	public ICodeSystem getCodeSystem() {
		// XXX: in case of a non-MAIN-registered code system, we would need a repository UUID to get the code system to get the repository UUID
		final ICodeSystem codeSystem = getRegistryService().getCodeSystemByShortName(MAIN_BRANCH_PATH_MAP, shortName);
		if (null != codeSystem) {
			return codeSystem;
		}

		throw new CodeSystemNotFoundException(shortName);
	}

	@Override
	public String getRepositoryUuid() {
		return getCodeSystem().getRepositoryUuid();
	}

	private IBranchPath getVersionBranchPath() {
		return BranchPathUtils.createVersionPath(version);
	}

	@Override
	public IBranchPath getBranchPath() {
		if (null == taskId) {
			return getVersionBranchPath();
		} else {
			return BranchPathUtils.createPath(getVersionBranchPath(), taskId);
		}
	}

	@Override
	public CDOBranch getCdoBranch() {
		final CDOBranch cdoBranch = getCdoBranchOrNull();
		if (null != cdoBranch) {
			return cdoBranch;
		}

		throw createCdoBranchNotFoundException();
	}

	private CDOBranch getCdoBranchOrNull() {
		return getConnectionManager().getByUuid(getRepositoryUuid()).getBranch(getBranchPath());
	}

	private NotFoundException createCdoBranchNotFoundException() {
		if (null == taskId) {
			return new CodeSystemVersionNotFoundException(version);
		} else {
			return new TaskNotFoundException(taskId);
		}
	}

	@Override
	public ICodeSystemVersion getCodeSystemVersion() {
		final Collection<ICodeSystemVersion> codeSystemVersions = getCodeSystemService().getAllTagsWithHead(getRepositoryUuid());
		for (final ICodeSystemVersion codeSystemVersion : codeSystemVersions) {
			if (codeSystemVersion.getVersionId().equals(version)) {
				return codeSystemVersion;
			}
		}

		throw new CodeSystemVersionNotFoundException(version);
	}
	
	@Override
	public void checkStorageExists() {
		getCodeSystemVersion();
		getCdoBranch();
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("StorageRef [shortName=");
		builder.append(shortName);
		builder.append(", version=");
		builder.append(version);
		builder.append(", taskId=");
		builder.append(taskId);
		builder.append("]");
		return builder.toString();
	}
}
