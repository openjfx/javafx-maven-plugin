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
import org.twdata.maven.mojoexecutor.MojoExecutor;
import org.twdata.maven.mojoexecutor.MojoExecutor.Element;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

/**
 * Runs resources and compile plugin
 */
class Compile {

    public static void compile(MavenProject project, MavenSession session, BuildPluginManager pluginManager,
                               Map<String, String> elements,
                               List<String> compilerArgs, List<String> excludes) throws MojoExecutionException {
        MojoExecutor.ExecutionEnvironment env = executionEnvironment(
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
        
        final List<Element> config = elements.entrySet().stream()
                .map(entry -> element(name(entry.getKey()), entry.getValue()))
                .collect(Collectors.toList());
        config.add(element(name("compilerArgs"), compilerArgs.stream()
                .filter(Objects::nonNull)
                .map(s -> new Element("arg", s))
                .toArray(Element[]::new)));
        if (!elements.containsKey("release") && elements.get("release") == null && !excludes.contains("module-info.java")) {
            excludes.add("module-info.java");
        }
        config.add(element(name("excludes"), excludes.stream()
                .filter(Objects::nonNull)
                .map(s -> new Element("exclude", s))
                .toArray(Element[]::new)));
        
        executeMojo(plugin(groupId("org.apache.maven.plugins"),
                artifactId("maven-compiler-plugin"),
                version("3.8.1")),
                goal("compile"),
                configuration(config.toArray(new Element[config.size()])),
                env);
    }
}
