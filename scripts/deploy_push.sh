#!/bin/bash

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

ARQUILLIAN_PROJECT_DIR=${1}
DOCKER_SCRIPTS_LOCATION=${2}

VARIABLE_TO_SET_GH_PATH="--git-dir=${ARQUILLIAN_PROJECT_DIR}/.git --work-tree=${ARQUILLIAN_PROJECT_DIR}"
GH_REF=`git ${VARIABLE_TO_SET_GH_PATH} remote get-url origin | awk "{sub(/https:\/\//,\"https://${GITHUB_AUTH}@\")}; 1" | awk "{sub(/\.git$/, \"\")} 1"`
echo "gh ref: ${GH_REF}"
GIT_PROJECT=`git ${VARIABLE_TO_SET_GH_PATH} remote get-url origin | awk "{sub(/\.git$/, \"\")} 1"`
echo "git project: ${GIT_PROJECT}"

LAST_COMMIT=`git ls-remote ${GIT_PROJECT} master | awk '{print $1;}'`
CURRENT_BRANCH=`git status | grep 'On branch' | awk '{print $3;}'`

echo "=> fetching"
git ${VARIABLE_TO_SET_GH_PATH} fetch origin
echo "=> retrieving master branch"
git ${VARIABLE_TO_SET_GH_PATH} checkout -b master origin/master
echo "=> pulling"
git ${VARIABLE_TO_SET_GH_PATH} pull -f origin master
echo "=> returning back"
git ${VARIABLE_TO_SET_GH_PATH} checkout ${CURRENT_BRANCH}

docker exec -it arquillian-blog ${DOCKER_SCRIPTS_LOCATION}/deploy.sh

echo "=> Killing and removing arquillian-blog container..."
docker kill arquillian-blog
docker rm arquillian-blog

echo "=> Pushing generated pages to master..."
echo "=> git ${VARIABLE_TO_SET_GH_PATH} push ${GH_REF} master"
git ${VARIABLE_TO_SET_GH_PATH} push ${GH_REF} master
echo "=> Changing to branch ${CURRENT_BRANCH}..."
git ${VARIABLE_TO_SET_GH_PATH} checkout ${CURRENT_BRANCH}

NEW_COMMIT=`git ls-remote ${GIT_PROJECT} master | awk '{print $1;}'`
if [[ "${NEW_COMMIT}" = "${LAST_COMMIT}" ]]; then
    echo "=> There wasn't pushed any new commit - see the log for more information"
    exit 1;
fi
