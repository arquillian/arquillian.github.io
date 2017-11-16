#!/bin/bash

WORKING_DIR=${1}

BLOG=$(git --no-pager show --pretty="" --name-only ${TRAVIS_COMMIT_RANGE} | grep 'blog/')

if [ -z $BLOG ]; then
    echo -e "No blogs found in this PR. Skipping publishing screenshot"
    exit 0
fi;
DATE=$(cat $BLOG | grep 'date' | cut -d':' -f 2 | tr "-" "/" | tr -d '[:space:]')
NAME=$(basename $BLOG .textile | tr -s '.' '-')

SCREENSHOT_DIR="${WORKDIR:-/tmp/screenshots}" 

BLOG_URL="http://localhost:4242/blog/${DATE}/${NAME}/"

if [ ! -z "${TRAVIS_PULL_REQUEST// }" ] && [ "${TRAVIS_PULL_REQUEST}" != "false" ]; then
    echo -e "${LIGHT_GREEN} Taking screenshot of ${BLOG_URL}"
    docker run --net=host --shm-size 1G --rm -v ${SCREENSHOT_DIR}:/screenshots alekzonder/puppeteer:latest full_screenshot ${BLOG_URL} 1366x768
    content_type="image/png"
    filename="full_screenshot_1366_768.png"
    filepath="${SCREENSHOT_DIR}/${filename}"

    imgur=$(curl -X POST "https://api.imgur.com/3/image" \
    -H "Authorization: Client-ID ${CLIENT_ID}" \
    -H "Referer: https://api.imgur.com/3/image" \
    -H "content-type: multipart/form-data" \
    -F "image=@${filepath}" \
    | jq '.data.link' | sed -e 's/^"//' -e 's/"$//')

    curl https://api.github.com/repos/${TRAVIS_REPO_SLUG}/issues/${TRAVIS_PULL_REQUEST}/comments \
      -H "Authorization:token ${GH_TOKEN}" \
      -X POST \
      -d  "{ \"body\": \"### Blog Preview\n[Build ${TRAVIS_BUILD_NUMBER}](https://travis-ci.org/${TRAVIS_REPO_SLUG}/builds/${TRAVIS_BUILD_ID})\n\n![blog-preview](${imgur})\"}"
fi;
