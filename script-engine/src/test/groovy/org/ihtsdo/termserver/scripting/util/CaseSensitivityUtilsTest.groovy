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
        "Sperms/mL"   || false  // Only first letter capital before slash
    }

    def "test isAllNumbersOrSymbols"() {
        expect:
        caseSensitivityUtils.isAllNumbersOrSymbols(term) == expected

        where:
        term          || expected
        "123'45"        || true   // Numbers and symbols
        "hello"       || false  // Regular word
        "hello123"         || false  // Regular word with numbers
        "123é"         || false   // contains a letter character
        "ø123"          || false // contains a letter character
    }


}
