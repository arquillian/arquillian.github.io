#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. ${DIR}/build_prod_and_run.sh

if [[ ${TRAVIS} = "true" ]]; then
    if [[ ${TRAVIS_BRANCH} = "develop" ]]; then
        if [[ ${TRAVIS_PULL_REQUEST} != "false" ]]; then
            echo "=> The pages won't be deployed - it is a build for pull request"
            exit 0;
        fi
    else
        echo "=> The pages won't be deployed - the targeted branch is not \"develop\""
        exit 0;
    fi
fi

docker exec -it ${DOCKER_ID} ${DOCKER_SCRIPTS_LOCATION}/deploy.sh
docker kill ${DOCKER_ID}
docker rm ${DOCKER_ID}

git branch
echo "=> Pushing generated pages to master..."
git push origin master
