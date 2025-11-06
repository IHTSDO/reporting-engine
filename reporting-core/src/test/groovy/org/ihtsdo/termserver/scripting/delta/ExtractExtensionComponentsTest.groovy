package org.ihtsdo.termserver.scripting.delta

import org.ihtsdo.termserver.scripting.domain.Concept
import org.ihtsdo.termserver.scripting.domain.Description
import spock.lang.Specification

import static org.ihtsdo.otf.RF2Constants.DescriptionType.*
import static org.ihtsdo.otf.RF2Constants.*

class ExtractExtensionComponentsTest extends Specification {
    def "should return #expectedResult when checking concept has correct SYN terms: #testName"() {
        when:
            Description description1 = Mock(Description)
            description1.isPreferred(lang1) >> pref1
            description1.getType() >> desc1

            Description description2 = Mock(Description)
            description2.isPreferred(lang2) >> pref2
            description2.getType() >> desc2

            Description description3 = Mock(Description)
            description3.isPreferred(lang3) >> pref3
            description3.getType() >> desc3

            Description description4 = Mock(Description)
            description4.isPreferred(lang4) >> pref4
            description4.getType() >> desc4

            Description description5 = Mock(Description)
            description5.isPreferred(lang5) >> pref5
            description5.getType() >> desc5

            Concept concept = Mock(Concept)
            concept.getDescriptions(ActiveState.ACTIVE) >> [description1, description2, description3, description4, description5]

        then:
            new ExtractExtensionComponents().validateLangResetEntryCount(concept, false, DescriptionType.SYNONYM) == expectedResult

        where:
            testName              | pref1 | lang1              | desc1           | pref2 | lang2              | desc2   | pref3 | lang3              | desc3           | pref4 | lang4              | desc4 | pref5 | lang5              | desc5   || expectedResult
            "Null check"          | null  | null               | null            | null  | null               | null    | null  | null               | null            | null  | null               | null  | null  | null               | null    || false
            "All OK"              | true  | GB_ENG_LANG_REFSET | SYNONYM         | true  | US_ENG_LANG_REFSET | SYNONYM | true  | GB_ENG_LANG_REFSET | FSN             | true  | US_ENG_LANG_REFSET | FSN   | false | US_ENG_LANG_REFSET | FSN     || true
            "Missing a preferred" | false | GB_ENG_LANG_REFSET | SYNONYM         | true  | US_ENG_LANG_REFSET | SYNONYM | true  | GB_ENG_LANG_REFSET | FSN             | true  | US_ENG_LANG_REFSET | FSN   | false | US_ENG_LANG_REFSET | FSN     || false
            "Missing a language"  | true  | US_ENG_LANG_REFSET | SYNONYM         | true  | US_ENG_LANG_REFSET | SYNONYM | true  | GB_ENG_LANG_REFSET | FSN             | true  | US_ENG_LANG_REFSET | FSN   | false | US_ENG_LANG_REFSET | FSN     || false
            "Missing a synonym"   | true  | GB_ENG_LANG_REFSET | TEXT_DEFINITION | true  | US_ENG_LANG_REFSET | SYNONYM | true  | GB_ENG_LANG_REFSET | FSN             | true  | US_ENG_LANG_REFSET | FSN   | false | US_ENG_LANG_REFSET | FSN     || false
            "Extra dtSYN"         | true  | GB_ENG_LANG_REFSET | SYNONYM         | true  | US_ENG_LANG_REFSET | SYNONYM | true  | GB_ENG_LANG_REFSET | FSN             | true  | US_ENG_LANG_REFSET | FSN   | true  | US_ENG_LANG_REFSET | SYNONYM || false
    }

    def "should return #expectedResult when checking concept has correct FSN terms: #testName"() {
        when:
        Description description1 = Mock(Description)
        description1.isPreferred(lang1) >> pref1
        description1.getType() >> desc1

        Description description2 = Mock(Description)
        description2.isPreferred(lang2) >> pref2
        description2.getType() >> desc2

        Description description3 = Mock(Description)
        description3.isPreferred(lang3) >> pref3
        description3.getType() >> desc3

        Description description4 = Mock(Description)
        description4.isPreferred(lang4) >> pref4
        description4.getType() >> desc4

        Description description5 = Mock(Description)
        description5.isPreferred(lang5) >> pref5
        description5.getType() >> desc5

        Concept concept = Mock(Concept)
        concept.getDescriptions(ActiveState.ACTIVE) >> [description1, description2, description3, description4, description5]

        then:
        new ExtractExtensionComponents().validateLangResetEntryCount(concept, false, DescriptionType.FSN) == expectedResult

        where:
        testName              | pref1 | lang1              | desc1           | pref2 | lang2              | desc2   | pref3 | lang3              | desc3           | pref4 | lang4              | desc4 | pref5 | lang5              | desc5   || expectedResult
        "Missing a fsn"       | true  | GB_ENG_LANG_REFSET | SYNONYM         | true  | US_ENG_LANG_REFSET | SYNONYM | true  | GB_ENG_LANG_REFSET | TEXT_DEFINITION | true  | US_ENG_LANG_REFSET | FSN   | false | US_ENG_LANG_REFSET | FSN     || false
        "Extra dtdtFSN"       | true  | GB_ENG_LANG_REFSET | SYNONYM         | true  | US_ENG_LANG_REFSET | SYNONYM | true  | GB_ENG_LANG_REFSET | FSN             | true  | US_ENG_LANG_REFSET | FSN   | true  | US_ENG_LANG_REFSET | FSN     || false
    }
}
