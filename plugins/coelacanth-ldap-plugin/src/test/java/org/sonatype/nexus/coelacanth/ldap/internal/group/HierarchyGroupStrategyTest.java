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
package org.sonatype.nexus.coelacanth.ldap.internal.group;

import java.util.Set;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.coelacanth.ldap.GroupStrategyType;
import org.sonatype.nexus.coelacanth.ldap.LdapConfiguration;
import org.sonatype.nexus.coelacanth.ldap.LdapEntry;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

public class HierarchyGroupStrategyTest
    extends TestSupport
{
  private HierarchyGroupStrategy underTest;

  private LdapConfiguration config;

  @Before
  public void setup() {
    underTest = new HierarchyGroupStrategy();
    config = LdapConfiguration.builder()
        .ldapUrl("ldap://localhost:389/dc=example,dc=com")
        .groupStrategy(GroupStrategyType.HIERARCHY)
        .build();
  }

  @Test
  public void reportsItsType() {
    assertThat(underTest.getType(), is(GroupStrategyType.HIERARCHY));
  }

  @Test
  public void derivesGroupsFromOrganizationalUnits() {
    Set<String> groups = underTest.findGroups(config, entry("uid=jane,ou=dev,ou=uk,dc=example,dc=com"));

    // ordered from most specific to least, which is the DN order
    assertThat(groups, contains("dev", "uk"));
  }

  @Test
  public void ignoresDomainComponents() {
    Set<String> groups = underTest.findGroups(config, entry("uid=jane,dc=example,dc=com"));

    assertThat(groups, is(empty()));
  }

  @Test
  public void skipsTheUsersOwnRdn() {
    // ou in the leading position identifies the entry itself, not a group
    Set<String> groups = underTest.findGroups(config, entry("ou=jane,ou=dev,dc=example,dc=com"));

    assertThat(groups, contains("dev"));
  }

  @Test
  public void toleratesWhitespaceAndCase() {
    Set<String> groups = underTest.findGroups(config, entry("uid=jane, OU=dev , ou=uk,dc=example,dc=com"));

    assertThat(groups, contains("dev", "uk"));
  }

  @Test
  public void handlesMalformedComponents() {
    Set<String> groups = underTest.findGroups(config, entry("uid=jane,garbage,ou=dev,dc=example,dc=com"));

    assertThat(groups, contains("dev"));
  }

  @Test
  public void emptyForNullEntry() {
    assertThat(underTest.findGroups(config, null), is(empty()));
  }

  @Test
  public void emptyForEntryWithoutDn() {
    assertThat(underTest.findGroups(config, new LdapEntry(null, "jane", null)), is(empty()));
  }

  private LdapEntry entry(final String dn) {
    return new LdapEntry(dn, "jane", null);
  }
}
