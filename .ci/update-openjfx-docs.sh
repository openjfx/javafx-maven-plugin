#!/usr/bin/env bash

export REPO_NAME=openjfx-docs
export REPO_SLUG=openjfx/openjfx-docs
export VERSION_FILE_LOCATION=js/gluon.js

cd $TRAVIS_BUILD_DIR
git clone https://github.com/$REPO_SLUG
cd $REPO_NAME

# Update javafx-maven-plugin version
sed -i "0,/JFX_MVN_PLUGIN_VERSION = \".*\"/s//JFX_MVN_PLUGIN_VERSION = \"$1\"/" $VERSION_FILE_LOCATION

git commit $VERSION_FILE_LOCATION -m "Upgrade javafx-maven-plugin version to $1"
git push https://gluon-bot:$GITHUB_PASSWORD@github.com/$REPO_SLUG HEAD:master