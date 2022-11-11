#!/bin/bash

# Check for 5 input variables
if [ $# -lt 5 ]
	then
		echo "Not enough arguments supplied!" >&2
		exit 1
fi

# Run with five parameters
previousReleaseName=$1
previousReleasePath=$2
currentReleaseName=$3
currentReleasePath=$4
uploadFolder=$5

# Set other parameters
s3Release=snomed-releases

rootFolder=$(pwd)

previousZip=`echo "${previousReleasePath}" | sed -n 's/^\(.*\/\)*\(.*\)/\2/p'`
currentZip=`echo "${currentReleasePath}" | sed -n 's/^\(.*\/\)*\(.*\)/\2/p'`

previousDate=`echo "${previousZip}" | sed "s/.*_\([0-9]\{8\}\).*/\1/"`
currentDate=`echo "${currentZip}" | sed "s/.*_\([0-9]\{8\}\).*/\1/"`

runFolder="${previousReleaseName}_${previousDate}-${currentReleaseName}_${currentDate}"

# Debug
echo "Running script with the following parameters:"
echo $previousReleaseName
echo $previousReleasePath
echo $currentReleaseName
echo $currentReleasePath
echo $uploadFolder

echo "Names of .zip archives:"
echo $previousZip
echo $currentZip

echo "Root folder and run folder:"
echo $rootFolder
echo $runFolder

# Create a new folder and copy the main scripts 
mkdir ${rootFolder}/${runFolder}
cp ${rootFolder}/compare-files.sh ${rootFolder}/${runFolder}/compare-files.sh
cp ${rootFolder}/compare-packages-parallel.sh ${rootFolder}/${runFolder}/compare-packages-parallel.sh

# If they exist copy the zip files from S3 into the run folder
previousFileCheck=`aws s3 ls s3://${s3Release}/${previousReleasePath} | wc -l`
if [ "${previousFileCheck}" = "0" ] ; then
	echo "File s3://${s3Release}/${previousReleasePath} is not in S3!" >&2
	exit 1
else
	aws s3 cp s3://${s3Release}/${previousReleasePath} ${rootFolder}/${runFolder}/${previousZip}
fi

currentFileCheck=`aws s3 ls s3://${s3Release}/${currentReleasePath} | wc -l`
if [ "${currentFileCheck}" = "0" ] ; then
	echo "File s3://${s3Release}/${currentReleasePath} is not in S3!" >&2
	exit 1
else
	aws s3 cp s3://${s3Release}/${currentReleasePath} ${rootFolder}/${runFolder}/${currentZip}
fi

# Run the compare
cd ${rootFolder}/${runFolder} || exit 1
source compare-packages-parallel.sh "${previousReleaseName}_${previousDate}" "$previousZip" "${currentReleaseName}_${currentDate}" "$currentZip" -normaliseDates

echo "Copy the results to S3"
aws s3 cp ${rootFolder}/${runFolder}/target/c s3://snomed-compares/${uploadFolder} --recursive

# Tidy up running folder
rm -rf ${rootFolder}/${runFolder}