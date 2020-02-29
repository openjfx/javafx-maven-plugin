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
    <version>0.0.4</version>
    <configuration>
        <mainClass>hellofx/org.openjfx.App</mainClass>
    </configuration>
</plugin>
```

To compile the project (optional):

```
mvn javafx:compile
```

Alternatively, the `maven-compiler-plugin` can be used:

```
mvn compile
```

Note that including this plugin is convenient for a better 
project integration within your IDE.

To run the project:

```
mvn javafx:run
```

For modular projects, to create and run a custom image:

```
mvn javafx:jlink

target/image/bin/java -m hellofx/org.openjfx.App
```

### javafx:compile options

When compiling with ``javafx:compile``, the source level, 
target level and/or the release level for the Java compiler can be set. 
The default value is 11.

This configuration changes these levels to 12, for instance:

```
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.4</version>
    <configuration>
        <source>12</source>
        <target>12</target>
        <release>12</release>
        <mainClass>org.openjfx.hellofx/org.openjfx.App</mainClass>
    </configuration>
</plugin>
```

If required, compiler arguments can be set. For instance:

```
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.4</version>
    <configuration>
        <compilerArgs>
            <arg>--add-exports</arg>
            <arg>javafx.graphics/com.sun.glass.ui=org.openjfx.hellofx</arg>
        </compilerArgs>
        <mainClass>org.openjfx.hellofx/org.openjfx.App</mainClass>
    </configuration>
</plugin>
```

### javafx:run options

The plugin includes by default: `--module-path`, `--add-modules` and `-classpath` options. 

Optionally, the configuration can be modified with:

- `mainClass`: The main class, fully qualified name, with or without module name
- `workingDirectory`: The current working directory
- `skip`: Skip the execution. Values: false (default), true
- `outputFile` File to redirect the process output
- `options`: A list of VM options passed to the executable.
- `commandlineArgs`: Arguments separated by space for the executed program
- `includePathExceptionsInClasspath`: When resolving the module-path, setting this value to true will include the 
dependencies that generate path exceptions in the classpath. By default the value is false, and these dependencies 
won't be included.

For instance, the following configuration adds some VM options and a command line argument:

```
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.4</version>
    <configuration>
        <mainClass>org.openjfx.hellofx/org.openjfx.App</mainClass>
        <options>
            <option>--add-opens</option>
            <option>java.base/java.lang=org.openjfx.hellofx</option>
        </options>
        <commandlineArgs>-Xmx1024m</commandlineArgs>
    </configuration>
</plugin>
```

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
    <version>0.0.4</version>
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

a custom image can be created and run as:

```
mvn clean javafx:jlink

target/hello/bin/hellofx
```

## Issues and Contributions ##

Issues can be reported to the [Issue tracker](https://github.com/openjfx/javafx-maven-plugin/issues/).

Contributions can be submitted via [Pull requests](https://github.com/openjfx/javafx-maven-plugin/pulls/), 
providing you have signed the [Gluon Individual Contributor License Agreement (CLA)](https://docs.google.com/forms/d/16aoFTmzs8lZTfiyrEm8YgMqMYaGQl0J8wA0VJE2LCCY).

