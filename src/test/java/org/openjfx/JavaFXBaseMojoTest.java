package org.openjfx;

import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.openjfx.model.RuntimePathOption.CLASSPATH;
import static org.openjfx.model.RuntimePathOption.MODULEPATH;

public class JavaFXBaseMojoTest {

    private static Path path;
    private static String tempDirPath;
    
    private JavaFXBaseMojo mojo;
    private JavaModuleDescriptor moduleDescriptor;

    @BeforeClass
    public static void setup() throws IOException {
        tempDirPath = System.getProperty("java.io.tmpdir");
        path = Files.createDirectories(Paths.get(tempDirPath, "test", "test"));
    }
    
    @Before
    public void create() {
        mojo = new JavaFXBaseMojo() {
            @Override
            public void execute() {
                //no-op
            }
        };
        moduleDescriptor = JavaModuleDescriptor.newModule("hellofx").build();
    }
    
    @Test
    public void parentTest() {
        Assert.assertEquals(Paths.get(tempDirPath), JavaFXBaseMojo.getParent(path, 2));
    }

    @Test
    public void mainClassStringWithModuleDescriptor() {
        Assert.assertEquals("hellofx/org.openjfx.Main", mojo.createMainClassString("org.openjfx.Main", moduleDescriptor, null));
    }

    @Test
    public void mainClassStringWithoutModuleDescriptor() {
        Assert.assertEquals("org.openjfx.Main", mojo.createMainClassString("org.openjfx.Main", null, null));
        Assert.assertEquals("hellofx/org.openjfx.Main", mojo.createMainClassString("hellofx/org.openjfx.Main", null, null));
    }

    @Test
    public void mainClassStringWithClasspathWithModuleDescriptor() {
        Assert.assertEquals("org.openjfx.Main", mojo.createMainClassString("org.openjfx.Main", moduleDescriptor, CLASSPATH));
        Assert.assertEquals("org.openjfx.Main", mojo.createMainClassString("hellofx/org.openjfx.Main", moduleDescriptor, CLASSPATH));
    }

    @Test
    public void mainClassStringWithClasspathWithoutModuleDescriptor() {
        Assert.assertEquals("org.openjfx.Main", mojo.createMainClassString("org.openjfx.Main", null, CLASSPATH));
        Assert.assertEquals("org.openjfx.Main", mojo.createMainClassString("hellofx/org.openjfx.Main", null, CLASSPATH));
    }

    @Test
    public void mainClassStringWithModulepathWithModuleDescriptor() {
        Assert.assertEquals("hellofx/org.openjfx.Main", mojo.createMainClassString("org.openjfx.Main", moduleDescriptor, MODULEPATH));
        Assert.assertEquals("hellofx/org.openjfx.Main", mojo.createMainClassString("hellofx/org.openjfx.Main", moduleDescriptor, MODULEPATH));
    }

    @Test
    public void mainClassStringWithModulepathWithoutModuleDescriptor() {
        Assert.assertEquals("org.openjfx.Main", mojo.createMainClassString("org.openjfx.Main", null, MODULEPATH));
        Assert.assertEquals("hellofx/org.openjfx.Main", mojo.createMainClassString("hellofx/org.openjfx.Main", null, MODULEPATH));
    }

    @Test
    public void invalidParentTest() {
        Assert.assertNull(JavaFXBaseMojo.getParent(path, 10));
    }

    @Test
    public void invalidPathTest() {
        Assert.assertNull(JavaFXBaseMojo.getParent(Paths.get("/some-invalid-path"), 0));
    }

    @Test
    public void invalidPathWithDepthTest() {
        Assert.assertNull(JavaFXBaseMojo.getParent(Paths.get("/some-invalid-path"), 2));
    }

    @AfterClass
    public static void destroy() throws IOException {
        Files.walk(path.getParent())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .filter(f -> "test".equals(f.getName()))
                .forEach(File::delete);
    }
}
