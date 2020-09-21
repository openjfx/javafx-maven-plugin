/*
 * Copyright 2019, 2020, Gluon
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
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.openjfx.model.RuntimePathOption.CLASSPATH;
import static org.openjfx.model.RuntimePathOption.MODULEPATH;

@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class JavaFXRunMojo extends JavaFXBaseMojo {

    /**
     * <p>
     * The executable. Can be a full path or the name of the executable. In the latter case, the executable must be in
     * the PATH for the execution to work.
     * </p>
     */
    @Parameter(property = "javafx.executable", defaultValue = "java")
    private String executable;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info( "skipping execute as per configuration" );
            return;
        }

        if (executable == null) {
            throw new MojoExecutionException("The parameter 'executable' is missing or invalid");
        }

        if (basedir == null) {
            throw new IllegalStateException( "basedir is null. Should not be possible." );
        }

        try {
            handleWorkingDirectory();

            Map<String, String> enviro = handleSystemEnvVariables();
            CommandLine commandLine = getExecutablePath(executable, enviro, workingDirectory);

            boolean usingOldJDK = isTargetUsingJava8(commandLine);

            List<String> commandArguments = createCommandArguments(usingOldJDK);
            String[] args = commandArguments.toArray(new String[commandArguments.size()]);
            commandLine.addArguments(args, false);
            getLog().debug("Executing command line: " + commandLine);

            Executor exec = new DefaultExecutor();
            exec.setWorkingDirectory(workingDirectory);

            try {
                int resultCode;
                if (outputFile != null) {
                    if ( !outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
                        getLog().warn( "Could not create non existing parent directories for log file: " + outputFile );
                    }

                    FileOutputStream outputStream = null;
                    try {
                        outputStream = new FileOutputStream(outputFile);
                        resultCode = executeCommandLine(exec, commandLine, enviro, outputStream);
                    } finally {
                        IOUtil.close(outputStream);
                    }
                } else {
                    resultCode = executeCommandLine(exec, commandLine, enviro, System.out, System.err);
                }

                if (resultCode != 0) {
                    String message = "Result of " + commandLine.toString() + " execution is: '" + resultCode + "'.";
                    getLog().error(message);
                    throw new MojoExecutionException(message);
                }
            } catch (ExecuteException e) {
                getLog().error("Command execution failed.", e);
                e.printStackTrace();
                throw new MojoExecutionException("Command execution failed.", e);
            } catch (IOException e) {
                getLog().error("Command execution failed.", e);
                throw new MojoExecutionException("Command execution failed.", e);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error", e);
        }
    }

    private List<String> createCommandArguments(boolean oldJDK) throws MojoExecutionException {
        List<String> commandArguments = new ArrayList<>();
        preparePaths(getParent(Paths.get(executable), 2));

        if (options != null) {
            options.stream()
                    .filter(Objects::nonNull)
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(this::splitComplexArgumentString)
                    .flatMap(Collection::stream)
                    .forEach(commandArguments::add);
        }
        if (!oldJDK) {
            if (runtimePathOption == MODULEPATH || modulepathElements != null && !modulepathElements.isEmpty()) {
                commandArguments.add(" --module-path");
                String modulePath = StringUtils.join(modulepathElements.iterator(), File.pathSeparator);
                commandArguments.add(modulePath);

                commandArguments.add(" --add-modules");
                if (moduleDescriptor != null) {
                    commandArguments.add(" " + moduleDescriptor.name());
                } else {
                    String modules = pathElements.values().stream()
                            .filter(Objects::nonNull)
                            .map(JavaModuleDescriptor::name)
                            .filter(Objects::nonNull)
                            .filter(module -> module.startsWith(JAVAFX_PREFIX) && !module.endsWith("Empty"))
                            .collect(Collectors.joining(","));
                    commandArguments.add(" " + modules);
                }
            }
        }

        if (runtimePathOption == CLASSPATH || classpathElements != null && (oldJDK || !classpathElements.isEmpty())) {
            commandArguments.add(" -classpath");
            String classpath = "";
            if (oldJDK || moduleDescriptor != null) {
                classpath = project.getBuild().getOutputDirectory() + File.pathSeparator;
            }
            classpath += StringUtils.join(classpathElements.iterator(), File.pathSeparator);
            commandArguments.add(classpath);
        }

        if (mainClass != null) {
            if (moduleDescriptor != null) {
                commandArguments.add(" --module");
                if (!mainClass.startsWith(moduleDescriptor.name() + "/")) {
                    commandArguments.add(" " + moduleDescriptor.name() + "/" + mainClass);
                } else {
                    commandArguments.add(" " + mainClass);
                }
            } else {
                commandArguments.add(" " + mainClass);
            }
        }

        if (commandlineArgs != null) {
            commandArguments.add(commandlineArgs);
        }
        return commandArguments;
    }

    // for tests

    void setExecutable(String executable) {
        this.executable = executable;
    }

    void setBasedir(File basedir) {
        this.basedir = basedir;
    }

    void setCommandlineArgs(String commandlineArgs) {
        this.commandlineArgs = commandlineArgs;
    }

    List<String> splitComplexArgumentStringAdapter(String vmOptions) {
        return splitComplexArgumentString(vmOptions);
    }
}
