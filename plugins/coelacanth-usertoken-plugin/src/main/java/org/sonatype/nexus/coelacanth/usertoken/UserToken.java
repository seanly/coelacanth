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

import java.time.OffsetDateTime;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

/**
 * A user's token: the name part is their Nexus user id, the pass part is the generated secret.
 *
 * The pair is designed to be dropped into any client that speaks HTTP basic authentication, so a build tool never
 * has to hold the user's real password. Unlike a password it can be revoked on its own, and it carries no value
 * outside this server even when the account is backed by LDAP or an identity provider.
 */
public class UserToken
{
  private final String nameCode;

  private final String passCode;

  private final OffsetDateTime created;

  public UserToken(final String nameCode, final String passCode, @Nullable final OffsetDateTime created) {
    this.nameCode = nameCode;
    this.passCode = passCode;
    this.created = created;
  }

  /**
   * The basic-auth user name, which is the owner's Nexus user id.
   */
  public String getNameCode() {
    return nameCode;
  }

  /**
   * The basic-auth password. This is the secret and is only ever shown to its owner, once per request.
   */
  public String getPassCode() {
    return passCode;
  }

  @Nullable
  public OffsetDateTime getCreated() {
    return created;
  }

  /**
   * Renders the pair the way a client would send it, which is what the user pastes into a tool that takes a single
   * credential string.
   */
  public String encodeBasicAuth() {
    String pair = nameCode + ':' + passCode;
    return Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String toString() {
    // never render the pass code
    return "UserToken{nameCode=" + nameCode + ", created=" + created + '}';
  }
}
