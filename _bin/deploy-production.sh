#!/bin/bash

set -e

function pushdq() { pushd "$1" > /dev/null; }
function popdq() { popd > /dev/null; }
function error() { echo "$*" >&2; }

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
KEEP=0
PUSH=0
MESSAGE='manual publish'
while getopts "ckpm:r:" option
do
  case $option in
    c) CLEAN=1 ;;
    k) KEEP=1 ;;
    p) PUSH=1 ;;
    m) MESSAGE=$OPTARG ;;
    r) DEPLOY_DIR=$OPTARG ;;
  esac
done

if [ -z $DEPLOY_DIR ]; then
  DEPLOY_DIR="$ROOT_DIR/_deploy"
  if [[ ! -d "$DEPLOY_DIR/.git" ]]; then
    error "Specify the path to the clone of $DEPLOY_REPO"
    exit 1
  fi
else
  if [[ ! -d "$DEPLOY_DIR/.git" ]]; then
    error "Not a git repository: $DEPLOY_DIR"
    exit 1
  fi
fi

set -e

pushdq $DEPLOY_DIR
if ! git remote -v | grep -qF "$DEPLOY_REPO"; then
  error "Not a $DEPLOY_REPO clone: $DEPLOY_DIR"
  exit 1
fi
popdq

cd $ROOT_DIR

if [[ `git status -s | wc -l` -gt 0 ]]; then
  error "Please commit these local changes before publishing:"
  error `git status -s`
  exit 1
fi

if [[ `git diff upstream/develop | wc -l` -gt 0 ]]; then
  error "Please push these local changes before publishing:"
  error `git log upstream/develop..`
  exit 1
fi

# TODO check if github repository has been updated since the contributions file was written, then nuke the contributions file
#pushdq $GITHUB_DIR
#rm -f *-contributors.json
#popdq

if [ $CLEAN -eq 1 ]; then
  pushdq $LANYRD_DIR
  rm -f search-*.html
  popdq

  rm -rf $DATACACHE_DIR
fi

if [ $KEEP -eq 0 ]; then
  #rm -rf $SITE_DIR
  #rm -rf $SASS_CACHE_DIR
  awestruct --force -g -P production
else
  awestruct -P production -g
fi


pushdq $DEPLOY_DIR
git pull origin master
popdq

rsync -a --delete --exclude='.git' "$SITE_DIR/" "$DEPLOY_DIR/"

pushdq $DEPLOY_DIR
git add -A .
git commit -m "$MESSAGE"
if [ $PUSH -eq 1 ]; then
  git push origin master
fi
popdq

exit 0
