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

import org.apache.commons.exec.*;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.languages.java.jpms.*;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class JavaFXRunMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private BuildPluginManager pluginManager;

    @Component
    private LocationManager locationManager;

    @Parameter(property = "javafx.mainClass", required = true)
    private String mainClass;

    /**
     * Skip the execution.
     */
    @Parameter(property = "javafx.skip", defaultValue = "false")
    private boolean skip;

    /**
     * <p>
     * The executable. Can be a full path or the name of the executable. In the latter case, the executable must be in
     * the PATH for the execution to work.
     * </p>
     */
    @Parameter(property = "javafx.executable", defaultValue = "java")
    private String executable;

    @Parameter(readonly = true, required = true, defaultValue = "${basedir}")
    private File basedir;

    /**
     * The current working directory. Optional. If not specified, basedir will be used.
     */
    @Parameter(property = "javafx.workingdir")
    private File workingDirectory;

    @Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
    private List<String> compilePath;

    @Parameter(property = "javafx.outputFile")
    private File outputFile;

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
    @Parameter(property = "exec.asyncDestroyOnShutdown", defaultValue = "true")
    private boolean asyncDestroyOnShutdown = true;

    /**
     * <p>
     * A list of vm options passed to the {@code executable}.
     * </p>
     *
     */
    @Parameter
    private List<?> options;

    /**
     * Arguments separated by space for the executed program. For example: "-j 20"
     */
    @Parameter(property = "javafx.args")
    private String commandlineArgs;

    private List<String> classpathElements;
    private List<String> modulepathElements;
    private Map<String, JavaModuleDescriptor> pathElements;
    private JavaModuleDescriptor moduleDescriptor;
    private ProcessDestroyer processDestroyer;

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

            List<String> commandArguments = new ArrayList<>();
            handleArguments(commandArguments);

            Map<String, String> enviro = handleSystemEnvVariables();
            CommandLine commandLine = getExecutablePath(enviro, workingDirectory);
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

    private void handleWorkingDirectory() throws MojoExecutionException {
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

    private Map<String, String> handleSystemEnvVariables() throws MojoExecutionException {
        Map<String, String> enviro = new HashMap<>();
        try {
            Properties systemEnvVars = CommandLineUtils.getSystemEnvVars();
            for (Map.Entry<?, ?> entry : systemEnvVars.entrySet()) {
                enviro.put((String) entry.getKey(), (String) entry.getValue());
            }
        } catch (IOException x) {
            getLog().error("Could not assign default system enviroment variables.", x);
        }

        return enviro;
    }

    private CommandLine getExecutablePath(Map<String, String> enviro, File dir) {
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
        return toRet;
    }

    private void handleArguments(List<String> commandArguments) throws MojoExecutionException, MojoFailureException {
        preparePaths();

        if (options != null) {
            options.stream()
                    .filter(Objects::nonNull)
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .forEach(commandArguments::add);
        }

        if (modulepathElements != null && !modulepathElements.isEmpty()) {
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
                        .filter(module -> !module.endsWith("Empty"))
                        .collect(Collectors.joining(","));
                commandArguments.add(" " + modules);
            }
        }

        if (classpathElements != null && !classpathElements.isEmpty()) {
            commandArguments.add(" -classpath");
            String classpath = "";
            if (moduleDescriptor != null) {
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
    }

    private void preparePaths() throws MojoExecutionException, MojoFailureException {
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

            for (Map.Entry<File, Exception> pathException : resolvePathsResult.getPathExceptions().entrySet()) {
                Throwable cause = pathException.getValue();
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                String fileName = pathException.getKey().getName();
                getLog().warn("Can't extract module name from " + fileName + ": " + cause.getMessage());
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

            if (moduleDescriptorPath == null) {
                pathElements.forEach((k, v) -> {
                    if (v != null && v.name() != null) {
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
        List<File> list = project.getArtifacts().stream()
                .sorted((a1, a2) -> {
                    int compare = a1.compareTo(a2);
                    if (compare == 0) {
                        // give precedence to classifiers
                        return a1.hasClassifier() ? 1 : (a2.hasClassifier() ? -1 : 0);
                    }
                    return compare;
                })
                .map(Artifact::getFile)
                .collect(Collectors.toList());
        list.add(0, new File(project.getBuild().getOutputDirectory()));
        return list;
    }

    static String findExecutable(final String executable, final List<String> paths) {
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

    protected int executeCommandLine(Executor exec, CommandLine commandLine, Map<String, String> enviro,
                                     OutputStream out, OutputStream err) throws ExecuteException, IOException {
        // note: don't use BufferedOutputStream here since it delays the outputs MEXEC-138
        PumpStreamHandler psh = new PumpStreamHandler(out, err, System.in);
        return executeCommandLine(exec, commandLine, enviro, psh);
    }

    private int executeCommandLine(Executor exec, CommandLine commandLine, Map<String, String> enviro,
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

    private void compile() throws MojoExecutionException {
        Compile.compile(project, session, pluginManager);
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
