package org.ihtsdo.termserver.scripting.fixes.refset;


import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RefsetFixer extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(RefsetFixer.class);

	protected RefsetFixer(BatchFix clone) {
		super(clone);
	}

	@Override
	protected int doFix(Task task, Concept concept, String info)
			throws TermServerScriptException, ValidationFailure {
		// TODO Auto-generated method stub
		return 0;
	}

}
