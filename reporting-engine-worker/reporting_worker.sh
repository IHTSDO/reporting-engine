#!/bin/bash

#Variables to be set by calling script: RPW_Environment, RPW_Version
echo "****************************************"
echo "Reporting Platform Worker installation for environment: $RPW_Environment and version: $RPW_Version"
echo "Time is now: `date`"
echo "****************************************"
echo

# Check that we're running as root
if [[ $EUID -ne 0 ]]; then
	echo "This script must be run as root"
	exit 1
fi

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
	sed -i "/schedule.manager.queue.request/c\schedule.manager.queue.request = ${RPW_Environment}.schedule-manager.request" ${rp_config}
	sed -i "/schedule.manager.queue.response/c\schedule.manager.queue.response = ${RPW_Environment}.schedule-manager.response" ${rp_config}
	sed -i "/schedule.manager.queue.metadata/c\schedule.manager.queue.metadata = ${RPW_Environment}.schedule-manager.metadata" ${rp_config}
	sed -i "/reports.s3.cloud.path=/c\reports.s3.cloud.path=authoring/reporting-service/${RPW_Environment}" ${rp_config}
else
	echo "$rp_config not found"
fi

supervisorctl start reporting-engine-worker || true;
echo "Installation and configuration complete at `date`"
