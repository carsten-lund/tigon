/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.tigon.app.program;

import co.cask.tigon.api.flow.FlowSpecification;
import co.cask.tigon.internal.app.FlowSpecificationAdapter;
import co.cask.tigon.lang.ApiResourceListHolder;
import co.cask.tigon.lang.ClassLoaders;
import co.cask.tigon.lang.jar.BundleJarUtil;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import org.apache.twill.filesystem.Location;

import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import javax.annotation.Nullable;

/**
 * Default implementation of program.
 */
public final class DefaultProgram implements Program {

  private final String mainClassName;
  private final ProgramType processorType;

  private final Location programJarLocation;
  private final File expandFolder;
  private final File specFile;
  private boolean expanded;
  private ClassLoader classLoader;
  private FlowSpecification specification;

  /**
   * Creates a program instance.
   *
   * @param programJarLocation Location of the program jar file.
   * @param expandFolder Local directory for expanding the jar file into. If it is {@code null},
   *                     the {@link #getClassLoader()} methods would throw exception.
   */
  DefaultProgram(Location programJarLocation, @Nullable File expandFolder) throws IOException {
    this.programJarLocation = programJarLocation;
    this.expandFolder = expandFolder;
    this.processorType = ProgramType.FLOW;

    Manifest manifest = BundleJarUtil.getManifest(programJarLocation);
    if (manifest == null) {
      throw new IOException("Failed to load manifest in program jar from " + programJarLocation.toURI());
    }

    mainClassName = getAttribute(manifest, ManifestFields.MAIN_CLASS);

    // Load the app spec from the jar file if no expand folder is provided. Otherwise do lazy loading after the jar
    // is expanded.
    String specPath = getAttribute(manifest, ManifestFields.SPEC_FILE);
    if (expandFolder == null) {
      specification = FlowSpecificationAdapter.create().fromJson(
        CharStreams.newReaderSupplier(BundleJarUtil.getEntry(programJarLocation, specPath), Charsets.UTF_8));
      specFile = null;
    } else {
      specFile = new File(expandFolder, specPath);
    }
  }

  public DefaultProgram(Location programJarLocation, ClassLoader classLoader) throws IOException {
    this(programJarLocation, (File) null);
    this.classLoader = classLoader;
  }

  @Override
  public String getMainClassName() {
    return mainClassName;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> Class<T> getMainClass() throws ClassNotFoundException {
    return (Class<T>) getClassLoader().loadClass(mainClassName);
  }

  @Override
  public ProgramType getType() {
    return processorType;
  }

  @Override
  public String getId() {
    return getMainClassName();
  }

  @Override
  public String getName() {
    return getId();
  }

  @Override
  public synchronized FlowSpecification getSpecification() {
    if (specification == null) {
      expandIfNeeded();
      try {
        specification = FlowSpecificationAdapter.create().fromJson(
          CharStreams.newReaderSupplier(Files.newInputStreamSupplier(specFile), Charsets.UTF_8));
      } catch (IOException e) {
        throw Throwables.propagate(e);
      }
    }
    return specification;
  }

  @Override
  public Location getJarLocation() {
    return programJarLocation;
  }

  @Override
  public synchronized ClassLoader getClassLoader() {
    if (classLoader == null) {
      expandIfNeeded();
      try {
        classLoader = ClassLoaders.newProgramClassLoader(expandFolder, ApiResourceListHolder.getResourceList());
      } catch (IOException e) {
        throw Throwables.propagate(e);
      }
    }
    return classLoader;
  }

  private String getAttribute(Manifest manifest, Attributes.Name name) throws IOException {
    String value = manifest.getMainAttributes().getValue(name);
    check(value != null, "Fail to get %s attribute from jar", name);
    return value;
  }

  private void check(boolean condition, String fmt, Object... objs) throws IOException {
    if (!condition) {
      throw new IOException(String.format(fmt, objs));
    }
  }

  private synchronized void expandIfNeeded() {
    if (expanded) {
      return;
    }

    Preconditions.checkState(expandFolder != null, "Directory for jar expansion is not defined.");
    try {
      BundleJarUtil.unpackProgramJar(programJarLocation, expandFolder);
      expanded = true;
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }
}
