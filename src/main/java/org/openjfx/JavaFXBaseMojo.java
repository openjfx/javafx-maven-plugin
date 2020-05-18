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
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.OS;
import org.apache.commons.exec.ProcessDestroyer;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.codehaus.plexus.languages.java.jpms.LocationManager;
import org.codehaus.plexus.languages.java.jpms.ModuleNameSource;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsRequest;
import org.codehaus.plexus.languages.java.jpms.ResolvePathsResult;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;


abstract class JavaFXBaseMojo extends AbstractMojo {

    static final Pattern WINDOWS_EXECUTABLE_PATTERN = Pattern.compile("^(?<basename>.+)(?<extension>\\.(exe|com|bat|cmd))$");
    static final String JAVAFX_PREFIX = "javafx";

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private BuildPluginManager pluginManager;

    @Component
    private LocationManager locationManager;

    @Component
    private ToolchainManager toolchainManager;

    @Parameter(property = "javafx.mainClass", required = true)
    String mainClass;

    /**
     * Skip the execution.
     */
    @Parameter(property = "javafx.skip", defaultValue = "false")
    boolean skip;

    @Parameter(readonly = true, required = true, defaultValue = "${basedir}")
    File basedir;

    @Parameter(readonly = true, required = true, defaultValue = "${project.build.directory}")
    File builddir;

    /**
     * The current working directory. Optional. If not specified, basedir will be used.
     */
    @Parameter(property = "javafx.workingDirectory")
    File workingDirectory;

    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> compilePath;

    @Parameter(property = "javafx.outputFile")
    File outputFile;

    /**
     * If set to true the child process executes asynchronously and build execution continues in parallel.
     */
    @Parameter(property = "javafx.async", defaultValue = "false")
    private boolean async;

    /**
     * If set to true, the asynchronous child process is destroyed upon JVM shutdown. If set to false, asynchronous
     * child process continues execution after JVM shutdown. Applies only to asynchronous processes; ignored for
     * synchronous processes.
     */
    @Parameter(property = "javafx.asyncDestroyOnShutdown", defaultValue = "true")
    private boolean asyncDestroyOnShutdown;

    /**
     * <p>
     * A list of vm options passed to the {@code executable}.
     * </p>
     *
     */
    @Parameter
    List<?> options;

    /**
     * Arguments separated by space for the executed program. For example: "-j 20"
     */
    @Parameter(property = "javafx.args")
    String commandlineArgs;

    /**
     * <p>The -source argument for the Java compiler.</p>
     */
    @Parameter(property = "javafx.source", defaultValue = "11")
    private String source;

    /**
     * <p>The -target argument for the Java compiler.</p>
     */
    @Parameter(property = "javafx.target", defaultValue = "11")
    private String target;

    /**
     * The -release argument for the Java compiler
     */
    @Parameter(property = "javafx.release", defaultValue = "11")
    private String release;

    /**
     * If set to true, it will include the dependencies that
     * generate path exceptions in the classpath. Default is false.
     */
    @Parameter(property = "javafx.includePathExceptionsInClasspath", defaultValue = "false")
    private boolean includePathExceptionsInClasspath;

    List<String> classpathElements;
    List<String> modulepathElements;
    Map<String, JavaModuleDescriptor> pathElements;
    JavaModuleDescriptor moduleDescriptor;
    private ProcessDestroyer processDestroyer;

    static boolean isMavenUsingJava8() {
        return System.getProperty("java.version").startsWith("1.8");
    }

    static boolean isTargetUsingJava8(CommandLine commandLine) {
        final String executable = commandLine.getExecutable();
        try {
            return executable != null && Files.exists(Paths.get(executable).toRealPath().resolve("../../jre/lib/rt.jar").normalize());
        } catch (IOException e) {
            return false;
        }
    }

