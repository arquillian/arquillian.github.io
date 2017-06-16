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
    -imf=*|--ignore-maven-failure=*)
    IGNORE_MAVEN_FAILURE="${i#*=}"
    shift
    ;;
    -imf|--ignore-maven-failure)
    IGNORE_MAVEN_FAILURE="true"
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
    *)
    ;;
esac
done

######################### set variables & clone & create dirs #########################

CURRENT_DIR=`pwd`
GIT_PROJECT="git@github.com:MatousJobanek/arquillian.github.com.git"

WORKING_DIR=`readlink -f ${WORKING_DIR:-/tmp/arquillian-blog}`
echo "=> Working directory is: ${WORKING_DIR}"
if [ ! -d ${WORKING_DIR} ]; then
    echo "=> Creating the working directory"
    mkdir ${WORKING_DIR}

elif [[ "$CLEAN" = "true" || "$CLEAN" = "yes" ]] ; then
    echo "=> cleaning working directory"
    rm -rf ${WORKING_DIR}/*
fi

if [[ ${TRAVIS} != "true" ]]; then
    CURRENT_BRANCH=`git branch | grep \* | cut -d ' ' -f2`
    LS_REMOTE_BRANCH=`git ls-remote --heads ${GIT_PROJECT} ${CURRENT_BRANCH}`
    if [ -z "${GITHUB_AUTH}" ]; then
        BRANCH_TO_CLONE="${CURRENT_BRANCH}"
    else
        BRANCH_TO_CLONE="develop"
    fi

    ARQUILLIAN_PROJECT_DIR="${WORKING_DIR}/arquillian.github.com"
    if [ ! -d "${ARQUILLIAN_PROJECT_DIR}" ]; then
        echo "=> Cloning branch ${BRANCH_TO_CLONE} from project ${GIT_PROJECT} into ${ARQUILLIAN_PROJECT_DIR}"
        git clone -b ${BRANCH_TO_CLONE} ${GIT_PROJECT} ${ARQUILLIAN_PROJECT_DIR}
    else
        echo "=> The project arquillian.github.com project will not be cloned because it exist on location: ${ARQUILLIAN_PROJECT_DIR}"
    fi
else
    ARQUILLIAN_PROJECT_DIR="${PWD}"
    echo "=> Travis environment - using project ${ARQUILLIAN_PROJECT_DIR}"
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
cd arquillian.github.com
echo 'bundle install -j 10 --path ./.gems'
bundle install -j 10 --path ./.gems
EOF" > ${SCRIPTS_LOCATION}/install_bundle.sh



echo "#!/bin/bash
bash --login <<EOF
cd arquillian.github.com

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
cd arquillian.github.com

echo \"=========================================\"
echo  'running awestruct --server -P production'
echo \"=========================================\"

touch ${DOCKER_LOGS_LOCATION}/${AWESTRUCT_PROD_LOG}
setsid awestruct --server -P production 2>&1 | tee ${DOCKER_LOGS_LOCATION}/${AWESTRUCT_PROD_LOG} &

while ! grep -m1 'Use Ctrl-C to stop' < ${DOCKER_LOGS_LOCATION}/${AWESTRUCT_PROD_LOG}; do
    echo -n '='
    sleep 1
done
EOF" > ${SCRIPTS_LOCATION}/build_prod_and_run.sh



echo "#!/bin/bash
bash --login <<EOF

git config --global user.email "arquillian-team@lists.jboss.org"
git config --global user.name "Alien Ike"
echo ${GITHUB_AUTH} > ~/.github-auth

cd arquillian.github.com
echo 'running awestruct -P production --deploy'
awestruct -P production --deploy > ${DOCKER_LOGS_LOCATION}/awestruct-production-deploy_log  2>&1
cat ${DOCKER_LOGS_LOCATION}/awestruct-production-deploy_log
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

echo "=> Launching arquillian-blog container... "
DOCKER_ID=`docker run -d -it --net=host -v ${ARQUILLIAN_PROJECT_DIR}:/home/dev/${ARQUILLIAN_PROJECT_DIR##*/} --name=arquillian-blog -v ${LOGS_LOCATION}:${DOCKER_LOGS_LOCATION} -v ${SCRIPTS_LOCATION}:${DOCKER_SCRIPTS_LOCATION} -p 4242:4242 arquillian/blog`
echo "=> Running container with id ${DOCKER_ID}"


######################### Executing scripts inside of docker image - building & running #########################

echo "=> Installing gems inside of the container..."
docker exec -it ${DOCKER_ID} ${DOCKER_SCRIPTS_LOCATION}/install_bundle.sh

echo "=> Building the pages with dev profile..."
docker exec -it ${DOCKER_ID} ${DOCKER_SCRIPTS_LOCATION}/build_dev.sh
if grep -q 'An error occurred' ${LOGS_LOCATION}/${AWESTRUCT_DEV_LOG}; then
    >&2 echo "=> There occurred an error when the pages were being generated with the command 'awestruct -d'."
    >&2 echo "=> Check the output or the log files located in ${LOGS_LOCATION}"
    >&2 echo "=> Killing and removing arquillian-blog container..."
    docker kill arquillian-blog
    docker rm arquillian-blog
    exit 1
fi

echo "=> Building & running the pages with prod profile..."
docker exec -it ${DOCKER_ID} ${DOCKER_SCRIPTS_LOCATION}/build_prod_and_run.sh
if grep -q 'An error occurred' ${LOGS_LOCATION}/${AWESTRUCT_PROD_LOG}; then
    >&2 echo "=> There occurred an error when the pages were being generated with the command 'running awestruct -P production --deploy'."
    >&2 echo "=> Check the output or the log files located in ${LOGS_LOCATION}"
    >&2 echo "=> Killing and removing arquillian-blog container..."
    docker kill arquillian-blog
    docker rm arquillian-blog
    exit 1
fi

PROCESS_LINE=`docker exec -i ${DOCKER_ID} ps aux | grep puma | grep -v grep`

PROCESS_TO_KILL=`echo ${PROCESS_LINE} | awk '{print $2}'`
