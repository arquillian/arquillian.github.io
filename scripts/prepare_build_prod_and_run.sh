#!/bin/bash

######################### parse input parameters #########################

for i in "$@"
do
case $i in
    -wd=*|--working-dir=*)
    WORKING_DIR="${i#*=}"
    shift
    ;;
    -ga=*|--github-auth=*)
    GITHUB_AUTH="${i#*=}"
    shift
    ;;
    -c=*|--clean=*)
    CLEAN="${i#*=}"
    shift
    ;;
    -c|--clean)
    CLEAN="true"
    shift
    ;;
    -itf=*|--ignore-test-failure=*)
    IGNORE_TEST_FAILURE="${i#*=}"
    shift
    ;;
    -itf|--ignore-test-failure)
    IGNORE_TEST_FAILURE="true"
    shift
    ;;
    -bc=*|--browser-command=*)
    BROWSER_COMMAND="${i#*=}"
    shift
    ;;
    -bt=*|--browser-test=*)
    BROWSER_TEST="${i#*=}"
    shift
    ;;
    -uc=*|--use-cache=*)
    USE_CACHE="${i#*=}"
    shift
    ;;
    -sc=*|--store-cache=*)
    STORE_CACHE="${i#*=}"
    shift
    ;;
    -gp=*|--github-project=*)
    GIT_PROJECT="${i#*=}"
    shift
    ;;
    -h|--help)
    echo "Usage: you can use scripts publish.sh and prepare_build_prod_and_run.sh with following parameters"
    echo -e ""
    echo -e "  -wd=<path>  \t\t \t Path to working directory - where all necessary directories and files will be stored."
    echo -e "  --working-dir=<path> \t  \t Default value is -/tmp/arquillian-blog"
    echo -e ""
    echo -e "  -ga=<token> \t\t \t Your GitHub authentication token"
    echo -e "  --github-auth=<token>  \t If not set, content of the file .../project-directory/.github-auth is taken"
    echo -e ""
    echo -e "  -c [-c=<true/false>] \t \t If the content of the working directory should be removed."
    echo -e "  --clean \t \t \t Default value is false"
    echo -e ""
    echo -e "  -itf [-itf=<true/false>] \t If a potential failure of test execution should be ignored"
    echo -e "  --ignore-test-failure  \t Default value is false"
    echo ""
    echo -e "  -bc=<command> \t\t Command that should be used for opening a browser"
    echo -e "  --browser-command=<command>  \t Default value is 'firefox'"
    echo ""
    echo -e "  -bt=<browser> \t\t Browser to be used for executing UI tests"
    echo -e "  --browser-test=<browser>  \t Default value is 'chromeHeadless'"
    echo ""
    echo -e "  -uc=<path> \t\t \t Path to '_tmp' directory to be used as a cache. If specified then it is copied to the project directory"
    echo -e "  --use-cache=<path> "
    echo ""
    echo -e "  -sc=<path> \t\t  \t Path to a location where the '_tmp' directory should be stored as a cache"
    echo -e "  --store-cache=<path>"
    echo ""
    echo -e "  -gp=<url> \t\t \t Url to GitHub project to be cloned and used for generating web pages"
    echo -e "  --github-project=<url>  \t Default value is: `git remote get-url origin`"
    echo ""
    exit
    ;;
    *)
    ;;
esac
done

######################### set variables & clone & create dirs #########################

CURRENT_DIR=`pwd`

WORKING_DIR=`readlink -f ${WORKING_DIR:-/tmp/arquillian-blog}`
echo "=> Working directory is: ${WORKING_DIR}"
if [ ! -d ${WORKING_DIR} ]; then
    echo "=> Creating the working directory"
    mkdir ${WORKING_DIR}

elif [[ "$CLEAN" = "true" || "$CLEAN" = "yes" ]] ; then
    echo "=> cleaning working directory"
    rm -rf ${WORKING_DIR}/*
fi

if [[ -z "${GIT_PROJECT}" ]]; then
    GIT_PROJECT=`git remote get-url origin`
fi

if [[ ${TRAVIS} != "true" ]]; then
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
else
    ARQUILLIAN_PROJECT_DIR="${PWD}"
    echo "=> Travis environment - using project ${ARQUILLIAN_PROJECT_DIR}"
fi

ARQUILLIAN_PROJECT_DIR_NAME=${ARQUILLIAN_PROJECT_DIR##*/}

if [[ -n "${USE_CACHE}" && -d "${USE_CACHE}" ]]; then
    echo "=> Copying cached _tmp directory ${USE_CACHE} to ${ARQUILLIAN_PROJECT_DIR}/_tmp"
    cp -rf ${USE_CACHE} ${ARQUILLIAN_PROJECT_DIR}/_tmp
