/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.service.codesystem;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.transform;

import java.util.Collection;
import java.util.List;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.datastore.TerminologyRegistryService;
import com.b2international.snowowl.datastore.UserBranchPathMap;
import com.b2international.snowowl.rest.domain.codesystem.ICodeSystem;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemNotFoundException;
import com.b2international.snowowl.rest.impl.domain.codesystem.CodeSystem;
import com.b2international.snowowl.rest.service.codesystem.ICodeSystemService;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Ordering;

/**
 * @author apeteri
 */
public class CodeSystemServiceImpl implements ICodeSystemService {

	private static final Function<? super com.b2international.snowowl.datastore.ICodeSystem, ICodeSystem> CODE_SYSTEM_CONVERTER = 
			new Function<com.b2international.snowowl.datastore.ICodeSystem, ICodeSystem>() {

		@Override
		public ICodeSystem apply(final com.b2international.snowowl.datastore.ICodeSystem input) {
			final CodeSystem result = new CodeSystem();
			result.setCitation(input.getCitation());
			result.setName(input.getName());
			result.setOid(input.getOid());
			result.setOrganizationLink(input.getOrgLink());
			result.setPrimaryLanguage(input.getLanguage());
			result.setShortName(input.getShortName());
			return result;
		}
	};

	private static final Ordering<ICodeSystem> SHORT_NAME_ORDERING = Ordering.natural().onResultOf(new Function<ICodeSystem, String>() {
		@Override
		public String apply(final ICodeSystem input) {
			return input.getShortName();
		}
	});

	protected static final UserBranchPathMap MAIN_BRANCH_PATH_MAP = new UserBranchPathMap();

	protected static TerminologyRegistryService getRegistryService() {
		return ApplicationContext.getServiceForClass(TerminologyRegistryService.class);
	}

	@Override
	public List<ICodeSystem> getCodeSystems() {
		return toSortedCodeSystemList(getRegistryService().getCodeSystems(MAIN_BRANCH_PATH_MAP));
	}

	@Override
	public ICodeSystem getCodeSystemByShortNameOrOid(String shortNameOrOid) {
		checkNotNull(shortNameOrOid, "Shortname Or OID parameter may not be null.");
		final TerminologyRegistryService service = getRegistryService();
		com.b2international.snowowl.datastore.ICodeSystem codeSystem = service.getCodeSystemByOid(MAIN_BRANCH_PATH_MAP, shortNameOrOid);
		if (codeSystem == null) {
			codeSystem = service.getCodeSystemByShortName(MAIN_BRANCH_PATH_MAP, shortNameOrOid);
			if (codeSystem == null) {
				throw new CodeSystemNotFoundException(shortNameOrOid);
			}
		}
		return toCodeSystem(codeSystem).get();
	}
	
	private Optional<ICodeSystem> toCodeSystem(final com.b2international.snowowl.datastore.ICodeSystem sourceCodeSystem) {
		return Optional.fromNullable(sourceCodeSystem).transform(CODE_SYSTEM_CONVERTER);
	}

	private List<ICodeSystem> toSortedCodeSystemList(final Collection<com.b2international.snowowl.datastore.ICodeSystem> sourceCodeSystems) {
		return SHORT_NAME_ORDERING.immutableSortedCopy(transform(sourceCodeSystems, CODE_SYSTEM_CONVERTER));
	}
}
