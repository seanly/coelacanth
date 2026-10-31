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

import javax.annotation.Nullable;

import org.sonatype.nexus.capability.CapabilityConfigurationSupport;

/**
 * Configuration for {@link UserTokenCapability}.
 */
public class UserTokenCapabilityConfiguration
    extends CapabilityConfigurationSupport
{
  public static final String P_PROTECT_CONTENT = "protectContent";

  public static final String P_EXPIRATION_ENABLED = "expirationEnabled";

  public static final String P_EXPIRATION_DAYS = "expirationDays";

  private final boolean protectContent;

  private final boolean expirationEnabled;

  private final Integer expirationDays;

  public UserTokenCapabilityConfiguration(final Map<String, String> properties) {
    this.protectContent = Boolean.parseBoolean(properties.get(P_PROTECT_CONTENT));
    this.expirationEnabled = Boolean.parseBoolean(properties.get(P_EXPIRATION_ENABLED));
    this.expirationDays = parseDays(properties.get(P_EXPIRATION_DAYS));
  }

  /**
   * Whether repository access should require a token rather than accepting a password.
   */
  public boolean isProtectContent() {
    return protectContent;
  }

  public boolean isExpirationEnabled() {
    return expirationEnabled;
  }

  /**
   * @return {@code null} when unset, so the settings default applies
   */
  @Nullable
  public Integer getExpirationDays() {
    return expirationDays;
  }

  private static Integer parseDays(final String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(value.trim());
    }
    catch (NumberFormatException e) {
      // the form field validates this; fall back to the default rather than failing activation
      return null;
    }
  }
}
