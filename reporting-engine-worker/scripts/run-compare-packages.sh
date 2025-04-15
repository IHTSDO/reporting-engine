#!/bin/bash

# Check for 5 input variables
if [ $# -lt 5 ]
	then
		echo "Not enough arguments supplied. Usage: run-compare-packages.sh <Previous Archive Name> <Previous Archive Location> <Current Archive Name> <Current Archive Location> <Run Folder>" >&2
		exit 1
fi

# Run with five parameters
previousName=$1
previousZip=$2
currentName=$3
currentZip=$4
runFolder=$5

rootFolder=$(pwd)

# Debug
echo "Running script with the following parameters:"
echo "Previous package ${previousName}: ${previousZip}"
echo "Current package ${currentName}: ${currentZip}"
echo "Run folder: ${runFolder}"

echo -n "Root folder: "
echo $rootFolder

# Create a new folder and copy the main scripts into it
mkdir -p ${rootFolder}/results/${runFolder}

cp ${rootFolder}/scripts/compare-packages-parallel.sh ${rootFolder}/results/${runFolder}/compare-packages-parallel.sh
cp ${rootFolder}/scripts/compare-files.sh ${rootFolder}/results/${runFolder}/compare-files.sh

# Run the compare script from the new folder
cd ${rootFolder}/results/${runFolder} || exit 1

source compare-packages-parallel.sh "${previousName}" "${rootFolder}/releases/${previousZip}" "${currentName}" "${rootFolder}/releases/${currentZip}"

echo "Copy the results to S3"
aws s3 cp ${rootFolder}/results/${runFolder}/target/c s3://snomed-compares/${runFolder} --recursive