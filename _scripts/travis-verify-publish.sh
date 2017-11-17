#!/bin/bash

######################### Parse & load variables #########################

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${SCRIPT_DIR}/parse_arguments.sh
. ${WORKING_DIR}/variables
. ${SCRIPT_DIR}/colors


# Takes screenshot of the blog post and publishes as comment to the PR
${SCRIPT_DIR}/screenshot.sh ${WORKING_DIR}

######################### Verify & deploy & push #########################

### runs tests against localhost
${SCRIPT_DIR}/verify.sh ${WORKING_DIR}

EXIT_VALUE=$?
if [[ "${EXIT_VALUE}" != "0" ]]; then
    exit ${EXIT_VALUE}
fi


### stop awestruct production process
docker exec -i arquillian-org kill ${PROCESS_TO_KILL}

### deploy & push & run test against production
${SCRIPT_DIR}/deploy_push_verify.sh ${WORKING_DIR}

exit $?

