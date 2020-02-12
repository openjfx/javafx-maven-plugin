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
package edu.uchc.connjur.maven;

import org.apache.commons.exec.CommandLine;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.openjfx.JavaFXBaseMojo;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class ConnjurPackageMojo extends JavaFXBaseMojo {
    /**
     * <p>
     * The executable. Can be a full path or the name of the executable. In the latter case, the executable must be in
     * the PATH for the execution to work.
     * </p>
     */
    @Parameter(property = "javafx.executable", defaultValue = "java")
    private String executable;
    /**
     * <p>
     * The directory where the package will go. Can be relative or absolute path
     * </p>
     */
    @Parameter(property = "packageDirectory", defaultValue = "package")
    private String packageDirectory;
    /**
     * <p>
     * If true, the generated script will use absolute paths. By default, it uses
     * relative paths
     * </p>
     */
    @SuppressWarnings("unused")
    @Parameter(property = "absolute", defaultValue = "false")
    private boolean absolute;

    /**
     * <p>
     * Name of subdirectory jars are copied into.
     * </p>
     */
    @Parameter(property = "jarDirectory", defaultValue = "jars")
    private String jarDirectory;

    private Path output;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("skipping execute as per configuration");
            return;
        }

        if (basedir == null) {
            throw new IllegalStateException("basedir is null. Should not be possible.");
        }
        output = basedir.toPath()
                .resolve(project.getBuild().getOutputDirectory())
                .resolve("..")
                .resolve(packageDirectory)
                .normalize();

        try {
            handleWorkingDirectory();

            Map<String, String> enviro = handleSystemEnvVariables();
            CommandLine commandLine = getExecutablePath(executable, enviro, workingDirectory);

            boolean usingOldJDK = isTargetUsingJava8(commandLine);

            final File outputDirectory = output.toFile();
            if (outputDirectory.exists()) {
                if (!outputDirectory.isDirectory()) {
                    throw new MojoExecutionException("Existing output " + outputDirectory + " is not a directory");
                }
            } else {
                if (!outputDirectory.mkdirs()) {
                    throw new MojoExecutionException("Can't create directory " + outputDirectory);
                }
            }
            createPackage(usingOldJDK);
        } catch (MojoExecutionException mee) {
            throw mee;
        } catch (Exception e) {
            throw new MojoExecutionException("Error", e);
        }
    }

    private static class Copier implements Consumer<Path> {
        private final Path source;
        private final Path destination;

        Copier(Path source, Path destination) {
            this.source = source;
            this.destination = destination;
        }

        @Override
        public void accept(Path path) {
            try {
                Path fileDest = destination.resolve(source.relativize(path));
                if (Files.isDirectory(fileDest)) {
                    if (!Files.exists(fileDest)) {
                        Files.createDirectory(fileDest);
                    }
                    return;
                }
                Files.copy(path,fileDest,StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Can't copy " + source + " to "  + destination,e);
            }
        }
    }

    private void copy(List<String> source, StringBuilder script)
            throws IOException, MojoExecutionException {
        final String modulePrefix = jarDirectory + File.separatorChar;
        final Path destinationDirPath = output.resolve(jarDirectory);
        if (!Files.isDirectory(destinationDirPath) || !Files.exists(destinationDirPath)) {
            if (!destinationDirPath.toFile().mkdirs()) {
                throw new MojoExecutionException("Can't create directory " + destinationDirPath);
            }
        }
        for (String element : source) {
            Path fpath = Paths.get(element);
            final boolean isDirectory = Files.isDirectory(fpath);
            final Path basename = fpath.getFileName();
            final Path destination = destinationDirPath.resolve(basename);
            Path copied;
            if (isDirectory) {
                copied = destination;
                destination.toFile().mkdirs();
                Files.walk(fpath, FileVisitOption.FOLLOW_LINKS).forEach( new Copier(fpath,destination));
            }  else {
                copied = Files.copy(fpath, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            if (absolute) {
                script.append(copied);
                script.append(File.pathSeparatorChar);
            } else {
                script.append(modulePrefix);
                script.append(basename);
            }
            script.append(File.pathSeparatorChar);

        }
    }

    private void createPackage(boolean oldJDK) throws MojoExecutionException {
        try {
            preparePaths(getParent(Paths.get(executable), 2));

            StringBuilder script = new StringBuilder("#!/bin/bash");
            if (options != null) {
                options.stream()
                        .filter(Objects::nonNull)
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .forEach(script::append);
            }
            script.append(System.lineSeparator());
            script.append("java ");
            if (!oldJDK) {
                if (modulepathElements != null && !modulepathElements.isEmpty()) {
                    script.append("--module-path ");
                    copy(modulepathElements, script);

                    script.append(" --add-modules ");
                    if (moduleDescriptor != null) {
                        script.append(moduleDescriptor.name());
                    } else {
                        String modules = pathElements.values().stream()
                                .filter(Objects::nonNull)
                                .map(JavaModuleDescriptor::name)
                                .filter(Objects::nonNull)
                                .filter(module -> module.startsWith(JAVAFX_PREFIX) && !module.endsWith("Empty"))
                                .collect(Collectors.joining(","));
                        script.append(modules);
                    }
                }
            }

            if (classpathElements != null && (oldJDK || !classpathElements.isEmpty())) {
                script.append(" -classpath ");
                copy(classpathElements, script);
            }

            if (mainClass != null) {
                if (moduleDescriptor != null) {
                    script.append(" --module");
                    if (!mainClass.startsWith(moduleDescriptor.name() + "/")) {
                        script.append(" " + moduleDescriptor.name() + "/" + mainClass);
                    } else {
                        script.append(" " + mainClass);
                    }
                } else {
                    script.append(" " + mainClass);
                }
            }

            if (commandlineArgs != null) {
                script.append(commandlineArgs);
            }

            final Path scriptPath = output.resolve("script.sh");
            Files.write(scriptPath, script.toString().getBytes());
            final Set<PosixFilePermission> perm = Files.getPosixFilePermissions(scriptPath);
            perm.add(PosixFilePermission.OWNER_EXECUTE);
            perm.add(PosixFilePermission.GROUP_EXECUTE);
            perm.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(scriptPath, perm);

        } catch (IOException e) {
            throw new MojoExecutionException("Package create error", e);
        }
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
}
