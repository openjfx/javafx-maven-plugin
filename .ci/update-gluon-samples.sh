SAMPLES_REPO_SLUG=gluonhq/gluon-samples

cd $TRAVIS_BUILD_DIR
git clone https://github.com/$SAMPLES_REPO_SLUG
cd gluon-samples

# Update plugin version
mvn versions:set-property -Dproperty=javafx.maven.plugin.version -DnewVersion="$1" -DgenerateBackupPoms=false

git commit pom.xml -m "Update javafx-maven-plugin version to $1"
git push https://gluon-bot:$GITHUB_PASSWORD@github.com/$SAMPLES_REPO_SLUG HEAD:master
