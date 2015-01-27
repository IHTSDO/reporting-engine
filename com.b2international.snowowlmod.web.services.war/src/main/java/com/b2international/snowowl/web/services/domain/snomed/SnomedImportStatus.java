/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import static com.google.common.collect.EnumBiMap.create;
import static com.google.common.collect.Maps.unmodifiableBiMap;

import java.util.EnumMap;

import com.b2international.snowowl.rest.snomed.domain.ISnomedImportConfiguration;
import com.b2international.snowowl.rest.snomed.domain.ISnomedImportConfiguration.ImportStatus;
import com.google.common.collect.BiMap;

/**
 * @author apeteri
 * @since 1.0
 */
public enum SnomedImportStatus {
	WAITING_FOR_FILE,
	RUNNING,
	COMPLETED,
	FAILED;
	
	/**
	 * Returns with the {@link ISnomedImportConfiguration.ImportStatus import status} instance
	 * that stands for the {@link SnomedImportStatus} argument.
	 * @param status the import status for the SNOMED&nbsp;CT import process.
	 * @return the mapped import status instance.
	 */
	public static ImportStatus getImportStatus(final SnomedImportStatus status) {
		return STATUS_MAPPING.get(status);
	}
	
	/**
	 * Returns with the {@link SnomedImportStatus import status} instance
	 * that stands for the {@link ISnomedImportConfiguration.ImportStatus } argument.
	 * @param status the import status for the SNOMED&nbsp;CT import process.
	 * @return the mapped import status instance.
	 */
	public static SnomedImportStatus getImportStatus(final ImportStatus status) {
		return STATUS_MAPPING.inverse().get(status);
	}
	
	private static final BiMap<SnomedImportStatus, ImportStatus> STATUS_MAPPING = // 
			unmodifiableBiMap(create(new EnumMap<SnomedImportStatus, ImportStatus>(SnomedImportStatus.class) {
		
		private static final long serialVersionUID = 1272336677673814738L;

		{
			put(WAITING_FOR_FILE, ImportStatus.WAITING_FOR_FILE);
			put(COMPLETED, ImportStatus.COMPLETED);
			put(FAILED, ImportStatus.FAILED);
			put(RUNNING, ImportStatus.RUNNING);
		}
		
	}));
}
