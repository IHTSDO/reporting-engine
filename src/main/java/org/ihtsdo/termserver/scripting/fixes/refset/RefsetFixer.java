package org.ihtsdo.termserver.scripting.fixes.refset;

import java.io.IOException;
import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

public abstract class RefsetFixer extends BatchFix {
	
	protected RefsetFixer(BatchFix clone) {
		super(clone);
	}

	public void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);

	}


	@Override
	protected int doFix(Task task, Concept concept, String info)
			throws TermServerScriptException, ValidationFailure {
		// TODO Auto-generated method stub
		return 0;
	}

}