    void preparePaths(Path jdkHome) throws MojoExecutionException {
        if (project == null) {
            return;
        }

        String outputDirectory = project.getBuild().getOutputDirectory();
        if (outputDirectory == null || outputDirectory.isEmpty()) {
            throw new MojoExecutionException("Error: Output directory doesn't exist");
        }

        File[] classes = new File(outputDirectory).listFiles();
        if (classes == null || classes.length == 0) {
            throw new MojoExecutionException("Error: Output directory is empty");
        }

        File moduleDescriptorPath = Stream
                .of(classes)
                .filter(file -> "module-info.class".equals(file.getName()))
                .findFirst()
                .orElse(null);

        modulepathElements = new ArrayList<>(compilePath.size());
        classpathElements = new ArrayList<>(compilePath.size());
        pathElements = new LinkedHashMap<>(compilePath.size());

        try {
            Collection<File> dependencyArtifacts = getCompileClasspathElements(project);
            getLog().debug("Total dependencyArtifacts: " + dependencyArtifacts.size());
            ResolvePathsRequest<File> fileResolvePathsRequest = ResolvePathsRequest.ofFiles(dependencyArtifacts);

            ResolvePathsResult<File> resolvePathsResult;
            if (moduleDescriptorPath != null) {
                getLog().debug("module descriptor: " + moduleDescriptorPath);
                fileResolvePathsRequest.setMainModuleDescriptor(moduleDescriptorPath);
            }
            if (jdkHome != null) {
                fileResolvePathsRequest.setJdkHome(jdkHome.toFile());
            }
            resolvePathsResult = locationManager.resolvePaths(fileResolvePathsRequest);

            if (!resolvePathsResult.getPathExceptions().isEmpty() && !isMavenUsingJava8()) {
                // for each path exception, show a warning to plugin user...
                for (Map.Entry<File, Exception> pathException : resolvePathsResult.getPathExceptions().entrySet()) {
                    Throwable cause = pathException.getValue();
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    String fileName = pathException.getKey().getName();
                    getLog().warn("Can't extract module name from " + fileName + ": " + cause.getMessage());
                }
                // ...if includePathExceptionsInClasspath is NOT enabled; provide configuration hint to plugin user
                if (!includePathExceptionsInClasspath) {
                    getLog().warn("Some dependencies encountered issues while attempting to be resolved as modules" +
                        " and will not be included in the classpath; you can change this behavior via the " +
                        " 'includePathExceptionsInClasspath' configuration parameter.");
                }
            }

            if (moduleDescriptorPath != null) {
                moduleDescriptor = resolvePathsResult.getMainModuleDescriptor();
            }

            for (Map.Entry<File, ModuleNameSource> entry : resolvePathsResult.getModulepathElements().entrySet()) {
                if (ModuleNameSource.FILENAME.equals(entry.getValue())) {
                    final String message = "Required filename-based automodules detected. "
                            + "Please don't publish this project to a public artifact repository!";

                    if (moduleDescriptor != null && moduleDescriptor.exports().isEmpty()) {
                        // application
                        getLog().info(message);
                    } else {
                        // library
                        getLog().warn(message);
                    }
                    break;
                }
            }

            getLog().debug("pathElements: " + resolvePathsResult.getPathElements().size());
            resolvePathsResult.getPathElements().forEach((key, value) -> pathElements.put(key.getPath(), value));
            getLog().debug("classpathElements: " + resolvePathsResult.getClasspathElements().size());
            resolvePathsResult.getClasspathElements()
                    .forEach(file -> classpathElements.add(file.getPath()));
            getLog().debug("modulepathElements: " + resolvePathsResult.getModulepathElements().size());
            resolvePathsResult.getModulepathElements().keySet()
                    .forEach(file -> modulepathElements.add(file.getPath()));

            if (includePathExceptionsInClasspath) {
                resolvePathsResult.getPathExceptions().keySet()
                    .forEach(file -> classpathElements.add(file.getPath()));
            }

            if (moduleDescriptorPath == null) {
                // non-modular projects
                pathElements.forEach((k, v) -> {
                    if (v != null && v.name() != null && v.name().startsWith(JAVAFX_PREFIX)) {
                        // only JavaFX jars are required in the module-path
                        modulepathElements.add(k);
                    } else {
                        classpathElements.add(k);
                    }
                });
            }

        } catch (Exception e) {
            getLog().warn(e.getMessage());
        }

        getLog().debug("Classpath:" + classpathElements.size());
        classpathElements.forEach(s -> getLog().debug(" " + s));

        getLog().debug("Modulepath: " + modulepathElements.size());
        modulepathElements.forEach(s -> getLog().debug(" " + s));

        getLog().debug("pathElements: " + pathElements.size());
        pathElements.forEach((k, v) -> getLog().debug(" " + k + " :: " + (v != null && v.name() != null ? v.name() : v)));
    }

