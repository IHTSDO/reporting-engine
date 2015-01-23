/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.service;

import java.util.List;

import com.b2international.snowowl.rest.snomed.domain.classification.IClassificationRun;
import com.b2international.snowowl.rest.snomed.domain.classification.IEquivalentConceptSet;
import com.b2international.snowowl.rest.snomed.domain.classification.IRelationshipChangeList;

/**
 * Provides access to the classification functionality of the Snow Owl Server.
 *
 * @author Andras Peteri
 */
public interface ISnomedClassificationService {

	/**
	 * 
	 * @param version
	 * @param taskId
	 * @param userId
	 * @return
	 */
	List<IClassificationRun> getAllClassificationRuns(String version, String taskId, String userId);

	/**
	 * 
	 * @param version
	 * @param taskId
	 * @param userId
	 * @return
	 */
	IClassificationRun beginClassification(String version, String taskId, String reasonerId, String userId);

	/**
	 * 
	 * @param version
	 * @param taskId
	 * @param classificationId
	 * @param userId
	 * @return
	 */
	IClassificationRun getClassificationRun(String version, String taskId, String classificationId, String userId);

	/**
	 * 
	 * @param version
	 * @param taskId
	 * @param classificationId
	 * @param userId
	 * @return
	 */
	List<IEquivalentConceptSet> getEquivalentConceptSets(String version, String taskId, String classificationId, String userId);

	/**
	 * 
	 * @param version
	 * @param taskId
	 * @param classificationId
	 * @return
	 */
	IRelationshipChangeList getRelationshipChanges(String version, String taskId, String classificationId, String userId, int offset, int limit);

	/**
	 * 
	 * @param version
	 * @param taskId
	 * @param classificationId
	 * @param userId
	 */
	void persistChanges(String version, String taskId, String classificationId, String userId);

	/**
	 * 
	 * @param version
	 * @param taskId
	 * @param classificationId
	 * @param userId
	 */
	void removeClassificationRun(String version, String taskId, String classificationId, String userId);
}
