#!/bin/bash

#RPW_Version
RP_Environment="dev"

# Check that we're running as root
if [[ $EUID -ne 0 ]]; then
	echo "This script must be run as root" 
	exit 1
fi

#In Dev and UAT we'll install the latest version
#In Prod, we want a specific number

if [ "$RP_Environment" == "dev"]; then
    # TODO Do we need a function here to edit /etc/apt/sources.d ?
	RPW_Version="latest"
elif [ "$RP_Environment" == "dev"]; then
	RPW_Version="latest"
else 
	RPW_Version="3.6.1"
fi

{ # try
	apt-get update
	apt-get install reporting-worker=$RPW_Version
} || { # catch
    # save log for exception 
}

supervisorctl start reporting-worker