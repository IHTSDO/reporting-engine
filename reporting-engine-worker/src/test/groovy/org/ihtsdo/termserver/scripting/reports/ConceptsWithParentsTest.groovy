package org.ihtsdo.termserver.scripting.reports

import org.ihtsdo.otf.RF2Constants
import org.ihtsdo.termserver.scripting.domain.Concept
import org.snomed.otf.scheduler.domain.JobRun
import spock.lang.Specification

class ConceptsWithParentsTest extends Specification {
    def "test append ecl '#ecl' and terms filter '#terms'"() {
        expect:
            (new ConceptsWithParents()).appendTermsFilter(ecl, terms) == excpectedEcl

        where:
            ecl                                                                   | terms            || excpectedEcl
            null                                                                  | null             || ''
            null                                                                  | 'heart,card'     || ''
            ''                                                                    | 'heart,card'     || ''
            ' <<  1144725004 '                                                    | 'capsule'        || '<< 1144725004 {{term = "capsule"}}'
            ' <<  38975235 '                                                      | null             || '<< 38975235'
            ' <<  38975235 '                                                      | ''               || '<< 38975235'
            ' <<  38975235 '                                                      | ' heart , card ' || '<< 38975235 {{term = ("heart" "card")}}'
            ' <<  38975235  {{term = ("heart" "card")}} '                         | ' foo , bar '    || '<< 38975235 {{term = ("heart" "card")}}'
            ' <  56265001 |Heart disease|  {{ C definitionStatus = primitive }} ' | ' heart , card ' || '< 56265001 |Heart disease| {{ C definitionStatus = primitive }} {{term = ("heart" "card")}}'
    }

    def "run the job and test results"() {
        given:
            Concept c100 = new Concept('100', 'FSN: 100')
            Concept c110 = new Concept('110', 'FSN: 110')
            Concept c111 = new Concept('111', 'FSN: 111')
            Concept c112 = new Concept('112', 'FSN: 112')
            Concept c120 = new Concept('120', 'FSN: 120')
            Concept c121 = new Concept('121', 'FSN: 121')
            Concept c122 = new Concept('122', 'FSN: 122')
            Concept c200 = new Concept('200', 'FSN: 200')
            Concept c210 = new Concept('210', 'FSN: 210')
            Concept c211 = new Concept('211', 'FSN: 211')
            Concept c212 = new Concept('212', 'FSN: 212')
            Concept c220 = new Concept('220', 'FSN: 220')
            Concept c221 = new Concept('221', 'FSN: 221')
            Concept c222 = new Concept('222', 'FSN: 222')
            c100.semTag = "(body structure 100)"
            c110.semTag = "(body structure 110)"
            c111.semTag = "(body structure 111)"
            c112.semTag = "(body structure 112)"
            c120.semTag = "(body structure 120)"
            c121.semTag = "(body structure 121)"
            c122.semTag = "(body structure 122)"
            c200.semTag = "(body structure 200)"
            c210.semTag = "(body structure 210)"
            c211.semTag = "(body structure 211)"
            c212.semTag = "(body structure 212)"
            c220.semTag = "(body structure 220)"
            c221.semTag = "(body structure 221)"
            c222.semTag = "(body structure 222)"

            // Inferred grand-parents.
            c110.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c112)
            c120.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c121)
            c120.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c122)
            c110.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c111)
            // Stated parents.
            c100.addParent(RF2Constants.CharacteristicType.STATED_RELATIONSHIP, c120)
            c100.addParent(RF2Constants.CharacteristicType.STATED_RELATIONSHIP, c110)
            // Inferred parents.
            c100.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c110)
            c100.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c120)

            // Inferred grand-parents.
            // NOTE: Randomised order for c2XX also duplicated parents/grand-parents ....
            c220.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c221)
            c220.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c222)
            c210.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c211)
            c210.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c212)
            c210.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c221) // Deliberate duplication.
            // Stated parents.
            c200.addParent(RF2Constants.CharacteristicType.STATED_RELATIONSHIP, c220)
            c200.addParent(RF2Constants.CharacteristicType.STATED_RELATIONSHIP, c220) // Deliberate duplication.
            c200.addParent(RF2Constants.CharacteristicType.STATED_RELATIONSHIP, c210)
            // Inferred parents.
            c200.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c210)
            c200.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c220)
            c200.addParent(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, c210) // Deliberate duplication.

            List<Concept> conceptsOfInterest = new ArrayList<Concept>()
            conceptsOfInterest.add(c200)
            conceptsOfInterest.add(c100)
            JobRun jobRunMock = Mock()
            jobRunMock.getMandatoryParamValue('ECL') >> '<< 100'
            jobRunMock.getParamValue('Filter for terms') >> 'capsule'
            def cwp = Spy(new ConceptsWithParents(jobRun: jobRunMock))
            cwp.findConcepts(_) >> conceptsOfInterest
            cwp.report(*_) >> null

            int reportRow = 0

        when:
            //TODO We need to work out how to stub the call to the superclass in super.postInit(tabNames, columnHeadings, false);
            cwp.determineConceptsOfInterest()
            cwp.runJob()

        then:
            cwp.summaryDetails["Concepts reported"] == 2
            cwp.summaryDetails["Issue count"] == 2

            2 * cwp.countIssue(_)
            0 * cwp.incrementSummaryInformation('White Listed Count')
            2 * cwp.incrementSummaryInformation('Concepts reported', 1)

            2 * cwp.report(*_) >>
                    {
                        reportMethodArguments ->
                            Concept val = reportMethodArguments[0]
                            String[] rest = reportMethodArguments[1]

                            if (reportRow == 0) {
                                assert val.conceptId == "100"
                                assert val.fsn == 'FSN: 100'
                                assert val.semTag == '(body structure 100)'
                                assert rest[0] == 'P'
                                assert rest[1] == '110 |FSN: 110|,\n120 |FSN: 120|'
                                assert rest[2] == '110 |FSN: 110|,\n120 |FSN: 120|'
                                assert rest[3] == '111 |FSN: 111|,\n112 |FSN: 112|,\n121 |FSN: 121|,\n122 |FSN: 122|'
                            } else {
                                assert val.conceptId == "200"
                                assert val.fsn == 'FSN: 200'
                                assert val.semTag == '(body structure 200)'
                                assert rest[0] == 'P'
                                assert rest[1] == '210 |FSN: 210|,\n220 |FSN: 220|'
                                assert rest[2] == '210 |FSN: 210|,\n220 |FSN: 220|'
                                assert rest[3] == '211 |FSN: 211|,\n212 |FSN: 212|,\n221 |FSN: 221|,\n222 |FSN: 222|'
                            }

                            reportRow += 1
                    }

            reportRow == 2
    }
}
