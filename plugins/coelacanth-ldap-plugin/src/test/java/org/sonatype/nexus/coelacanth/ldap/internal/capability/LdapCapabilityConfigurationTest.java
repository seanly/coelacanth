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

import java.util.HashMap;
import java.util.Map;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.coelacanth.ldap.GroupStrategyType;
import org.sonatype.nexus.coelacanth.ldap.LdapConfiguration;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class LdapCapabilityConfigurationTest
    extends TestSupport
{
  @Test
  public void appliesDefaultsForOmittedFields() {
    LdapConfiguration config = configure(Map.of(
        LdapCapabilityConfiguration.P_LDAP_URL, "ldap://localhost:389/dc=example,dc=com"));

    assertThat(config.getUserIdAttribute(), is("uid"));
    assertThat(config.getGroupStrategy(), is(GroupStrategyType.NONE));
    assertThat(config.getConnectTimeoutMillis(), is(10_000));
    assertThat(config.getReadTimeoutMillis(), is(15_000));
    assertThat(config.getMaxSearchResults(), is(300));
    assertThat(config.getReferralStrategy(), is("follow"));
    assertThat(config.isSearchSubtree(), is(true));
  }

  @Test
  public void blankStringsFallBackToDefaults() {
    LdapConfiguration config = configure(Map.of(
        LdapCapabilityConfiguration.P_LDAP_URL, "ldap://localhost:389",
        LdapCapabilityConfiguration.P_USER_ID_ATTRIBUTE, "   ",
        LdapCapabilityConfiguration.P_REFERRAL_STRATEGY, ""));

    assertThat(config.getUserIdAttribute(), is("uid"));
    assertThat(config.getReferralStrategy(), is("follow"));
  }

  @Test
  public void trimsSurroundingWhitespace() {
    LdapConfiguration config = configure(Map.of(
        LdapCapabilityConfiguration.P_LDAP_URL, "  ldap://localhost:389  ",
        LdapCapabilityConfiguration.P_SEARCH_BASE, " ou=people "));

    assertThat(config.getLdapUrl(), is("ldap://localhost:389"));
    assertThat(config.getSearchBase(), is("ou=people"));
  }

  @Test
  public void parsesGroupStrategy() {
    assertThat(strategyFor("STATIC"), is(GroupStrategyType.STATIC));
    assertThat(strategyFor("dynamic"), is(GroupStrategyType.DYNAMIC));
    assertThat(strategyFor("Hierarchy"), is(GroupStrategyType.HIERARCHY));
  }

  @Test
  public void unknownGroupStrategyFallsBackToNone() {
    assertThat(strategyFor("nonsense"), is(GroupStrategyType.NONE));
  }

  @Test
  public void searchBindIsImpliedByAbsentUserDnPattern() {
    LdapConfiguration searchBind = configure(Map.of(
        LdapCapabilityConfiguration.P_LDAP_URL, "ldap://localhost:389"));
    assertThat(searchBind.isSearchBind(), is(true));

    LdapConfiguration directBind = configure(Map.of(
        LdapCapabilityConfiguration.P_LDAP_URL, "ldap://localhost:389",
        LdapCapabilityConfiguration.P_USER_DN_PATTERN, "uid={0},ou=people"));
    assertThat(directBind.isSearchBind(), is(false));
  }

  @Test
  public void detectsTls() {
    assertThat(configure(Map.of(LdapCapabilityConfiguration.P_LDAP_URL, "ldaps://localhost:636")).isSecure(),
        is(true));
    assertThat(configure(Map.of(LdapCapabilityConfiguration.P_LDAP_URL, "ldap://localhost:389")).isSecure(),
        is(false));
  }

  @Test
  public void unparseableNumberFallsBackToDefault() {
    LdapConfiguration config = configure(Map.of(
        LdapCapabilityConfiguration.P_LDAP_URL, "ldap://localhost:389",
        LdapCapabilityConfiguration.P_CONNECT_TIMEOUT, "not-a-number"));

    assertThat(config.getConnectTimeoutMillis(), is(10_000));
  }

  @Test
  public void nonPositiveTimeoutFallsBackToDefault() {
    LdapConfiguration config = configure(Map.of(
        LdapCapabilityConfiguration.P_LDAP_URL, "ldap://localhost:389",
        LdapCapabilityConfiguration.P_READ_TIMEOUT, "0"));

    assertThat(config.getReadTimeoutMillis(), is(15_000));
  }

  @Test
  public void passwordIsCarriedThroughUntrimmed() {
    // a password may legitimately begin or end with a space
    Map<String, String> properties = new HashMap<>();
    properties.put(LdapCapabilityConfiguration.P_LDAP_URL, "ldap://localhost:389");
    properties.put(LdapCapabilityConfiguration.P_MANAGER_PASSWORD, " secret ");

    assertThat(new LdapCapabilityConfiguration(properties).getConfiguration().getManagerPassword(), is(" secret "));
  }

  @Test
  public void absentOptionalFieldsAreNull() {
    LdapConfiguration config = configure(Map.of(
        LdapCapabilityConfiguration.P_LDAP_URL, "ldap://localhost:389"));

    assertThat(config.getManagerDn(), nullValue());
    assertThat(config.getManagerPassword(), nullValue());
    assertThat(config.getUserSearchFilter(), nullValue());
    assertThat(config.getGroupSearchBase(), nullValue());
  }

  @Test
  public void toStringOmitsPassword() {
    Map<String, String> properties = new HashMap<>();
    properties.put(LdapCapabilityConfiguration.P_LDAP_URL, "ldap://localhost:389");
    properties.put(LdapCapabilityConfiguration.P_MANAGER_PASSWORD, "topsecret");

    String rendered = new LdapCapabilityConfiguration(properties).getConfiguration().toString();

    assertThat(rendered.contains("topsecret"), is(false));
  }

  private GroupStrategyType strategyFor(final String value) {
    return configure(Map.of(
        LdapCapabilityConfiguration.P_LDAP_URL, "ldap://localhost:389",
        LdapCapabilityConfiguration.P_GROUP_STRATEGY, value)).getGroupStrategy();
  }

  private LdapConfiguration configure(final Map<String, String> properties) {
    return new LdapCapabilityConfiguration(new HashMap<>(properties)).getConfiguration();
  }
}