else
    LANYRD_RETURN_CODE=`curl -I http://lanyrd.com/ | head -n 1 | cut -d$' ' -f2`
    if [[ "${LANYRD_RETURN_CODE}" =~ [4,5][0-9][0-9] ]]; then
        echo "=> Lanyrd does not seem to be available - it returns ${LANYRD_RETURN_CODE}. The backup stored in _backup will be used."
        ${ARQUILLIAN_PROJECT_DIR}/_backup/restore_cache.sh
    fi
fi

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
if [ -z "${GITHUB_AUTH}" ]; then
    GITHUB_AUTH=`cat ${SCRIPT_DIR}/../.github-auth`
fi
echo "=> Setting .github-auth file"
echo ${GITHUB_AUTH} > ${ARQUILLIAN_PROJECT_DIR}/.github-auth

DOCKER_LOGS_LOCATION="/home/dev/log"
LOGS_LOCATION="${WORKING_DIR}/log"
if [ ! -d "${LOGS_LOCATION}" ]; then
    echo "=> Creating ${LOGS_LOCATION} directory"
    mkdir ${LOGS_LOCATION}
fi
chmod o+w ${LOGS_LOCATION}

DOCKER_SCRIPTS_LOCATION="/home/dev/scripts"
SCRIPTS_LOCATION="${WORKING_DIR}/scripts"
if [ ! -d "${SCRIPTS_LOCATION}" ]; then
    echo "=> Creating ${SCRIPTS_LOCATION} directory"
    mkdir ${SCRIPTS_LOCATION}
fi

AWESTRUCT_DEV_LOG="awestruct-d_log"
AWESTRUCT_PROD_LOG="awestruct-server-production_log"

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
echo 'Deployed'
EOF" > ${SCRIPTS_LOCATION}/deploy.sh

chmod +x ${SCRIPTS_LOCATION}/*



######################### Build & run docker image #########################

#rm -r _tmp _site

echo "=> Killing and removing any already existing arquillian-blog containers..."
docker kill arquillian-blog
docker rm arquillian-blog

cd ${ARQUILLIAN_PROJECT_DIR}
echo "=> Building arquillian-blog image..."
docker build -t arquillian/blog .
cd ${CURRENT_DIR}

if [[ -z "$(docker images -q arquillian/blog 2> /dev/null)" ]]; then
  echo "=> The docker image arquillian/blog has not been built - see the log for more information."
  exit 1
fi

echo "=> Launching arquillian-blog container... "
DOCKER_ID=`docker run -d -it --net=host -v ${ARQUILLIAN_PROJECT_DIR}:/home/dev/${ARQUILLIAN_PROJECT_DIR##*/} --name=arquillian-blog -v ${LOGS_LOCATION}:${DOCKER_LOGS_LOCATION} -v ${SCRIPTS_LOCATION}:${DOCKER_SCRIPTS_LOCATION} -p 4242:4242 arquillian/blog`
echo "=> Running container with id ${DOCKER_ID}"


######################### Executing scripts inside of docker image - building & running #########################

echo "=> Installing gems inside of the container..."
docker exec -it arquillian-blog ${DOCKER_SCRIPTS_LOCATION}/install_bundle.sh

echo "=> Building the pages with dev profile..."
docker exec -it arquillian-blog ${DOCKER_SCRIPTS_LOCATION}/build_dev.sh
if grep -q 'An error occurred' ${LOGS_LOCATION}/${AWESTRUCT_DEV_LOG}; then
    >&2 echo "=> There occurred an error when the pages were being generated with the command 'awestruct -d'."
    >&2 echo "=> Check the output or the log files located in ${LOGS_LOCATION}"
    >&2 echo "=> Killing and removing arquillian-blog container..."
    docker kill arquillian-blog
    docker rm arquillian-blog
    exit 1
fi

echo "=> Building & running the pages with prod profile..."
docker exec -it arquillian-blog ${DOCKER_SCRIPTS_LOCATION}/build_prod_and_run.sh
if grep -q 'An error occurred' ${LOGS_LOCATION}/${AWESTRUCT_PROD_LOG}; then
    >&2 echo "=> There occurred an error when the pages were being generated with the command 'running awestruct -P production --deploy'."
    >&2 echo "=> Check the output or the log files located in ${LOGS_LOCATION}"
    >&2 echo "=> Killing and removing arquillian-blog container..."
    docker kill arquillian-blog
    docker rm arquillian-blog
    exit 1
fi

if [[ -n "${STORE_CACHE}" ]]; then
    echo "=> Copying cached _tmp dir from ${ARQUILLIAN_PROJECT_DIR}/_tmp to ${STORE_CACHE} to store"
    cp -fr ${ARQUILLIAN_PROJECT_DIR}/_tmp ${STORE_CACHE}
fi

PROCESS_LINE=`docker exec -i arquillian-blog ps aux | grep puma | grep -v grep`

PROCESS_TO_KILL=`echo ${PROCESS_LINE} | awk '{print $2}'`
