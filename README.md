# Maven plugin to package JavaFX 11+ applications 

Maven plugin to package JavaFX 11+ applications.


[![Apache License](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

This plugin supports legacy Java applications that lack necessary module information to be assembled using jlink.
To _run_ or package via [jlink](https://docs.oracle.com/javase/9/tools/jlink.htm#JSWOR-GUID-CECAC52B-CFEE-46CB-8166-F17A8E9280E9),
see [javafx-maven-plugin](https://github.com/openjfx/javafx-maven-plugin).


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
	<groupId>edu.uchc.connjur</groupId>
	<artifactId>connjurpackage-maven-plugin</artifactId>
	<version>1.0.0</version>
	<configuration>
	  <mainClass>hellofx/org.openjfx.App</mainClass>
	  <!-- optional configurations
	  <absolute>true</absolute>
	  <jarDirectory>myjars</jarDirectory>
	  <packageDirectory>mypackages</packageDirectory>
	  -->
	</configuration>
</plugin>
```

### connjurpackage:package 

The one goal is connjurpackage:package.
```
mvn connjurpackage:package
```
The plugin includes by default: `--module-path`, `--add-modules` and `-classpath` options. 

Optionally, the configuration can be modified with:

#### inherited options
- `mainClass`: The main class, fully qualified name, with or without module name
- `workingDirectory`: The current working directory
- `skip`: Skip the execution. Values: false (default), true
- `outputFile` File to redirect the process output
- `options`: A list of VM options passed to the executable.
- `commandlineArgs`: Arguments separated by space for the executed program
- `includePathExceptionsInClasspath`: When resolving the module-path, setting this value to true will include the 
dependencies that generate path exceptions in the classpath. By default the value is false, and these dependencies 
won't be included.
#### package options
- `absolute`: use absolute paths in generated scripts (default is relative)
- `jarDirectory`: specify name of jar directory (default is "jars") 
- `packageDirectory`: specify name of package directory (default is "package") 

See https://github.com/openjfx/javafx-maven-plugin for details on _inherited options._

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

