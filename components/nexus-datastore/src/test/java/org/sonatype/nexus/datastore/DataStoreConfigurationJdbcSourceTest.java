/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.datastore;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;

import com.google.common.collect.Lists;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.common.app.ApplicationDirectories;
import org.sonatype.nexus.datastore.api.DataStoreConfiguration;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.sonatype.nexus.datastore.api.DataStoreManager.DEFAULT_DATASTORE_NAME;

public class DataStoreConfigurationJdbcSourceTest
    extends TestSupport
{
  private static final String JDBC_URL_PROPERTY = "nexus.datastore.nexus.jdbcUrl";

  private static final String USERNAME_PROPERTY = "nexus.datastore.nexus.username";

  private static final String PASSWORD_PROPERTY = "nexus.datastore.nexus.password";

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @Mock
  private ApplicationDirectories directories;

  private File workDirectory;

  private DataStoreConfigurationJdbcSource underTest;

  @Before
  public void setUp() throws Exception {
    System.clearProperty(JDBC_URL_PROPERTY);
    System.clearProperty(USERNAME_PROPERTY);
    System.clearProperty(PASSWORD_PROPERTY);
    workDirectory = temporaryFolder.newFolder();
    when(directories.getWorkDirectory("etc/fabric", false)).thenReturn(new File(workDirectory, "etc/fabric"));
    underTest = new DataStoreConfigurationJdbcSource(directories);
  }

  @Test
  public void disabledWhenNoExternalUrlIsConfigured() {
    assertFalse(underTest.isEnabled());
    assertThat(underTest.browseStoreNames().iterator().hasNext(), equalTo(false));
  }

  @Test
  public void systemPropertiesWinOverThePropertiesFile() throws Exception {
    writeStoreProperties("jdbc:postgresql://file/nexus", "file-user", "file-pass");
    System.setProperty(JDBC_URL_PROPERTY, "jdbc:postgresql://property/nexus");
    System.setProperty(USERNAME_PROPERTY, "property-user");
    System.setProperty(PASSWORD_PROPERTY, "property-pass");

    DataStoreConfiguration configuration = underTest.load(DEFAULT_DATASTORE_NAME);

    assertTrue(underTest.isEnabled());
    assertThat(configuration.getName(), equalTo(DEFAULT_DATASTORE_NAME));
    assertThat(configuration.getType(), equalTo("jdbc"));
    assertThat(configuration.getSource(), equalTo("jdbc"));
    assertThat(configuration.getAttributes().get("jdbcUrl"), equalTo("jdbc:postgresql://property/nexus"));
    assertThat(configuration.getAttributes().get("username"), equalTo("property-user"));
    assertThat(configuration.getAttributes().get("password"), equalTo("property-pass"));
    assertThat(Lists.newArrayList(underTest.browseStoreNames()), contains(DEFAULT_DATASTORE_NAME));
  }

  @Test
  public void environmentVariablesAreUsedWhenPropertiesAreAbsent() {
    environmentVariables.set("NEXUS_DATASTORE_NEXUS_JDBCURL", "jdbc:postgresql://env/nexus");
    environmentVariables.set("NEXUS_DATASTORE_NEXUS_USERNAME", "env-user");
    environmentVariables.set("NEXUS_DATASTORE_NEXUS_PASSWORD", "env-pass");

    DataStoreConfiguration configuration = underTest.load(DEFAULT_DATASTORE_NAME);

    assertThat(configuration.getAttributes().get("jdbcUrl"), equalTo("jdbc:postgresql://env/nexus"));
    assertThat(configuration.getAttributes().get("username"), equalTo("env-user"));
    assertThat(configuration.getAttributes().get("password"), equalTo("env-pass"));
  }

  @Test
  public void propertiesFileSuppliesTheUrlWhenNothingElseIsSet() throws Exception {
    writeStoreProperties("jdbc:postgresql://file/nexus", "file-user", null);

    DataStoreConfiguration configuration = underTest.load(DEFAULT_DATASTORE_NAME);

    assertThat(configuration.getAttributes().get("jdbcUrl"), equalTo("jdbc:postgresql://file/nexus"));
    assertThat(configuration.getAttributes().get("username"), equalTo("file-user"));
    assertThat(configuration.getAttributes().get("password"), nullValue());
  }

  private void writeStoreProperties(final String jdbcUrl, final String username, final String password) throws Exception {
    File fabric = new File(workDirectory, "etc/fabric");
    assertTrue(fabric.mkdirs());
    Properties properties = new Properties();
    properties.setProperty("jdbcUrl", jdbcUrl);
    if (username != null) {
      properties.setProperty("username", username);
    }
    if (password != null) {
      properties.setProperty("password", password);
    }
    try (FileOutputStream output = new FileOutputStream(new File(fabric, "nexus-store.properties"))) {
      properties.store(output, null);
    }
  }
}
