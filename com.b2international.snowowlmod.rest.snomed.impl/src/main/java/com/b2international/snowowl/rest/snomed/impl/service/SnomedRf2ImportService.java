/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.impl.service;

import com.b2international.snowowl.datastore.CodeSystemService;
import com.b2international.snowowl.datastore.ContentAvailabilityInfoManager;
import com.b2international.snowowl.datastore.ICodeSystemVersion;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemVersionNotFoundException;
import com.b2international.snowowl.rest.snomed.domain.ISnomedImportConfiguration;
import com.b2international.snowowl.rest.snomed.domain.ISnomedImportConfiguration.ImportStatus;
import com.b2international.snowowl.rest.snomed.domain.Rf2ReleaseType;
import com.b2international.snowowl.rest.snomed.domain.exception.SnomedImportConfigurationNotFoundException;
import com.b2international.snowowl.rest.snomed.domain.exception.SnomedImportException;
import com.b2international.snowowl.rest.snomed.domain.exception.SnomedImportIdNotFoundException;
import com.b2international.snowowl.rest.snomed.impl.domain.SnomedImportConfiguration;
import com.b2international.snowowl.rest.snomed.service.ISnomedRf2ImportService;
import com.b2international.snowowl.snomed.importer.net4j.SnomedImportResult;
import com.b2international.snowowl.snomed.importer.rf2.util.ImportUtil;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.*;

import static com.b2international.commons.CompareUtils.isEmpty;
import static com.b2international.commons.FileUtils.copyContentToTempFile;
import static com.b2international.snowowl.core.ApplicationContext.getServiceForClass;
import static com.b2international.snowowl.datastore.BranchPathUtils.createVersionPath;
import static com.b2international.snowowl.datastore.ICodeSystemVersion.GET_VERSION_ID_FUNC;
import static com.b2international.snowowl.rest.snomed.domain.ISnomedImportConfiguration.ImportStatus.*;
import static com.b2international.snowowl.rest.snomed.domain.Rf2ReleaseType.DELTA;
import static com.b2international.snowowl.rest.snomed.domain.Rf2ReleaseType.FULL;
import static com.b2international.snowowl.snomed.common.ContentSubType.getByNameIgnoreCase;
import static com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator.REPOSITORY_UUID;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;
import static com.google.common.collect.Iterables.find;
import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Maps.uniqueIndex;
import static java.lang.String.valueOf;
import static java.util.Collections.synchronizedMap;
import static java.util.UUID.randomUUID;

/**
 * {@link ISnomedRf2ImportService SNOMED&nbsp;CT RF2 import service} implementation.
 * Used for importing SNOMED&nbsp;CT content into the system from RF2 release archives.
 * @author Akos Kitta
 *
 */
public class SnomedRf2ImportService implements ISnomedRf2ImportService {
	
	private static final Logger LOG = LoggerFactory.getLogger(SnomedRf2ImportService.class);
	
	/**
	 * A mapping between the registered import identifiers and the associated import configurations.
	 * <p>
	 * Keys are versions.<br>Values are mapping between the import IDs and the configurations.
	 */
	private static final Supplier<Map<String, Map<UUID, ISnomedImportConfiguration>>> CONFIGURATION_MAPPING = // 
			memoize(new Supplier<Map<String, Map<UUID, ISnomedImportConfiguration>>>() {
				public Map<String, Map<UUID, ISnomedImportConfiguration>> get() {
					return synchronizedMap(new HashMap<String, Map<UUID, ISnomedImportConfiguration>>());
				}
			} );
	
	@Override
	public ISnomedImportConfiguration getImportDetails(final String version, final UUID importId) {
		checkNotNull(version, "Version argument should be specified.");
		final Map<UUID, ISnomedImportConfiguration> configurationMapping = // 
				getConfigurationMapping(version);
		
		if (isEmpty(configurationMapping)) {
			throw new SnomedImportIdNotFoundException(importId);
		}
		
		final ISnomedImportConfiguration configuration = configurationMapping.get(importId);
		if (null == configuration) {
			throw new SnomedImportConfigurationNotFoundException(importId);
		}
		
		return configuration;
	}

	@Override
	public void deleteImportDetails(final String version, final UUID importId) {
		checkNotNull(version, "Version argument should be specified.");
		final Map<UUID, ISnomedImportConfiguration> configurationMapping = // 
				getConfigurationMapping(version);
		
		if (isEmpty(configurationMapping)) {
			throw new SnomedImportIdNotFoundException(importId);
		}
		
		final ISnomedImportConfiguration configuration = configurationMapping.get(importId);
		if (null == configuration) {
			throw new SnomedImportConfigurationNotFoundException(importId);
		}
		
		configurationMapping.remove(importId);
	}

	private Map<UUID, ISnomedImportConfiguration> getConfigurationMapping(final String version) {
		return CONFIGURATION_MAPPING.get().get(checkVersion(version));
	}
	
