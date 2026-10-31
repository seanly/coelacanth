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
package org.sonatype.nexus.coelacanth.usertoken.internal.capability;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.i18n.I18N;
import org.sonatype.goodies.i18n.MessageBundle;
import org.sonatype.nexus.capability.CapabilityDescriptorSupport;
import org.sonatype.nexus.capability.CapabilityType;
import org.sonatype.nexus.capability.Tag;
import org.sonatype.nexus.capability.Taggable;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenSettings;
import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.formfields.CheckboxFormField;
import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.NumberTextFormField;

import static org.sonatype.nexus.capability.CapabilityType.capabilityType;

/**
 * Declares the user token capability, whose presence switches the feature on.
 */
@AvailabilityVersion(from = "1.0")
@Named(UserTokenCapabilityDescriptor.TYPE_ID)
@Singleton
public class UserTokenCapabilityDescriptor
    extends CapabilityDescriptorSupport<UserTokenCapabilityConfiguration>
    implements Taggable
{
  public static final String TYPE_ID = "coelacanth.usertoken";

  public static final CapabilityType TYPE = capabilityType(TYPE_ID);

  private interface Messages
      extends MessageBundle
  {
    @DefaultMessage("User Token")
    String name();

    @DefaultMessage("Require User Tokens for Repository Access")
    String protectContentLabel();

    @DefaultMessage("Refuse plain passwords for repository traffic and accept only user tokens. Leave this off"
        + " while rolling tokens out, or existing builds will start failing immediately.")
    String protectContentHelp();

    @DefaultMessage("User Token Expiration")
    String expirationEnabledLabel();

    @DefaultMessage("Expire tokens after a fixed age. Enabling this applies to tokens that already exist, so any"
        + " token older than the chosen age expires as soon as the setting is saved.")
    String expirationEnabledHelp();

    @DefaultMessage("User Token Expiry (days)")
    String expirationDaysLabel();

    @DefaultMessage("Number of days a token stays valid, 1 to 999. Defaults to 30.")
    String expirationDaysHelp();
  }

  private static final Messages messages = I18N.create(Messages.class);

  private final List<FormField> formFields;

  @Inject
  public UserTokenCapabilityDescriptor() {
    this.formFields = Arrays.asList(
        new CheckboxFormField(UserTokenCapabilityConfiguration.P_PROTECT_CONTENT,
            messages.protectContentLabel(), messages.protectContentHelp(), FormField.OPTIONAL),
        new CheckboxFormField(UserTokenCapabilityConfiguration.P_EXPIRATION_ENABLED,
            messages.expirationEnabledLabel(), messages.expirationEnabledHelp(), FormField.OPTIONAL),
        new NumberTextFormField(UserTokenCapabilityConfiguration.P_EXPIRATION_DAYS,
            messages.expirationDaysLabel(), messages.expirationDaysHelp(), FormField.OPTIONAL)
            .withInitialValue(UserTokenSettings.DEFAULT_EXPIRATION_DAYS)
            .withMinimumValue(1)
            .withMaximumValue(999));
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
    return formFields;
  }

  @Override
  protected UserTokenCapabilityConfiguration createConfig(final Map<String, String> properties) {
    return new UserTokenCapabilityConfiguration(properties);
  }

  @Override
  protected String renderAbout() {
    return render(TYPE_ID + "-about.vm");
  }

  @Override
  public Set<Tag> getTags() {
    return Tag.tags(Tag.categoryTag("Security"));
  }
}