    private List<File> getCompileClasspathElements(MavenProject project) {
        List<File> list = new ArrayList<>();
        list.add(new File(project.getBuild().getOutputDirectory()));

        // include systemPath dependencies
        list.addAll(project.getDependencies().stream()
                .filter(d -> d.getSystemPath() != null && ! d.getSystemPath().isEmpty())
                .map(d -> new File(d.getSystemPath()))
                .collect(toList()));

        list.addAll(project.getArtifacts().stream()
                .sorted((a1, a2) -> {
                    int compare = a1.compareTo(a2);
                    if (compare == 0) {
                        // give precedence to classifiers
                        return a1.hasClassifier() ? 1 : (a2.hasClassifier() ? -1 : 0);
                    }
                    return compare;
                })
                .map(Artifact::getFile)
                .collect(toList()));
        return list.stream()
                .distinct()
                .collect(toList());
    }

    void handleWorkingDirectory() throws MojoExecutionException {
        if (workingDirectory == null) {
            workingDirectory = basedir;
        }

        if (!workingDirectory.exists()) {
            getLog().debug("Making working directory '" + workingDirectory.getAbsolutePath() + "'.");
            if (!workingDirectory.mkdirs()) {
                throw new MojoExecutionException("Could not make working directory: '" + workingDirectory.getAbsolutePath() + "'");
            }
        }
    }

    Map<String, String> handleSystemEnvVariables() {
        Map<String, String> enviro = new HashMap<>();
        try {
            Properties systemEnvVars = CommandLineUtils.getSystemEnvVars();
            for (Map.Entry<?, ?> entry : systemEnvVars.entrySet()) {
                enviro.put((String) entry.getKey(), (String) entry.getValue());
            }
        } catch (IOException x) {
            getLog().error("Could not assign default system environment variables.", x);
        }

        return enviro;
    }

    protected Path resolve(
            final String property,
            final String toolSpec,
            final Map<String, String> env,
            final File workingDirectory
    ) throws MojoExecutionException {

        Path tool = Paths.get(toolSpec);

        // if the set path is absolute, use it
        if (tool.isAbsolute()) {
            getLog().warn(format("Toolchain will be ignored, '%s' is set to '%s'", property, tool));
            return tool;
        }

        // munge tool name as needed for following steps
        final List<String> candidateToolNames = getCandidateToolNames(tool);

        // if the path is unqualified resolve to a directory in the following sequence
        if (tool.equals(tool.getFileName())) {

            // try to resolve to the maven toolchain
            Toolchain toolchain = getJDKToolchain();
            if (toolchain != null) {
                getLog().info("Toolchain in javafx-maven-plugin: " + toolchain);
                final String pathString = toolchain.findTool(tool.toString());
                if (pathString != null) {
                    return Paths.get(pathString);
                }
            }

            // try to resolve to the invocation JDK
            final Path jdkBin = Paths.get(System.getProperty("java.home"), "../bin");
            for (String candidate : candidateToolNames) {
                Path toolPath = jdkBin.resolve(candidate).normalize().toAbsolutePath();
                if (Files.isExecutable(toolPath)) {
                    return toolPath;
                }
            }

            // search $PATH explicitly
            for (final Path basedir : getExecutablePaths(env)) {
                for (String candidate : candidateToolNames) {
                    Path toolPath = basedir.resolve(candidate).normalize().toAbsolutePath();
                    if (Files.isExecutable(toolPath)) {
                        return toolPath;
                    }
                }
            }

        }

        // resolve relative to the working directory
        for (String candidate : candidateToolNames) {
            Path toolPath = workingDirectory.toPath().resolve(candidate).normalize().toAbsolutePath();
            if (Files.isExecutable(toolPath)) {
                return toolPath;
            }
        }

        // fall back to treating the tool path as basedir-relative
        for (String candidate : candidateToolNames) {
            Path toolPath = basedir.toPath().resolve(candidate).normalize().toAbsolutePath();
            if (Files.isExecutable(toolPath)) {
                return toolPath;
            }
        }

        throw new MojoExecutionException(format("Could not resolve '%s': %s", property, tool));
    }

    private static List<String> getCandidateToolNames(final Path tool) {
        final List<String> candidateToolNames = new ArrayList<>();
        final String toolName = tool.toString();
        final Matcher matcher = WINDOWS_EXECUTABLE_PATTERN.matcher(toolName.toLowerCase());
        if (OS.isFamilyWindows() && !matcher.matches()) {
            Stream.of(".exe", ".com", ".cmd", ".bat")
                  .forEach(extension -> candidateToolNames.add(toolName + extension));
        } else if (!OS.isFamilyWindows() && matcher.matches()) {
            candidateToolNames.add(matcher.group("basename"));
        } else {
            candidateToolNames.add(toolName);
        }
        return candidateToolNames;
    }

