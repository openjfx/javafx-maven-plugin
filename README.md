# Maven plugin for JavaFX

[![Maven Central](https://img.shields.io/maven-central/v/org.openjfx/javafx-maven-plugin.svg?color=%234DC71F)](https://search.maven.org/#search|ga|1|org.openjfx.javafx-maven-plugin)
[![Travis CI](https://api.travis-ci.com/openjfx/javafx-maven-plugin.svg?branch=master)](https://travis-ci.com/openjfx/javafx-maven-plugin)
[![Apache License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Maven plugin to run JavaFX 11+ applications

## Install

The plugin is available via Maven Central. 

In case you want to build and install the latest snapshot, you can
clone the project, set JDK 11 and run

```
mvn install
``` 

## Usage

Create a new Maven project, use an existing one like [HelloFX](https://github.com/openjfx/samples/tree/master/CommandLine/Modular/Maven/hellofx), or use an [archetype](https://github.com/openjfx/javafx-maven-archetypes).

The project can be modular or non-modular.

JavaFX dependencies are added as usual:

```
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>12.0.2</version>
</dependency>
```

Add the plugin:

```
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.8</version>
    <configuration>
        <mainClass>hellofx/org.openjfx.App</mainClass>
    </configuration>
</plugin>
```

Compile the project:

```
mvn compile
```

This step is optional and can be configured using the [maven-compiler-plugin](https://maven.apache.org/plugins/maven-compiler-plugin/).

Run the project:

```
mvn javafx:run
```

For modular projects, create and run a custom image:

```
mvn javafx:jlink

target/image/bin/java -m hellofx/org.openjfx.App
```

### javafx:run options

The plugin includes by default: `--module-path`, `--add-modules` and `-classpath` options. 

Optionally, the configuration can be modified with:

- `mainClass`: The main class, fully qualified name, with or without module name
- `workingDirectory`: The current working directory
- `skip`: Skip the execution. Values: false (default), true
- `outputFile`: File to redirect the process output
- `options`: A list of VM options passed to the executable.
- `commandlineArgs`: Arguments separated by space for the executed program
- `includePathExceptionsInClasspath`: When resolving the module-path, setting this value to true will include the 
dependencies that generate path exceptions in the classpath. By default, the value is false, and these dependencies 
won't be included.
- `runtimePathOption`: By default, the plugin will place *each* dependency either on modulepath or on classpath (based on certain factors).
When `runtimePathOption` configuration is set, the plugin will place *all* the dependencies on either modulepath or classpath.

    If set as `MODULEPATH`, a module descriptor is required. All dependencies need to be either modularized or contain an Automatic-Module-Name.

    If set as `CLASSPATH`, a Launcher class ([like this one](https://github.com/openjfx/samples/blob/master/CommandLine/Non-modular/CLI/hellofx/src/hellofx/Launcher.java))
is required to run a JavaFX application. Also, if a module-info descriptor is present, it will be ignored.

    Values: MODULEPATH or CLASSPATH.

This plugin supports Maven toolchains using the "jdk" tool.

### Example

The following configuration adds some VM options, and a command line argument:

```
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.8</version>
    <configuration>
        <mainClass>org.openjfx.hellofx/org.openjfx.App</mainClass>
        <options>
            <option>-Dbar=${bar}</option>
            <option>--add-opens</option>
            <option>java.base/java.lang=org.openjfx.hellofx</option>
        </options>
        <commandlineArgs>foo</commandlineArgs>
    </configuration>
</plugin>
```

When running maven with
```
mvn -Dbar=myBar javafx:run
```
it will be processed by the main method like:

```java
public static void main(String[] args) {
    if (args.length > 0 && "foo".equals(args[0])) {
        // do something
    }
    if ("myBar".equals(System.getProperty("bar"))) {
        // do something
    }
    launch();
}
```

Note that the evaluation of `System.getProperty("bar")` can happen in any other place in the code.

**Note**

It is possible to use a local SDK instead of Maven Central. 
This is helpful for developers trying to test a local build of OpenJFX. 
Since transitive dependencies are not resolved, 
all the required jars needs to be added as a separate dependency, like:

```
<properties>
    <sdk>/path/to/javafx-sdk</sdk>
</properties>

<dependencies>
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx.base</artifactId>
        <version>1.0</version>
        <scope>system</scope>
        <systemPath>${sdk}/lib/javafx.base.jar</systemPath>
    </dependency>
    ...
</dependencies>
```

### javafx:jlink options

The same command line options for `jlink` can be set:

- `stripDebug`: Strips debug information out. Values: false (default) or true
- `stripJavaDebugAttributes`: Strip Java debug attributes out (since Java 13), Values: false (default) or true
- `compress`: Compression level of the resources being used. Values: 0 (default), 1, 2. 
- `noHeaderFiles`: Removes the `includes` directory in the resulting runtime image. Values: false (default) or true
- `noManPages`: Removes the `man` directory in the resulting runtime image. Values: false (default) or true
- `bindServices`: Adds the option to bind services. Values: false (default) or true
- `ignoreSigningInformation`: Adds the option to ignore signing information. Values: false (default) or true
- `jlinkVerbose`: Adds the verbose option. Values: false (default) or true
- `launcher`: Adds a launcher script with the given name. 
    - If `options` are defined, these will be passed to the launcher script as vm options. 
    - If `commandLineArgs` are defined, these will be passed to the launcher script as command line arguments.
- `jlinkImageName`: The name of the folder with the resulting runtime image
- `jlinkZipName`: When set, creates a zip of the resulting runtime image
- `jlinkExecutable`: The `jlink` executable. It can be a full path or the name of the executable, if it is in the PATH.
- `jmodsPath`: When using a local JavaFX SDK, sets the path to the local JavaFX jmods

For instance, with the following configuration:

```
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.8</version>
    <configuration>
        <stripDebug>true</stripDebug>
        <compress>2</compress>
        <noHeaderFiles>true</noHeaderFiles>
        <noManPages>true</noManPages>
        <launcher>hellofx</launcher>
        <jlinkImageName>hello</jlinkImageName>
        <jlinkZipName>hellozip</jlinkZipName>
        <mainClass>hellofx/org.openjfx.MainApp</mainClass>
    </configuration>
</plugin>
```

A custom image can be created and run as:

```
mvn clean javafx:jlink

target/hello/bin/hellofx
```

## Issues and Contributions ##

Issues can be reported to the [Issue tracker](https://github.com/openjfx/javafx-maven-plugin/issues/).

Contributions can be submitted via [Pull requests](https://github.com/openjfx/javafx-maven-plugin/pulls/), 
providing you have signed the [Gluon Individual Contributor License Agreement (CLA)](https://docs.google.com/forms/d/16aoFTmzs8lZTfiyrEm8YgMqMYaGQl0J8wA0VJE2LCCY).

