package org.ihtsdo.termserver.scripting.creation;

import static org.junit.Assert.assertEquals;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.junit.*;

public class TestHairFollicleCreator implements ScriptConstants {
	GraphLoader gl = GraphLoader.getGraphLoader();
	Concept hairFollicle;
	Concept skin;
	Concept skinOfUpperArm;
	Concept shoulder;
	Concept upperArmStructure;
	
	@Before
	public void setup () {
		hairFollicle = SnomedUtils.createConcept("67290009", "Hair follicle structure (body structure)" );
		skin = SnomedUtils.createConcept("127856007","Skin and/or subcutaneous tissue structure (body structure)");
		skinOfUpperArm = SnomedUtils.createConcept("416827001", "Skin and/or subcutaneous tissue structure of upper arm (body structure)|");
		upperArmStructure = SnomedUtils.createConcept("40983000", "Upper arm structure (body structure)");
		shoulder = SnomedUtils.createConcept("420657004","Structure of shoulder and/or upper arm (body structure)");
		
		//All of these have to be added to the Body Structure hierarchy so we can find them
		BODY_STRUCTURE.addChild(CharacteristicType.INFERRED_RELATIONSHIP, hairFollicle);
		BODY_STRUCTURE.addChild(CharacteristicType.INFERRED_RELATIONSHIP, skin);
		BODY_STRUCTURE.addChild(CharacteristicType.INFERRED_RELATIONSHIP, skinOfUpperArm);
		BODY_STRUCTURE.addChild(CharacteristicType.INFERRED_RELATIONSHIP, upperArmStructure);
		BODY_STRUCTURE.addChild(CharacteristicType.INFERRED_RELATIONSHIP, shoulder);
		
		upperArmStructure.addParent(CharacteristicType.STATED_RELATIONSHIP, shoulder);
		upperArmStructure.addParent(CharacteristicType.INFERRED_RELATIONSHIP, shoulder);
		skin.addChild(CharacteristicType.INFERRED_RELATIONSHIP, skinOfUpperArm);
		
		gl.registerConcept(hairFollicle);
		gl.registerConcept(skin);
		gl.registerConcept(skinOfUpperArm);
		gl.registerConcept(upperArmStructure);
		gl.registerConcept(shoulder);
	}

	@Test
	public void hairFollicleCreatorTest () throws TermServerScriptException {
		ConceptCreationSupervisor supervisor = ConceptCreationSupervisor.getSupervisor();
		Concept[] inspiration = new Concept[] { hairFollicle, skinOfUpperArm };
		Concept newFollicle = supervisor.createConcept(new HashSet<Concept>(Arrays.asList(inspiration)));
		assertEquals(newFollicle.getChildren(CharacteristicType.STATED_RELATIONSHIP).size(), 1);
		assertEquals(newFollicle.getParents(CharacteristicType.STATED_RELATIONSHIP).size(), 2);

	}
}
