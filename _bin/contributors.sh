#!/bin/sh

CONTRIBUTORS=`grep -h 'login' _tmp/github/contributors-* | sed 's/ *"login": "\([^"]\+\)",\?/\1/' | sort | uniq`
for c in $CONTRIBUTORS; do
  echo $c
done
echo $CONTRIBUTORS | wc -w
