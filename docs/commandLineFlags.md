TS Scripting Command Line Flags
================================

Flag | Use | Example | Notes 
----- | ----- | ----- | -----
-a | Specified author's username | -a pwilliams | Only really needed when tasks are going to be created 
-c | Authenticated cookie string for intended environment | -c uat-ims-ihtsdo=0IPyP4K3zs1234rdAhxFqw00 
-d |  Dry-run  (Y/N) | When set to Y, no changes will be made to the database. | -d Y | This can be combined with an internal variable of `runStandAlone=true` so that no calls whatsoever are made to the TS.  Note that the flag defaults to Y for safety.
-f | Input file.  Specifies the location of a file to be processed | -f 20 |
-iC | Concept SCTID File | | Specifies the location of a text file containing concept identifiers.  Usually used when generating a delta archive.  Specify "dummy" as the filename to work with locally generated identifiers.
-iD | Description SCTID File | | Specifies the location of a text file containing description identifiers.  Usually used when generating a delta archive. Specify "dummy" as the filename to work with locally generated identifiers.
-iR | Relationship SCTID File | | Specifies the location of a text file containing relationship identifiers.  Usually used when generating a delta archive. Specify "dummy" as the filename to work with locally generated identifiers.
-n | Task size.  Specifies the number of concepts to be allocated to each task | -b 20 |
-o | Output directory | | Defaults to the current directory if not specified.
-p | Project | -p DRUGJUL18


### Flags specific to Batch Fixes

Flag | Use | Example | Notes 
-----|-----|-----|-----
-l | Limit | -l 5 | When testing, specify just the first few tasks to create, to save time.  Processing will halt after N tasks have been completed.
-r | Restart position | -r 23 | When working with an input file, specifies that the first N lines should be skipped.
-t | Task Delay | -t 25 | Specifies the time in seconds that the process should sleep for after completing each task
-t2 | Concept delay | -t 3 | Specifies the time in seconds that the process should sleep for after saving each concept