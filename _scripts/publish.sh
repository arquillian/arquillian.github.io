#!/bin/bash

######################### Prepare & build & run #########################

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${SCRIPT_DIR}/prepare_build_prod_and_run.sh
. ${SCRIPT_DIR}/colors

######################### verify & open page & ask & publish #########################

### execute tests against running awestruct production pages
${SCRIPT_DIR}/verify.sh ${WORKING_DIR}


### open browser on http://localhost:4242/ and wait till user confirms that he wants to continue with the deploy phase
$BROWSER_COMMAND http://localhost:4242/ > /dev/null 2>&1 &

echo -e "${LIGHT_GREEN}====================================================================================================== ${CLEAR}"
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


### stop awestruct production process
docker exec -i arquillian-org kill ${PROCESS_TO_KILL}

### deploy & push & run test against production
${SCRIPT_DIR}/deploy_push_verify.sh ${WORKING_DIR}

exit $?