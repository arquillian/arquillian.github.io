#!/bin/bash

######################### Load & set variables #########################

WORKING_DIR=${1}
. ${WORKING_DIR}/variables
. ${SCRIPT_DIR}/colors

TEST_PROJECT_DIRECTORY="${WORKING_DIR}/arquillian.github.com-tests"
ARQUILLIAN_BLOG_TEST_URL=${ARQUILLIAN_BLOG_TEST_URL:-"http://localhost:4242/"}

######################### Running tests #########################

### if UI tests project exists, then remove
if [ -d ${TEST_PROJECT_DIRECTORY} ]; then
    rm -rf ${TEST_PROJECT_DIRECTORY}
fi


### clone project containing UI tests
CLONE_TESTS_COMMAND="git clone https://github.com/hemanik/arquillian.github.com-tests.git ${TEST_PROJECT_DIRECTORY}"
echo -e "${LIGHT_GREEN}->  cloning tests project using command: ${CLONE_TESTS_COMMAND}"
$CLONE_TESTS_COMMAND


### execute UI tests
#todo use mvnw
MAVEN_COMMAND="mvn clean verify -f ${TEST_PROJECT_DIRECTORY}/pom.xml -Darquillian.blog.url=${ARQUILLIAN_BLOG_TEST_URL} -Dbrowser=${BROWSER_TEST}"
echo -e "${LIGHT_GREEN}->  Running tests using command: ${MAVEN_COMMAND}${CLEAR}"
$MAVEN_COMMAND 2>&1 | tee ${LOGS_LOCATION}/maven-ui-tests_log


### get information if the tests have failed
if grep -q '\[INFO\] BUILD FAILURE' ${LOGS_LOCATION}/maven-ui-tests_log; then
    if [[ "${IGNORE_TEST_FAILURE}" != "true" && "${IGNORE_TEST_FAILURE}" != "yes" ]] ; then
        echo -e "${RED}-> There was a test failure.${CLEAR}"
        echo -e "${RED}-> Check the output or the log files located in ${LOGS_LOCATION}${CLEAR}"
        exit 1
    fi
fi