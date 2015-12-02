package org.ihtsdo.snowowl.authoring.single.api.validation;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.exceptions.BadRequestException;
import com.b2international.snowowl.datastore.BranchPathUtils;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserConcept;
import com.b2international.snowowl.snomed.api.impl.DescriptionService;
import com.b2international.snowowl.snomed.datastore.SnomedTerminologyBrowser;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.ihtsdo.drools.RuleExecutor;
import org.ihtsdo.drools.exception.BadRequestRuleExecutorException;
import org.ihtsdo.drools.response.InvalidContent;
import org.ihtsdo.snowowl.authoring.single.api.validation.domain.ValidationConcept;
import org.ihtsdo.snowowl.authoring.single.api.validation.service.ValidationConceptService;
import org.ihtsdo.snowowl.authoring.single.api.validation.service.ValidationDescriptionService;
import org.ihtsdo.snowowl.authoring.single.api.validation.service.ValidationRelationshipService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class SnomedBrowserValidationService {

	@Autowired
	private IEventBus eventBus;

	private RuleExecutor ruleExecutor;

	public SnomedBrowserValidationService() {
		ruleExecutor = newRuleExecutor();
	}

	public List<SnomedInvalidContent> validateConcept(String branchPath, ISnomedBrowserConcept browserConcept) {
		IBranchPath path = BranchPathUtils.createPath(branchPath);
		SnomedTerminologyBrowser terminologyBrowser = ApplicationContext.getServiceForClass(SnomedTerminologyBrowser.class);
		
		ValidationConceptService validationConceptService = new ValidationConceptService(path, terminologyBrowser);
		ValidationDescriptionService validationDescriptionService = new ValidationDescriptionService(new DescriptionService(eventBus, branchPath));
		ValidationRelationshipService validationRelationshipService = new ValidationRelationshipService(eventBus, branchPath);
		try {
			List<InvalidContent> list = ruleExecutor.execute(new ValidationConcept(browserConcept), validationConceptService, validationDescriptionService, validationRelationshipService,
					false, false);
			List<SnomedInvalidContent> invalidContent = Lists.transform(list, new Function<InvalidContent, SnomedInvalidContent>() {
				@Override
				public SnomedInvalidContent apply(InvalidContent input) {
					return new SnomedInvalidContent(input);
				}
			});
			return invalidContent;
		} catch (BadRequestRuleExecutorException e) {
			throw new BadRequestException(e.getMessage());
		}
	}

	public int reloadRules() {
		ruleExecutor = newRuleExecutor();
		return ruleExecutor.getRulesLoaded();
	}

	private RuleExecutor newRuleExecutor() {
		// TODO: Move path to configuration
		return new RuleExecutor("/opt/termserver/snomed-drools-rules");
	}

}
