package org.ihtsdo.termserver.scripting.creation

import org.ihtsdo.otf.exception.TermServerScriptException
import org.ihtsdo.termserver.scripting.GraphLoader
import org.ihtsdo.termserver.scripting.domain.Concept
import org.ihtsdo.termserver.scripting.domain.ScriptConstants
import org.ihtsdo.termserver.scripting.util.SnomedUtils
import spock.lang.Specification

class TestHairFollicleCreator extends Specification implements ScriptConstants {
    GraphLoader gl = GraphLoader.graphLoader
    Concept hairFollicle
    Concept skin
    Concept skinOfUpperArm
    Concept shoulder
    Concept upperArmStructure

    def setup() {
        hairFollicle = SnomedUtils.createConcept('67290009', 'Hair follicle structure (body structure)')
        skin = SnomedUtils.createConcept('127856007', 'Skin and/or subcutaneous tissue structure (body structure)')
        skinOfUpperArm = SnomedUtils.createConcept('416827001', 'Skin and/or subcutaneous tissue structure of upper arm (body structure)|')
        upperArmStructure = SnomedUtils.createConcept('40983000', 'Upper arm structure (body structure)')
        shoulder = SnomedUtils.createConcept('420657004', 'Structure of shoulder and/or upper arm (body structure)')

        //All of these have to be added to the Body Structure hierarchy so we can find them
        BODY_STRUCTURE.addChild(CharacteristicType.INFERRED_RELATIONSHIP, hairFollicle)
        BODY_STRUCTURE.addChild(CharacteristicType.INFERRED_RELATIONSHIP, skin)
        BODY_STRUCTURE.addChild(CharacteristicType.INFERRED_RELATIONSHIP, skinOfUpperArm)
        BODY_STRUCTURE.addChild(CharacteristicType.INFERRED_RELATIONSHIP, upperArmStructure)
        BODY_STRUCTURE.addChild(CharacteristicType.INFERRED_RELATIONSHIP, shoulder)

        upperArmStructure.addParent(CharacteristicType.STATED_RELATIONSHIP, shoulder)
        upperArmStructure.addParent(CharacteristicType.INFERRED_RELATIONSHIP, shoulder)
        skin.addChild(CharacteristicType.INFERRED_RELATIONSHIP, skinOfUpperArm)

        gl.registerConcept(hairFollicle)
        gl.registerConcept(skin)
        gl.registerConcept(skinOfUpperArm)
        gl.registerConcept(upperArmStructure)
        gl.registerConcept(shoulder)
    }

    def "hair follicle creator test"() throws TermServerScriptException {
        given:
            ConceptCreationSupervisor supervisor = ConceptCreationSupervisor.supervisor
            Concept[] inspiration = new Concept[]{hairFollicle, skinOfUpperArm}
        when:
            Concept newFollicle = supervisor.createConcept(new HashSet<Concept>(Arrays.asList(inspiration)))
        then:
            1 == newFollicle.getChildren(CharacteristicType.STATED_RELATIONSHIP).size()
            2 == newFollicle.getParents(CharacteristicType.STATED_RELATIONSHIP).size()
    }
}