    CommandLine getCommandLine(Path exec) {
        final CommandLine toRet;
        if (isWindowsBatchFile(exec)) {
            // run the windows batch script in isolation and exit at the end
            final String comSpec = System.getenv("ComSpec");
            toRet = new CommandLine(comSpec == null ? "cmd" : comSpec);
            toRet.addArgument("/c");
            toRet.addArgument(exec.toString());
        } else {
            toRet = new CommandLine(exec.toFile());
        }
        getLog().debug("Executable " + toRet.toString());
        return toRet;
    }

    int executeCommandLine(Executor exec, CommandLine commandLine, Map<String, String> enviro,
                           OutputStream out, OutputStream err) throws IOException {
        // note: don't use BufferedOutputStream here since it delays the outputs MEXEC-138
        PumpStreamHandler psh = new PumpStreamHandler(out, err, System.in);
        return executeCommandLine(exec, commandLine, enviro, psh);
    }

    int executeCommandLine(Executor exec, CommandLine commandLine, Map<String, String> enviro,
                           FileOutputStream outputFile) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(outputFile);
        PumpStreamHandler psh = new PumpStreamHandler(bos);
        return executeCommandLine(exec, commandLine, enviro, psh);
    }

    /**
     * Returns the path of the parent directory.
     * At the given depth if the path has no parent, the method returns null.
     * @param path Path against which the parent needs to be evaluated
     * @param depth Depth of the path relative to parent
     * @return Path to the parent, if exists. Null, otherwise.
     */
    static Path getParent(Path path, int depth) {
        if (path == null || !Files.exists(path) || depth > path.getNameCount()) {
            return null;
        }
        return path.getRoot().resolve(path.subpath(0, path.getNameCount() - depth));
    }

    private static boolean isWindowsBatchFile(final Path exec) {
        return OS.isFamilyWindows() && !hasNativeExtension(exec) && hasExecutableExtension(exec);
    }

    private static boolean hasNativeExtension(final Path exec) {
        final String lowerCase = exec.getFileName().toString().toLowerCase();
        return lowerCase.endsWith(".exe") || lowerCase.endsWith(".com");
    }

    private static boolean hasExecutableExtension(final Path exec) {
        final String lowerCase = exec.getFileName().toString().toLowerCase();
        for (final String ext : getExecutableExtensions()) {
            if (lowerCase.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> getExecutableExtensions() {
        final String pathExt = System.getenv("PATHEXT");
        return pathExt == null ? asList(".bat", ".cmd")
                : asList(StringUtils.split(pathExt.toLowerCase(), File.pathSeparator));
    }

    private List<Path> getExecutablePaths(Map<String, String> env) {
        String path = env.get("PATH");
        return path == null ? emptyList() : stream(path.split(File.pathSeparator)).map(Paths::get).collect(toList());
    }

    private int executeCommandLine(Executor exec, final CommandLine commandLine, Map<String, String> enviro,
                                   final PumpStreamHandler psh) throws IOException {
        exec.setStreamHandler(psh);

        int result;
        try {
            psh.start();
            if (async) {
                if (asyncDestroyOnShutdown) {
                    exec.setProcessDestroyer(getProcessDestroyer());
                }

                exec.execute(commandLine, enviro, new ExecuteResultHandler() {
                    public void onProcessFailed(ExecuteException e) {
                        getLog().error("Async process failed for: " + commandLine, e);
                    }

                    public void onProcessComplete(int exitValue) {
                        getLog().debug("Async process complete, exit value = " + exitValue + " for: " + commandLine);
                        try {
                            psh.stop();
                        } catch (IOException e) {
                            getLog().error("Error stopping async process stream handler for: " + commandLine, e);
                        }
                    }
                });
                result = 0;
            } else {
                result = exec.execute(commandLine, enviro);
            }
        } finally {
            if (!async) {
                psh.stop();
            }
        }
        return result;
    }

    private ProcessDestroyer getProcessDestroyer() {
        if (processDestroyer == null) {
            processDestroyer = new ShutdownHookProcessDestroyer();
        }
        return processDestroyer;
    }

    protected Toolchain getJDKToolchain() {
        return (toolchainManager == null || session == null)
               ? null
               : toolchainManager.getToolchainFromBuildContext("jdk", session);
    }

}
