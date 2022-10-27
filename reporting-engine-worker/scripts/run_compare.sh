#!/bin/bash

# Check for 5 input variables
if [ $# -lt 5 ]
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

# Set other parameters
#rootFolder=/opt/scripts/compare
rootFolder=$(pwd)
runPrevious=`echo "${previousZip}" | cut -d"_" -f2`
runPreviousDate=`echo "${previousZip}" | cut -d"_" -f4`
runCurrentDate=`echo "${currentZip}" | cut -d"_" -f4`
runFolder="${runPrevious}_${runPreviousDate:0:8}_${runCurrentDate:0:8}"
s3Release=snomed-releases/prod/published

# Debug
echo "Running script with the following parameters:"
echo $previousRelease
echo $previousZip
echo $currentRelease
echo $currentZip
echo $publishedFolder

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
source compare-packages-parallel.sh "$previousRelease" "$previousZip" "$currentRelease" "$currentZip" -normaliseDates
#cd -

echo "Copy the c results to S3"
aws s3 cp ${rootFolder}/${runFolder}/target/c s3://snomed-compares/${runFolder}_"$(date '+%Y-%m-%d-%H%M')" --recursive

# Tidy up running folder
rm -rf ${rootFolder}/${runFolder}
