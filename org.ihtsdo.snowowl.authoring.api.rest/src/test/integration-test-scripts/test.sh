#!/bin/bash
set -e

baseUrl="http://localhost:8080/snowowl/ihtsdo-authoring"
response=".response.txt"

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

function fail {
	echo
	echo
	echo "-- Test failed! --"
	echo
	exit 1
}
echo "Save logical model"
curl -is -X POST -H 'Content-Type:application/json' --data-binary "`cat test-logical-model.json`" "$baseUrl/models/logical" > ${response}
checkResponse

echo "Save lexical model"
curl -is -X POST -H 'Content-Type:application/json' --data-binary "`cat test-lexical-model.json`" "$baseUrl/models/lexical" > ${response}
checkResponse

echo "Create template"
curl -is -X POST -H 'Content-Type:application/json' --data-binary "`cat test-template.json`" "$baseUrl/templates" > ${response}
checkResponse

echo "Validate matrix, expect error"
curl -is -X POST -H 'Content-Type:application/json' --data-binary "`cat matrix-bad-last-attribute-value.json`" "$baseUrl/templates/test-template/matrices" > ${response}
cat ${response}; echo
grep "HTTP/1.1 406" ${response} && echo " - as expected" || fail
echo

echo "Validate matrix, with decent data"
curl -is -X POST -H 'Content-Type:application/json' --data-binary "`cat matrix-valid.json`" "$baseUrl/templates/test-template/matrices" > ${response}
checkResponse

echo "Done"
