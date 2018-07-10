package org.ihtsdo.termserver.scripting.creation;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.junit.*;

public class TestHairFollicleCreator {
	GraphLoader gl = GraphLoader.getGraphLoader();
	Concept hairFollicle;
	Concept upperArm;
	
	@Before
	public void setup () {
		//gl.registerConcept(SnomedUtils.createConcept(term, semTag, parent));
	}

	@Test
	public void hairFollicleCreatorTest () {
		ConceptCreationSupervisor supervisor = ConceptCreationSupervisor.getSupervisor();
		//supervisor.createConcept(inspiration)
	}
}
