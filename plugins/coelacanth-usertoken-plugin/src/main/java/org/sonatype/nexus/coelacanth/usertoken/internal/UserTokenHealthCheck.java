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
package org.sonatype.nexus.coelacanth.usertoken.internal;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.coelacanth.usertoken.UserTokenRealm;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenService;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenSettings;
import org.sonatype.nexus.security.realm.RealmManager;

import com.codahale.metrics.health.HealthCheck;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Reports whether the feature is coherently configured.
 *
 * The case worth catching is {@code protectContent} with the realm disabled: repository access would then demand a
 * token that nothing can validate, locking every build out.
 */
@Named("UserToken")
@Singleton
public class UserTokenHealthCheck
    extends HealthCheck
{
  private final RealmManager realmManager;

  private final UserTokenSettings settings;

  private final UserTokenService userTokenService;

  @Inject
  public UserTokenHealthCheck(
      final RealmManager realmManager,
      final UserTokenSettings settings,
      final UserTokenService userTokenService)
  {
    this.realmManager = checkNotNull(realmManager);
    this.settings = checkNotNull(settings);
    this.userTokenService = checkNotNull(userTokenService);
  }

  @Override
  protected Result check() {
    boolean realmEnabled = realmManager.isRealmEnabled(UserTokenRealm.NAME);

    if (!settings.isEnabled()) {
      if (realmEnabled) {
        return Result.unhealthy("User token realm is enabled but the capability is not active;"
            + " issued tokens will be accepted while nobody can create or reset one.");
      }
      return Result.healthy("User tokens not in use.");
    }

    if (!realmEnabled) {
      return Result.unhealthy("User tokens are enabled but the %s realm is not in the realm list;"
          + " tokens will be rejected.", UserTokenRealm.NAME);
    }

    if (settings.isProtectContent()) {
      return Result.healthy("User tokens required for repository access; %d issued.", userTokenService.count());
    }

    return Result.healthy("User tokens enabled; %d issued.", userTokenService.count());
  }
}
