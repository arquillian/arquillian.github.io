#!/bin/bash

######################### Prepare & build & run #########################

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${SCRIPT_DIR}/prepare_build_prod_and_run.sh

######################### verify & open page & ask & publish #########################

${SCRIPT_DIR}/verify.sh ${WORKING_DIR}

$BROWSER_COMMAND http://localhost:4242/ > /dev/null 2>&1 &

echo -e "======================================================================================================"
echo -e "Generation of the blog web pages has been finished!"
echo -e "Check the current state in your browser, go through the test results and check the generation output."
while true; do
    read -p "Do you want to deploy the generated web pages? [y/n]:" yn
    case $yn in
	[Yy]* ) break;;
	[Nn]* ) echo -e "Exiting - for more information see the logs: ${LOGS_LOCATION}";
	        docker kill arquillian-org;
	        docker rm arquillian-org;
		    exit;;
	* ) echo "Please answer yes or no.";;
    esac
done

docker exec -i arquillian-org kill ${PROCESS_TO_KILL}

${SCRIPT_DIR}/deploy_push.sh ${WORKING_DIR}

exit $?