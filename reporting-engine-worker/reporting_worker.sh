#!/bin/bash

# Check that we're running as root
if [[ $EUID -ne 0 ]]; then
        echo "This script must be run as root"
        exit 1
fi

# Source profile for Consul config
source /root/.profile

# Set variables
# RPW and RPW_Environment passed in via terraform
#
# Package and git repo release versions from Consul
RPW_Version=`consul kv get consul/reporting-service/${RPW_Env}/autoscaling/reporting.service.version`

# For testing and manual override
#RPW_Env="dev"
#RPW_Version="latest"
#RPW_Environment="dev-snowstorm-int"

#Variables to be set by calling script: RPW_Environment, RPW_Version, RPW_Env
echo "**************************************************************************"
echo "Reporting Platform Worker installation for environment: $RPW_Env"
echo " RPW_Environment: $RPW_Environment"
echo " RPW_Verison: $RPW_Version"
echo " " 
echo "Time is now: `date`"
echo "**************************************************************************"
echo " "

supervisorctl stop reporting-engine-worker || true;

if [[ "$RPW_Environment" =~ "dev" ]]; then
	echo "Setting snapshot apt repo"
	rm -f /etc/apt/sources.list.d/maven3_ihtsdotools_org_repository_debian_releases.list || true;
	echo "deb https://maven3.ihtsdotools.org/repository/debian-snapshots/ bionic main" > /etc/apt/sources.list.d/maven3_ihtsdotools_org_repository_debian_snapshots.list
else
	echo "Setting release apt repo"
	rm -f /etc/apt/sources.list.d/maven3_ihtsdotools_org_repository_debian_snapshots.list || true;
	echo "deb https://maven3.ihtsdotools.org/repository/debian-releases/ bionic main" > /etc/apt/sources.list.d/maven3_ihtsdotools_org_repository_debian_releases.list
fi

{ # try
	apt-get update
	echo "Time is now: `date`"
	if [[ "$RPW_Version" == "latest" ]]; then
		apt-get --assume-yes install reporting-engine-worker
	else
		apt-get --assume-yes install reporting-engine-worker=${RPW_Version}
	fi
} || { # catch
	echo "Failure detected installing reporting-worker"
	echo "Failure installing/updating to the latest package of reporting-engine-worker. The reporting-worker will not be started."
	echo "Manual intervention required!"
	exit 1
}

# stop the worker again as the install will have started it
supervisorctl stop reporting-engine-worker || true;

rp_config="/opt/reporting-engine-worker/application.properties"
echo "Configuring $rp_config"
if [ -e  $rp_config ];then
	sed -i "/reporting.service.queue.request/c\reporting.service.queue.request = ${RPW_Environment}.reporting-service.request" ${rp_config}
	sed -i "/reporting.service.queue.response/c\reporting.service.queue.response = ${RPW_Environment}.reporting-service.response" ${rp_config}
	sed -i "/reporting.service.queue.metadata/c\reporting.service.queue.metadata = ${RPW_Environment}.reporting-service.metadata" ${rp_config}
	sed -i "/reports.s3.cloud.path=/c\reports.s3.cloud.path=authoring/reporting-service/${RPW_Env}" ${rp_config}
	
	echo "reporting.worker.queue.service-alert = ${RPW_Env}.service-alert" >> $rp_config
else
	echo "$rp_config not found"
fi

supervisorctl start reporting-engine-worker || true;
echo "Installation and configuration complete at `date`"
