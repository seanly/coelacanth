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
package org.sonatype.nexus.coelacanth.ldap.internal;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.directory.DirContext;

import org.sonatype.nexus.coelacanth.ldap.LdapConfiguration;
import org.sonatype.nexus.coelacanth.ldap.LdapConfigurationHolder;
import org.sonatype.nexus.coelacanth.ldap.LdapConnector;
import org.sonatype.nexus.coelacanth.ldap.LdapRealm;
import org.sonatype.nexus.security.realm.RealmManager;

import com.codahale.metrics.health.HealthCheck;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Reports whether the configured directory is reachable.
 *
 * Authentication failures caused by an unreachable directory are easy to mistake for bad credentials, so surfacing
 * reachability as a health check gives operators somewhere to look first.
 */
@Named("LDAP")
@Singleton
public class LdapHealthCheck
    extends HealthCheck
{
  private final RealmManager realmManager;

  private final LdapConfigurationHolder configurationHolder;

  private final LdapConnector connector;

  @Inject
  public LdapHealthCheck(
      final RealmManager realmManager,
      final LdapConfigurationHolder configurationHolder,
      final LdapConnector connector)
  {
    this.realmManager = checkNotNull(realmManager);
    this.configurationHolder = checkNotNull(configurationHolder);
    this.connector = checkNotNull(connector);
  }

  @Override
  protected Result check() {
    if (!realmManager.isRealmEnabled(LdapRealm.NAME)) {
      return Result.healthy("LDAP realm not in use.");
    }

    LdapConfiguration config = configurationHolder.get().orElse(null);
    if (config == null) {
      return Result.unhealthy("LDAP realm is enabled but no configuration is active.");
    }

    DirContext context = null;
    try {
      context = connector.openManagerContext(config);
      return Result.healthy("Connected to %s", config.getLdapUrl());
    }
    catch (Exception e) {
      return Result.unhealthy("Cannot reach %s: %s", config.getLdapUrl(), e.getMessage());
    }
    finally {
      connector.close(context);
    }
  }
}
