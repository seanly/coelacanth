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

import javax.annotation.Nullable;

/**
 * Immutable snapshot of the LDAP connection, search and group-mapping settings.
 *
 * Instances are built by {@link org.sonatype.nexus.coelacanth.ldap.internal.capability.LdapCapability} from the
 * capability properties and published to {@link LdapConfigurationHolder}.
 */
public class LdapConfiguration
{
  private final String ldapUrl;

  private final String searchBase;

  private final boolean searchSubtree;

  private final String userDnPattern;

  private final String managerDn;

  private final String managerPassword;

  private final String userSearchFilter;

  private final String userIdAttribute;

  private final String emailAttribute;

  private final String realNameAttribute;

  private final GroupStrategyType groupStrategy;

  private final String groupSearchBase;

  private final String groupObjectClass;

  private final String groupIdAttribute;

  private final String groupMemberAttribute;

  private final String groupMemberFormat;

  private final String userMemberOfAttribute;

  private final int connectTimeoutMillis;

  private final int readTimeoutMillis;

  private final boolean usePagedResults;

  private final int pageSize;

  private final int maxSearchResults;

  private final String referralStrategy;

  public LdapConfiguration(final Builder builder) {
    this.ldapUrl = builder.ldapUrl;
    this.searchBase = builder.searchBase;
    this.searchSubtree = builder.searchSubtree;
    this.userDnPattern = builder.userDnPattern;
    this.managerDn = builder.managerDn;
    this.managerPassword = builder.managerPassword;
    this.userSearchFilter = builder.userSearchFilter;
    this.userIdAttribute = builder.userIdAttribute;
    this.emailAttribute = builder.emailAttribute;
    this.realNameAttribute = builder.realNameAttribute;
    this.groupStrategy = builder.groupStrategy;
    this.groupSearchBase = builder.groupSearchBase;
    this.groupObjectClass = builder.groupObjectClass;
    this.groupIdAttribute = builder.groupIdAttribute;
    this.groupMemberAttribute = builder.groupMemberAttribute;
    this.groupMemberFormat = builder.groupMemberFormat;
    this.userMemberOfAttribute = builder.userMemberOfAttribute;
    this.connectTimeoutMillis = builder.connectTimeoutMillis;
    this.readTimeoutMillis = builder.readTimeoutMillis;
    this.usePagedResults = builder.usePagedResults;
    this.pageSize = builder.pageSize;
    this.maxSearchResults = builder.maxSearchResults;
    this.referralStrategy = builder.referralStrategy;
  }
  public String getLdapUrl() {
    return ldapUrl;
  }

  @Nullable
  public String getSearchBase() {
    return searchBase;
  }

  public boolean isSearchSubtree() {
    return searchSubtree;
  }

  @Nullable
  public String getUserDnPattern() {
    return userDnPattern;
  }

  @Nullable
  public String getManagerDn() {
    return managerDn;
  }

  @Nullable
  public String getManagerPassword() {
    return managerPassword;
  }

  @Nullable
  public String getUserSearchFilter() {
    return userSearchFilter;
  }

  public String getUserIdAttribute() {
    return userIdAttribute;
  }

  @Nullable
  public String getEmailAttribute() {
    return emailAttribute;
  }

  @Nullable
  public String getRealNameAttribute() {
    return realNameAttribute;
  }

  public GroupStrategyType getGroupStrategy() {
    return groupStrategy;
  }

  @Nullable
  public String getGroupSearchBase() {
    return groupSearchBase;
  }

  @Nullable
  public String getGroupObjectClass() {
    return groupObjectClass;
  }

  @Nullable
  public String getGroupIdAttribute() {
    return groupIdAttribute;
  }

  @Nullable
  public String getGroupMemberAttribute() {
    return groupMemberAttribute;
  }

  /**
   * How a user is referenced from a group's member attribute: {@code ${dn}} or {@code ${username}}.
   */
  @Nullable
  public String getGroupMemberFormat() {
    return groupMemberFormat;
  }

  @Nullable
  public String getUserMemberOfAttribute() {
    return userMemberOfAttribute;
  }

  public int getConnectTimeoutMillis() {
    return connectTimeoutMillis;
  }

  public int getReadTimeoutMillis() {
    return readTimeoutMillis;
  }

  public boolean isUsePagedResults() {
    return usePagedResults;
  }

  public int getPageSize() {
    return pageSize;
  }

  public int getMaxSearchResults() {
    return maxSearchResults;
  }

  public String getReferralStrategy() {
    return referralStrategy;
  }

  /**
   * {@code true} when the plugin should bind as the manager account and search for the user, {@code false} when it
   * should build the user DN directly from {@link #getUserDnPattern()}.
   */
  public boolean isSearchBind() {
    return userDnPattern == null || userDnPattern.isBlank();
  }

