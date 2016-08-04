#!/bin/bash
#
# Simple script to set up Okapi with modules, tenants, etc
# Extracts the examples from guide.md
#
# You should be in the main okapi directory, typically ~/proj/okapi
# (but you can be in .../okapi/doc too)
#
# Okapi should be running in a separate console window, start it with
#     java -jar okapi-core/target/okapi-core-fat.jar dev

########
# Parameters

OKAPI=${1:-"http://localhost:9130"}   # The usual place it runs on a single-machine setup
GUIDE=${2:-"doc/guide.md"}  # Where to find the guide
if [ -f $GUIDE ] # usually because running in doc
then
  echo "Extracting examples from $GUIDE"
else
  echo "$GUIDE not found, trying ./guide.md"
  GUIDE="./guide.md"
fi
if [ ! -f $GUIDE ] # Check again
then
  echo "No guide found: $GUIDE Giving up"
  exit 1
fi

# Check that Okapi is running
curl -w '\n' $OKAPI/_/proxy/tenants || exit 1

# Extract the example JSON from the guide
perl -n -e  'print if /^cat /../^END/;' $GUIDE  | sh

# And run the example curl commands that actually talk to Okapi, excluding
# the clean-up delete commands, so we leave Okapi fully loaded.
perl -n -e  'print if /^curl /../http/; ' $GUIDE |
  grep -v 8080 | grep -v DELETE |
  sh -x

# The last line of output should say "It works"
