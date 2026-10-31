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

/**
 * Thrown when the caller's token exists but has passed its expiry.
 *
 * The message text is matched by the user token screen, which switches its primary button from "Access" to
 * "Generate" when it sees it, so the wording is part of the contract with the front end.
 */
public class UserTokenExpiredException
    extends RuntimeException
{
  private static final long serialVersionUID = 1L;

  /**
   * Text the front end looks for. Must stay in sync with {@code USER_TOKEN.MESSAGES.EXPIRED} in
   * nexus-coreui-plugin's UserTokenStrings.
   */
  public static final String MESSAGE = "User Token expired";

  private final String userId;

  public UserTokenExpiredException(final String userId) {
    super(MESSAGE);
    this.userId = userId;
  }

  public String getUserId() {
    return userId;
  }
}
