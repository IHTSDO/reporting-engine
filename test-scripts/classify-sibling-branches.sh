#!/bin/bash
set -e;

source "snowowl-functions.sh"

# Setup
runId=`date "+%Y-%m-%d_%H-%M-%S_"`
projectBranch='MAIN/script-test'

createBranch 'MAIN' 'script-test'
createBranch ${projectBranch} ${runId}'A'
createBranch ${projectBranch} ${runId}'B'
createBranch ${projectBranch} ${runId}'C'
runBranch=${projectBranch}'/'${runId}

# Add description on two branches
addDescription ${runBranch}'A' 302509004 'Heart organ A'
addDescription ${runBranch}'B' 302509004 'Heart organ B'
addDescription ${runBranch}'C' 302509004 'Heart organ C'

requestClassificationOnBranch ${runBranch}'A'
requestClassificationOnBranch ${runBranch}'B'
requestClassificationOnBranch ${runBranch}'C'
