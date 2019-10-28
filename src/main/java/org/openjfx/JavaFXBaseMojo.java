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
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class JavaFXBaseMojo extends AbstractMojo {

    static final String JAVAFX_PREFIX = "javafx";

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private BuildPluginManager pluginManager;

    @Component
    private LocationManager locationManager;

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
     * A list of excluded classes passed to the compiler.
     */
    @Parameter
    private List<String> excludes;

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
     * A list of arguments passed to the compiler.
     */
    @Parameter
    private List<String> compilerArgs;

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

    void preparePaths() throws MojoExecutionException, MojoFailureException {
        if (project == null) {
            return;
        }

        String outputDirectory = project.getBuild().getOutputDirectory();
        if (outputDirectory == null || outputDirectory.isEmpty()) {
            throw new MojoExecutionException("Output directory doesn't exist, compile first");
        }

        File[] classes = new File(outputDirectory).listFiles();
        if (classes == null || classes.length == 0) {
            getLog().debug("Output directory was empty, compiling...");
            compile();
            classes = new File(outputDirectory).listFiles();
            if (classes == null || classes.length == 0) {
                throw new MojoExecutionException("Output directory is empty, compile first");
            }
        } else {
            // TODO: verify if classes require compiling
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
                fileResolvePathsRequest = fileResolvePathsRequest.setMainModuleDescriptor(moduleDescriptorPath);
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
            resolvePathsResult.getPathElements().entrySet()
                    .forEach(entry -> pathElements.put(entry.getKey().getPath(), entry.getValue()));
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
                .collect(Collectors.toList()));

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
                .collect(Collectors.toList()));
        return list.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    void compile() throws MojoExecutionException {
        if (compilerArgs == null) {
            compilerArgs = new ArrayList<>();
        }
        if (excludes == null) {
            excludes = new ArrayList<>();
        }
        String specifyRelease = isMavenUsingJava8() ? null : release;
        Compile.compile(project, session, pluginManager, source, target, specifyRelease, compilerArgs, excludes);
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

    CommandLine getExecutablePath(String executable, Map<String, String> enviro, File dir) {
        File execFile = new File(executable);
        String exec = null;
        if (execFile.isFile()) {
            getLog().debug("Toolchains are ignored, 'executable' parameter is set to " + executable);
            exec = execFile.getAbsolutePath();
        }

        if (exec == null && OS.isFamilyWindows()) {
            List<String> paths = this.getExecutablePaths(enviro);
            paths.add(0, dir.getAbsolutePath());
            exec = findExecutable(executable, paths);
        }

        if (exec == null) {
            String javaHome = System.getProperty("java.home", getJavaHome(enviro));
            if (javaHome != null && ! javaHome.isEmpty()) {
                exec = findExecutable(executable, Arrays.asList(javaHome.concat(File.separator).concat("bin")));
            }
        }

        if (exec == null) {
            exec = executable;
        }

        CommandLine toRet;
        if (OS.isFamilyWindows() && !hasNativeExtension(exec) && hasExecutableExtension(exec) ) {
            // run the windows batch script in isolation and exit at the end
            final String comSpec = System.getenv( "ComSpec" );
            toRet = new CommandLine( comSpec == null ? "cmd" : comSpec );
            toRet.addArgument( "/c" );
            toRet.addArgument( exec );
        } else {
            toRet = new CommandLine(exec);
        }
        getLog().debug("Executable " + toRet.toString());
        return toRet;
    }

    private static String findExecutable(final String executable, final List<String> paths) {
        File f = null;
        search: for (final String path : paths) {
            f = new File(path, executable);
            if (!OS.isFamilyWindows() && f.isFile()) {
                break;
            } else {
                for (final String extension : getExecutableExtensions()) {
                    f = new File(path, executable + extension);
                    if (f.isFile()) {
                        break search;
                    }
                }
            }
        }

        if (f == null || !f.exists()) {
            return null;
        }
        return f.getAbsolutePath();
    }

    private static boolean hasNativeExtension(final String exec) {
        final String lowerCase = exec.toLowerCase();
        return lowerCase.endsWith(".exe") || lowerCase.endsWith(".com");
    }

    private static boolean hasExecutableExtension(final String exec) {
        final String lowerCase = exec.toLowerCase();
        for (final String ext : getExecutableExtensions()) {
            if (lowerCase.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> getExecutableExtensions() {
        final String pathExt = System.getenv("PATHEXT");
        return pathExt == null ? Arrays.asList(".bat", ".cmd")
                : Arrays.asList(StringUtils.split(pathExt.toLowerCase(), File.pathSeparator));
    }

    private List<String> getExecutablePaths(Map<String, String> enviro) {
        List<String> paths = new ArrayList<>();
        paths.add("");

        String path = enviro.get("PATH");
        if (path != null) {
            paths.addAll(Arrays.asList(StringUtils.split(path, File.pathSeparator)));
        }
        return paths;
    }

    private String getJavaHome(Map<String, String> enviro) {
        return enviro.get("JAVA_HOME");
    }

    int executeCommandLine(Executor exec, CommandLine commandLine, Map<String, String> enviro,
                                     OutputStream out, OutputStream err) throws ExecuteException, IOException {
        // note: don't use BufferedOutputStream here since it delays the outputs MEXEC-138
        PumpStreamHandler psh = new PumpStreamHandler(out, err, System.in);
        return executeCommandLine(exec, commandLine, enviro, psh);
    }

    int executeCommandLine(Executor exec, CommandLine commandLine, Map<String, String> enviro,
                                   FileOutputStream outputFile) throws ExecuteException, IOException {
        BufferedOutputStream bos = new BufferedOutputStream(outputFile);
        PumpStreamHandler psh = new PumpStreamHandler(bos);
        return executeCommandLine(exec, commandLine, enviro, psh);
    }

    private int executeCommandLine(Executor exec, final CommandLine commandLine, Map<String, String> enviro,
                                   final PumpStreamHandler psh) throws ExecuteException, IOException {
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
}
