package org.openjfx.model;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;

import java.util.ArrayList;
import java.util.List;

public class JavaFXDependency extends Dependency {

    public JavaFXDependency(String artifactId, String version) {
        setArtifactId(artifactId);
        setVersion(version);
        setGroupId("org.openjfx");
        setExclusions(exclusions());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaFXDependency that = (JavaFXDependency) o;
        return this.getArtifactId().equals(that.getArtifactId());
    }
    
    private List<Exclusion> exclusions() {
        final Exclusion exclusion = new Exclusion();
        exclusion.setGroupId("org.openjfx");
        exclusion.setArtifactId("*");
        List<Exclusion> exclusions = new ArrayList<>();
        exclusions.add(exclusion);
        return exclusions;
    }
}
