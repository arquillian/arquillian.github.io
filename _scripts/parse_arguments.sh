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
    -cv=*|--chrome-driver-version=*)
    export CHROME_DRIVER_VERSION="${i#*=}"
    shift
    ;;
    -gp=*|--github-project=*)
    export GIT_PROJECT="${i#*=}"
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
    echo -e "  -gp=<url> \t\t \t Url to GitHub project to be cloned and used for generating web pages"
    echo -e "  --github-project=<url>  \t Default value is: `git remote get-url origin`"
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
