#!/bin/bash

# Check for 6 input variables
if [ $# -lt 6 ]
	then
		echo "Not enough arguments supplied!" >&2
		exit 1
fi

# Run with five parameters
previousRelease=$1
previousZip=$2
currentRelease=$3
currentZip=$4
publishedFolder=$5
uploadFolder=$6

# Set other parameters
s3Release=snomed-releases/prod/published

rootFolder=$(pwd)

previousDate=`echo "${previousZip}" | sed "s/.*_\([0-9]\{8\}\).*/\1/"`
currentDate=`echo "${currentZip}" | sed "s/.*_\([0-9]\{8\}\).*/\1/"`

runFolder="${previousRelease}_${previousDate}-${currentRelease}_${currentDate}"

# Debug
echo "Running script with the following parameters:"
echo $previousRelease
echo $previousZip
echo $currentRelease
echo $currentZip
echo $publishedFolder
echo $uploadFolder

echo "Root folder and run folder:"
echo $rootFolder
echo $runFolder

# Create a new folder and copy the main scripts 
mkdir ${rootFolder}/${runFolder}
cp ${rootFolder}/compare-files.sh ${rootFolder}/${runFolder}/compare-files.sh
cp ${rootFolder}/compare-packages-parallel.sh ${rootFolder}/${runFolder}/compare-packages-parallel.sh

# If they exist copy the zip files from S3 into the run folder
previousFileCheck=`aws s3 ls s3://${s3Release}/${publishedFolder}/${previousZip} | wc -l`
if [ "${previousFileCheck}" = "0" ] ; then
	echo "File s3://${s3Release}/${publishedFolder}/${previousZip} is not in S3!" >&2
	exit 1
else
	aws s3 cp s3://${s3Release}/${publishedFolder}/${previousZip} ${rootFolder}/${runFolder}/${previousZip}
fi

currentFileCheck=`aws s3 ls s3://${s3Release}/${publishedFolder}/${currentZip} | wc -l`
if [ "${currentFileCheck}" = "0" ] ; then
	echo "File s3://${s3Release}/${publishedFolder}/${currentZip} is not in S3!" >&2
	exit 1
else
	aws s3 cp s3://${s3Release}/${publishedFolder}/${currentZip} ${rootFolder}/${runFolder}/${currentZip}
fi

# Run the compare
cd ${rootFolder}/${runFolder} || exit 1
source compare-packages-parallel.sh "${previousRelease}_${previousDate}" "$previousZip" "${currentRelease}_${currentDate}" "$currentZip" -normaliseDates

echo "Copy the results to S3"
aws s3 cp ${rootFolder}/${runFolder}/target/c s3://snomed-compares/${uploadFolder} --recursive

# Tidy up running folder
rm -rf ${rootFolder}/${runFolder}