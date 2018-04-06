#!/usr/bin/env bash

######################### Parse arguments #########################

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${SCRIPT_DIR}/parse_arguments.sh
. ${SCRIPT_DIR}/colors

######################### set variables & clone & create dirs #########################

### set & clean working directory - the set one or default one: /tmp/arquillian-blog
WORKING_DIR=`readlink -f ${WORKING_DIR:-/tmp/arquillian-blog}`
echo -e "${LIGHT_GREEN}-> Working directory is: ${WORKING_DIR} ${CLEAR}"
if [ ! -d ${WORKING_DIR} ]; then
    echo -e "${LIGHT_GREEN}-> Creating the working directory ${CLEAR}"
    mkdir ${WORKING_DIR}

elif [[ "$CLEAN" = "true" || "$CLEAN" = "yes" ]] ; then
    echo -e "${LIGHT_GREEN}-> cleaning working directory ${CLEAR}"
    rm -rf ${WORKING_DIR}/*
fi


### for non-travis environment, the project is cloned
if [[ ${TRAVIS} != "true" ]]; then
    if [[ -z "${GIT_PROJECT}" ]]; then
        GIT_PROJECT=`git remote get-url origin`
    fi
    ARQUILLIAN_PROJECT_DIR="${WORKING_DIR}/${PWD##*/}"

    if [ ! -d "${ARQUILLIAN_PROJECT_DIR}" ]; then
        CURRENT_BRANCH=`git branch | grep \* | cut -d ' ' -f2`
        LS_REMOTE_BRANCH=`git ls-remote --heads ${GIT_PROJECT} ${CURRENT_BRANCH}`

        if [ -z "${LS_REMOTE_BRANCH}" ]; then
            BRANCH_TO_CLONE="${CURRENT_BRANCH}"
        else
            BRANCH_TO_CLONE="develop"
        fi

        echo -e "${LIGHT_GREEN}-> Cloning branch ${BRANCH_TO_CLONE} from project ${GIT_PROJECT} into ${ARQUILLIAN_PROJECT_DIR} ${CLEAR}"
        git clone -b ${BRANCH_TO_CLONE} ${GIT_PROJECT} ${ARQUILLIAN_PROJECT_DIR}

    else
        echo -e "${LIGHT_GREEN}-> The project ${ARQUILLIAN_PROJECT_DIR##*/} will not be cloned because it exist on location: ${ARQUILLIAN_PROJECT_DIR} ${CLEAR}"
    fi

### for travis it is expected that I'm located in the project dir to be processed
else
    ARQUILLIAN_PROJECT_DIR="${PWD}"
    echo -e "${LIGHT_GREEN}-> Travis environment - using project ${ARQUILLIAN_PROJECT_DIR} ${CLEAR}"
fi

ARQUILLIAN_PROJECT_DIR_NAME=${ARQUILLIAN_PROJECT_DIR##*/}

### sets .github-auth file (if not available already)
if [[ -z "${GITHUB_AUTH}" ]]; then
    if [[ -f ${SCRIPT_DIR}/../.github-auth ]]; then
        GITHUB_AUTH=`cat ${SCRIPT_DIR}/../.github-auth`
    else
        ### sets token with read permissions - no scopes
        GITHUB_AUTH="c23fbf83c47dd31b546d392b2ba054c356620b3c"
    fi
fi

echo -e "${LIGHT_GREEN}-> Setting .github-auth file ${CLEAR}"
echo ${GITHUB_AUTH} > ${ARQUILLIAN_PROJECT_DIR}/.github-auth

