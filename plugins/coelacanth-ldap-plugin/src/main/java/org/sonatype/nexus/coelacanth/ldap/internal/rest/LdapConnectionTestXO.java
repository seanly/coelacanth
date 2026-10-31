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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Outcome of a connection test.
 *
 * Reports each step separately so a half-working configuration is diagnosable: reaching the server, binding as the
 * manager account, and locating a user are distinct failures with distinct fixes.
 */
public class LdapConnectionTestXO
{
  private boolean connected;

  private boolean bound;

  private Boolean userFound;

  private Integer groupCount;

  private String message;

  private final List<String> groups = new ArrayList<>();

  public boolean isConnected() {
    return connected;
  }

  public void setConnected(final boolean connected) {
    this.connected = connected;
  }

  public boolean isBound() {
    return bound;
  }

  public void setBound(final boolean bound) {
    this.bound = bound;
  }

  /**
   * @return {@code null} when no user id was supplied to look up
   */
  @Nullable
  public Boolean getUserFound() {
    return userFound;
  }

  public void setUserFound(@Nullable final Boolean userFound) {
    this.userFound = userFound;
  }

  @Nullable
  public Integer getGroupCount() {
    return groupCount;
  }

  public void setGroupCount(@Nullable final Integer groupCount) {
    this.groupCount = groupCount;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public List<String> getGroups() {
    return groups;
  }
}
