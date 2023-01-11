# Current GMDN monthly delta manual steps
## Step 1: Download GMDN files from sftp://ftp.gmdnagency.org 
(You can use an FTP client such as FileZilla or custom java client. See config.properties for log in details)

We need previous and current month data files to do the comparison.
e.g gmdnData22_12.zip and gmdnData23_1.zip

## Step 2: Unzip data files into xml files. 
e.g gmdnData22_12.xml gmdnData23_1.xml

## Step 3: Run GmdnContentDeltaGeneratorRunner with above xml files to generate delta reports

- ActiveTermsDelta.csv
- ModifiedTermsDelta.csv
- ObsoleteTermsDelta.csv

## Step 4: Update GMDN monthly delta report GoogleSheet for Mark and Donna
- Current year:
https://docs.google.com/spreadsheets/d/1_mlX_zp5FQYeTz0LpUdTDl_IHQwq9TUSZSXPt-9muHc/edit?pli=1#gid=0

- Last year:
https://docs.google.com/spreadsheets/d/1fcKRGC-1h__dyaz76GzwD-3cZ2FtOHmTEJlD5glH2nI/edit#gid=547415301
