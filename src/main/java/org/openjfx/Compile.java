/*
 * Copyright 2019 Gluon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openjfx;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Runs resources and compile plugin
 */
class Compile {

    public static void compile(MavenProject project, MavenSession session, BuildPluginManager pluginManager,
                               String source, String target, String release) throws MojoExecutionException {
        ExecutionEnvironment env = executionEnvironment(
                project,
                session,
                pluginManager);

        executeMojo(
                plugin(groupId("org.apache.maven.plugins"),
                        artifactId("maven-resources-plugin"),
                        version("2.6")),
                goal("resources"),
                configuration(),
                env);

        executeMojo(
                plugin(groupId("org.apache.maven.plugins"),
                        artifactId("maven-compiler-plugin"),
                        version("3.8.0")),
                goal("compile"),
                configuration(
                        element(name("source"), source),
                        element(name("target"), target),
                        element(name("release"), release)
                ),
                env);
    }
}
