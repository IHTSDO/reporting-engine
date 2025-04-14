package org.ihtsdo.termserver.scripting.util

import spock.lang.Specification

class CaseSensitivityUtilsTest extends Specification {
    def caseSensitivityUtils = new CaseSensitivityUtils()

    def "test startsWithAcronym with term '#term'"() {
        expect:
        caseSensitivityUtils.startsWithAcronym(term) == expected

        where:
        term          || expected
        "NASA"        || true   // Acronym
        "IgE"         || true   // Acronym with mixed case
        "Hello"       || false  // Regular word
        "French-English"         || false   // Repetition of sentence capitalization in dashed word
        "abc"         || false  // Lowercase word
    }


}
