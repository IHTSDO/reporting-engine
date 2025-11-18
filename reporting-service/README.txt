### Setting up a local database for reporting-service

create database dev_int_reporting_service;
CREATE USER 'reporting_service'@'localhost' IDENTIFIED BY 'snomed';
ALTER USER 'reporting_service'@'localhost' IDENTIFIED WITH mysql_native_password BY 'snomed';
GRANT ALL PRIVILEGES ON dev_int_reporting_service.* TO 'reporting_service'@'localhost';