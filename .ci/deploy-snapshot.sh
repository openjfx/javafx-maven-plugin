#!/usr/bin/env bash

# Find version
ver=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

# deploy if snapshot found
if [[ $ver == *"SNAPSHOT"* ]] 
then
    cp .travis.settings.xml $HOME/.m2/settings.xml
    mvn deploy -DskipTests=true -Dgpg.skip
fi