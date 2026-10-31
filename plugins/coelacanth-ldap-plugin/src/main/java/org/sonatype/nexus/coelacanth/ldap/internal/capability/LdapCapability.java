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
package org.sonatype.nexus.coelacanth.ldap.internal.capability;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.goodies.i18n.I18N;
import org.sonatype.goodies.i18n.MessageBundle;
import org.sonatype.nexus.capability.CapabilitySupport;
import org.sonatype.nexus.capability.Condition;
import org.sonatype.nexus.coelacanth.ldap.LdapRealm;
import org.sonatype.nexus.coelacanth.ldap.LdapConfigurationHolder;
import org.sonatype.nexus.security.realm.RealmManager;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Applies the LDAP settings and enables the realm while the capability is active.
 *
 * Activation publishes the configuration to {@link LdapConfigurationHolder} and adds {@link LdapRealm} to the realm
 * list; passivation reverses both. Keeping the realm registration tied to the capability means an operator never
 * ends up with an enabled realm that has nothing configured behind it.
 */
@Named(LdapCapabilityDescriptor.TYPE_ID)
public class LdapCapability
    extends CapabilitySupport<LdapCapabilityConfiguration>
{
  private interface Messages
      extends MessageBundle
  {
    @DefaultMessage("%s")
    String description(String ldapUrl);

    @DefaultMessage("Not configured")
    String notConfigured();
  }

  private static final Messages messages = I18N.create(Messages.class);

  private final RealmManager realmManager;

  private final LdapConfigurationHolder configurationHolder;

  private final LdapRealm ldapRealm;

  @Inject
  public LdapCapability(
      final RealmManager realmManager,
      final LdapConfigurationHolder configurationHolder,
      final LdapRealm ldapRealm)
  {
    this.realmManager = checkNotNull(realmManager);
    this.configurationHolder = checkNotNull(configurationHolder);
    this.ldapRealm = checkNotNull(ldapRealm);
  }

  @Override
  protected LdapCapabilityConfiguration createConfig(final Map<String, String> properties) {
    return new LdapCapabilityConfiguration(properties);
  }

  @Override
  protected String renderDescription() {
    return configurationHolder.get()
        .map(config -> messages.description(config.getLdapUrl()))
        .orElseGet(messages::notConfigured);
  }

  @Override
  public Condition activationCondition() {
    return conditions().capabilities().passivateCapabilityDuringUpdate();
  }

  @Override
  protected void onActivate(final LdapCapabilityConfiguration config) {
    configurationHolder.set(config.getConfiguration());

    // Stale authorization entries would otherwise survive a settings change
    ldapRealm.clearCaches();

    realmManager.enableRealm(LdapRealm.NAME);
    log.info("LDAP realm enabled against {}", config.getConfiguration().getLdapUrl());
  }

  @Override
  protected void onPassivate(final LdapCapabilityConfiguration config) {
    if (isShuttingDown()) {
      log.info("Skipping LDAP realm disable during shutdown");
      return;
    }

    disableRealmQuietly();
    configurationHolder.clear();
    ldapRealm.clearCaches();
  }

  private void disableRealmQuietly() {
    try {
      realmManager.disableRealm(LdapRealm.NAME);
      log.info("LDAP realm disabled");
    }
    catch (Exception e) {
      log.warn("Failed to disable LDAP realm", e);
    }
  }

  /**
   * Mirrors the check used by the default-role capability: during OSGi shutdown the realm configuration store may
   * already be gone, and touching it logs noisy failures.
   */
  private boolean isShuttingDown() {
    return Thread.currentThread().getName().contains("FelixStartLevel");
  }
}
