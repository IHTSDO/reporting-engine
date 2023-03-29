package org.ihtsdo.termserver.scripting.util

import spock.lang.Specification

class SnomedUtilsTest extends Specification {
    def "deconstruct FSN test for #input"() {
        expect:
            SnomedUtils.deconstructFSN(input) == new String[]{first, second}

        where:
            input       || first | second
            null        || null  | null
            ''          || ''    | null
            'foo'       || 'foo' | null
            'foo (bar)' || 'foo' | '(bar)'
    }

    def "deconstruct filename test '#filePath'"() {
        expect:
            SnomedUtils.deconstructFilename(filePath) == new String[]{path, fileName, extension}

        where:
            filePath                                   || path              | fileName     | extension
            null                                       || ''                | ''           | ''
            new File('')                               || ''                | ''           | ''
            new File('/')                              || ''                | ''           | ''
            new File('/foo')                           || ''                | 'foo'        | ''
            new File('/foo/')                          || ''                | 'foo'        | ''
            new File('/foo/bar')                       || '/foo'            | 'bar'        | ''
            new File('/foo/bar/')                      || '/foo'            | 'bar'        | ''
            new File('/foo/bar/myfile')                || '/foo/bar'        | 'myfile'     | ''
            new File('/foo/bar/myfile.txt')            || '/foo/bar'        | 'myfile'     | 'txt'
            new File('/foo/bar/wibble/myfile')         || '/foo/bar/wibble' | 'myfile'     | ''
            new File('/foo/bar/wibble/myfile.txt')     || '/foo/bar/wibble' | 'myfile'     | 'txt'
            new File('/foo/bar/wibble/myfile.txt.zip') || '/foo/bar/wibble' | 'myfile.txt' | 'zip'
    }

    def "make machine readable test '#eclIn' > '#eclOut'"() {
        expect:
            SnomedUtils.makeMachineReadable(eclIn) == eclOut

        where:
            eclIn                                                                                                      || eclOut
            '<<49755003'                                                                                               || '<<49755003'
            ' <<   49755003 '                                                                                          || '<<49755003'
            '  ( <<  49755003 )  '                                                                                     || '(<<49755003)'
            '( <<49755003 |Morph (morph)|  OR 81745001)'                                                               || '(<<49755003 OR 81745001)'
            '( << 49755003 |Morph (morph)|  OR 81745001)'                                                              || '(<<49755003 OR 81745001)'
            '( <<49755003 | morpf (sdkgd) << a | OR  81745001 | color |  ) {{term=(wild: "*color")}}'                  || '(<<49755003 OR 81745001){{TERM=(WILD:"*COLOR")}}'
            '(<<49755003 OR 81745001){{TERM=(WILD:"*DYSPLASIA" WILD:"*NEOPLASIA")}}'                                   || '(<<49755003 OR 81745001){{TERM=(WILD:"*DYSPLASIA" WILD:"*NEOPLASIA")}}'
            """  (  <<  49755003   OR   81745001  ) 
                      {  {  TERM  =  (  WILD    :  "  *  DYSPLASIA  " 
                      WILD  :  "  *  NEOPLASIA    "  )  }  }  """                                       || '(<<49755003 OR 81745001){{TERM=(WILD:"*DYSPLASIA" WILD:"*NEOPLASIA")}}'
            """ === 430621000 |Malignant neoplasm of lower respiratory tract (disorder)|   +   
                188361007 |Malignant neoplasm of thorax (disorder)|   +   
              126713003 |Neoplasm of lung (disorder)|   :    
              { 363698007 |Finding site (attribute)| = 39607008 |Lung structure (body structure)|  ,   
               116676008 |Associated morphology (attribute)| = 
                1240414004 |Malignant neoplasm (morphologic abnormality)| }  """                        || '===430621000 + 188361007 + 126713003:{363698007=39607008, 116676008=1240414004}'
    }
}
