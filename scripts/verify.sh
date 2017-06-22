#!/bin/bash

######################### Load & set variables #########################

WORKING_DIR=${1}
. ${WORKING_DIR}/variables

TEST_PROJECT_DIRECTORY="${WORKING_DIR}/arquillian.github.com-tests"
ARQUILLIAN_BLOG_TEST_URL=${ARQUILLIAN_BLOG_TEST_URL:-"http://localhost:4242/"}

######################### Running tests #########################

if [ -d ${TEST_PROJECT_DIRECTORY} ]; then
    rm -rf ${TEST_PROJECT_DIRECTORY}
fi
git clone https://github.com/matousjobanek/arquillian.github.com-tests.git ${TEST_PROJECT_DIRECTORY}

#todo use mvnw
MAVEN_COMMAND="mvn clean verify -f ${TEST_PROJECT_DIRECTORY}/pom.xml -Darquillian.blog.url=${ARQUILLIAN_BLOG_TEST_URL} -Dbrowser=${BROWSER_TEST}"
echo "=> Running tests using command: ${MAVEN_COMMAND}"
$MAVEN_COMMAND 2>&1 | tee ${LOGS_LOCATION}/maven-ui-tests_log

if grep -q '\[INFO\] BUILD FAILURE' ${LOGS_LOCATION}/maven-ui-tests_log; then
    if [[ "${IGNORE_TEST_FAILURE}" != "true" && "${IGNORE_TEST_FAILURE}" != "yes" ]] ; then
        >&2 echo "=> There was a test failure."
        >&2 echo "=> Check the output or the log files located in ${LOGS_LOCATION}"
        exit 1
    fi
fi