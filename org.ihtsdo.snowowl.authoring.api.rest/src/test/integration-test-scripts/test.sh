#!/bin/bash
set -e

baseUrl="http://localhost:8080/snowowl/ihtsdo-authoring"
response=".response.txt"

function runTest {

	echo "Save logical model"
	curl -is -X POST -H 'Content-Type:application/json' --data-binary "`cat test-logical-model.json`" "$baseUrl/models/logical" > ${response}
	checkResponse

	echo "Save lexical model"
	curl -is -X POST -H 'Content-Type:application/json' --data-binary "`cat test-lexical-model.json`" "$baseUrl/models/lexical" > ${response}
	checkResponse

	echo "Create template"
	curl -is -X POST -H 'Content-Type:application/json' --data-binary "`cat test-template.json`" "$baseUrl/templates" > ${response}
	checkResponse

	echo "Save content work with invalid data"
	curl -is -X POST -H 'Content-Type:application/json' --data-binary "`cat work-bad-last-attribute-value.json`" "$baseUrl/templates/test-template/work" > ${response}
	checkResponse
	workLocation=`getLocation`
	echo "workLocation = $workLocation"

	echo "Validate content work, expect error"
	curl -is "$workLocation/validation" > ${response}
	grep '"anyErrors":true' ${response}
	checkResponse

	echo "Update content work, with decent data"
	curl -is -X PUT -H 'Content-Type:application/json' --data-binary "`cat work-valid.json`" "$workLocation" > ${response}
	checkResponse

	echo "Validate content work, expect no errors"
	curl -is -H 'Content-Type:application/json' "$workLocation/validation" > ${response}
	grep '"anyErrors":false' ${response}
	checkResponse

	echo "Commit content work to create concepts in SnowOwl"
	curl -is -X POST -H 'Content-Type:application/json' "$workLocation/commit" > ${response}
	cat ${response}
	checkResponse
	taskId=`getResponseValue taskId`
	echo "taskId = $taskId"

#	echo "Start task classification"
#	curl -is -X POST -H 'Content-Type:application/json' "$baseUrl/tasks/$taskId/classifications" > ${response}
#	checkResponse
#	classificationLocation=`getLocation`
#	echo "classificationLocation = $classificationLocation"
#
#	echo "Get classification status"
#	curl -is "$classificationLocation" > ${response}
#	cat $response
#	checkResponse

	echo "Done"
}

function checkResponse {
	if [[ `grep "HTTP/1.1 2" ${response}` ]]
	then
		echo " Good HTTP response"
		echo
	else
		echo " Bad HTTP response!"
		cat ${response}
		fail
	fi
}
function getLocation {
	grep 'Location' ${response} | cut -f2 -d ' ' | tr -d '\r'
}
function getResponseValue {
	grep "${1}" ${response} | sed "s/.*\"${1}\":\"\([^\"]*\).*/\1/"
}
function fail {
	echo
	echo
	echo "-- Test failed! --"
	echo
	exit 1
}

runTest