	@Override
	public void startImport(final String version, final UUID importId, 
			final InputStream inputStream) {
		
		checkNotNull(version, "Version argument should be specified.");
		checkNotNull(importId, "SNOMED CT import identifier should be specified.");
		checkNotNull(inputStream, "Cannot stream the content of the given SNOMED CT release archive.");
		
		final ISnomedImportConfiguration configuration = getImportDetails(version, importId);
		final ImportStatus currentStatus = configuration.getStatus();
		if (!WAITING_FOR_FILE.equals(currentStatus)) {
			final StringBuilder sb = new StringBuilder();
			sb.append("Cannot start SNOMED CT import. Import configuration is ");
			sb.append(valueOf(currentStatus).toLowerCase());
			sb.append(".");
			throw new SnomedImportException(sb.toString());
		}
		
		final Rf2ReleaseType releaseType = configuration.getRf2ReleaseType();
		final boolean contentAvailable = isContentAvailable();
		if (contentAvailable && FULL.equals(releaseType)) {
			throw new SnomedImportException("Importing a full release of SNOMED CT "
					+ "from an archive is prohibited when SNOMED CT ontology is "
					+ "already available on the terminology server. "
					+ "Please perform either a delta or a snapshot import instead.");
		}
		
		if (!contentAvailable && DELTA.equals(releaseType)) {
			throw new SnomedImportException("Importing a delta release of SNOMED CT "
					+ "from an archive is prohibited when SNOMED CT ontology is "
					+ "not available on the terminology server. "
					+ "Please perform either a full or a snapshot import instead.");
		}
		
		if (isImportAlreadyRunning(version)) {
			throw new SnomedImportException("Cannot perform SNOMED CT import from RF2 archive. "
					+ "An import is already in progress. Please try again later.");
		}
		
		final File archiveFile = copyContentToTempFile(inputStream, valueOf(randomUUID()));
		new Thread(new Runnable() {
			public void run() {
				try {
					((SnomedImportConfiguration) configuration).setStatus(RUNNING);
					final SnomedImportResult result = doImport(version, configuration, archiveFile);
					((SnomedImportConfiguration) configuration).setStatus(isEmpty(result.getValidationDefects()) ? COMPLETED : FAILED); 
				} catch (final Exception e) {
					LOG.error("Error during the import of " + archiveFile, e);
					((SnomedImportConfiguration) configuration).setStatus(FAILED);
				}
			}
		}).start();
		
	}

	private SnomedImportResult doImport(final String version, final ISnomedImportConfiguration configuration, final File archiveFile) throws Exception {
		return new ImportUtil().doImport(
				createVersionPath(version), 
				configuration.getLanguageRefSetId(), 
				getByNameIgnoreCase(valueOf(configuration.getRf2ReleaseType())), 
				archiveFile,
				configuration.shouldCreateVersion());
	}

	private boolean isImportAlreadyRunning(final String version) {
		return null != find(getConfigurations(version), new Predicate<ISnomedImportConfiguration>() {
			public boolean apply(final ISnomedImportConfiguration configuration) {
				return RUNNING.equals(configuration.getStatus());
			}
		}, null);
	}

	private Collection<ISnomedImportConfiguration> getConfigurations(final String version) {
		return getConfigurationMapping(version).values();
	}

	private boolean isContentAvailable() {
		return ContentAvailabilityInfoManager.INSTANCE.isAvailable(REPOSITORY_UUID);
	}
	
	@Override
	public UUID create(final String version, final ISnomedImportConfiguration configuration) {
		checkNotNull(version, "Version argument should be specified.");
		checkNotNull(configuration, "SNOMED CT import configuration should be specified.");
		checkVersion(version);
		final UUID importId = randomUUID();
		Map<UUID, ISnomedImportConfiguration> configurationMapping = CONFIGURATION_MAPPING.get().get(version);
		if (isEmpty(configurationMapping)) {
			configurationMapping = newHashMap();
			CONFIGURATION_MAPPING.get().put(version, configurationMapping);
		}
		configurationMapping.put(importId, configuration);
		return importId;
	}

	private String checkVersion(final String version) {
		checkNotNull(version, "Version argument should be specified.");
		if (!getVersionsWithHead().containsKey(version)) {
			throw new CodeSystemVersionNotFoundException(version);
		}
		return version;
	}
	
	private Map<String, ICodeSystemVersion> getVersionsWithHead() {
		return uniqueIndex(getAllVersionsWithHEad(), GET_VERSION_ID_FUNC);
	}

	private List<ICodeSystemVersion> getAllVersionsWithHEad() {
		return getCodeSystemService().getAllTagsWithHead(REPOSITORY_UUID);
	}

	private CodeSystemService getCodeSystemService() {
		return getServiceForClass(CodeSystemService.class);
	}
	
}
