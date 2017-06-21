#!/bin/bash

######################### Load & set variables #########################

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${SCRIPT_DIR}/parse_arguments.sh
. ${WORKING_DIR}/variables


######################### Verify & deploy & push #########################

${SCRIPT_DIR}/verify.sh ${WORKING_DIR}

EXIT_VALUE=$?
if [[ "${EXIT_VALUE}" != "0" ]]; then
    exit ${EXIT_VALUE}
fi

docker exec -i arquillian-org kill ${PROCESS_TO_KILL}

${SCRIPT_DIR}/deploy_push.sh ${WORKING_DIR}
if [[ "$?" != "0" ]]; then
    exit $?
fi

