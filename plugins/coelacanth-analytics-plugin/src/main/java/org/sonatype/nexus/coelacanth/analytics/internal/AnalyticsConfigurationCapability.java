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
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.i18n.I18N;
import org.sonatype.goodies.i18n.MessageBundle;
import org.sonatype.nexus.capability.CapabilityConfigurationSupport;
import org.sonatype.nexus.capability.CapabilityDescriptorSupport;
import org.sonatype.nexus.capability.CapabilitySupport;
import org.sonatype.nexus.capability.CapabilityType;
import org.sonatype.nexus.capability.Condition;
import org.sonatype.nexus.capability.Tag;
import org.sonatype.nexus.capability.Taggable;
import org.sonatype.nexus.coelacanth.analytics.internal.AnalyticsConfigurationCapability.Configuration;
import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.formfields.CheckboxFormField;
import org.sonatype.nexus.formfields.FormField;

import static org.sonatype.nexus.capability.CapabilityType.capabilityType;

/**
 * Supplies the {@code analytics-configuration} capability that the open source edition otherwise has no descriptor
 * for.
 *
 * Three places in core already read a capability of this type: the usage-metrics consent banner treats its absence
 * as "the administrator has not answered yet" and keeps prompting, and the Maven proxy appends a marker to its
 * outbound User-Agent describing the answer. Without a descriptor the capability cannot be created at all, so the
 * question can never be settled. Registering one here closes that gap, and
 * {@link AnalyticsConfigurationCapabilityBooter} answers it with "declined" on first boot.
 *
 * The capability has no behaviour of its own: it is a stored answer that other components consult.
 */
@Named(AnalyticsConfigurationCapability.TYPE_ID)
public class AnalyticsConfigurationCapability
    extends CapabilitySupport<Configuration>
{
  /**
   * Fixed by core. {@code AcknowledgeAnalyticsStateContributor} and {@code MavenProxyRequestHeaderSupport} both
   * hard-code this string, so it cannot be renamed.
   */
  public static final String TYPE_ID = "analytics-configuration";

  public static final CapabilityType TYPE = capabilityType(TYPE_ID);

  /**
   * Property the consent banner writes: {@code "true"} when the administrator accepts, {@code "false"} on decline.
   */
  public static final String P_SUBMIT_ANALYTICS = "submitAnalytics";

  private interface Messages
      extends MessageBundle
  {
    @DefaultMessage("Analytics Configuration")
    String name();

    @DefaultMessage("Analytics")
    String category();

    @DefaultMessage("Submit Usage Metrics")
    String submitLabel();

    @DefaultMessage("Allow anonymous usage metrics to be submitted. This edition contains no code that collects or"
        + " transmits metrics, so enabling it only records consent and changes the marker sent in the Maven proxy"
        + " User-Agent.")
    String submitHelp();

    @DefaultMessage("Submitting usage metrics")
    String enabled();

    @DefaultMessage("Declined")
    String disabled();
  }

  static final Messages messages = I18N.create(Messages.class);

  @Override
  protected Configuration createConfig(final Map<String, String> properties) {
    return new Configuration(properties);
  }

  @Override
  protected String renderDescription() {
    return context().isActive() ? messages.enabled() : messages.disabled();
  }

  @Override
  public Condition activationCondition() {
    return conditions().capabilities().passivateCapabilityDuringUpdate();
  }

  public static class Configuration
      extends CapabilityConfigurationSupport
  {
    private final boolean submitAnalytics;

    public Configuration(final Map<String, String> properties) {
      this.submitAnalytics = Boolean.parseBoolean(properties.get(P_SUBMIT_ANALYTICS));
    }

    public boolean isSubmitAnalytics() {
      return submitAnalytics;
    }
  }

  @AvailabilityVersion(from = "1.0")
  @Named(AnalyticsConfigurationCapability.TYPE_ID)
  @Singleton
  public static class Descriptor
      extends CapabilityDescriptorSupport<Configuration>
      implements Taggable
  {
    public Descriptor() {
      // Exposed, so an administrator who does want to opt in can enable it from the capabilities screen rather than
      // having to clear the stored answer to get the banner back.
      setExposed(true);
      setHidden(false);
    }

    @Override
    public CapabilityType type() {
      return TYPE;
    }

    @Override
    public String name() {
      return messages.name();
    }

    @Override
    public List<FormField> formFields() {
      return Collections.singletonList(
          new CheckboxFormField(P_SUBMIT_ANALYTICS, messages.submitLabel(), messages.submitHelp(),
              FormField.OPTIONAL));
    }

    @Override
    protected Configuration createConfig(final Map<String, String> properties) {
      return new Configuration(properties);
    }

    @Override
    protected String renderAbout() {
      return render(TYPE_ID + "-about.vm");
    }

    @Override
    public Set<Tag> getTags() {
      return Collections.singleton(Tag.categoryTag(messages.category()));
    }
  }
}
