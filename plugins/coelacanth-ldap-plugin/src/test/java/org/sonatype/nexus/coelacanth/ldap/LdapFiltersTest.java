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

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

public class LdapFiltersTest
    extends TestSupport
{
  @Test
  public void escapesFilterMetacharacters() {
    assertThat(LdapFilters.escapeFilterValue("*"), is("\\2a"));
    assertThat(LdapFilters.escapeFilterValue("("), is("\\28"));
    assertThat(LdapFilters.escapeFilterValue(")"), is("\\29"));
    assertThat(LdapFilters.escapeFilterValue("\\"), is("\\5c"));
    assertThat(LdapFilters.escapeFilterValue("\0"), is("\\00"));
  }

  @Test
  public void leavesOrdinaryValuesAlone() {
    assertThat(LdapFilters.escapeFilterValue("jane.doe"), is("jane.doe"));
    assertThat(LdapFilters.escapeFilterValue("CN=Jane Doe"), is("CN=Jane Doe"));
  }

  @Test
  public void nullSafe() {
    assertThat(LdapFilters.escapeFilterValue(null), nullValue());
    assertThat(LdapFilters.escapeDnValue(null), nullValue());
    assertThat(LdapFilters.interpolate(null, "jane", false), nullValue());
  }

  /**
   * The classic injection: a login name that closes the assertion and opens a wildcard one, turning
   * {@code (uid={0})} into a filter that matches every entry.
   */
  @Test
  public void neutralizesFilterInjection() {
    String filter = LdapFilters.interpolate("(uid={0})", "*)(uid=*", false);

    assertThat(filter, is("(uid=\\2a\\29\\28uid=\\2a)"));
    // the payload must not survive as syntax
    assertThat(filter, not(containsString(")(")));
  }

  /**
   * The always-true payload {@code admin)(|(uid=*} relies on closing the assertion and opening an OR clause.
   *
   * Note that {@code |} itself is not escaped, and does not need to be: RFC 4515 gives it meaning only immediately
   * after an opening parenthesis. With the parentheses escaped it is an inert literal inside the value.
   */
  @Test
  public void neutralizesAlwaysTrueInjection() {
    String filter = LdapFilters.interpolate("(uid={0})", "admin)(|(uid=*", false);

    assertThat(filter, is("(uid=admin\\29\\28|\\28uid=\\2a)"));
    // no unescaped parenthesis survives, so no new clause can be opened
    assertThat(filter.substring("(uid=".length(), filter.length() - 1), not(containsString("(")));
    assertThat(filter.substring("(uid=".length(), filter.length() - 1), not(containsString(")")));
  }

  @Test
  public void escapesDnMetacharacters() {
    assertThat(LdapFilters.escapeDnValue("Doe, Jane"), is("Doe\\, Jane"));
    assertThat(LdapFilters.escapeDnValue("a+b"), is("a\\+b"));
    assertThat(LdapFilters.escapeDnValue("a\"b"), is("a\\\"b"));
    assertThat(LdapFilters.escapeDnValue("a<b>c"), is("a\\<b\\>c"));
    assertThat(LdapFilters.escapeDnValue("a;b"), is("a\\;b"));
  }

  @Test
  public void escapesLeadingAndTrailingSpaceInDn() {
    assertThat(LdapFilters.escapeDnValue(" jane"), is("\\ jane"));
    assertThat(LdapFilters.escapeDnValue("jane "), is("jane\\ "));
  }

  @Test
  public void escapesLeadingHashInDn() {
    assertThat(LdapFilters.escapeDnValue("#jane"), is("\\#jane"));
  }

  @Test
  public void interpolatesDnPatternWithDnEscaping() {
    String dn = LdapFilters.interpolate("uid={0},ou=people", "Doe, Jane", true);

    assertThat(dn, is("uid=Doe\\, Jane,ou=people"));
  }

  @Test
  public void interpolationReplacesEveryPlaceholder() {
    String filter = LdapFilters.interpolate("(|(uid={0})(cn={0}))", "jane", false);

    assertThat(filter, is("(|(uid=jane)(cn=jane))"));
  }

  @Test
  public void nullUsernameYieldsEmptySubstitution() {
    assertThat(LdapFilters.interpolate("(uid={0})", null, false), is("(uid=)"));
  }
}
