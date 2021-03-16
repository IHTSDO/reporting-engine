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

