IHTSDO Terminology & Allied Services
===============================================
THIS PAGE is still WIP mode

This project is built on top of SNOWOWL® Term server. It has number of modules each serving several different functional areas.


	`org.ihtsdo.snowowl.api.rest.common` Common artifact used by other services
		
	`org.ihtsdo.snowowl.api.rest` This module has commong utility service used within Refset tool. May be some scope to rename this and divide in different 		 module based on their utility
	
	`org.ihtsdo.snowowl.authoring.api.rest` Module used solely for Template based authoring.


Build
========

All module build are maven based. Maven command must be passed with desired profile argument. As of `WRP-358` build profile argument is required. 

Default Profile is dev and when we are building for development environment, passing profile argument is not required. However for all other environments profile argument is required 

Production build must be done using following command `mvn -Pprod clean package` or at least `-Pprod` argument should be passed

similarly UAT build : `mvn -Puat clean package`

All packaging is java archived based.


Deployment
==========

All modules are deployed as jar plugin in OSGI container. 

Development Runtime & Deployment 
================================


## Pre-Requisite

TBD Describe data loading and index, term server binary


## Runtime

OSGI

## Deployment
Place the changed file in pickup directory

Verify deployment 
In order to test your deployment is good or not your should be able to browse api docs http://localhost:8080/snowowl/admin/api-viewer/ and invoke relevant services.

API Documentation is listed based on required realm. So chnage the realm on http://localhost:8080/snowowl/admin/api-viewer/

http://localhost:8080/snowowl/ihtsdo/api-docs -- Documentation for IHTSDO Snow Owl REST API
http://localhost:8080/snowowl/snomed-ct/api-docs -- Snow Owl Terminology Services API

##Logging
Application logs are written in serviceability/logs




