package org.ihtsdo.termserver.scripting

import spock.lang.Specification

class EclCacheTest extends Specification {
    def "Test isSimple logic with #testName: ECL='#ecl'"() {
        when:
            def cache = new EclCache(null, null)

        then:
            cache.isSimple(ecl) == expectedResult

        where:
            testName                       | ecl                                                                 || expectedResult
            "Simple SCTID"                 | "1234"                                                              || true
            "Simple SCTID with bar"        | "1234 |"                                                            || true
            "Simple SCTID with two bars"   | "1234 |foo|"                                                        || true
            "Complex SCTID with four bars" | "1234 |foo| 4321 | bar |"                                           || false
            "Complex with {"               | "{"                                                                 || false
            "Complex with ,"               | ","                                                                 || false
            "Complex with ^"               | "^"                                                                 || false
            "Complex with ("               | "("                                                                 || false
            "Complex with !"               | "!"                                                                 || false
            "Complex with :"               | ":"                                                                 || false
            "Complex with MINUS"           | "1234 MINUS 4321"                                                   || false
            "Complex with AND"             | "1234 AND 4321"                                                     || false
            "Example from RP-700"          | '<<49755003 |Morph.. | term=(wild: "*dysplasia" wild:"*neoplasia")' || false
    }
}
