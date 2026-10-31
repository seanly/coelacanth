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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.capability.CapabilityReference;
import org.sonatype.nexus.capability.CapabilityRegistry;
import org.sonatype.nexus.capability.CapabilityType;

import com.google.common.base.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AnalyticsConfigurationCapabilityBooterTest
    extends TestSupport
{
  @Mock
  private CapabilityRegistry registry;

  @Mock
  private CapabilityReference existing;

  private AnalyticsConfigurationCapabilityBooter underTest;

  @Before
  public void setup() {
    underTest = new AnalyticsConfigurationCapabilityBooter();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void createsADeclinedCapabilityOnFirstBoot() throws Exception {
    when(registry.get(any(Predicate.class))).thenReturn(Collections.emptyList());

    underTest.boot(registry);

    ArgumentCaptor<CapabilityType> type = ArgumentCaptor.forClass(CapabilityType.class);
    ArgumentCaptor<Boolean> enabled = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);

    verify(registry).addNonExposed(type.capture(), enabled.capture(), anyString(), properties.capture());

    assertThat(type.getValue(), is(AnalyticsConfigurationCapability.TYPE));
    // disabled is what the banner and the Maven User-Agent read as "declined"
    assertThat(enabled.getValue(), is(false));
    assertThat(properties.getValue().get(AnalyticsConfigurationCapability.P_SUBMIT_ANALYTICS), is("false"));
  }

  /**
   * An administrator who opts in must keep that choice across restarts, so the default is only ever applied when no
   * answer has been recorded.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void leavesAnExistingAnswerAlone() throws Exception {
    Collection<CapabilityReference> found = List.of(existing);
    when(registry.get(any(Predicate.class))).thenReturn((Collection) found);

    underTest.boot(registry);

    verify(registry, never()).addNonExposed(any(), anyBoolean(), anyString(), any());
  }
}
