#!/bin/bash

LIGHT_GREEN='\033[1;32m'
GREEN='\033[0;32m'
CLEAR='\033[0m'

echo -e "${LIGHT_GREEN}-> Updating timestamp of all cached files ...${CLEAR}"
find . -exec touch {} \;

echo -e "${LIGHT_GREEN}-> Restoring to _tmp ...${CLEAR}"
cp -R * ../_tmp

echo -e "${GREEN}Done${CLEAR}"
