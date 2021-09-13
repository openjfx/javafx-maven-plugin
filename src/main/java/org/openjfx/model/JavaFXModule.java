/*
 * Copyright (c) 2018, 2020, Gluon
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openjfx.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public enum JavaFXModule {

    BASE,
    GRAPHICS(BASE),
    CONTROLS(BASE, GRAPHICS),
    FXML(BASE, GRAPHICS),
    MEDIA(BASE, GRAPHICS),
    SWING(BASE, GRAPHICS),
    WEB(BASE, CONTROLS, GRAPHICS, MEDIA);

    static final String PREFIX_MODULE = "javafx.";
    private static final String PREFIX_ARTIFACT = "javafx-";

    private List<JavaFXModule> dependentModules;

    JavaFXModule(JavaFXModule...dependentModules) {
        this.dependentModules = List.of(dependentModules);
    }

    public static Optional<JavaFXModule> fromArtifactName(String artifactName) {
        return Stream.of(JavaFXModule.values())
                .filter(javaFXModule -> artifactName.equals(javaFXModule.getArtifactName()))
                .findFirst();
    }

    public String getModuleName() {
        return PREFIX_MODULE + name().toLowerCase(Locale.ROOT);
    }

    public String getModuleJarFileName() {
        return getModuleName() + ".jar";
    }

    public String getArtifactName() {
        return PREFIX_ARTIFACT + name().toLowerCase(Locale.ROOT);
    }

    public List<JavaFXModule> getDependentModules() {
        return dependentModules;
    }

    public List<JavaFXDependency> getTransitiveMavenDependencies(String platform, String version) {
        List<JavaFXDependency> mavenDependencies = new ArrayList<>();
        for (JavaFXModule dependentModule : dependentModules) {
            mavenDependencies.add(dependentModule.getMavenDependency(platform, version));
        }
        return mavenDependencies;
    }

    public JavaFXDependency getMavenDependency(String platform, String version) {
        final JavaFXDependency dependency = new JavaFXDependency(getArtifactName(), version);
        dependency.setVersion(version);
        dependency.setClassifier(platform);
        return dependency;
    }
}
