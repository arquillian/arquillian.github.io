#!/bin/bash

######################### Prepare & build & run #########################

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${SCRIPT_DIR}/prepare_build_prod_and_run.sh

IGNORE_MAVEN_FAILURE=${IGNORE_MAVEN_FAILURE:-true}
BROWSER_COMMAND=${BROWSER_COMMAND:-"firefox"}
BROWSER_TEST=${BROWSER_TEST:-"chromeHeadless"}

######################### Running tests #########################



if [ -d "arquillian.github.com-tests" ]; then
    rm -rf arquillian.github.com-tests
fi
TEST_PROJECT_DIRECTORY=${WORKING_DIR}/arquillian.github.com-tests
git clone git@github.com:MatousJobanek/arquillian.github.com-tests.git ${TEST_PROJECT_DIRECTORY}

#todo use mvnw
MAVEN_COMMAND="mvn clean verify -f ${TEST_PROJECT_DIRECTORY}/pom.xml -Darquillian.blog.url=http://localhost:4242/ -Dbrowser=${BROWSER_TEST}"
echo "=> Running tests using command: ${MAVEN_COMMAND}"
$MAVEN_COMMAND 2>&1 | tee ${LOGS_LOCATION}/maven-ui-tests_log


if grep -q '[INFO] BUILD FAILURE' ${LOGS_LOCATION}/maven-ui-tests_log; then
    if [[ "$IGNORE_MAVEN_FAILURE" != "true" && "$IGNORE_MAVEN_FAILURE" != "yes" ]] ; then
        >&2 echo "=> There occurred an error when the pages were being generated with the command 'running awestruct -P production --deploy'."
        >&2 echo "=> Check the output or the log files located in ${LOGS_LOCATION}"
        exit 1
    fi
fi

$BROWSER_COMMAND http://localhost:4242/ > /dev/null 2>&1 &

echo -e "======================================================================================================"
echo -e "Generation of the blog web pages has been finished!"
echo -e "Check the current state in your browser, go through the test results and check the generation output."
while true; do
    read -p "Do you want to deploy the generated web pages? [y/n]:" yn
    case $yn in
	[Yy]* ) break;;
	[Nn]* ) echo -e "Exiting - for more information see the logs: ${LOGS_LOCATION}";
	        docker kill arquillian-blog;
	        docker rm arquillian-blog;
		    exit;;
	* ) echo "Please answer yes or no.";;
    esac
done

docker exec -i arquillian-blog kill ${PROCESS_TO_KILL}

echo "params ${ARQUILLIAN_PROJECT_DIR} ${DOCKER_SCRIPTS_LOCATION}"
${SCRIPT_DIR}/deploy_push.sh ${ARQUILLIAN_PROJECT_DIR} ${DOCKER_SCRIPTS_LOCATION}
