#!/bin/bash

BIN_DIR=$(dirname "$0")
ROOT_DIR="$BIN_DIR/.."
SITE_DIR="$ROOT_DIR/_site"
SASS_CACHE_DIR="$ROOT_DIR/.sass-cache"

rm -rf "$SITE_DIR"
rm -rf "$SASS_CACHE_DIR"

awestruct -s "$@"
