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
package org.sonatype.nexus.coelacanth.ldap.internal.capability;

import java.util.Map;

import org.sonatype.nexus.capability.CapabilityConfigurationSupport;
import org.sonatype.nexus.coelacanth.ldap.GroupStrategyType;
import org.sonatype.nexus.coelacanth.ldap.LdapConfiguration;

/**
 * Translates the capability's raw property map into an {@link LdapConfiguration}.
 *
 * The {@code managerPassword} property is declared with a {@code PasswordFormField}, which implements
 * {@code Encrypted}, so the capability framework stores it encrypted and hands it back in clear here.
 */
public class LdapCapabilityConfiguration
    extends CapabilityConfigurationSupport
{
  public static final String P_LDAP_URL = "ldapUrl";

  public static final String P_SEARCH_BASE = "searchBase";

  public static final String P_SEARCH_SUBTREE = "searchSubtree";

  public static final String P_USER_DN_PATTERN = "userDnPattern";

  public static final String P_MANAGER_DN = "managerDn";

  public static final String P_MANAGER_PASSWORD = "managerPassword";

  public static final String P_USER_SEARCH_FILTER = "userSearchFilter";

  public static final String P_USER_ID_ATTRIBUTE = "userIdAttribute";

  public static final String P_EMAIL_ATTRIBUTE = "emailAttribute";

  public static final String P_REAL_NAME_ATTRIBUTE = "realNameAttribute";

  public static final String P_GROUP_STRATEGY = "groupStrategy";

  public static final String P_GROUP_SEARCH_BASE = "groupSearchBase";

  public static final String P_GROUP_OBJECT_CLASS = "groupObjectClass";

  public static final String P_GROUP_ID_ATTRIBUTE = "groupIdAttribute";

  public static final String P_GROUP_MEMBER_ATTRIBUTE = "groupMemberAttribute";

  public static final String P_GROUP_MEMBER_FORMAT = "groupMemberFormat";

  public static final String P_USER_MEMBER_OF_ATTRIBUTE = "userMemberOfAttribute";

  public static final String P_CONNECT_TIMEOUT = "connectTimeout";

  public static final String P_READ_TIMEOUT = "readTimeout";

  public static final String P_USE_PAGED_RESULTS = "usePagedResults";

  public static final String P_PAGE_SIZE = "pageSize";

  public static final String P_MAX_SEARCH_RESULTS = "maxSearchResults";

  public static final String P_REFERRAL_STRATEGY = "referralStrategy";

  private final LdapConfiguration configuration;

  public LdapCapabilityConfiguration(final Map<String, String> properties) {
    this.configuration = LdapConfiguration.builder()
        .ldapUrl(trimmed(properties, P_LDAP_URL))
        .searchBase(trimmed(properties, P_SEARCH_BASE))
        .searchSubtree(bool(properties, P_SEARCH_SUBTREE, true))
        .userDnPattern(trimmed(properties, P_USER_DN_PATTERN))
        .managerDn(trimmed(properties, P_MANAGER_DN))
        .managerPassword(properties.get(P_MANAGER_PASSWORD))
        .userSearchFilter(trimmed(properties, P_USER_SEARCH_FILTER))
        .userIdAttribute(trimmed(properties, P_USER_ID_ATTRIBUTE))
        .emailAttribute(trimmed(properties, P_EMAIL_ATTRIBUTE))
        .realNameAttribute(trimmed(properties, P_REAL_NAME_ATTRIBUTE))
        .groupStrategy(GroupStrategyType.parse(properties.get(P_GROUP_STRATEGY)))
        .groupSearchBase(trimmed(properties, P_GROUP_SEARCH_BASE))
        .groupObjectClass(trimmed(properties, P_GROUP_OBJECT_CLASS))
        .groupIdAttribute(trimmed(properties, P_GROUP_ID_ATTRIBUTE))
        .groupMemberAttribute(trimmed(properties, P_GROUP_MEMBER_ATTRIBUTE))
        .groupMemberFormat(trimmed(properties, P_GROUP_MEMBER_FORMAT))
        .userMemberOfAttribute(trimmed(properties, P_USER_MEMBER_OF_ATTRIBUTE))
        .connectTimeoutMillis(integer(properties, P_CONNECT_TIMEOUT))
        .readTimeoutMillis(integer(properties, P_READ_TIMEOUT))
        .usePagedResults(bool(properties, P_USE_PAGED_RESULTS, false))
        .pageSize(integer(properties, P_PAGE_SIZE))
        .maxSearchResults(integer(properties, P_MAX_SEARCH_RESULTS))
        .referralStrategy(trimmed(properties, P_REFERRAL_STRATEGY))
        .build();
  }

  public LdapConfiguration getConfiguration() {
    return configuration;
  }

  private static String trimmed(final Map<String, String> properties, final String key) {
    String value = properties.get(key);
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static boolean bool(final Map<String, String> properties, final String key, final boolean fallback) {
    String value = trimmed(properties, key);
    return value == null ? fallback : Boolean.parseBoolean(value);
  }

  private static Integer integer(final Map<String, String> properties, final String key) {
    String value = trimmed(properties, key);
    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(value);
    }
    catch (NumberFormatException e) {
      // the form field validates this; fall back to the builder default rather than failing activation
      return null;
    }
  }
}
