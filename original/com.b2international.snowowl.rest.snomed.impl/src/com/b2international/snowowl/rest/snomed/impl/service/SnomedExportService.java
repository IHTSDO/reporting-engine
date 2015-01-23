/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.impl.service;

import static com.b2international.commons.StringUtils.isEmpty;
import static com.b2international.snowowl.datastore.BranchPathUtils.createPath;
import static com.b2international.snowowl.datastore.BranchPathUtils.createVersionPath;
import static com.b2international.snowowl.snomed.common.ContentSubType.DELTA;
import static com.b2international.snowowl.snomed.common.ContentSubType.FULL;
import static com.b2international.snowowl.snomed.common.ContentSubType.SNAPSHOT;
import static com.b2international.snowowl.snomed.exporter.model.SnomedRf2ExportModel.createExportModelForSingleRefSet;
import static com.b2international.snowowl.snomed.exporter.model.SnomedRf2ExportModel.createExportModelWithAllRefSets;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.ImmutableMap.of;

import java.io.File;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;

import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.rest.snomed.domain.ISnomedExportConfiguration;
import com.b2international.snowowl.rest.snomed.domain.ISnomedRefSetExportConfiguration;
import com.b2international.snowowl.rest.snomed.domain.Rf2ReleaseType;
import com.b2international.snowowl.rest.snomed.exception.SnomedExportException;
import com.b2international.snowowl.rest.snomed.service.ISnomedExportService;
import com.b2international.snowowl.snomed.common.ContentSubType;
import com.b2international.snowowl.snomed.exporter.model.SnomedRf2ExportModel;

/**
 * {@link ISnomedExportService export service} implementation for the SNOMED&nbsp;CT ontology.
 * @author akitta
 *
 */
public class SnomedExportService implements ISnomedExportService {

	private static final Map<Rf2ReleaseType, ContentSubType> TYPE_MAPPING = of(
			Rf2ReleaseType.DELTA, DELTA, 
			Rf2ReleaseType.SNAPSHOT, SNAPSHOT, 
			Rf2ReleaseType.FULL, FULL
		);
	
	@Override
	public File export(final ISnomedExportConfiguration configuration) {
		checkNotNull(configuration, "Configuration was missing for the export operation.");
		return tryExport(convertConfiguration(configuration));
	}

	private File tryExport(final SnomedRf2ExportModel model) {
		try {
			return doExport(model);
		} catch (final Exception e) {
			return throwExportException(
					isEmpty(e.getMessage()) 
						? "Error occurred while exporting SNOMED CT." 
						: e.getMessage());
		}
	}

	private File doExport(final SnomedRf2ExportModel model) throws Exception {
		return getDelegateService().export(model, new NullProgressMonitor());
	}

	private com.b2international.snowowl.snomed.exporter.service.SnomedExportService getDelegateService() {
		return new com.b2international.snowowl.snomed.exporter.service.SnomedExportService();
	}

	private SnomedRf2ExportModel convertConfiguration(
			final ISnomedExportConfiguration configuration) {
		
		checkNotNull(configuration, "Configuration was missing for the export operation.");
		final ContentSubType contentSubType = convertType(configuration.getRf2ReleaseType());
		final IBranchPath exportBranchPath = getExportBranchPath(configuration);
		
		final SnomedRf2ExportModel model;
		if (configuration instanceof ISnomedRefSetExportConfiguration) {
			final String refSetId = ((ISnomedRefSetExportConfiguration) configuration).getRefSetId();
			checkNotNull(refSetId, "Reference set identifier was missing from the export configuration.");
			model = createExportModelForSingleRefSet(refSetId, contentSubType, exportBranchPath);
		} else {
			model = createExportModelWithAllRefSets(contentSubType, exportBranchPath);
		}
		
		final String namespaceId = configuration.getNamespaceId();
		checkNotNull(namespaceId, "Namespace ID was missing from the export configuration.");
		model.setNamespace(namespaceId);
		model.getModulesToExport().addAll(configuration.getModuleDependencyIds());
		model.setDeltaExportStartEffectiveTime(configuration.getDeltaExportStartEffectiveTime());
		model.setDeltaExportEndEffectiveTime(configuration.getDeltaExportEndEffectiveTime());
		model.setDeltaExport(ContentSubType.DELTA.equals(contentSubType));
		
		return model; 
	}
	
	private IBranchPath getExportBranchPath(final ISnomedExportConfiguration configuration) {
		checkNotNull(configuration, "Configuration was missing for the export operation.");
		final String version = configuration.getVersion();
		final String taskId = configuration.getTaskId();
		
		checkNotNull(version, "Code system version was missing from the export configuration.");
		final IBranchPath branchPathSuffix = createVersionPath(version);
		return isEmpty(taskId) 
			? branchPathSuffix 
			: createPath(branchPathSuffix, taskId);
	}
	
	private ContentSubType convertType(final Rf2ReleaseType typeToConvert) {
		checkNotNull(typeToConvert, "RF2 release type was missing from the export configuration.");
		final ContentSubType type = TYPE_MAPPING.get(typeToConvert);
		return checkNotNull(type, "Unknown or unexpected RF2 release type of: " + typeToConvert + ".");
	}

	private <T> T checkNotNull(final T arg, final String message) {
		return null == arg ? this.<T>throwExportException(message) : arg;
	}

	private <T> T throwExportException(final String message) {
		throw new SnomedExportException(nullToEmpty(message));
	}
}
