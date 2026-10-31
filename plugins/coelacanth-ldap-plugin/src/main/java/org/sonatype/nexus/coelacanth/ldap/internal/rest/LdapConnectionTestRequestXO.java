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
package org.sonatype.nexus.coelacanth.ldap.internal.rest;

import javax.annotation.Nullable;

/**
 * Optional overrides for a connection test.
 *
 * With an empty body the test uses the configuration that is currently active, which is the common case: an
 * operator saves the capability and then checks whether it works. Supplying fields here allows testing a change
 * before committing it.
 */
public class LdapConnectionTestRequestXO
{
  private String ldapUrl;

  private String searchBase;

  private String managerDn;

  private String managerPassword;

  private String userSearchFilter;

  private String userIdAttribute;

  private String testUserId;

  @Nullable
  public String getLdapUrl() {
    return ldapUrl;
  }

  public void setLdapUrl(@Nullable final String ldapUrl) {
    this.ldapUrl = ldapUrl;
  }

  @Nullable
  public String getSearchBase() {
    return searchBase;
  }

  public void setSearchBase(@Nullable final String searchBase) {
    this.searchBase = searchBase;
  }

  @Nullable
  public String getManagerDn() {
    return managerDn;
  }

  public void setManagerDn(@Nullable final String managerDn) {
    this.managerDn = managerDn;
  }

  @Nullable
  public String getManagerPassword() {
    return managerPassword;
  }

  public void setManagerPassword(@Nullable final String managerPassword) {
    this.managerPassword = managerPassword;
  }

  @Nullable
  public String getUserSearchFilter() {
    return userSearchFilter;
  }

  public void setUserSearchFilter(@Nullable final String userSearchFilter) {
    this.userSearchFilter = userSearchFilter;
  }

  @Nullable
  public String getUserIdAttribute() {
    return userIdAttribute;
  }

  public void setUserIdAttribute(@Nullable final String userIdAttribute) {
    this.userIdAttribute = userIdAttribute;
  }

  /**
   * A login name to look up, which also exercises the search filter and reports the groups that would be granted.
   */
  @Nullable
  public String getTestUserId() {
    return testUserId;
  }

  public void setTestUserId(@Nullable final String testUserId) {
    this.testUserId = testUserId;
  }
}
