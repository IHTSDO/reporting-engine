Classes for Generating Delta Archives
=====================================


### Extract Extension Components

This class is intended to extract a subset of components from an extension for promotion to some upstream module such as the International Edition.  It takes two archives:
* a snapshot of a source release where concepts are being selected from
* a current snapshot of some target release that will receive 

```bash
java -cp target/termserver-scripting.jar org.ihtsdo.termserver.scripting.delta.ExtractExtensionComponents  -c uat-ims-ihtsdo=2Lx9UDGGHcaCFpZaXaCVbg00 -a tmorrison -d N -p TESTAUTUS1 -iR /Users/Peter/Google\ Drive/005_Ad_hoc_queries/038_US_concept_promotion/prod_rel_ids_all.txt -f /Users/Peter/Google\ Drive/005_Ad_hoc_queries/038_US_concept_promotion/us_nutrition_all.txt
```
The Extension Extraction class is unique in that it offers a live 