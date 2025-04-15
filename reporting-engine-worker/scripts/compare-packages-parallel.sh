#!/bin/bash
set -e;

extractZip () {
	archive=$1
	targetDir=$2

	mkdir -p "${targetDir}"
	if [ "${enhancedZip}" ]; then
		7z e -bd -o"${targetDir}" "${archive}" "*.*" -r
	else
		unzip "${archive}" -d "${targetDir}"
	fi
}

stripBetaPrefix () {
	targetDir=$1

	currentDir=`pwd`

	cd ${targetDir}
	for thisFile in * ; do
		if [[ "${thisFile}" == x* ]]; then
			nonBetaName=`echo ${thisFile} | cut -c2-`
			mv -v "${thisFile}" "${nonBetaName}"
		fi
	done

	cd ${currentDir}
}

normaliseDates () {
	targetDir=$1

	currentDir=`pwd`

	cd "${targetDir}"
	for thisFile in * ; do
		noDateName=`echo ${thisFile} | sed "s/_\([0-9]\{8\}[.]\)/./"`
		mv -v "${thisFile}" "${noDateName}"
	done

	cd "${currentDir}"
}

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
sevenZipInstalled=`command -v 7z || true `
if [ -z "${sevenZipInstalled}" ]
then
	echo "Could not detect 7-Zip, falling back to use 'unzip'. This may cause problems for filenames with non-ASCII characters."
else
	enhancedZip=true
fi

rm -rf target || true
mkdir -p target

leftName=$1
leftArchive=$2
rightName=$3
rightArchive=$4

leftLocation="target/left_archive"
echo "Extracting left archive into ${leftLocation}"
extractZip "${leftArchive}" "${leftLocation}"

rightLocation="target/right_archive"
echo "Extracting right archive into ${rightLocation}"
extractZip "${rightArchive}" "${rightLocation}"

# Move the left structure into directory "a" excluding json files
mkdir -p target/a
echo "Moving ${leftName} from ${leftLocation} into flat structure 'a'"
find "${leftLocation}" -type f -not -name "*.json" | xargs -I {} mv {} target/a

# Move the right structure into directory "b" excluding json files
mkdir -p target/b
echo "Moving ${rightName} from ${rightLocation} into flat structure 'b'"
find "${rightLocation}" -type f -not -name "*.json" | xargs -I {} mv {} target/b

# Remove dates from a and b
echo
echo "Normalising dates"
normaliseDates target/a
normaliseDates target/b

echo
echo "Stripping any beta archive prefix"
stripBetaPrefix target/a
stripBetaPrefix target/b

echo
echo "Creating lists of files"
leftFilesList="_left_files.txt"
rightFilesList="_right_files.txt"

find target/a -type f -not -name ".*" -execdir basename {} ';' | sort > "${leftFilesList}"
find target/b -type f -not -name ".*" -execdir basename {} ';' | sort > "${rightFilesList}"

commonFilesList="_common_files.txt"
deletedFilesList="_deleted_files.txt"
createdFilesList="_created_files.txt"

# Common files in target/a and target/b
comm -1 -2 "${leftFilesList}" "${rightFilesList}" > "${commonFilesList}"
comm -2 -3 "${leftFilesList}" "${rightFilesList}" > "${deletedFilesList}"
comm -1 -3 "${leftFilesList}" "${rightFilesList}" > "${createdFilesList}"

# xargs fails with command too long error so have to use loops

# Create empty counterpart files for the deleted files in target/b
while IFS= read -r file
do
  head -n 1 target/a/${file} > target/b/${file}
  echo "${file}" >> ${commonFilesList}
done < ${deletedFilesList}

# Create empty counterpart files for the created files in target/a
while IFS= read -r file
do
  head -n 1 target/b/${file} > target/a/${file}
  echo "${file}" >> ${commonFilesList}
done < ${createdFilesList}

mkdir -p target/c

echo
echo "Files differences between ${leftName} and ${rightName}"
diff "${leftFilesList}" "${rightFilesList}" | tee target/c/diff__files.txt

echo
echo "File content differences between ${leftName} and ${rightName}"
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
echo "Creating process order file"
processOrderFile="_process_order.txt"
{
	grep "sct2_" "${commonFilesList}" | sort;
	grep "der2_" "${commonFilesList}" | sort;
	grep -E -v "sct2_|der2_" "${commonFilesList}" | sort;
} >> "${processOrderFile}"

echo
echo "Creating parallel feed file"
parallelFeed="_parallel_feed_${RANDOM}.txt"
while IFS= read -r file
do
	leftFile="target/a/${file}"
	rightFile="target/b/${file}"
	echo "${leftFile} ${rightFile} ${file}" >> "${parallelFeed}"
done < "${processOrderFile}"

parallel -j 6 --no-notice --ungroup --colsep ' ' -a ${parallelFeed} ./compare-files.sh

rm ${parallelFeed}
rm ${processOrderFile}
rm ${commonFilesList}
rm ${deletedFilesList}
rm ${createdFilesList}
rm ${leftFilesList}
rm ${rightFilesList}