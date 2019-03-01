# Maven plugin for JavaFX

Maven plugin to run JavaFX 11+ applications

## Install

Clone the project, set JDK 11 and run

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
    <version>11.0.2</version>
</dependency>
```

Add the plugin:

```
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.1</version>
    <configuration>
        <mainClass>org.openjfx.App</mainClass>
    </configuration>
</plugin>
```

To compile the project (optional):

```
mvn javafx:compile
```

To run the project:

```
mvn javafx:run
```

### Optional arguments:

The plugin includes by default: `--module-path`, `--add-modules` and `-classpath`. 

Optionally, other VM arguments and runtime arguments can be set:

```
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <version>0.0.1</version>
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