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
package org.sonatype.nexus.coelacanth.analytics.internal;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.capability.CapabilityType;
import org.sonatype.nexus.coelacanth.analytics.internal.AnalyticsConfigurationCapability.Configuration;
import org.sonatype.nexus.coelacanth.analytics.internal.AnalyticsConfigurationCapability.Descriptor;
import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.formfields.FormField;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class AnalyticsConfigurationCapabilityTest
    extends TestSupport
{
  /**
   * Core hard-codes this string in {@code AcknowledgeAnalyticsStateContributor} and
   * {@code MavenProxyRequestHeaderSupport}. If it drifts, the banner reappears and the Maven User-Agent reverts to
   * the undecided marker, with nothing failing loudly.
   */
  @Test
  public void typeIdMatchesTheOneCoreLooksFor() {
    assertThat(AnalyticsConfigurationCapability.TYPE_ID, is("analytics-configuration"));
    assertThat(AnalyticsConfigurationCapability.TYPE, is(CapabilityType.capabilityType("analytics-configuration")));
  }

  /**
   * The banner writes this property name; reading a different one would silently ignore the answer.
   */
  @Test
  public void propertyNameMatchesTheBanner() {
    assertThat(AnalyticsConfigurationCapability.P_SUBMIT_ANALYTICS, is("submitAnalytics"));
  }

  @Test
  public void parsesSubmitAnalytics() {
    assertThat(config(Map.of("submitAnalytics", "true")).isSubmitAnalytics(), is(true));
    assertThat(config(Map.of("submitAnalytics", "false")).isSubmitAnalytics(), is(false));
  }

  @Test
  public void absentPropertyMeansDeclined() {
    assertThat(config(Collections.emptyMap()).isSubmitAnalytics(), is(false));
  }

  @Test
  public void unparseableValueMeansDeclined() {
    assertThat(config(Map.of("submitAnalytics", "yes please")).isSubmitAnalytics(), is(false));
  }

  @Test
  public void descriptorReportsTheRightType() {
    assertThat(new Descriptor().type(), is(AnalyticsConfigurationCapability.TYPE));
  }

  /**
   * Exposed so an administrator can opt in from the capabilities screen instead of having to delete the stored
   * answer to get the banner back.
   */
  @Test
  public void descriptorIsVisibleInTheUi() {
    Descriptor descriptor = new Descriptor();

    assertThat(descriptor.isExposed(), is(true));
    assertThat(descriptor.isHidden(), is(false));
  }

  @Test
  public void descriptorOffersTheConsentCheckbox() {
    List<FormField> fields = new Descriptor().formFields();

    assertThat(fields.size(), is(1));
    assertThat(fields.get(0).getId(), is("submitAnalytics"));
  }

  @Test
  public void descriptorHasAName() {
    assertThat(new Descriptor().name(), notNullValue());
  }

  @Test
  public void descriptorIsTagged() {
    assertThat(new Descriptor().getTags().isEmpty(), is(false));
  }

  /**
   * Without this annotation {@code DatabaseCheckImpl} logs "Missing database version specified" and refuses the
   * descriptor, so the capability never shows up.
   */
  @Test
  public void descriptorDeclaresItsAvailabilityVersion() {
    AvailabilityVersion annotation = Descriptor.class.getAnnotation(AvailabilityVersion.class);

    assertThat(annotation, notNullValue());
    assertThat(annotation.from(), is("1.0"));
  }

  private Configuration config(final Map<String, String> properties) {
    return new Configuration(properties);
  }
}
