/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.service.codesystem;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.eclipse.core.runtime.IStatus;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.api.SnowowlServiceException;
import com.b2international.snowowl.datastore.TerminologyRegistryService;
import com.b2international.snowowl.datastore.UserBranchPathMap;
import com.b2international.snowowl.datastore.version.VersioningService;
import com.b2international.snowowl.rest.domain.codesystem.ICodeSystemVersion;
import com.b2international.snowowl.rest.domain.codesystem.ICodeSystemVersionProperties;
import com.b2international.snowowl.rest.exception.AlreadyExistsException;
import com.b2international.snowowl.rest.exception.BadRequestException;
import com.b2international.snowowl.rest.exception.LockedException;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemNotFoundException;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemVersionNotFoundException;
import com.b2international.snowowl.rest.impl.domain.codesystem.CodeSystemVersion;
import com.b2international.snowowl.rest.service.codesystem.ICodeSystemVersionService;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

/**
 * @author apeteri
 */
public class CodeSystemVersionServiceImpl implements ICodeSystemVersionService {

	private static final UserBranchPathMap MAIN_BRANCH_PATH_MAP = new UserBranchPathMap();

	private static class VersionIdPredicate implements Predicate<com.b2international.snowowl.datastore.ICodeSystemVersion> {

		private final String version;

		private VersionIdPredicate(final String version) {
			this.version = version;
		}

		@Override
		public boolean apply(final com.b2international.snowowl.datastore.ICodeSystemVersion input) {
			return version.equals(input.getVersionId());
		}
	}

	private static final Function<? super com.b2international.snowowl.datastore.ICodeSystemVersion, ICodeSystemVersion> CODE_SYSTEM_VERSION_CONVERTER = 
			new Function<com.b2international.snowowl.datastore.ICodeSystemVersion, ICodeSystemVersion>() {

		@Override
		public ICodeSystemVersion apply(final com.b2international.snowowl.datastore.ICodeSystemVersion input) {
			final CodeSystemVersion result = new CodeSystemVersion();
			result.setDescription(input.getDescription());
			result.setEffectiveDate(toDate(input.getEffectiveDate()));
			result.setImportDate(toDate(input.getImportDate()));
			result.setLastModificationDate(toDate(input.getLastUpdateDate()));
			result.setPatched(input.isPatched());
			result.setVersion(input.getVersionId());
			return result;
		}

		private Date toDate(final long timeStamp) {
			return timeStamp >= 0L ? new Date(timeStamp) : null;
		}
	};

	private static final Ordering<ICodeSystemVersion> VERSION_ID_ORDERING = Ordering.natural().onResultOf(new Function<ICodeSystemVersion, String>() {
		@Override
		public String apply(final ICodeSystemVersion input) {
			return input.getVersion();
		}
	});

	private static TerminologyRegistryService getRegistryService() {
		return ApplicationContext.getServiceForClass(TerminologyRegistryService.class);
	}

	@Override
	public List<ICodeSystemVersion> getCodeSystemVersions(final String shortName) {
		checkNotNull(shortName, "Short name may not be null.");

		final Collection<com.b2international.snowowl.datastore.ICodeSystemVersion> sourceCodeSystemVersions = getSourceCodeSystemVersions(shortName);
		return toSortedCodeSystemVersionList(sourceCodeSystemVersions);
	}

	@Override
	public ICodeSystemVersion getCodeSystemVersionById(final String shortName, final String version) {
		checkNotNull(shortName, "Short name may not be null.");
		checkNotNull(version, "Version identifier may not be null.");

		final Collection<com.b2international.snowowl.datastore.ICodeSystemVersion> sourceCodeSystemVersions = getSourceCodeSystemVersions(shortName);
		final com.b2international.snowowl.datastore.ICodeSystemVersion matchingVersion = Iterables.find(sourceCodeSystemVersions, new VersionIdPredicate(version), null);
		return toCodeSystemVersion(matchingVersion, version);
	}
	
	@Override
	public ICodeSystemVersion createVersion(String shortName, ICodeSystemVersionProperties properties) {
		com.b2international.snowowl.datastore.ICodeSystem codeSystem = getRegistryService().getCodeSystemByShortName(MAIN_BRANCH_PATH_MAP, shortName);
		if (codeSystem == null) {
			throw new CodeSystemNotFoundException(shortName);
		}
		final VersioningService versioningService = new VersioningService("com.b2international.snowowl.terminology.snomed");
		try {
			versioningService.acquireLock();
			configureVersion(properties, versioningService);
			final IStatus result = versioningService.tag();
			if (result.isOK()) {
				return getCodeSystemVersionById(shortName, properties.getVersion());
			}
			throw new SnowowlRuntimeException("Version creation failed due to " + result.getMessage());
		} catch (SnowowlServiceException e) {
			throw new LockedException(String.format("Cannot create version as %s is locked. Details: %s", shortName, e.getMessage()));
		} finally {
			try {
				versioningService.releaseLock();
			} catch (SnowowlServiceException e) {
				throw new SnowowlRuntimeException("Releasing lock failed: " +  e.getMessage());
			}
		}
	}

	private void configureVersion(ICodeSystemVersionProperties properties, final VersioningService versioningService) {
		versioningService.configureDescription(properties.getDescription());
		final IStatus dateResult = versioningService.configureEffectiveTime(properties.getEffectiveDate());
		if (!dateResult.isOK()) {
			throw new BadRequestException("The specified %s effective time is invalid. %s", properties.getEffectiveDate(), dateResult.getMessage());
		}
		final IStatus versionResult = versioningService.configureNewVersionId(properties.getVersion(), false);
		if (!versionResult.isOK()) {
			throw new AlreadyExistsException("Version", properties.getVersion());
		}
	}

	private Collection<com.b2international.snowowl.datastore.ICodeSystemVersion> getSourceCodeSystemVersions(final String shortName) {
		final Collection<com.b2international.snowowl.datastore.ICodeSystemVersion> sourceCodeSystemVersions = 
				getRegistryService().getCodeSystemVersions(MAIN_BRANCH_PATH_MAP, shortName);

		if (null == sourceCodeSystemVersions) {
			throw new CodeSystemNotFoundException(shortName);
		}

		return sourceCodeSystemVersions;
	}

	private List<ICodeSystemVersion> toSortedCodeSystemVersionList(
			final Collection<com.b2international.snowowl.datastore.ICodeSystemVersion> sourceCodeSystemVersions) {

		final Collection<ICodeSystemVersion> targetCodeSystemVersions = Collections2.transform(sourceCodeSystemVersions, CODE_SYSTEM_VERSION_CONVERTER);
		return VERSION_ID_ORDERING.immutableSortedCopy(targetCodeSystemVersions);
	}

	private ICodeSystemVersion toCodeSystemVersion(
			final com.b2international.snowowl.datastore.ICodeSystemVersion sourceCodeSystemVersion, 
			final String version) {

		if (null == sourceCodeSystemVersion) {
			throw new CodeSystemVersionNotFoundException(version);
		} else {
			return CODE_SYSTEM_VERSION_CONVERTER.apply(sourceCodeSystemVersion);
		}
	}
}
