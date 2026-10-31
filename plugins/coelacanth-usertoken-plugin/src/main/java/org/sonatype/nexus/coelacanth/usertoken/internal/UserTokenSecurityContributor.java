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

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.security.config.MemorySecurityConfiguration;
import org.sonatype.nexus.security.config.SecurityContributor;
import org.sonatype.nexus.security.config.SecurityContributorSupport;

/**
 * Contributes the privileges the user token screens check.
 *
 * Two domains are needed. {@code usertoken-settings} guards the administration screen and is what
 * {@code Permissions.USER_TOKENS_SETTINGS} in nexus-ui-plugin already refers to. {@code usertoken-current} guards a
 * user's access to their own token and is checked by the user menu entry.
 *
 * The read privilege on {@code usertoken-current} is granted to every authenticated user through the default role
 * rather than being handed out individually, since a user reaching their own token is the normal case.
 */
@Named
@Singleton
public class UserTokenSecurityContributor
    extends SecurityContributorSupport
    implements SecurityContributor
{
  private static final String SETTINGS_DOMAIN = "usertoken-settings";

  private static final String CURRENT_DOMAIN = "usertoken-current";

  @Override
  public MemorySecurityConfiguration getContribution() {
    MemorySecurityConfiguration configuration = new MemorySecurityConfiguration();

    configuration.addPrivilege(createApplicationPrivilege(
        "nx-usertoken-settings-all", ALL_DESCRIPTION_BASE + "User Token Settings", SETTINGS_DOMAIN, ACTION_ALL));
    configuration.addPrivilege(createApplicationPrivilege(
        "nx-usertoken-settings-read", READ_DESCRIPTION_BASE + "User Token Settings", SETTINGS_DOMAIN, ACTION_READ));
    configuration.addPrivilege(createApplicationPrivilege(
        "nx-usertoken-settings-update", UPDATE_DESCRIPTION_BASE + "User Token Settings", SETTINGS_DOMAIN,
        ACTION_UPDATE));

    configuration.addPrivilege(createApplicationPrivilege(
        "nx-usertoken-current-all", ALL_DESCRIPTION_BASE + "Own User Token", CURRENT_DOMAIN, ACTION_ALL));
    configuration.addPrivilege(createApplicationPrivilege(
        "nx-usertoken-current-read", READ_DESCRIPTION_BASE + "Own User Token", CURRENT_DOMAIN, ACTION_READ));

    return configuration;
  }
}
