/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.service;

import static com.google.common.base.Preconditions.checkState;

import com.b2international.snowowlmod.rest.snomed.impl.domain.UserIdGenerationStrategy;
import org.eclipse.emf.cdo.transaction.CDOTransaction;
import org.eclipse.emf.ecore.util.EcoreUtil;

import com.b2international.commons.ClassUtils;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.rest.domain.ComponentCategory;
import com.b2international.snowowl.rest.domain.IComponentRef;
import com.b2international.snowowl.rest.exception.AlreadyExistsException;
import com.b2international.snowowl.rest.exception.BadRequestException;
import com.b2international.snowowl.rest.exception.ComponentNotFoundException;
import com.b2international.snowowl.rest.impl.domain.InternalComponentRef;
import com.b2international.snowowl.rest.impl.service.AbstractComponentServiceImpl;
import com.b2international.snowowl.rest.snomed.domain.ISnomedComponent;
import com.b2international.snowowl.rest.snomed.domain.ISnomedComponentInput;
import com.b2international.snowowl.rest.snomed.domain.ISnomedComponentUpdate;
import com.b2international.snowowl.rest.snomed.service.ISnomedComponentService;
import com.b2international.snowowl.snomed.Component;
import com.b2international.snowowl.snomed.Concept;
import com.b2international.snowowl.snomed.datastore.SnomedConceptLookupService;
import com.b2international.snowowl.snomed.datastore.SnomedEditingContext;
import com.b2international.snowowl.snomed.datastore.SnomedRefSetLookupService;
import com.b2international.snowowl.snomed.datastore.services.AbstractSnomedRefSetMembershipLookupService;
import com.b2international.snowowl.snomed.datastore.services.SnomedBranchRefSetMembershipLookupService;
import com.b2international.snowowl.snomed.snomedrefset.SnomedRefSetMember;
import com.b2international.snowowl.snomed.snomedrefset.SnomedStructuralRefSet;

/**
 * @author apeteri
 */
public abstract class AbstractSnomedComponentServiceImpl<C extends ISnomedComponentInput, R extends ISnomedComponent, U extends ISnomedComponentUpdate, M extends Component>
extends AbstractComponentServiceImpl<C, R, U, SnomedEditingContext, M>
implements ISnomedComponentService<C, R, U> {

	protected final SnomedConceptLookupService snomedConceptLookupService = new SnomedConceptLookupService();
	protected final SnomedRefSetLookupService snomedRefSetLookupService = new SnomedRefSetLookupService();

	protected AbstractSnomedComponentServiceImpl(final String handledRepositoryUuid, final ComponentCategory handledCategory) {
		super(handledRepositoryUuid, handledCategory);
	}

	protected AbstractSnomedRefSetMembershipLookupService getMembershipLookupService(final IBranchPath branchPath) {
		return new SnomedBranchRefSetMembershipLookupService(branchPath);
	}

	protected Concept getModuleConcept(final ISnomedComponentInput input, final SnomedEditingContext editingContext) {
		return getConcept(input.getModuleId(), editingContext);
	}

	protected Concept getConcept(final String conceptId, final SnomedEditingContext editingContext) {
		final Concept concept = snomedConceptLookupService.getComponent(conceptId, editingContext.getTransaction());
		if (null == concept) {
			throw new ComponentNotFoundException(ComponentCategory.CONCEPT, conceptId);
		}

		return concept;
	}
	
	protected SnomedStructuralRefSet getStructuralRefSet(final String refSetId, final CDOTransaction transaction) {
		final SnomedStructuralRefSet structuralRefSet = (SnomedStructuralRefSet) snomedRefSetLookupService.getComponent(refSetId, transaction);
		if (null == structuralRefSet) {
			throw new BadRequestException("Reference set with identifier %s does not exist.", refSetId);
		}

		return structuralRefSet;
	}

	@Override
	protected boolean componentExists(final C input) {
		if (input.getIdGenerationStrategy() instanceof UserIdGenerationStrategy) {
			return componentExists(createComponentRef(input, input.getIdGenerationStrategy().getId())); 
		} else {
			return false;
		}
	}

	@Override
	protected AlreadyExistsException createDuplicateComponentException(final C input) {
		// XXX: If we arrive here, the component ID must have been given by the user, any other case does not make sense
		checkState(input.getIdGenerationStrategy() instanceof UserIdGenerationStrategy);
		return new AlreadyExistsException(handledCategory.getDisplayName(), input.getIdGenerationStrategy().getId());
	}

	@Override
	protected SnomedEditingContext createEditingContext(final IComponentRef ref) {
		final InternalComponentRef internalRef = ClassUtils.checkAndCast(ref, InternalComponentRef.class);
		return new SnomedEditingContext(internalRef.getBranchPath());
	}

	@Override
	protected String getComponentId(final M component) {
		return component.getId();
	}

	protected boolean updateModule(final String newModuleId, final Component component, final SnomedEditingContext editingContext) {
		if (null == newModuleId) {
			return false;
		}

		final String currentModuleId = component.getModule().getId();
		if (!currentModuleId.equals(newModuleId)) {
			component.setModule(getConcept(newModuleId, editingContext));
			return true;
		} else {
			return false;
		}
	}

	protected boolean updateStatus(final Boolean newActive, final Component component, final SnomedEditingContext editingContext) {
		if (null == newActive) {
			return false;
		}

		if (component.isActive() != newActive) {
			component.setActive(newActive);
			return true;
		} else {
			return false;
		}
	}

	// Taken from WidgetBeanUpdater
	protected void removeOrDeactivate(final SnomedRefSetMember member) {
		if (member.isReleased()) {
			member.setActive(false);
			member.unsetEffectiveTime();
		} else {
			EcoreUtil.remove(member);
		}
	}
}
