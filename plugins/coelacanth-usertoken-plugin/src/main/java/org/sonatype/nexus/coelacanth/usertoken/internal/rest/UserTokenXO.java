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
package org.sonatype.nexus.coelacanth.usertoken.internal.rest;

import org.sonatype.nexus.coelacanth.usertoken.UserToken;

/**
 * The token as returned to its owner.
 */
public class UserTokenXO
{
  private String nameCode;

  private String passCode;

  private String basicAuth;

  private String created;

  public UserTokenXO() {
    // for deserialization
  }

  public static UserTokenXO from(final UserToken token) {
    UserTokenXO xo = new UserTokenXO();
    xo.nameCode = token.getNameCode();
    xo.passCode = token.getPassCode();
    xo.basicAuth = token.encodeBasicAuth();
    xo.created = token.getCreated() == null ? null : token.getCreated().toString();
    return xo;
  }

  public String getNameCode() {
    return nameCode;
  }

  public void setNameCode(final String nameCode) {
    this.nameCode = nameCode;
  }

  public String getPassCode() {
    return passCode;
  }

  public void setPassCode(final String passCode) {
    this.passCode = passCode;
  }

  /**
   * The base64 pair, for clients that take a single pre-encoded credential.
   */
  public String getBasicAuth() {
    return basicAuth;
  }

  public void setBasicAuth(final String basicAuth) {
    this.basicAuth = basicAuth;
  }

  public String getCreated() {
    return created;
  }

  public void setCreated(final String created) {
    this.created = created;
  }
}
