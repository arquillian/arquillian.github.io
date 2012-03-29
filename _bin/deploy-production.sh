#!/bin/bash

BIN_DIR=$(dirname "$0")
ROOT_DIR="$BIN_DIR/.."
TMP_DIR="$ROOT_DIR/_tmp"
SITE_DIR="$ROOT_DIR/_site"
SASS_CACHE_DIR="$ROOT_DIR/.sass-cache"
DATACACHE_DIR="$TMP_DIR/datacache"
GITHUB_DIR="$TMP_DIR/restcache/github"
LANYRD_DIR="$TMP_DIR/lanyrd"
DEPLOY_REPO='git@github.com:arquillian/arquillian.github.com.git'

if [[ $# -ne 1 ]]; then
  DEPLOY_DIR="$ROOT_DIR/_deploy"
  if [[ ! -d "$DEPLOY_DIR/.git" ]]; then
    echo "Specify the path to the clone of $DEPLOY_REPO"
    exit 1
  fi
else
  DEPLOY_DIR="$1"
  if [[ ! -d "$DEPLOY_DIR/.git" ]]; then
    echo "Not a git repository: $DEPLOY_DIR"
    exit 1
  fi
fi

set -e

pushd $DEPLOY_DIR
if ! git remote -v | grep -qF "$DEPLOY_REPO"; then
  echo "Not a $DEPLOY_REPO clone: $DEPLOY_DIR"
  exit 1
fi
popd

cd $ROOT_DIR

pushd $GITHUB_DIR
# TODO check if github repository has been updated since the contributions file was written, then nuke the contributions file
#rm -f contributors-*.json
popd

pushd $LANYRD_DIR
rm -f search-*.html
popd

rm -rf $DATACACHE_DIR
rm -rf $SITE_DIR
rm -rf $SASS_CACHE_DIR

awestruct -P production -g

pushd $DEPLOY_DIR
git pull
popd

rsync -a "$SITE_DIR/" "$DEPLOY_DIR/"
#rsync -a --delete "$SITE_DIR/" "$DEPLOY_DIR/src/main/webapp/"

pushd $DEPLOY_DIR
git add .
git commit -m 'publish'
#git push
popd

exit 0
