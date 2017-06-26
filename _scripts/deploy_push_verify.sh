#!/bin/bash

######################### Load & set variables #########################

WORKING_DIR=${1}
. ${WORKING_DIR}/variables
. ${SCRIPT_DIR}/colors

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

######################### Deploy #########################

### if travis, then check if the branch is develop - if not then stop the deployment - same thing for pull requests
if [[ ${TRAVIS} = "true" ]]; then
    if [[ ${TRAVIS_BRANCH} = "develop" ]]; then
        if [[ ${TRAVIS_PULL_REQUEST} != "false" ]]; then
            echo -e "${LIGHT_GREEN}-> The pages won't be deployed - it is a build for pull request ${CLEAR}"
            exit 0;
        fi
    else
        echo -e "${LIGHT_GREEN}-> The pages won't be deployed - the targeted branch is not \"develop\" ${CLEAR}"
        exit 0;
    fi
fi


### get & set git information about the project
VARIABLE_TO_SET_GH_PATH="--git-dir=${ARQUILLIAN_PROJECT_DIR}/.git --work-tree=${ARQUILLIAN_PROJECT_DIR}"
GH_AUTH_REF=`git ${VARIABLE_TO_SET_GH_PATH} remote get-url origin | awk "{sub(/https:\/\//,\"https://${GITHUB_AUTH}@\")}; 1" | awk "{sub(/\.git$/, \"\")} 1"`
GIT_PROJECT=`git ${VARIABLE_TO_SET_GH_PATH} remote get-url origin | awk "{sub(/\.git$/, \"\")} 1"`

LAST_COMMIT=`git ls-remote ${GIT_PROJECT} master | awk '{print $1;}'`
CURRENT_BRANCH=`git rev-parse --abbrev-ref HEAD`


### gets latest changes of master branch
echo -e "${LIGHT_GREEN}-> retrieving master branch ${CLEAR}"
if [[ ${TRAVIS} = "true" ]]; then
    CURRENT_BRANCH=`git status | grep HEAD | awk '{print $4}'`
    git ${VARIABLE_TO_SET_GH_PATH} config remote.origin.fetch +refs/heads/*:refs/remotes/origin/*
    git ${VARIABLE_TO_SET_GH_PATH} fetch --unshallow origin master
else
    git ${VARIABLE_TO_SET_GH_PATH} fetch origin
fi

git ${VARIABLE_TO_SET_GH_PATH} checkout master
git ${VARIABLE_TO_SET_GH_PATH} pull -f origin master
git ${VARIABLE_TO_SET_GH_PATH} checkout ${CURRENT_BRANCH}


### execute awestruct deploy logic
echo -e "${LIGHT_GREEN}-> Running deploy script ${CLEAR}"
docker exec -it arquillian-org ${DOCKER_SCRIPTS_LOCATION}/deploy.sh


######################### Create timestamp & push #########################

### get timestamp
echo -e "${LIGHT_GREEN}-> creating timestamp ${CLEAR}"
TIMESTAMP=`date --rfc-3339=seconds`


### prepare script for storing timestamp & pushing changes to master
echo "#!/bin/bash
bash --login <<EOF

cd ${ARQUILLIAN_PROJECT_DIR_NAME}

echo '${TIMESTAMP}' > ./last_update.txt
git add ./last_update.txt
git commit -m 'Changed last update timestamp'

echo -e '${LIGHT_GREEN}-> Pushing generated pages to master... ${CLEAR}'
git push ${GH_AUTH_REF} master

echo -e '${LIGHT_GREEN}-> Changing to branch ${CURRENT_BRANCH}... ${CLEAR}'
git checkout ${CURRENT_BRANCH}

EOF" > ${SCRIPTS_LOCATION}/timestamp_push.sh
chmod +x ${SCRIPTS_LOCATION}/*


### execute the script for creating timestamp and pushing changes to master
docker exec -it arquillian-org ${DOCKER_SCRIPTS_LOCATION}/timestamp_push.sh


### clean running container
echo -e "${LIGHT_GREEN}-> Killing and removing arquillian-org container... ${CLEAR}"
docker kill arquillian-org
docker rm arquillian-org


### checks if the latest commit (on remote master branch) is same as it was before deploy phase - if there has been anything pushed
NEW_COMMIT=`git ls-remote ${GIT_PROJECT} master | awk '{print $1;}'`
if [[ "${NEW_COMMIT}" = "${LAST_COMMIT}" ]]; then
    echo -e "${RED} There wasn't pushed any new commit - see the log for more information ${CLEAR}"
    exit 1;
fi


######################### Wait for latest version if pushed to arquillian organization #########################

if [[ ! "${GIT_PROJECT}" =~ .*[\:,\/]arquillian\/arquillian\.github\..* ]]; then
    echo -e "${GREEN} Tests won't be executed against production because it hasn't been pushed to the arquillian organization ${CLEAR}"
    exit 0;
fi

limit=30
while [[ "`curl http://arquillian.org/last_update.txt 2> /dev/null`" != "${TIMESTAMP}" ]]; do
    let "limit--"
    echo -e "${YELLOW} Waiting for the timestamp ${TIMESTAMP} being available on http://arquillian.org/last_update.txt ${CLEAR}"
    echo -e "${YELLOW} timeout: ${limit} ${CLEAR}"
    if [[ ${limit} == "0" ]]; then
        echo -e "${RED}-> the webpages hasn't been updated in last 30 seconds ${CLEAR}"
        exit 1
    fi
    sleep 1
done


######################### Verify production #########################

echo "
export ARQUILLIAN_BLOG_TEST_URL=http://arquillian.org/
" >> ${WORKING_DIR}/variables

${SCRIPT_DIR}/verify.sh ${WORKING_DIR}

exit $?