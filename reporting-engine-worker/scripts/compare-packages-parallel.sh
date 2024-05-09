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

createList () {
	sourceDir=$1
	targetFile=$2	

	find "${sourceDir}" -type f -execdir basename {} ';' | sort > "${targetFile}"
}

countLines () {
	sourceDir=$1
	targetFile=$2

	for thisFile in ${sourceDir}/* ; do
		fileName=`basename "${thisFile}"`
		fileCount=$((`wc -l "${thisFile}" | awk '{print $1}'` - 1)) # exclude header line
		echo -e "${fileName}\t${fileCount}" >> "${targetFile}"
	done
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
		noDateName=`echo ${thisFile} | sed "s/_\([0-9]\{8\}\)//"`
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
flags=$5

if [ "${flags}" == "-normaliseDates" ]; then
	normaliseDates=true
	echo "Option set to make effective dates in filenames the same"
fi

leftLocation="target/left_archive"
echo "Extracting left archive into ${leftLocation}"
extractZip "${leftArchive}" "${leftLocation}"

rightLocation="target/right_archive"
echo "Extracting right archive into ${rightLocation}"
extractZip "${rightArchive}" "${rightLocation}"

# Move the left structure into directory "a" excluding json files
mkdir -p target/a
echo "Moving ${leftName} from ${leftLocation} into flat structure 'a'"
find "${leftLocation}" -type f ! -name "*.json" | xargs -I {} mv {} target/a

# Move the right structure into directory "b" excluding json files
mkdir -p target/b
echo "Moving ${rightName} from ${rightLocation} into flat structure 'b'"
find "${rightLocation}" -type f ! -name "*.json" | xargs -I {} mv {} target/b

# Remove dates from a and b
if [ "${normaliseDates}" ]; then
	echo
	echo "Normalising dates"
	normaliseDates target/a
	normaliseDates target/b
fi

echo
echo "Stripping any beta archive prefix"
stripBetaPrefix target/a
stripBetaPrefix target/b

echo
echo "Creating lists"

leftFilesList="_${leftName}"_files_list.txt
rightFilesList="_${rightName}"_files_list.txt
commonFilesList="_common_files_list.txt"

createList "target/a" "${leftFilesList}"
createList "target/b" "${rightFilesList}"
comm -1 -2 "${leftFilesList}" "${rightFilesList}" > "${commonFilesList}"

mkdir -p target/c

echo
echo "Calculating line count without header line"
countLines "target/a" "target/c/left_files_line_counts.txt"
countLines "target/b" "target/c/right_files_line_counts.txt"

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
rm ${leftFilesList}
rm ${rightFilesList}