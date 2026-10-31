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
 * Escaping helpers for values interpolated into LDAP search filters and distinguished names.
 *
 * Without escaping, a user id such as {@code *)(uid=*} turns a filter like {@code (uid={0})} into one that matches
 * every entry in the directory, which is the classic LDAP injection bypass. Every value that originates outside of
 * this plugin must pass through here before it reaches the directory.
 */
public final class LdapFilters
{
  private LdapFilters() {
    // static utility
  }

  /**
   * Escapes a value for use inside a search filter assertion, per RFC 4515 section 3.
   */
  public static String escapeFilterValue(final String value) {
    if (value == null) {
      return null;
    }

    StringBuilder buf = new StringBuilder(value.length() + 16);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '*':
          buf.append("\\2a");
          break;
        case '(':
          buf.append("\\28");
          break;
        case ')':
          buf.append("\\29");
          break;
        case '\\':
          buf.append("\\5c");
          break;
        case '\0':
          buf.append("\\00");
          break;
        case '/':
          // not required by RFC 4515, but some directories treat it specially in filters
          buf.append("\\2f");
          break;
        default:
          buf.append(c);
          break;
      }
    }
    return buf.toString();
  }

  /**
   * Escapes a value for use inside a distinguished name component, per RFC 4514 section 2.4.
   */
  public static String escapeDnValue(final String value) {
    if (value == null) {
      return null;
    }

    StringBuilder buf = new StringBuilder(value.length() + 16);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean leadingOrTrailingSpace = c == ' ' && (i == 0 || i == value.length() - 1);
      if (leadingOrTrailingSpace || c == '#' && i == 0) {
        buf.append('\\').append(c);
      }
      else {
        switch (c) {
          case ',':
          case '+':
          case '"':
          case '\\':
          case '<':
          case '>':
          case ';':
            buf.append('\\').append(c);
            break;
          case '\0':
            buf.append("\\00");
            break;
          default:
            buf.append(c);
            break;
        }
      }
    }
    return buf.toString();
  }

  /**
   * Substitutes an escaped {@code username} into every {@code {0}} placeholder of a filter or DN template.
   *
   * @param template the configured filter or DN pattern, for example {@code (sAMAccountName={0})}
   * @param username the raw, untrusted login name
   * @param dnContext {@code true} when the template is a DN pattern, {@code false} for a search filter
   */
  public static String interpolate(final String template, final String username, final boolean dnContext) {
    if (template == null) {
      return null;
    }
    String escaped = dnContext ? escapeDnValue(username) : escapeFilterValue(username);
    return template.replace("{0}", escaped == null ? "" : escaped);
  }
}
