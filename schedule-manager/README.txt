### Setting up a local database for Schedule-Manager

create database dev_int_schedule_manager;
CREATE USER 'schedule_manager'@'localhost' IDENTIFIED BY 'snomed';
ALTER USER 'schedule_manager'@'localhost' IDENTIFIED WITH mysql_native_password BY 'snomed';
GRANT ALL PRIVILEGES ON dev_int_schedule_manager.* TO 'schedule_manager'@'localhost';