Terminology Server Batch Scripting
===============================================
This project is a collection of classes which work with a local (cached) snapshot of some specified authoring project so that only concepts which are targetted for modification are actually requested in full from the Terminology Server.

The types of jobs undertaken by these classes cover:
* Modifications to concepts grouped into tasks.
* Modifications generated as a delta archive
* Reports
* Generating refsets
* Generating delta archives of translations
* Generating Snapshots
* Taking a delta archive and playing the changes it describes via the API into tasks

### Warning
This code was written at a time when requesting a SNAPSHOT export for local use was relatively inexpensive (eg 20mins).   Since such an operation now takes up to 90 minutes, it is preferable to generate a local SNAPSHOT by obtaining a current delta and adding it to the eg by using the [Snapshot Generator class](/docs/SnapshotGenerator.md)



Download and Build
================================

Requirement - Java 8, Maven, Git
```
git clone git@git.ihtsdotools.org:ihtsdo/termserver-scripting.git
cd termserver-scripting
mvn clean package
```

Running 
=======
Classes can either be run from the command line, or via an IDE.   In either event, the steps are roughly the same:
* Build the code
* Pre-obtain a Snapshot of the target project if you want to avoid .  This should be saved in the current directory as <ProjectName>_<environment>.zip 
* Obtain a logged in cookie string (eg by using the authoring tooling and using developers tools to view request headers)
* Specify the project, authoring and cookie string as parameters to the class 

eg -c uat-ims-ihtsdo=0IPyP4K3zs1234rdAhxFqw00 -a pwilliams -p BLKUPDATE2 

You may also need to supply, say, a list of relationship ids to be consumed usign the -iR parameter, or a file to be processed using -f   a full list of command line flags can be found [here](docs/commandLineFlags.md)

* It's recommended that you give the process sufficient memory to run eg `-Xms6g -Xmx8g`
