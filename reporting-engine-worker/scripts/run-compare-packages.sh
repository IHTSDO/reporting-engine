#!/bin/bash

# Check for 3 input variables
if [ $# -lt 5 ]
	then
		echo "Not enough arguments supplied. Usage: run_compare.sh <Left Archive Name> <Left Archive Location> <Right Archive Name> <Right Archive Location> <Run Folder>" >&2
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
echo $previousName
echo $previousZip
echo $currentName
echo $currentZip
echo $runFolder

echo "Root folder:"
echo $rootFolder

# Create a new folder and copy the main scripts into it
mkdir -p ${rootFolder}/results/${runFolder}

cp ${rootFolder}/scripts/compare-packages-parallel.sh ${rootFolder}/results/${runFolder}/compare-packages-parallel.sh
cp ${rootFolder}/scripts/compare-files.sh ${rootFolder}/results/${runFolder}/compare-files.sh

# Run the compare script from the new folder
cd ${rootFolder}/results/${runFolder} || exit 1

source compare-packages-parallel.sh "${previousName}" "${rootFolder}/releases/${previousZip}" "${currentName}" "${rootFolder}/releases/${currentZip}" -normaliseDates

echo "Copy the results to S3"
aws s3 cp ${rootFolder}/results/${runFolder}/target/c s3://snomed-compares/${runFolder} --recursive