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

import org.apache.commons.exec.CommandLine;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static org.openjfx.JavaFXBaseMojo.Executable.JAVAC;

@Mojo(name = "compile", defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class JavaFXCompileMojo extends JavaFXBaseMojo {

    /**
     * <p>
     * The executable. Can be a full path or the name of the executable.
     * In the latter case, the executable must be in the PATH for the execution to work.
     * </p>
     */
    @Parameter(property = "javafx.javacExecutable", defaultValue = "javac")
    private String javacExecutable;

    public void execute() throws MojoExecutionException {

        CommandLine commandLine;

        if (javaHome != null && "javac".equalsIgnoreCase(javacExecutable)) {
            javacExecutable = getPathFor(JAVAC);
        }

        if (javacExecutable == null) {
            throw new MojoExecutionException("The parameter 'executable' is missing or invalid");
        }

        compile(javacExecutable);
    }
}
