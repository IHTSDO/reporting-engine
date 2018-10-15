package org.ihtsdo.termserver.scripting.fixes.refset;


import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

public abstract class RefsetFixer extends BatchFix {
	
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
