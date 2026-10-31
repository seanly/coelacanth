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
package org.sonatype.nexus.coelacanth.ldap;

/**
 * How group membership is discovered in the directory.
 *
 * Directories expose membership in incompatible ways, so the strategy has to be chosen per deployment rather than
 * guessed. See {@link GroupStrategy} and its implementations in
 * {@code org.sonatype.nexus.coelacanth.ldap.internal.group}.
 */
public enum GroupStrategyType
{
  /**
   * No group mapping. Users authenticate but receive only the roles mapped to them individually in Nexus.
   */
  NONE,

  /**
   * The group entry lists its members. Membership is read from the group's member attribute
   * ({@code member} / {@code uniqueMember}), whose values are user DNs or user ids.
   */
  STATIC,

  /**
   * The user entry lists its groups. Membership is read from an attribute on the user, typically {@code memberOf}.
   *
   * Note that {@code memberOf} is an overlay in OpenLDAP and is not populated by default.
   */
  DYNAMIC,

  /**
   * Membership is implied by the user's position in the DN tree, so {@code uid=jane,ou=dev,ou=uk,dc=example,dc=com}
   * yields the groups {@code dev} and {@code uk}. No membership attribute is read.
   */
  HIERARCHY;

  public static GroupStrategyType parse(final String value) {
    if (value == null || value.isBlank()) {
      return NONE;
    }
    try {
      return valueOf(value.trim().toUpperCase());
    }
    catch (IllegalArgumentException e) {
      return NONE;
    }
  }
}
