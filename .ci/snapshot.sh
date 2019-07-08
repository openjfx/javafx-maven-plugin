#!/usr/bin/env bash

# Find version
ver=$(grep -m1 '<version>' pom.xml)
ver=${ver%<*};
ver=${ver#*>};

# deploy if snapshot found
if [[ $ver == *"SNAPSHOT"* ]] 
then
    cp .travis.settings.xml $HOME/.m2/settings.xml
    mvn deploy -DskipTests=true -Dgpg.skip
fi 