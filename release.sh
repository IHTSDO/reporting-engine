#!/bin/bash
##
#  This script can be used to perform a release of the project.
#  Changes in develop will be merged to master. The master and develop versions will be bumped. Branches will be pushed back to git.
##

set -e

# if [ $# -ne 1 ]; then
	# echo "Please provide the new snow-owl release version as an agrument."
	# exit 1
# fi

# newRelease=$1
# echo
# echo "The new release version provided is '${newRelease}'"
# echo

echo "Checking out develop."
git checkout develop
echo "Pulling commits."
git pull
echo "Checking out master."
git checkout master
echo "Pulling commits."
git pull
echo "Merging develop into master."
git merge develop

snapVersion=`grep -m1 'SNAPSHOT</version>' pom.xml | sed 's/.*>\(.[^<]*\).*/\1/'`
newRelease=`echo $snapVersion | sed 's/-SNAPSHOT//'`
newMinor=`echo $((\`echo $newRelease | sed 's/[0-9]*\.[0-9]*\.\([0-9]*\)/\1/'\` + 1))`
newSnap=`echo $newRelease | sed 's/\([0-9]*\)$//' | tr -d '\n';echo "${newMinor}-SNAPSHOT"`

echo $snapVersion
echo $newRelease
echo $newMinor
echo $newSnap

echo "Updating master version. New release version is '${newRelease}'"
mvn versions:set versions:commit -DnewVersion="${newRelease}"
gsed -i "0,/<version>[^<]*/s//<version>${newRelease}/" pom.xml

echo "Master root pom changes."
git diff pom.xml

#read -p "Commit master changes? " -n 1 -r; echo; if [[ ! $REPLY =~ ^[Yy]$ ]]; then echo "Exiting"; exit 1; fi

echo "Commiting master changes."
git commit -a -m "IHTSDO Release ${newRelease}"

git log -n5
#read -p "Push master changes? " -n 1 -r; echo; if [[ ! $REPLY =~ ^[Yy]$ ]]; then echo "Exiting"; exit 1; fi
# echo "Pushing master."
# git push


echo "Checking out develop."
git checkout develop

echo "Merging master into develop."
git merge master

echo "Updating develop version. New Snapshot version is '${newSnap}'"
mvn versions:set versions:commit -DnewVersion="${newSnap}"
gsed -i "0,/<version>[^<]*/s//<version>${newSnap}/" pom.xml

echo "Develop root pom changes."
git diff pom.xml

# read -p "Commit develop changes? " -n 1 -r; echo; if [[ ! $REPLY =~ ^[Yy]$ ]]; then echo "Exiting"; exit 1; fi

echo "Commiting develop changes."
git commit -a -m "IHTSDO dev version ${newSnap}"

git log -n5
# read -p "Push develop changes? " -n 1 -r; echo; if [[ ! $REPLY =~ ^[Yy]$ ]]; then echo "Exiting"; exit 1; fi
# echo "Pushing develop."
# git push
