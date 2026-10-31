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
package org.sonatype.nexus.coelacanth.usertoken.internal.capability;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.goodies.i18n.I18N;
import org.sonatype.goodies.i18n.MessageBundle;
import org.sonatype.nexus.capability.CapabilitySupport;
import org.sonatype.nexus.capability.Condition;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenRealm;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenSettings;
import org.sonatype.nexus.security.realm.RealmManager;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Turns the user token feature on and registers {@link UserTokenRealm}.
 *
 * The realm is inserted at the front of the realm list: a token lookup is a local store read and returns null for
 * anything that is not a token, so trying it first costs almost nothing and avoids sending the token to a remote
 * password realm that would reject it.
 */
@Named(UserTokenCapabilityDescriptor.TYPE_ID)
public class UserTokenCapability
    extends CapabilitySupport<UserTokenCapabilityConfiguration>
{
  private interface Messages
      extends MessageBundle
  {
    @DefaultMessage("Enabled")
    String enabled();

    @DefaultMessage("Enabled, required for repository access")
    String enabledAndRequired();
  }

  private static final Messages messages = I18N.create(Messages.class);

  private final RealmManager realmManager;

  private final UserTokenSettings settings;

  @Inject
  public UserTokenCapability(final RealmManager realmManager, final UserTokenSettings settings) {
    this.realmManager = checkNotNull(realmManager);
    this.settings = checkNotNull(settings);
  }

  @Override
  protected UserTokenCapabilityConfiguration createConfig(final Map<String, String> properties) {
    return new UserTokenCapabilityConfiguration(properties);
  }

  @Override
  protected String renderDescription() {
    return settings.isProtectContent() ? messages.enabledAndRequired() : messages.enabled();
  }

  @Override
  public Condition activationCondition() {
    return conditions().capabilities().passivateCapabilityDuringUpdate();
  }

  @Override
  protected void onActivate(final UserTokenCapabilityConfiguration config) {
    settings.setEnabled(true);
    settings.setProtectContent(config.isProtectContent());
    settings.setExpirationEnabled(config.isExpirationEnabled());
    settings.setExpirationDays(config.getExpirationDays());

    realmManager.enableRealm(UserTokenRealm.NAME, 0);
    log.info("User token realm enabled (protectContent={}, expiration={}/{}d)", config.isProtectContent(),
        config.isExpirationEnabled(), settings.getExpirationDays());
  }

  @Override
  protected void onPassivate(final UserTokenCapabilityConfiguration config) {
    settings.setEnabled(false);
    settings.setProtectContent(false);
    settings.setExpirationEnabled(false);

    if (isShuttingDown()) {
      log.info("Skipping user token realm disable during shutdown");
      return;
    }

    try {
      realmManager.disableRealm(UserTokenRealm.NAME);
      log.info("User token realm disabled");
    }
    catch (Exception e) {
      log.warn("Failed to disable user token realm", e);
    }
  }

  private boolean isShuttingDown() {
    return Thread.currentThread().getName().contains("FelixStartLevel");
  }
}
