#!/usr/bin/env bash

# Exit immediately if any command in the script fails
set -e

# Configure GIT
git config --global user.name "Gluon Bot"
git config --global user.email "githubbot@gluonhq.com"

# Decrypt encrypted files
openssl aes-256-cbc -K $encrypted_04317ab43744_key -iv $encrypted_04317ab43744_iv -in .ci/gpg_keys.tar.enc -out gpg_keys.tar -d
if [[ ! -s gpg_keys.tar ]]
   then echo "Decryption failed."
   exit 1
fi
tar xvf gpg_keys.tar

# Release artifacts
cp .travis.settings.xml $HOME/.m2/settings.xml && mvn deploy -DskipTests=true -B -U -Prelease

# Update version by 1
newVersion=${TRAVIS_TAG%.*}.$((${TRAVIS_TAG##*.} + 1))

# Update README with the latest released version
sed -i '/<plugin>/ {
    :start
    N
    /<\/plugin>$/!b start
    /<artifactId>javafx-maven-plugin<\/artifactId>/ {
        /<version>.*<\/version>/s//<version>'"$TRAVIS_TAG"'<\/version>/
    }
}' README.md
git commit README.md -m "Use latest release v$TRAVIS_TAG in README"

# Update project version to next snapshot version
mvn versions:set -DnewVersion=$newVersion-SNAPSHOT -DgenerateBackupPoms=false

git commit pom.xml -m "Prepare development of $newVersion"
git push https://gluon-bot:$GITHUB_PASSWORD@github.com/$TRAVIS_REPO_SLUG HEAD:master

# Update archetypes
bash .ci/update-archetypes.sh "$TRAVIS_TAG"

# Update openjfx-docs
bash .ci/update-openjfx-docs.sh "$TRAVIS_TAG"