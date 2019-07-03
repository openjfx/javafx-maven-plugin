#!/usr/bin/env bash

# Decrypt encrypted files
openssl aes-256-cbc -K $encrypted_8795c864beda_key -iv $encrypted_8795c864beda_iv -in .ci/secring.gpg.enc -out secring.gpg -d

# Release artifacts
cp .travis.settings.xml $HOME/.m2/settings.xml && mvn deploy -DskipTests=true -B -U -Prelease

# Update version by 1
newVersion=${TRAVIS_TAG%.*}.$((${TRAVIS_TAG##*.} + 1))

# Replace first occurrence of TRAVIS_TAG with newVersion appended with SNAPSHOT
sed -i "0,/<version>$TRAVIS_TAG/s//<version>$newVersion-SNAPSHOT/" pom.xml

git commit pom.xml -m "Upgrade version to $newVersion-SNAPSHOT" --author "Github Bot <githubbot@gluonhq.com>"
git push https://gluon-bot:$GITHUB_PASSWORD@github.com/gluonhq/javafx-maven-plugin HEAD:master