#!/bin/bash
set -e;

function extractZip {
	zipName=$1
	dirName=$2
	echo "Extracting $zipName to $dirName"
	mkdir -p $dirName
	if [ "$enhancedZip" = true ]; then
		7z e -bd -o$dirName "$zipName" "*.*" -r
	else
		unzip "$zipName" -d $dirName
	fi
}

function createLists {
	listName=$1
	dir=$2
	home=`pwd`
	cd $dir
	find . -type f > ${home}/target/"${listName}"_file_list.txt
	cd - > /dev/null
}

function stripBetaPrefix() {
	targetDir=$1
	tempDir=`pwd`
	cd ${targetDir}
	for thisFile in * ; do
		if [[ ${thisFile} == x* ]]; then
			nonBetaName=`echo ${thisFile} | cut -c2-`
			echo "Stripping beta prefix from ${targetDir}/${thisFile}"
			mv ${thisFile} ${nonBetaName}
		fi
	done
	cd ${tempDir}
}

rm -rf target || true
mkdir -p target

if [ $# -lt 4 ]; then
	echo "Usage: compare-packages-parallel.sh <Left Archive Name> <Left Archive Location> <Right Archive Name> <Right Archive Location> [-normaliseDates]" >&2
	exit 1
fi

echo "Checking if parallel is installed"
parallelInstalled=`parallel -? 2>/dev/null | grep GNU || true`
if [ -z "${parallelInstalled}" ]
then
	echo "Could not detect the GNU Program 'Parallel'. If MAC, do brew install parallel" >&2
	exit 1
fi

echo "Checking if if 7-zip is installed"
SevenZipInstalled=`command -v 7z || true `
if [ -z "${SevenZipInstalled}" ]
then
	echo "Could not detect 7-Zip, falling back to use 'unzip'. This may cause problems for filenames with non-ASCII characters."
else
	enhancedZip=true
fi

leftName=$1
leftArchive=$2
rightName=$3
rightArchive=$4
flags=$5

if [ "${flags}" == "-normaliseDates" ]; then
	normaliseDates=true
	echo "Option set to make effective dates in filenames the same"
fi

leftLocation="target/left_archive"
echo "Left archive in ${leftLocation}"
extractZip "${leftArchive}" "${leftLocation}"
echo "Extracted left archive"

rightLocation="target/right_archive"
echo "Right archive in ${rightLocation}"
extractZip "${rightArchive}" "${rightLocation}"
echo "Extracted right archive"

#Move the left structure into directory "a" excluding json files
mkdir -p target/a
echo "Moving ${leftName} from ${leftLocation} into flat structure 'a'"
find ${leftLocation} -type f ! -name "*.json" | xargs -I {} mv {} target/a

#Move the right structure into directory "b" excluding json files
mkdir -p target/b
echo "Moving ${rightName} from ${rightLocation} into flat structure 'b'"
find ${rightLocation} -type f ! -name "*.json" | xargs -I {} mv {} target/b

#If we're normalising the dates, remove dates from a and b
if [ "${normaliseDates}" == true ]; then
	echo
	echo "Normalising dates"
	for thisFile in target/a/* ; do
		newFileName=`echo ${thisFile} | sed "s/_\([0-9]\{8\}\)//"`
		mv -v ${thisFile} ${newFileName}
	done
	for thisFile in target/b/* ; do
		newFileName=`echo ${thisFile} | sed "s/_\([0-9]\{8\}\)//"`
		mv -v ${thisFile} ${newFileName}
	done
fi

echo
echo "Strip any Beta archive prefix"
stripBetaPrefix target/a
stripBetaPrefix target/b

echo
echo "Create lists"
createLists "${leftName}" "target/a"
createLists "${rightName}" "target/b"

mkdir -p target/c

echo "_File list differences ${leftName} vs ${rightName}"
diff target/"${leftName}"_file_list.txt target/"${rightName}"_file_list.txt | tee target/c/diff_file_list.txt #&& echo "None"
echo

echo "_File content differences_"
echo "Between $leftName $leftArchive and $rightName $rightArchive"
if [[ $OSTYPE == 'darwin'* ]]; then
	# MacOS
	echo `md5 $leftArchive`
	echo `md5 $rightArchive`
elif [[ $OSTYPE == 'linux'* ]]; then
	# Linux
	echo `md5sum $leftArchive`
	echo `md5sum $rightArchive`
fi

echo "Line count diff is ${leftName} minus ${rightName}"
echo "File size diff is ${leftName} minus ${rightName}"

echo
processOrderFile="_process_order.txt"
{
	find target/b -type f | sed "s/target\/b\///" | grep "sct2_" | sort;
	find target/b -type f | sed "s/target\/b\///" | grep "der2_" | sort;
	#Now we'll just do a file size comparison of any other file
	find target/b -type f | sed "s/target\/b\///" | egrep -v "sct2_|der2_" | sort;
} >> ${processOrderFile}

parallelFeed="ParallelFeed_${RANDOM}.txt"
while IFS= read -r file
do
	leftFile="target/a/${file}"
	rightFile="target/b/${file}"
	echo "${leftFile} ${rightFile} ${file} ${leftName} ${rightName}" >> ${parallelFeed}
done < "$processOrderFile"

parallel -j 6 --no-notice --ungroup --colsep ' ' -a ${parallelFeed} ./compare-files.sh

rm ${parallelFeed}
rm ${processOrderFile}