/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Date;

import com.b2international.snowowl.rest.snomed.service.ISnomedRf2ImportService;


/**
 * Representation of a configuration used by the 
 * {@link ISnomedRf2ImportService SNOMED&nbsp;CT RF2 import service}.
 * @author Akos Kitta
 *
 */
public interface ISnomedImportConfiguration {

	/**
	 * Returns with the RF2 release type of the current import configuration.
	 * @return the desired RF2 release type.
	 */
	Rf2ReleaseType getRf2ReleaseType();
	
	/**
	 * Returns the code system version identifier, eg. "{@code 2014-01-31}".
	 * @return the code system version identifier
	 */
	String getVersion();
	
	/**
	 * Returns with the language reference set identifier concept ID for the preferred language
	 * that has to be used for the import process.
	 * @return the language reference set concept ID of the preferred language.
	 */
	String getLanguageRefSetId();
	
	/**
	 * Returns with {@code true} if a new version has to be created after processing
	 * each individual effective times during the RF2 import process. Has no effect if the 
	 * {@link #getRf2ReleaseType() RF2 release type} is *NOT* {@link Rf2ReleaseType#FULL full}.
	 * @return {@code true} if a version has to be created for each individual effective times.
	 * Otherwise returns with {@code false}.
	 */
	boolean shouldCreateVersion();
	
	/**
	 * Returns with the current status of the SNOMED&nbsp;CT import process backed
	 * by the current import configuration.
	 * @return the import status for the configuration.
	 */
	ImportStatus getStatus();
	
	/**
	 * Returns the start date of the import, or <code>null</code> if not started yet.
	 * 
	 * @return
	 */
	Date getStartDate();
	
	/**
	 * Returns the completion date of the import, or <code>null</code> if not completed yet.
	 * @return
	 */
	Date getCompletionDate();
	
	/**
	 * Enumeration for representing the current state of an RF2 import process. 
	 * @author Akos Kitta
	 */
	static enum ImportStatus {
		
		WAITING_FOR_FILE("Waiting for file"),
		RUNNING("Running"),
		COMPLETED("Completed"),
		FAILED("Failed");
		
		private String label;

		private ImportStatus(final String label) {
			this.label = checkNotNull(label, "label");
		}
		
		@Override
		public String toString() {
			return label;
		}
		
	}

}
