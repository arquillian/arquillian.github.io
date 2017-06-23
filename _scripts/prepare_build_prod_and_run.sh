#!/bin/bash

######################### Parse arguments #########################

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${SCRIPT_DIR}/parse_arguments.sh

######################### set variables & clone & create dirs #########################

### set & clean working directory - the set one or default one: /tmp/arquillian-blog
WORKING_DIR=`readlink -f ${WORKING_DIR:-/tmp/arquillian-blog}`
echo "=> Working directory is: ${WORKING_DIR}"
if [ ! -d ${WORKING_DIR} ]; then
    echo "=> Creating the working directory"
    mkdir ${WORKING_DIR}

elif [[ "$CLEAN" = "true" || "$CLEAN" = "yes" ]] ; then
    echo "=> cleaning working directory"
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

        echo "=> Cloning branch ${BRANCH_TO_CLONE} from project ${GIT_PROJECT} into ${ARQUILLIAN_PROJECT_DIR}"
        git clone -b ${BRANCH_TO_CLONE} ${GIT_PROJECT} ${ARQUILLIAN_PROJECT_DIR}

    else
        echo "=> The project ${ARQUILLIAN_PROJECT_DIR##*/} will not be cloned because it exist on location: ${ARQUILLIAN_PROJECT_DIR}"
    fi

### for travis it is expected that I'm located in the project dir to be processed
else
    ARQUILLIAN_PROJECT_DIR="${PWD}"
    .echo "=> Travis environment - using project ${ARQUILLIAN_PROJECT_DIR}"
fi