  /**
   * {@code true} when the directory is reached over TLS.
   */
  public boolean isSecure() {
    return ldapUrl != null && ldapUrl.toLowerCase().startsWith("ldaps://");
  }

  @Override
  public String toString() {
    // deliberately omits managerPassword
    return "LdapConfiguration{ldapUrl=" + ldapUrl
        + ", searchBase=" + searchBase
        + ", searchBind=" + isSearchBind()
        + ", groupStrategy=" + groupStrategy
        + '}';
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Collects the settings and applies the defaults that the capability form leaves blank.
   */
  public static class Builder
  {
    private String ldapUrl;

    private String searchBase;

    private boolean searchSubtree = true;

    private String userDnPattern;

    private String managerDn;

    private String managerPassword;

    private String userSearchFilter;

    private String userIdAttribute = "uid";

    private String emailAttribute = "mail";

    private String realNameAttribute = "cn";

    private GroupStrategyType groupStrategy = GroupStrategyType.NONE;

    private String groupSearchBase;

    private String groupObjectClass = "groupOfNames";

    private String groupIdAttribute = "cn";

    private String groupMemberAttribute = "member";

    private String groupMemberFormat = "${dn}";

    private String userMemberOfAttribute = "memberOf";

    private int connectTimeoutMillis = 10_000;

    private int readTimeoutMillis = 15_000;

    private boolean usePagedResults;

    private int pageSize = 500;

    private int maxSearchResults = 300;

    private String referralStrategy = "follow";

    public Builder ldapUrl(final String value) {
      this.ldapUrl = value;
      return this;
    }

    public Builder searchBase(final String value) {
      this.searchBase = value;
      return this;
    }

    public Builder searchSubtree(final boolean value) {
      this.searchSubtree = value;
      return this;
    }

    public Builder userDnPattern(final String value) {
      this.userDnPattern = value;
      return this;
    }

    public Builder managerDn(final String value) {
      this.managerDn = value;
      return this;
    }

    public Builder managerPassword(final String value) {
      this.managerPassword = value;
      return this;
    }

    public Builder userSearchFilter(final String value) {
      this.userSearchFilter = value;
      return this;
    }

    public Builder userIdAttribute(final String value) {
      this.userIdAttribute = defaulted(value, userIdAttribute);
      return this;
    }

    public Builder emailAttribute(final String value) {
      this.emailAttribute = value;
      return this;
    }

    public Builder realNameAttribute(final String value) {
      this.realNameAttribute = value;
      return this;
    }

    public Builder groupStrategy(final GroupStrategyType value) {
      this.groupStrategy = value == null ? GroupStrategyType.NONE : value;
      return this;
    }

    public Builder groupSearchBase(final String value) {
      this.groupSearchBase = value;
      return this;
    }

    public Builder groupObjectClass(final String value) {
      this.groupObjectClass = defaulted(value, groupObjectClass);
      return this;
    }

    public Builder groupIdAttribute(final String value) {
      this.groupIdAttribute = defaulted(value, groupIdAttribute);
      return this;
    }

    public Builder groupMemberAttribute(final String value) {
      this.groupMemberAttribute = defaulted(value, groupMemberAttribute);
      return this;
    }

    public Builder groupMemberFormat(final String value) {
      this.groupMemberFormat = defaulted(value, groupMemberFormat);
      return this;
    }

    public Builder userMemberOfAttribute(final String value) {
      this.userMemberOfAttribute = defaulted(value, userMemberOfAttribute);
      return this;
    }

    public Builder connectTimeoutMillis(final Integer value) {
      this.connectTimeoutMillis = value == null || value <= 0 ? connectTimeoutMillis : value;
      return this;
    }

    public Builder readTimeoutMillis(final Integer value) {
      this.readTimeoutMillis = value == null || value <= 0 ? readTimeoutMillis : value;
      return this;
    }

    public Builder usePagedResults(final boolean value) {
      this.usePagedResults = value;
      return this;
    }

    public Builder pageSize(final Integer value) {
      this.pageSize = value == null || value <= 0 ? pageSize : value;
      return this;
    }

    public Builder maxSearchResults(final Integer value) {
      this.maxSearchResults = value == null || value <= 0 ? maxSearchResults : value;
      return this;
    }

    public Builder referralStrategy(final String value) {
      this.referralStrategy = defaulted(value, referralStrategy);
      return this;
    }

    public LdapConfiguration build() {
      return new LdapConfiguration(this);
    }

    private static String defaulted(final String value, final String fallback) {
      return value == null || value.isBlank() ? fallback : value;
    }
  }
}
