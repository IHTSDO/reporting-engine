Classes for Generating Delta Archives
=====================================


### Extract Extension Components

This class is intended to extract a subset of components from an extension for promotion to some upstream module such as the International Edition.

A zip file (eg TESTAUTUS1.zip) should be placed in /releases wher the script is run.
The iR argument is a list of available relationshipids, but this can be "dummy.txt" in the first instance, and the script will report back how many are required.

```bash
cd reporting-engine-worker
java -cp target/reporting-engine-worker*.jar org.ihtsdo.termserver.scripting.delta.ExtractExtensionComponents  -c uat-ims-ihtsdo=2Lx9UDGGHcaCFpZaXaCVbg00 -a tmorrison -d N -p TESTAUTUS1 -iR /Users/Peter/Google\ Drive/005_Ad_hoc_queries/038_US_concept_promotion/prod_rel_ids_all.txt -f /Users/Peter/Google\ Drive/005_Ad_hoc_queries/038_US_concept_promotion/us_nutrition_all.txt
```
The Extension Extraction class is unique in that it offers live checking that all dependencies referenced by the concept (parents and attributes types/values) are active in the live Terminology Server, and replaces them with alternatives suggested by historical associations.