ARQUILLIAN_PROJECT_DIR_NAME=${ARQUILLIAN_PROJECT_DIR##*/}

### if specified then the whole _tmp directory is copied
if [[ -n "${USE_CACHE}" && -d "${USE_CACHE}" ]]; then
    echo "=> Copying cached _tmp directory ${USE_CACHE} to ${ARQUILLIAN_PROJECT_DIR}/_tmp"
    cp -rf ${USE_CACHE} ${ARQUILLIAN_PROJECT_DIR}/_tmp

### Checks Lanyrd availability, if not available, then _backup/restore_cache.sh is used
else
    LANYRD_RETURN_CODE=`curl -I http://lanyrd.com/ | head -n 1 | cut -d$' ' -f2`
    if [[ "${LANYRD_RETURN_CODE}" =~ [4,5][0-9][0-9] ]]; then
        echo "=> Lanyrd does not seem to be available - it returns ${LANYRD_RETURN_CODE}. The backup stored in _backup will be used."
        ${ARQUILLIAN_PROJECT_DIR}/_backup/restore_cache.sh
    fi
fi

### if specified then the whole .gems directory is copied
if [[ -n "${GEMS_CACHE}" && -d "${GEMS_CACHE}" ]]; then
    echo "=> Copying cached .gems directory ${GEMS_CACHE} to ${ARQUILLIAN_PROJECT_DIR}/.gems"
    cp -rf ${GEMS_CACHE} ${ARQUILLIAN_PROJECT_DIR}/.gems
fi


### sets .github-auth file (if not available already)
if [ -z "${GITHUB_AUTH}" ]; then
    GITHUB_AUTH=`cat ${SCRIPT_DIR}/../.github-auth`
fi
echo "=> Setting .github-auth file"
echo ${GITHUB_AUTH} > ${ARQUILLIAN_PROJECT_DIR}/.github-auth


### sets logs locations and names - for both standard and docker environments
DOCKER_LOGS_LOCATION="/home/dev/log"
LOGS_LOCATION="${WORKING_DIR}/log"
if [ ! -d "${LOGS_LOCATION}" ]; then
    echo "=> Creating ${LOGS_LOCATION} directory"
    mkdir ${LOGS_LOCATION}
fi
chmod o+w ${LOGS_LOCATION}

AWESTRUCT_DEV_LOG="awestruct-d_log"
AWESTRUCT_PROD_LOG="awestruct-server-production_log"


### sets scripts locations - for both standard and docker environments
DOCKER_SCRIPTS_LOCATION="/home/dev/scripts"
SCRIPTS_LOCATION="${WORKING_DIR}/scripts"
if [ ! -d "${SCRIPTS_LOCATION}" ]; then
    echo "=> Creating ${SCRIPTS_LOCATION} directory"
    mkdir ${SCRIPTS_LOCATION}
fi


######################### prepare scripts #########################

echo "#!/bin/bash
bash --login <<EOF
cd ${ARQUILLIAN_PROJECT_DIR_NAME}
echo 'bundle install -j 10 --path ./.gems'
bundle install -j 10 --path ./.gems
EOF" > ${SCRIPTS_LOCATION}/install_bundle.sh



echo "#!/bin/bash
bash --login <<EOF
cd ${ARQUILLIAN_PROJECT_DIR_NAME}

echo \"======================\"
echo 'running awestruct -d'
echo \"======================\"

touch ${DOCKER_LOGS_LOCATION}/${AWESTRUCT_DEV_LOG}
awestruct -d 2>&1 | tee ${DOCKER_LOGS_LOCATION}/${AWESTRUCT_DEV_LOG} &

while ! grep -m1 'Use Ctrl-C to stop' < ${DOCKER_LOGS_LOCATION}/${AWESTRUCT_DEV_LOG}; do
    sleep 1
done
kill %1
EOF" > ${SCRIPTS_LOCATION}/build_dev.sh



echo "#!/bin/bash
bash --login <<EOF
cd ${ARQUILLIAN_PROJECT_DIR_NAME}

echo \"=========================================\"
echo  'running awestruct --server -P production'
echo \"=========================================\"

touch ${DOCKER_LOGS_LOCATION}/${AWESTRUCT_PROD_LOG}
setsid awestruct --server -P production 2>&1 | tee ${DOCKER_LOGS_LOCATION}/${AWESTRUCT_PROD_LOG} &

while ! grep -m1 'Use Ctrl-C to stop' < ${DOCKER_LOGS_LOCATION}/${AWESTRUCT_PROD_LOG}; do
    sleep 1
done
EOF" > ${SCRIPTS_LOCATION}/build_prod_and_run.sh



echo "#!/bin/bash
bash --login <<EOF

git config --global user.email "arquillian-team@lists.jboss.org"
git config --global user.name "Alien Ike"
echo ${GITHUB_AUTH} > ~/.github-auth

cd ${ARQUILLIAN_PROJECT_DIR_NAME}
echo \"=========================================\"
echo 'running awestruct -P production --deploy'
echo \"=========================================\"

touch ${DOCKER_LOGS_LOCATION}/awestruct-production-deploy_log
awestruct -P production --deploy 2>&1 | tee ${DOCKER_LOGS_LOCATION}/awestruct-production-deploy_log

echo '=> Deployed'
EOF" > ${SCRIPTS_LOCATION}/deploy.sh

chmod +x ${SCRIPTS_LOCATION}/*



######################### Build & run docker image #########################


### cleans running containers
echo "=> Killing and removing any already existing arquillian-org containers..."
docker kill arquillian-org
docker rm arquillian-org


### builds or pulls image
if [[ "$BUILD_IMAGE" = "true" || "$BUILD_IMAGE" = "yes" ]]; then
    cd ${ARQUILLIAN_PROJECT_DIR}
    echo "=> Building arquillian-org image..."
    docker build -t arquillian-org .
    cd ${CURRENT_DIR}

    if [[ -z "$(docker images -q arquillian/blog 2> /dev/null)" ]]; then
      echo "=> The docker image arquillian-org has not been built - see the log for more information."
      exit 1
    fi
else
    echo "=> Pulling arquillian-org image..."
    docker pull arquillian/arquillian-org
fi


### starts container
echo "=> Launching arquillian-org container... "
DOCKER_ID=`docker run -d -it --net=host -v ${ARQUILLIAN_PROJECT_DIR}:/home/dev/${ARQUILLIAN_PROJECT_DIR##*/} --name=arquillian-org -v ${LOGS_LOCATION}:${DOCKER_LOGS_LOCATION} -v ${SCRIPTS_LOCATION}:${DOCKER_SCRIPTS_LOCATION} -p 4242:4242 arquillian/arquillian-org`
echo "=> Running container with id ${DOCKER_ID}"


### if running on travis, gets id of the travis group and creates same in the container. Then add the user to this group
if [[ ${TRAVIS} = "true" ]]; then
    docker exec -i --user root arquillian-org bash --login <<< "groupadd -g $(id -g) travis"
    docker exec -i --user root arquillian-org bash --login <<< "usermod -G travis dev"
fi


######################### Executing scripts inside of docker image - building & running #########################

### installs gems
echo "=> Installing gems inside of the container..."
docker exec -it arquillian-org ${DOCKER_SCRIPTS_LOCATION}/install_bundle.sh


### builds pages using dev profile & then stops the process
echo "=> Building the pages with dev profile..."
docker exec -it arquillian-org ${DOCKER_SCRIPTS_LOCATION}/build_dev.sh
if grep -q 'An error occurred' ${LOGS_LOCATION}/${AWESTRUCT_DEV_LOG}; then
    >&2 echo "=> There occurred an error when the pages were being generated with the command 'awestruct -d'."
    >&2 echo "=> Check the output or the log files located in ${LOGS_LOCATION}"
    >&2 echo "=> Killing and removing arquillian-org container..."
    docker kill arquillian-org
    docker rm arquillian-org
    exit 1
fi


### builds pages using prod profile & keeps it running to make it testable
echo "=> Building & running the pages with prod profile..."
docker exec -it arquillian-org ${DOCKER_SCRIPTS_LOCATION}/build_prod_and_run.sh
if grep -q 'An error occurred' ${LOGS_LOCATION}/${AWESTRUCT_PROD_LOG}; then
    >&2 echo "=> There occurred an error when the pages were being generated with the command 'running awestruct -P production --deploy'."
    >&2 echo "=> Check the output or the log files located in ${LOGS_LOCATION}"
    >&2 echo "=> Killing and removing arquillian-org container..."
    docker kill arquillian-org
    docker rm arquillian-org
    exit 1
fi


### if specified, the _tmp directory is copied & stored
if [[ -n "${STORE_CACHE}" ]]; then
    if [[ -d "${STORE_CACHE}" ]]; then
        rm -rf ${STORE_CACHE}
    fi
    echo "=> Copying cached _tmp dir from ${ARQUILLIAN_PROJECT_DIR}/_tmp to ${STORE_CACHE} to store"
    cp -fr ${ARQUILLIAN_PROJECT_DIR}/_tmp ${STORE_CACHE}
fi


### if specified, the .gems directory is copied & stored
if [[ -n "${GEMS_CACHE}" ]]; then
    if [[ -d "${GEMS_CACHE}" ]]; then
        rm -rf ${GEMS_CACHE}
    fi
    echo "=> Copying cached _tmp dir from ${ARQUILLIAN_PROJECT_DIR}/.gems to ${GEMS_CACHE} to store"
    cp -fr ${ARQUILLIAN_PROJECT_DIR}/.gems ${GEMS_CACHE}
fi


### retrieves PID of the process running awestruct production build
PROCESS_LINE=`docker exec -i arquillian-org ps aux | grep puma | grep -v grep`
PROCESS_TO_KILL=`echo ${PROCESS_LINE} | awk '{print $2}'`


######################### Writing variables into ${WORKING_DIR}/variables #########################

echo "
export PROCESS_TO_KILL=${PROCESS_TO_KILL}
export ARQUILLIAN_PROJECT_DIR=${ARQUILLIAN_PROJECT_DIR}
export DOCKER_SCRIPTS_LOCATION=${DOCKER_SCRIPTS_LOCATION}
export LOGS_LOCATION=${LOGS_LOCATION}
export ARQUILLIAN_PROJECT_DIR_NAME=${ARQUILLIAN_PROJECT_DIR_NAME}
export SCRIPTS_LOCATION=${SCRIPTS_LOCATION}
export SCRIPT_DIR=${SCRIPT_DIR}
" > ${WORKING_DIR}/variables
