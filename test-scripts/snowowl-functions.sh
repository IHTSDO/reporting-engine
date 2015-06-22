#!/bin/bash

set -e;

# snowOwlUrl="https://dev-term.ihtsdotools.org/snowowl/snomed-ct/v2"
snowOwlUrl="http://localhost:8080/snowowl/snomed-ct/v2"

response=".temp-http-response.txt"
auth="Authorization: Basic c25vd293bDpzbm93b3ds"
contentType="Content-Type: application/json"

function checkResponseCode {
	alsoAllowCode=$1
	if [ -z "$alsoAllowCode" ]; then alsoAllowCode='zzz'; fi
	statusLine=`grep "HTTP/1.1" ${response}`
	echo ${statusLine}
	echo
	statusCode=`echo ${statusLine} | cut -d ' ' -f 2`
	echo ${statusCode} | grep -q "^2" || echo ${statusCode} | grep -q "^${alsoAllowCode}" || ( >&2 echo "Bad response code" && >&2 cat ${response} && echo && exit 1 )
}
function createBranch {
	parent=$1
	name=$2
	echo "> Creating branch under $parent called $name"
	curl -isS -H "$auth" -H "$contentType" -X POST "$snowOwlUrl/branches" -d '{"parent" : "'${parent}'", "name" : "'${name}'" }' > "${response}" && checkResponseCode 409
	echo
}

function getBranchStatus {
	branch=$1
	curl -sS -H "$auth" -H "$contentType" -X GET "$snowOwlUrl/branches/${branch}"
	echo
}

function mergeBranch {
	source=$1
	target=$2
	echo "> Merging branch $source to $target"
	echo "> - getting status of $source"
	getBranchStatus ${source}
	echo "> - getting status of $target"
	getBranchStatus ${target}
	echo "> - performing merge"
	curl -ivS -H "$auth" -H "$contentType" -X POST "$snowOwlUrl/merges" -d '{"source": "'${source}'", "target": "'${target}'", "commitComment": "'"Merging $source to $target"'"}' > "${response}" && checkResponseCode
}

function addDescription {
	branch=$1
	conceptId=$2
	term=$3
	echo "> On branch $branch adding to concept $conceptId description '$term'"
	curl -isS -H "$auth" -H "$contentType" -X POST "$snowOwlUrl/"${branch}"/descriptions" \
		-d '{ "commitComment": "add description test", "conceptId": "'${conceptId}'", "typeId": "900000000000013009", "caseSignificance": "INITIAL_CHARACTER_CASE_INSENSITIVE", "term": "'"$term"'", "languageCode": "en", "acceptability": {"900000000000508004" : "PREFERRED"},"moduleId": "900000000000207008"}' \
		> "${response}" && checkResponseCode
	echo
}

function requestClassificationOnBranch {
	branch=$1
	echo "> Requesting classification on branch ${branch}"
	curl -isS -H "$auth" -H "$contentType" -X POST "$snowOwlUrl/"${branch}"/classifications" -d '{ "reasonerId": "au.csiro.snorocket.owlapi3.snorocket.factory" }'
}

function requestELKClassificationOnBranch {
	branch=$1
	echo "> Requesting classification on branch ${branch}"
	curl -isS -H "$auth" -H "$contentType" -X POST "$snowOwlUrl/"${branch}"/classifications" -d '{ "reasonerId": "org.semanticweb.elk.elk.reasoner.factory" }'
}
