#!/bin/bash

######################### Load & set variables #########################

WORKING_DIR=${1}
. ${WORKING_DIR}/variables


######################### Deploy & push #########################

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [[ ${TRAVIS} = "true" ]]; then
    if [[ ${TRAVIS_BRANCH} = "develop" ]]; then
        if [[ ${TRAVIS_PULL_REQUEST} != "false" ]]; then
            echo "=> The pages won't be deployed - it is a build for pull request"
            exit 0;
        fi
    else
        echo "=> The pages won't be deployed - the targeted branch is not \"develop\""
        exit 0;
    fi
fi

VARIABLE_TO_SET_GH_PATH="--git-dir=${ARQUILLIAN_PROJECT_DIR}/.git --work-tree=${ARQUILLIAN_PROJECT_DIR}"
GH_AUTH_REF=`git ${VARIABLE_TO_SET_GH_PATH} remote get-url origin | awk "{sub(/https:\/\//,\"https://${GITHUB_AUTH}@\")}; 1" | awk "{sub(/\.git$/, \"\")} 1"`
GIT_PROJECT=`git ${VARIABLE_TO_SET_GH_PATH} remote get-url origin | awk "{sub(/\.git$/, \"\")} 1"`

LAST_COMMIT=`git ls-remote ${GIT_PROJECT} master | awk '{print $1;}'`
CURRENT_BRANCH=`git rev-parse --abbrev-ref HEAD`

git ${VARIABLE_TO_SET_GH_PATH} pull --all

echo "=> retrieving master branch"
if [[ ${TRAVIS} = "true" ]]; then
    CURRENT_BRANCH=`git status | grep HEAD | awk '{print $4}'`
    git ${VARIABLE_TO_SET_GH_PATH} config remote.origin.fetch +refs/heads/*:refs/remotes/origin/*
    git ${VARIABLE_TO_SET_GH_PATH} fetch --unshallow origin master
else
    git ${VARIABLE_TO_SET_GH_PATH} fetch origin
fi

git ${VARIABLE_TO_SET_GH_PATH} checkout master
git ${VARIABLE_TO_SET_GH_PATH} pull -f origin master
git ${VARIABLE_TO_SET_GH_PATH} checkout ${CURRENT_BRANCH}

    git ${VARIABLE_TO_SET_GH_PATH} log --pretty=oneline -10

echo "=> Running deploy script"
docker exec -it arquillian-org ${DOCKER_SCRIPTS_LOCATION}/deploy.sh

    git ${VARIABLE_TO_SET_GH_PATH} log --pretty=oneline -10

echo "=> Killing and removing arquillian-org container..."
docker kill arquillian-org
docker rm arquillian-org

echo "=> creating timestamp"
TIMESTAMP=`date --rfc-3339=seconds`
echo ${TIMESTAMP} > ${ARQUILLIAN_PROJECT_DIR}/last_update.txt
echo "=> adding"
git ${VARIABLE_TO_SET_GH_PATH} add ${ARQUILLIAN_PROJECT_DIR}/last_update.txt
echo "=> commiting"
git ${VARIABLE_TO_SET_GH_PATH} commit -m "Changed last update timestamp"

    git ${VARIABLE_TO_SET_GH_PATH} log --pretty=oneline -10

echo "=> Pushing generated pages to master..."
git ${VARIABLE_TO_SET_GH_PATH} push ${GH_AUTH_REF} master

echo "=> Changing to branch ${CURRENT_BRANCH}..."
git ${VARIABLE_TO_SET_GH_PATH} checkout ${CURRENT_BRANCH}

NEW_COMMIT=`git ls-remote ${GIT_PROJECT} master | awk '{print $1;}'`
if [[ "${NEW_COMMIT}" = "${LAST_COMMIT}" ]]; then
    echo "=> There wasn't pushed any new commit - see the log for more information"
    exit 1;
fi


######################### Wait for latest version if pushed to arquillian organization #########################

if [[ ! "${GIT_PROJECT}" =~ .*[\:,\/]arquillian\/arquillian\.github\..* ]]; then
    echo "=> Tests won't be executed against production because it hasn't been pushed to the arquillian organization"
    exit 0;
fi

limit=30
while `curl http://arquillian.org/last_update.txt` != "${TIMESTAMP}"; do
    let "limit--"
    if [[ limit == "0" ]]; then
        echo "=> the webpages hasn't been updated in last 30 seconds"
        exit 1
    fi
    sleep 1
done


######################### Verify production #########################

echo "
export ARQUILLIAN_BLOG_TEST_URL=http://arquillian.org/
" >> ${WORKING_DIR}/variables

${SCRIPT_DIR}/verify.sh ${WORKING_DIR}

exit $?