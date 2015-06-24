#!/bin/bash
set -e;

source "snowowl-functions.sh"

# Setup
runId=`date "+%Y-%m-%d_%H-%M-%S_"`
projectBranch='MAIN/script-test'

createBranch 'MAIN' 'script-test'
createBranch ${projectBranch} ${runId}'A'
createBranch ${projectBranch} ${runId}'B'
runBranch=${projectBranch}'/'${runId}

# Add description on two branches
addDescription ${runBranch}'A' 302509004 'Heart organ A'
addDescription ${runBranch}'B' 302509004 'Heart organ A'

# Merge A to project
mergeBranch ${projectBranch} ${runBranch}'A'
mergeBranch ${runBranch}'A' ${projectBranch}
# Rebase B
echo "> Branch B needs a rebase..."
mergeBranch ${projectBranch} ${runBranch}'B' || ( echo "Rebase failed .. retrying" && mergeBranch ${projectBranch} ${runBranch}'B' )
# Merge B to project
mergeBranch ${runBranch}'B' ${projectBranch}
