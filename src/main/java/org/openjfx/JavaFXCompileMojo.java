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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

@Mojo(name = "compile", defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class JavaFXCompileMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private BuildPluginManager pluginManager;

    /**
     * <p>The -source argument for the Java compiler.</p>
     */
    @Parameter(property = "javafx.source", defaultValue = "11")
    protected String source;

    /**
     * <p>The -target argument for the Java compiler.</p>
     */
    @Parameter(property = "javafx.target", defaultValue = "11")
    protected String target;

    /**
     * The -release argument for the Java compiler
     */
    @Parameter(property = "javafx.release", defaultValue = "11")
    protected String release;

    public void execute() throws MojoExecutionException {
        Compile.compile(project, session,  pluginManager, source, target, release);
    }
}
