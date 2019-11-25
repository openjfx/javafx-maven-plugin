package org.openjfx;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class JavaFXBaseMojoTest {

    private static Path path;
    private static String tempDirPath;

    @BeforeClass
    public static void setup() throws IOException {
        tempDirPath = System.getProperty("java.io.tmpdir");
        path = Files.createDirectories(Paths.get(tempDirPath, "test", "test"));
    }
    
    @Test
    public void parentTest() {
        Assert.assertEquals(Paths.get(tempDirPath), JavaFXBaseMojo.getParent(path, 2));
    }

    @Test
    public void invalidParentTest() {
        Assert.assertNull(JavaFXBaseMojo.getParent(path, 5));
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
                .forEach(File::delete);
    }
}
