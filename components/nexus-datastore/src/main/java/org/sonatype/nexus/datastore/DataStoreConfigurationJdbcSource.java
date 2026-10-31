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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.app.ApplicationDirectories;
import org.sonatype.nexus.datastore.api.DataStoreConfiguration;

import com.google.common.collect.ImmutableSet;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.datastore.api.DataStoreManager.DEFAULT_DATASTORE_NAME;

/**
 * Supplies the default datastore from an external JDBC URL when one is configured.
 * System properties win, then environment variables, then {@code etc/fabric/nexus-store.properties}
 * under the work directory. When no URL is set this source stays disabled and the embedded H2 default is used.
 */
@Named(DataStoreConfigurationJdbcSource.JDBC)
@Priority(0)
@Singleton
public class DataStoreConfigurationJdbcSource
    extends ComponentSupport
    implements DataStoreConfigurationSource
{
  static final String JDBC = "jdbc";

  private static final String JDBC_URL = "jdbcUrl";

  private static final String USERNAME = "username";

  private static final String PASSWORD = "password";

  private static final String STORE_PROPERTIES = "etc/fabric/nexus-store.properties";

  private static final String JDBC_URL_PROPERTY = "nexus.datastore.nexus.jdbcUrl";

  private static final String USERNAME_PROPERTY = "nexus.datastore.nexus.username";

  private static final String PASSWORD_PROPERTY = "nexus.datastore.nexus.password";

  private static final String JDBC_URL_ENV = "NEXUS_DATASTORE_NEXUS_JDBCURL";

  private static final String USERNAME_ENV = "NEXUS_DATASTORE_NEXUS_USERNAME";

  private static final String PASSWORD_ENV = "NEXUS_DATASTORE_NEXUS_PASSWORD";

  private final ApplicationDirectories directories;

  @Inject
  public DataStoreConfigurationJdbcSource(final ApplicationDirectories directories) {
    this.directories = checkNotNull(directories);
  }

  @Override
  public String getName() {
    return "JDBC";
  }

  @Override
  public boolean isEnabled() {
    return jdbcUrl() != null;
  }

  @Override
  public Iterable<String> browseStoreNames() {
    if (!isEnabled()) {
      return Collections.emptySet();
    }
    return ImmutableSet.of(DEFAULT_DATASTORE_NAME);
  }

  @Override
  public DataStoreConfiguration load(final String storeName) {
    checkArgument(DEFAULT_DATASTORE_NAME.equalsIgnoreCase(storeName),
        "%s is not valid, %s is the only valid data store name", storeName, DEFAULT_DATASTORE_NAME);
    String jdbcUrl = jdbcUrl();
    checkArgument(jdbcUrl != null, "No external JDBC URL is configured");

    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(JDBC_URL, jdbcUrl);
    putIfPresent(attributes, USERNAME, setting(USERNAME_PROPERTY, USERNAME_ENV, USERNAME));
    putIfPresent(attributes, PASSWORD, setting(PASSWORD_PROPERTY, PASSWORD_ENV, PASSWORD));

    DataStoreConfiguration configuration = new DataStoreConfiguration();
    configuration.setName(DEFAULT_DATASTORE_NAME);
    configuration.setType(JDBC);
    configuration.setSource(JDBC);
    configuration.setAttributes(attributes);

    log.info("Loaded '{}' data store configuration from external JDBC settings", storeName);
    return configuration;
  }

  private String jdbcUrl() {
    return setting(JDBC_URL_PROPERTY, JDBC_URL_ENV, JDBC_URL);
  }

  private String setting(final String propertyName, final String environmentName, final String fileKey) {
    String value = firstNonBlank(System.getProperty(propertyName), System.getenv(environmentName));
    if (value != null) {
      return value;
    }
    String fromFile = fileProperties().getProperty(fileKey);
    if (fromFile != null && !fromFile.trim().isEmpty()) {
      return fromFile.trim();
    }
    return null;
  }

  private Properties fileProperties() {
    Path path = directories.getWorkDirectory("etc/fabric", false).toPath().resolve("nexus-store.properties");
    if (!Files.isRegularFile(path)) {
      return new Properties();
    }
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(path)) {
      properties.load(input);
    }
    catch (IOException e) {
      log.warn("Unable to read {}", STORE_PROPERTIES, e);
    }
    return properties;
  }

  private static void putIfPresent(final Map<String, String> attributes, final String key, final String value) {
    if (value != null && !value.trim().isEmpty()) {
      attributes.put(key, value.trim());
    }
  }

  private static String firstNonBlank(final String first, final String second) {
    if (first != null && !first.trim().isEmpty()) {
      return first.trim();
    }
    if (second != null && !second.trim().isEmpty()) {
      return second.trim();
    }
    return null;
  }
}
