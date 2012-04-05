#!/bin/bash

set -e

BIN_DIR=$(dirname "$0")
ROOT_DIR="$BIN_DIR/.."
TMP_DIR="$ROOT_DIR/_tmp"
SITE_DIR="$ROOT_DIR/_site"
SASS_CACHE_DIR="$ROOT_DIR/.sass-cache"
DATACACHE_DIR="$TMP_DIR/datacache"
GITHUB_DIR="$TMP_DIR/restcache/github"
LANYRD_DIR="$TMP_DIR/lanyrd"
DEPLOY_REPO='git@github.com:arquillian/arquillian.github.com.git'

CLEAN=0
PUSH=0
while getopts "cpr:" option
do
  case $option in
    c) CLEAN=1 ;;
    p) PUSH=1 ;;
    r) DEPLOY_DIR=$OPTARG ;;
  esac
done

if [ -z $DEPLOY_DIR ]; then
  DEPLOY_DIR="$ROOT_DIR/_deploy"
  if [[ ! -d "$DEPLOY_DIR/.git" ]]; then
    echo "Specify the path to the clone of $DEPLOY_REPO"
    exit 1
  fi
else
  if [[ ! -d "$DEPLOY_DIR/.git" ]]; then
    echo "Not a git repository: $DEPLOY_DIR"
    exit 1
  fi
fi

set -e

pushd $DEPLOY_DIR > /dev/null
if ! git remote -v | grep -qF "$DEPLOY_REPO"; then
  echo "Not a $DEPLOY_REPO clone: $DEPLOY_DIR"
  exit 1
fi
popd

cd $ROOT_DIR

if [[ `git status -s | wc -l` -gt 0 ]]; then
  echo "Please commit these local changes before publishing:"
  git status -s
  exit 1
fi

if [[ `git diff upstream/develop | wc -l` -gt 0 ]]; then
  echo "Please push these local changes before publishing:"
  git log upstream/develop..
  exit 1
fi

pushd $GITHUB_DIR > /dev/null
# TODO check if github repository has been updated since the contributions file was written, then nuke the contributions file
#rm -f contributors-*.json
popd

pushd $LANYRD_DIR > /dev/null
rm -f search-*.html
popd

if [ $CLEAN -eq 1 ]; then
  rm -rf $DATACACHE_DIR
fi
rm -rf $SITE_DIR
rm -rf $SASS_CACHE_DIR

awestruct -P production -g

pushd $DEPLOY_DIR > /dev/null
git pull
popd

rsync -a --delete --exclude='.git' "$SITE_DIR/" "$DEPLOY_DIR/"

pushd $DEPLOY_DIR > /dev/null
git add .
git commit -m 'publish'
if [ $PUSH -eq 1 ]; then
  git push origin master
fi
popd

exit 0
