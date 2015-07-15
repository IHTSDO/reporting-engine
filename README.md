IHTSDO Terminology & Allied Services
===============================================
THIS PAGE is still WIP mode

This project is built on top of SNOWOWL® Term server. It has number of modules each serving several different functional areas.


	`org.ihtsdo.snowowl.api.rest.common` Common artifact used by other services
		
	`org.ihtsdo.snowowl.api.rest` This module has commong utility service used within Refset tool. May be some scope to rename this and divide in different 		 module based on their utility
	
	`org.ihtsdo.snowowl.authoring.single.api.rest` Module used for single concept authoring.
	
	`org.ihtsdo.snowowl.authoring.template.api.rest` Module was used for template based authoring but is not currently enabled.


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

###Verify deployment 
When the IHTSDO End-points are combined with modules in this project such as Authoring (Single Concept Authoring - SCA), two main end points are provided:
1.  An enhanced SnowOwl endpoint:  http://localhost:8080/snowowl/snomed-ct/v2/  which can be seen on the development server as https://dev-term.ihtsdotools.org/snowowl/snomed-ct/v2/

2.  An authoring endpoint more tightly integrated with Jira and authoring workflow:  http://localhost:8080/snowowl/ihtsdo-sca/ which can be seen in development at https://dev-term.ihtsdotools.org/snowowl/ihtsdo-sca/

##Logging
Application logs are written in serviceability/logs




