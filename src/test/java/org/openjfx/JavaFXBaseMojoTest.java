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

    @Test
    public void testSplitComplexArgumentString() {
        String option = "param1 " +
                "param2   \n   " +
                "param3\n" +
                "param4=\"/path/to/my file.log\"   " +
                "'var\"foo   var\"foo' " +
                "'var\"foo'   " +
                "'var\"foo' " +
                "\"foo'var foo'var\" " +
                "\"foo'var\" " +
                "\"foo'var\"";

        String expected = "START," +
                "param1," +
                "param2," +
                "param3," +
                "param4=\"/path/to/my file.log\"," +
                "'var\"foo   var\"foo'," +
                "'var\"foo'," +
                "'var\"foo'," +
                "\"foo'var foo'var\"," +
                "\"foo'var\"," +
                "\"foo'var\"";

        String splitOption = mojo.splitComplexArgumentString(option)
                .stream().reduce("START", (s1, s2) -> s1 + "," + s2);

        Assert.assertEquals(expected, splitOption);
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
