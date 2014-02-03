#!/bin/bash
# Stop on error
set -e

# Disable script
#exit

echo "Bringing snomed-release-system up to date."
echo "Running as user `whoami`"
echo

# Pull updates. (Project should be cloned over ssh using an ssh-key without a password to allow unattended git pull.)
cd ../.source/snomed-release-system

echo "Pulling updates..."
git pull --verbose 2>&1
echo

echo "Maven build..."
mvn clean install
echo

echo "Deploy API..."
cp api/target/api.war ../../.webapps/
echo

echo "Deploy Static Site..."
rm -rf ../../snomed-release-system-web/*
cp -r web/* ../../snomed-release-system-web/
echo

echo "Done"