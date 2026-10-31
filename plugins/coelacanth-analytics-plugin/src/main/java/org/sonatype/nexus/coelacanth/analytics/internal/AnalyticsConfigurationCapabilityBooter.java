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
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.capability.CapabilityBooterSupport;
import org.sonatype.nexus.capability.CapabilityRegistry;

/**
 * Records "declined" as the usage-metrics answer on first boot.
 *
 * This settles the question before anyone is asked, so the consent banner never appears and the Maven proxy sends
 * the declined marker instead of the undecided one.
 *
 * It uses {@code maybeAddCapability}, which only writes when no capability of the type exists yet. An administrator
 * who later opts in through the capabilities screen keeps that choice across restarts; this does not reassert the
 * default on every boot.
 */
@Named
@Singleton
public class AnalyticsConfigurationCapabilityBooter
    extends CapabilityBooterSupport
{
  private static final String NOTES = "Created by coelacanth-analytics-plugin: usage metrics declined by default."
      + " Enable this capability to opt in.";

  @Override
  protected void boot(final CapabilityRegistry registry) throws Exception {
    Map<String, String> properties =
        Collections.singletonMap(AnalyticsConfigurationCapability.P_SUBMIT_ANALYTICS, "false");

    maybeAddCapability(registry, AnalyticsConfigurationCapability.TYPE, false, NOTES, properties);
  }
}
