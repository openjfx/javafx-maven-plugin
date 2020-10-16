/*
 * Copyright 2020, Gluon
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
package org.openjfx.model;

/**
 * All the classes and dependencies are added to either the classpath or modulepath depending on the option set in the plugin configuration.
 * If not set, the plugin chooses the suitable option for classes and dependencies based on a few parameters, like presence of module descriptor file. 
 */
public enum RuntimePathOption {
    /**
     * Puts all the dependencies on the classpath. If a module-info.java is present, it is ignored.
     * A <a href="https://github.com/openjfx/samples/blob/master/CommandLine/Non-modular/CLI/hellofx/src/hellofx/Launcher.java">Launcher class</a> is
     * required to run a JavaFX application from the classpath.
     */
    CLASSPATH,
    /**
     * Puts all the dependencies on the modulepath.
     */
    MODULEPATH
}
