Terminology Server - Script Engine
===============================================
This project is a library of classes which work with a local (cached) snapshot of some specified authoring project, so that only concepts which are targeted for modification are actually requested in full from the Terminology Server.

The types of jobs undertaken by these classes cover:
* Modifications to concepts, grouped into tasks.
* Modifications generated as a delta archive
* Reports
* Generating refsets
* Generating delta archives of translations
* Generating Snapshots
* Taking a delta archive and playing the changes it describes via the API into tasks
* Extract all relevant content from an extension or edition of SNOMED CT


Download and Build
================================

Requirement - Java 8 (or later), Maven, Git

```bash	
git clone git@git.ihtsdotools.org:ihtsdo/reporting-engine.git
cd reporting-engine/script-engine
mvn clean package
```

Running 
=======
Classes can either be run from the command line, or via an IDE.  In either event, the steps are roughly the same:
* Build the code
* Specify the project, authoring and cookie string as parameters to the class 

eg ` -c uat-ims-ihtsdo=0IPyP4K3zs1234rdAhxFqw00 -a pwilliams -p BLKUPDATE2 `

You may also need to supply, say, a list of relationship ids to be consumed using the -iR parameter, or a file to be processed using "-f".  A full list of command line flags can be found [here](docs/commandLineFlags.md)

* It's recommended that you give the process sufficient memory to run eg `-Xms14g -Xmx16g`
* As well as the parameters specified via the command line, the runtime code will ask for a number of options to be confirmed / selected.   Most importantly, a number from 1 - 9 which identifies which environment is to be selected.   
* Any default option specified in square brackets can be accepted by pressing return.

```	
Select an environment 
  0: http://localhost:8080/
  1: https://dev-authoring.ihtsdotools.org/
  2: https://uat-authoring.ihtsdotools.org/
  3: https://uat-flat-termserver.ihtsdotools.org/
  4: https://prod-authoring.ihtsdotools.org/
  5: https://dev-ms-authoring.ihtsdotools.org/
  6: https://uat-ms-authoring.ihtsdotools.org/
  7: https://prod-ms-authoring.ihtsdotools.org/
Choice: 2
Specify Project [DRUGJUL18]: 
Number of concepts per task [40]: 
Time delay between tasks (throttle) seconds [15]: 20
Time delay between concepts (throttle) seconds [5]: 3
```

* All classes will produce a time and environment stamped processing report, which will be the report output itself in the case of a report.  This can be written either to Google Sheets (if an appropriate developer token is supplied) or CSV on the local disk.

Content Extraction
==================

Supplied with a zip archive, (optionally also a dependency release) and a list of concept ids, the Extension Extract class can be used to extract those components along with any dependencies, move them into the target module and produce a delta archive as output. In addition, the process will check the active status of any components in the target system, and attempt to provide alternatives (via historical associations) if the referenced components have been made inactive.   If this should occur, new relationship ids may be required and these can be provided in a text file.

Steps
-----

Run the script from the script-engine subdirectory.  Taking an extract from the Nebraska Extension as a worked example:

``` bash
cd script-engine

java  -Xms14G -Xmx14G -cp target/scripting-tools-*.jar \
org.ihtsdo.termserver.scripting.delta.ExtractExtensionComponents \
-c ims-ihtsdo=FfGemoPuRxg68YJeZYOfXQAAAAAAAIACcHdpbGxpYW1z \
-iR dummy -p Nebraska_20210316.zip -f extract.txt \
-dp SnomedCT_InternationalRF2_PRODUCTION_20210131T120000Z.zip
```

Each of those parameters is described below:

| Argument | Meaning |
| -------- | ------- |
| -f extract.txt | A text file of the identifiers of the concepts to be extracted, one per line. |
| -c ims-ihtsdo=FfGemoPuRxg68YJeZYOfXQAAAAAAAIACcHdpbGxpYW1z | An authorised cookie is required on the relevant SNOMED International environment. This can be found by right-clicking on the 'lock' icon to the left of the browser URL address bar when viewing one of the tools provided by SNOMED International. |
| -Xms14G -Xmx14G | Ensures the process has 14Gb of memory to work with |
| -dp SnomedCT_InternationalRF2_PRODUCTION_20200131T120000Z.zip | Optional dependency package, but needed when working with an extension (this should also be in the releases subdirectory) |
| -cp script-engine/target/scripting-tools-*.jar | Run the jar file that was produced during the build |
| -p Nebraska_20200316.zip | This is the ""project"" file, either an Edition package or Extension package. In this case we're working with an extension rather than an edition archive, so we must also supply the dependency release via the -dp parameters. These files are expected to exist in a 'releases' folder in the current directory.
| org.ihtsdo.termserver.scripting.delta.ExtractExtensionComponents | This is the Java class within the Jar that performs the extract |
| -iR dummy | Uses dummy SCTIDs where they're needed when new relationships need to be generated. -iD and -iC are equivalent files for descriptions and concepts. In future developments, it's likely that we'll need iD and not iR |

If the file you are working with does not come with a complete set of components as a delta file, unzip then rename the contents of the archive to a snapshot eg using:

```bash
find . -exec rename 's|Delta|Snapshot|' {} + 
```

At the end of the process a delta RF2 zip file will be produced and saved in the directory where you ran the command.
