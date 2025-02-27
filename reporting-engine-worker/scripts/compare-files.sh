#!/bin/bash
set -e

leftFile=$1
rightFile=$2
fileName=$3

tmpOutput="target/${fileName}_comparison_details_${RANDOM}.txt"

if [ -f "${rightFile}" ] && [ -f "${leftFile}" ]
then 

	echo "Comparing ${leftFile} and ${rightFile}" > ${tmpOutput}
	
	if [[ ${fileName} == *.txt ]]
	then	

		leftFileCount=`wc -l ${leftFile} | awk '{print $1}'`
		echo "${leftFile} line count: $leftFileCount" >> ${tmpOutput}
		rightFileCount=`wc -l ${rightFile} | awk '{print $1}'`
		echo "${rightFile} line count: $rightFileCount" >> ${tmpOutput}
		echo "Line count diff: $[$leftFileCount-$rightFileCount]" >> ${tmpOutput}

		echo -n "Header differences count (x2): " >> ${tmpOutput}
		headerDiffCount=`diff <(head -n 1 ${leftFile}) <(head -n 1 ${rightFile}) | wc -l | awk '{print $1}'`
		echo ${headerDiffCount} >> ${tmpOutput}
		echo -e "${fileName}\t${headerDiffCount}" >> target/c/diff__headers.txt

		echo -n "Content differences count (x2): " >> ${tmpOutput}
    	tmpFile="tmp_${fileName}.txt"
    	sed '1d' "${leftFile}" | sort > ${tmpFile}
		mv ${tmpFile} ${leftFile}
    	sed '1d' "${rightFile}" | sort > ${tmpFile}
		mv ${tmpFile} ${rightFile}
		diff ${leftFile} ${rightFile} | tee target/c/${fileName} | wc -l >> ${tmpOutput}

		if [[ ${fileName} == *Refset* || ${fileName} == *Relationship* ]]
		then
			echo -n "Content without id column differences count (x2): " >> ${tmpOutput}
			leftFileTrim="${leftFile}_no_first_col.txt"
			rightFileTrim="${rightFile}_no_first_col.txt"
			cut -f2- ${leftFile} | sort > ${leftFileTrim}
			cut -f2- ${rightFile} | sort > ${rightFileTrim}
			diff ${leftFileTrim} ${rightFileTrim} | tee target/c/${fileName}_no_first_col.txt | wc -l >> ${tmpOutput}
		fi

		if [[ ${fileName} == *Relationship* ]]
		then
			echo -n "Content without id or group column differences count (x2): " >> ${tmpOutput}
			leftFileTrim2="${leftFile}_no_1_7_col.txt"
			rightFileTrim2="${rightFile}_no_1_7_col.txt"
			#Ideally I'd use cut's --complement here but it doesn't exist for mac
			cut -f2,3,4,5,6,8,9,10 ${leftFile} | sort > ${leftFileTrim2}
			cut -f2,3,4,5,6,8,9,10 ${rightFile} | sort > ${rightFileTrim2}
			diff ${leftFileTrim2} ${rightFileTrim2} | tee target/c/${fileName}_no_1_7_col.txt | wc -l >> ${tmpOutput}
		fi

		if [[ ${fileName} == *ModuleDependencySnapshot* ]]
    	then
    		echo -n "Packages modules: " >> ${tmpOutput}
    		awk '$3 == "1" {print $4}' ${leftFile} | sort -u > target/c/left_modules.txt
    		awk '$3 == "1" {print $4}' ${rightFile} | sort -u > target/c/right_modules.txt
    	fi
	fi
	
	echo -n "File size difference (bytes): " >> ${tmpOutput}
	if [[ $OSTYPE == 'darwin'* ]]; then
		#MacOS
		leftSize=`stat -f%z ${leftFile}`
		rightSize=`stat -f%z ${rightFile}`
	elif [[ $OSTYPE == 'linux'* ]]; then
		#Linux
		leftSize=`stat --format="%s" ${leftFile}`
		rightSize=`stat --format="%s" ${rightFile}`
	fi

	echo "${leftSize} - ${rightSize}" | bc >> ${tmpOutput}
	echo >> ${tmpOutput}

else 
  
	echo -n "Skipping ${fileName}: " >> ${tmpOutput}

	if ! [ -f "${rightFile}" ]; then
		echo "Right file is not found." >> ${tmpOutput}
	fi

	if ! [ -f "${leftFile}" ]; then
		echo "Left file is not found." >> ${tmpOutput}
	fi	

	echo >> ${tmpOutput}
fi

cat ${tmpOutput}
rm  ${tmpOutput}
