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
package org.sonatype.nexus.coelacanth.usertoken;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Runtime state of the user token feature, written by the capability or the administration REST endpoint.
 *
 * {@link #isProtectContent()} is the "require user tokens for repository access" switch: when set, a plain password
 * is refused for repository traffic and only a token will do. It is surfaced here so the filter that enforces it has
 * one place to read.
 */
@Named
@Singleton
public class UserTokenSettings
{
  /**
   * Expiry applied when the administrator enables expiration without naming a number of days.
   */
  public static final int DEFAULT_EXPIRATION_DAYS = 30;

  private final AtomicBoolean enabled = new AtomicBoolean();

  private final AtomicBoolean protectContent = new AtomicBoolean();

  private final AtomicBoolean expirationEnabled = new AtomicBoolean();

  private final AtomicInteger expirationDays = new AtomicInteger(DEFAULT_EXPIRATION_DAYS);

  public boolean isEnabled() {
    return enabled.get();
  }

  public void setEnabled(final boolean value) {
    enabled.set(value);
  }

  public boolean isProtectContent() {
    return protectContent.get();
  }

  public void setProtectContent(final boolean value) {
    protectContent.set(value);
  }

  public boolean isExpirationEnabled() {
    return expirationEnabled.get();
  }

  public void setExpirationEnabled(final boolean value) {
    expirationEnabled.set(value);
  }

  public int getExpirationDays() {
    return expirationDays.get();
  }

  /**
   * Clamps to the 1-999 range the administration form advertises, falling back to the default when unset.
   */
  public void setExpirationDays(final Integer value) {
    if (value == null) {
      expirationDays.set(DEFAULT_EXPIRATION_DAYS);
      return;
    }
    expirationDays.set(Math.min(999, Math.max(1, value)));
  }
}
