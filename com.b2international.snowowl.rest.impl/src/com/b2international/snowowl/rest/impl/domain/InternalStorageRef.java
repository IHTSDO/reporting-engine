/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain;

import org.eclipse.emf.cdo.common.branch.CDOBranch;

import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.datastore.ICodeSystem;
import com.b2international.snowowl.datastore.ICodeSystemVersion;
import com.b2international.snowowl.rest.domain.IStorageRef;

/**
 * @author apeteri
 */
public interface InternalStorageRef extends IStorageRef {

	/**
	 * @return
	 */
	ICodeSystem getCodeSystem();

	/**
	 * @return
	 */
	String getRepositoryUuid();

	/**
	 * @return
	 */
	IBranchPath getBranchPath();

	/**
	 * @return
	 */
	CDOBranch getCdoBranch();

	/**
	 * @return
	 */
	ICodeSystemVersion getCodeSystemVersion();

	/**
	 * 
	 */
	void checkStorageExists();
}
