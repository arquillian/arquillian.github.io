#!/bin/bash

######################### Load & set variables #########################

WORKING_DIR=${1}
. ${WORKING_DIR}/variables
. ${SCRIPT_DIR}/colors

TEST_PROJECT_DIRECTORY="${WORKING_DIR}/arquillian.github.io-functional-tests"
ARQUILLIAN_BLOG_TEST_URL=${ARQUILLIAN_BLOG_TEST_URL:-"http://localhost:4242/"}

######################### Running tests #########################

### if UI tests project exists, then remove
if [ -d ${TEST_PROJECT_DIRECTORY} ]; then
    rm -rf ${TEST_PROJECT_DIRECTORY}
fi

### get & set git information about the project
VARIABLE_TO_SET_GH_PATH="--git-dir=${ARQUILLIAN_PROJECT_DIR}/.git --work-tree=${ARQUILLIAN_PROJECT_DIR}"
git ${VARIABLE_TO_SET_GH_PATH} config remote.origin.fetch +refs/heads/*:refs/remotes/origin/*

echo -e "${LIGHT_GREEN}->  fetching functional-tests branch into directory ${TEST_PROJECT_DIRECTORY}"
if [ -f ${ARQUILLIAN_PROJECT_DIR}/.git/shallow ]; then
    git ${VARIABLE_TO_SET_GH_PATH} fetch --unshallow origin functional-tests
else
    git ${VARIABLE_TO_SET_GH_PATH} fetch origin functional-tests
fi

### check if the branch is already present
if [ -z "$(git ${VARIABLE_TO_SET_GH_PATH} rev-parse --verify functional-tests)" ]; then
    git ${VARIABLE_TO_SET_GH_PATH} worktree add -b functional-tests ${TEST_PROJECT_DIRECTORY} origin/functional-tests;
fi

### execute UI tests
#todo use mvnw
MAVEN_COMMAND="mvn clean verify -f ${TEST_PROJECT_DIRECTORY}/pom.xml -Darquillian.blog.url=${ARQUILLIAN_BLOG_TEST_URL} -Dbrowser=${BROWSER_TEST}"
echo -e "${LIGHT_GREEN}->  Running tests using command: ${MAVEN_COMMAND}${CLEAR}"
$MAVEN_COMMAND 2>&1 | tee ${LOGS_LOCATION}/maven-ui-tests_log


### get information if the tests have failed
if grep -q '\[INFO\] BUILD FAILURE\|\[ERROR\]' ${LOGS_LOCATION}/maven-ui-tests_log; then
    if [[ "${IGNORE_TEST_FAILURE}" != "true" && "${IGNORE_TEST_FAILURE}" != "yes" ]] ; then
        echo -e "${RED}-> There was a test failure.${CLEAR}"
        echo -e "${RED}-> Check the output or the log files located in ${LOGS_LOCATION}${CLEAR}"
        exit 1
    fi
fi