#!/bin/bash

######################### parse input parameters #########################

for i in "$@"
do
case $i in
    -wd=*|--working-dir=*)
    export WORKING_DIR="${i#*=}"
    shift
    ;;
    -ga=*|--github-auth=*)
    export GITHUB_AUTH="${i#*=}"
    shift
    ;;
    -c=*|--clean=*)
    export CLEAN="${i#*=}"
    shift
    ;;
    -c|--clean)
    CLEAN="true"
    shift
    ;;
    -itf=*|--ignore-test-failure=*)
    export IGNORE_TEST_FAILURE="${i#*=}"
    shift
    ;;
    -itf|--ignore-test-failure)
    export IGNORE_TEST_FAILURE="true"
    shift
    ;;
    -bc=*|--browser-command=*)
    export BROWSER_COMMAND="${i#*=}"
    shift
    ;;
    -bt=*|--browser-test=*)
    export BROWSER_TEST="${i#*=}"
    shift
    ;;
    -uc=*|--use-cache=*)
    export USE_CACHE="${i#*=}"
    shift
    ;;
    -sc=*|--store-cache=*)
    export STORE_CACHE="${i#*=}"
    shift
    ;;
    -gc=*|--gems-cache=*)
    export GEMS_CACHE="${i#*=}"
    shift
    ;;
    -gp=*|--github-project=*)
    export GIT_PROJECT="${i#*=}"
    shift
    ;;
    -bi=*|--build-image=*)
    export BUILD_IMAGE="${i#*=}"
    shift
    ;;
     -bi|--build-image)
    export BUILD_IMAGE="true"
    shift
    ;;
    -h|--help)
    echo -e "Usage: you can use scripts publish.sh and prepare_build_prod_and_run.sh with following parameters"
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
    echo -e ""
    echo -e "  -bc=<command> \t\t Command that should be used for opening a browser"
    echo -e "  --browser-command=<command>  \t Default value is 'firefox'"
    echo -e ""
    echo -e "  -bt=<browser> \t\t Browser to be used for executing UI tests"
    echo -e "  --browser-test=<browser>  \t Default value is 'chromeHeadless'"
    echo -e ""
    echo -e "  -uc=<path> \t\t \t Path to '_tmp' directory to be used as a cache. If specified then it is copied to the project directory"
    echo -e "  --use-cache=<path> "
    echo -e ""
    echo -e "  -sc=<path> \t\t  \t Path to a location where the '_tmp' directory should be stored as a cache"
    echo -e "  --store-cache=<path>"
    echo -e ""
    echo -e "  -gc=<path> \t\t  \t Path to a location where the '.gems' directory should be stored and loaded (if already exists)"
    echo -e "  --gems-cache=<path>"
    echo -e ""
    echo -e "  -gp=<url> \t\t \t Url to GitHub project to be cloned and used for generating web pages"
    echo -e "  --github-project=<url>  \t Default value is: `git remote get-url origin`"
    echo -e ""
    echo -e "  -bi [-bi=<true/false>] \t If the arquillian-org image should be locally build and not pulled"
    echo -e "  --build-image \t \t Default value is false"
    exit
    ;;
    *)
    ;;
esac
done


export CURRENT_DIR=`pwd`
export IGNORE_TEST_FAILURE=${IGNORE_TEST_FAILURE:-"false"}
export BROWSER_TEST=${BROWSER_TEST:-"chromeHeadless"}
export BROWSER_COMMAND=${BROWSER_COMMAND:-"firefox"}