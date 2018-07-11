package org.ihtsdo.termserver.scripting.creation;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.CharacteristicType;
import org.junit.*;

public class TestHairFollicleCreator {
	GraphLoader gl = GraphLoader.getGraphLoader();
	Concept hairFollicle;
	Concept skin;
	Concept skinOfUpperArm;
	Concept shoulder;
	Concept upperArm;
	
	@Before
	public void setup () {
		hairFollicle = new Concept ("67290009", "Hair follicle structure (body structure)" );
		skin = new Concept ("127856007","Skin and/or subcutaneous tissue structure (body structure)");
		skinOfUpperArm = new Concept ("416827001", "Skin and/or subcutaneous tissue structure of upper arm (body structure)|");
		upperArm = new Concept ("40983000", "Upper arm structure (body structure)");
		shoulder = new Concept ("420657004","Structure of shoulder and/or upper arm (body structure)");
		
		upperArm.addParent(CharacteristicType.STATED_RELATIONSHIP, shoulder);
		skin.addChild(CharacteristicType.INFERRED_RELATIONSHIP, skinOfUpperArm);
		
		gl.registerConcept(hairFollicle);
		gl.registerConcept(skin);
		gl.registerConcept(skinOfUpperArm);
		gl.registerConcept(upperArm);
		gl.registerConcept(shoulder);
	}

	@Test
	public void hairFollicleCreatorTest () throws TermServerScriptException {
		ConceptCreationSupervisor supervisor = ConceptCreationSupervisor.getSupervisor();
		Concept[] inspiration = new Concept[] { hairFollicle, skinOfUpperArm };
		Concept newFollicle = supervisor.createConcept(new HashSet<Concept>(Arrays.asList(inspiration)));
		assertEquals(newFollicle.getChildren(CharacteristicType.STATED_RELATIONSHIP).size(), 2);
	}
}
