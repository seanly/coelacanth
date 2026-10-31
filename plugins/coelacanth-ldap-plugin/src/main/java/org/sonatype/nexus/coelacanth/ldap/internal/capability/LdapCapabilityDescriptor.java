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

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.i18n.I18N;
import org.sonatype.goodies.i18n.MessageBundle;
import org.sonatype.nexus.capability.CapabilityDescriptorSupport;
import org.sonatype.nexus.capability.CapabilityType;
import org.sonatype.nexus.capability.Tag;
import org.sonatype.nexus.capability.Taggable;
import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.formfields.CheckboxFormField;
import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.NumberTextFormField;
import org.sonatype.nexus.formfields.PasswordFormField;
import org.sonatype.nexus.formfields.StringTextFormField;

import static org.sonatype.nexus.capability.CapabilityType.capabilityType;

/**
 * Declares the LDAP configuration form.
 *
 * Fields are rendered by the capability framework, so the whole administration screen comes for free without any
 * front-end code.
 */
@AvailabilityVersion(from = "1.0")
@Named(LdapCapabilityDescriptor.TYPE_ID)
@Singleton
public class LdapCapabilityDescriptor
    extends CapabilityDescriptorSupport<LdapCapabilityConfiguration>
    implements Taggable
{
  public static final String TYPE_ID = "coelacanth.ldap";

  public static final CapabilityType TYPE = capabilityType(TYPE_ID);

  private interface Messages
      extends MessageBundle
  {
    @DefaultMessage("LDAP Authentication")
    String name();

    @DefaultMessage("LDAP URL")
    String ldapUrlLabel();

    @DefaultMessage("Directory URL including the base DN, e.g. ldap://ldap.example.com:389/dc=example,dc=com."
        + " Use ldaps:// for TLS.")
    String ldapUrlHelp();

    @DefaultMessage("Search Base")
    String searchBaseLabel();

    @DefaultMessage("Context searched for users, relative to the base DN in the URL, e.g. ou=people."
        + " Narrow this as much as possible: it is the main lever on search cost.")
    String searchBaseHelp();

    @DefaultMessage("Search Subtree")
    String searchSubtreeLabel();

    @DefaultMessage("Search the whole subtree below the search base rather than only its immediate children.")
    String searchSubtreeHelp();

    @DefaultMessage("User DN Pattern")
    String userDnPatternLabel();

    @DefaultMessage("Direct-bind template, e.g. uid={0},ou=people. Leave blank to use search bind, which is"
        + " required by Active Directory and by any directory that forbids anonymous bind.")
    String userDnPatternHelp();

    @DefaultMessage("Manager DN")
    String managerDnLabel();

    @DefaultMessage("Account used to search the directory, e.g. cn=nexus,ou=services,dc=example,dc=com."
        + " Needs read access to user entries and, for group mapping, to group attributes.")
    String managerDnHelp();

    @DefaultMessage("Manager Password")
    String managerPasswordLabel();

    @DefaultMessage("Password for the manager account. Stored encrypted.")
    String managerPasswordHelp();

    @DefaultMessage("User Search Filter")
    String userSearchFilterLabel();

    @DefaultMessage("RFC 2254 filter where {0} is the login name, e.g. (sAMAccountName={0}) for Active Directory."
        + " Defaults to the user id attribute. The login name is escaped before substitution.")
    String userSearchFilterHelp();

    @DefaultMessage("User ID Attribute")
    String userIdAttributeLabel();

    @DefaultMessage("Attribute holding the login name, e.g. uid or sAMAccountName. Defaults to uid.")
    String userIdAttributeHelp();

    @DefaultMessage("Email Attribute")
    String emailAttributeLabel();

    @DefaultMessage("Attribute holding the email address. Defaults to mail.")
    String emailAttributeHelp();

    @DefaultMessage("Real Name Attribute")
    String realNameAttributeLabel();

    @DefaultMessage("Attribute holding the display name. Defaults to cn.")
    String realNameAttributeHelp();

    @DefaultMessage("Group Strategy")
    String groupStrategyLabel();

    @DefaultMessage("One of NONE, STATIC, DYNAMIC or HIERARCHY. STATIC reads members from the group entry;"
        + " DYNAMIC reads groups from the user entry (memberOf), which OpenLDAP does not populate unless the"
        + " memberOf overlay is enabled; HIERARCHY derives groups from the ou components of the user DN.")
    String groupStrategyHelp();

    @DefaultMessage("Group Search Base")
    String groupSearchBaseLabel();

    @DefaultMessage("Context searched for groups when using the STATIC strategy. Defaults to the user search base.")
    String groupSearchBaseHelp();

    @DefaultMessage("Group Object Class")
    String groupObjectClassLabel();

    @DefaultMessage("Object class of group entries, e.g. groupOfNames or group. Defaults to groupOfNames.")
    String groupObjectClassHelp();

    @DefaultMessage("Group ID Attribute")
    String groupIdAttributeLabel();

    @DefaultMessage("Attribute holding the group name, e.g. cn. Defaults to cn.")
    String groupIdAttributeHelp();

    @DefaultMessage("Group Member Attribute")
    String groupMemberAttributeLabel();

    @DefaultMessage("STATIC strategy: attribute on the group listing its members, e.g. member or uniqueMember."
        + " Defaults to member.")
    String groupMemberAttributeHelp();

    @DefaultMessage("Group Member Format")
    String groupMemberFormatLabel();

    @DefaultMessage("How the member attribute references a user: ${dn} for the full DN, ${username} for the bare"
        + " login name. Defaults to ${dn}.")
    String groupMemberFormatHelp();

    @DefaultMessage("Member Of Attribute")
    String userMemberOfAttributeLabel();

    @DefaultMessage("DYNAMIC strategy: attribute on the user listing its groups. Defaults to memberOf."
        + " For nested Active Directory groups use msds-memberOfTransitive.")
    String userMemberOfAttributeHelp();

    @DefaultMessage("Connect Timeout (ms)")
    String connectTimeoutLabel();

    @DefaultMessage("Socket connect timeout. Defaults to 10000. Never leave this unbounded: JNDI waits forever by"
        + " default and an unresponsive directory would pin request threads.")
    String connectTimeoutHelp();

    @DefaultMessage("Read Timeout (ms)")
    String readTimeoutLabel();

    @DefaultMessage("Response read timeout. Defaults to 15000.")
    String readTimeoutHelp();

    @DefaultMessage("Use Paged Results")
    String usePagedResultsLabel();

    @DefaultMessage("Request paged results. The directory must support the LDAP paged results control.")
    String usePagedResultsHelp();

    @DefaultMessage("Page Size")
    String pageSizeLabel();

    @DefaultMessage("Entries per page when paging is enabled. Defaults to 500.")
    String pageSizeHelp();

    @DefaultMessage("Max Search Results")
    String maxSearchResultsLabel();

    @DefaultMessage("Upper bound on entries returned by a user or group search. Defaults to 300.")
    String maxSearchResultsHelp();

    @DefaultMessage("Referral Strategy")
    String referralStrategyLabel();

    @DefaultMessage("One of follow, ignore or throw. Defaults to follow.")
    String referralStrategyHelp();
  }

  private static final Messages messages = I18N.create(Messages.class);

  private final List<FormField> formFields;

  @Inject
  public LdapCapabilityDescriptor() {
    this.formFields = Arrays.asList(
        new StringTextFormField(LdapCapabilityConfiguration.P_LDAP_URL, messages.ldapUrlLabel(),
            messages.ldapUrlHelp(), FormField.MANDATORY),
        new StringTextFormField(LdapCapabilityConfiguration.P_SEARCH_BASE, messages.searchBaseLabel(),
            messages.searchBaseHelp(), FormField.OPTIONAL),
        new CheckboxFormField(LdapCapabilityConfiguration.P_SEARCH_SUBTREE, messages.searchSubtreeLabel(),
            messages.searchSubtreeHelp(), FormField.OPTIONAL).withInitialValue(true),
        new StringTextFormField(LdapCapabilityConfiguration.P_USER_DN_PATTERN, messages.userDnPatternLabel(),
            messages.userDnPatternHelp(), FormField.OPTIONAL),
        new StringTextFormField(LdapCapabilityConfiguration.P_MANAGER_DN, messages.managerDnLabel(),
            messages.managerDnHelp(), FormField.OPTIONAL),
        new PasswordFormField(LdapCapabilityConfiguration.P_MANAGER_PASSWORD, messages.managerPasswordLabel(),
            messages.managerPasswordHelp(), FormField.OPTIONAL),
        new StringTextFormField(LdapCapabilityConfiguration.P_USER_SEARCH_FILTER, messages.userSearchFilterLabel(),
            messages.userSearchFilterHelp(), FormField.OPTIONAL),
        new StringTextFormField(LdapCapabilityConfiguration.P_USER_ID_ATTRIBUTE, messages.userIdAttributeLabel(),
            messages.userIdAttributeHelp(), FormField.OPTIONAL).withInitialValue("uid"),
        new StringTextFormField(LdapCapabilityConfiguration.P_EMAIL_ATTRIBUTE, messages.emailAttributeLabel(),
            messages.emailAttributeHelp(), FormField.OPTIONAL).withInitialValue("mail"),
        new StringTextFormField(LdapCapabilityConfiguration.P_REAL_NAME_ATTRIBUTE, messages.realNameAttributeLabel(),
            messages.realNameAttributeHelp(), FormField.OPTIONAL).withInitialValue("cn"),
        new StringTextFormField(LdapCapabilityConfiguration.P_GROUP_STRATEGY, messages.groupStrategyLabel(),
            messages.groupStrategyHelp(), FormField.OPTIONAL, "^(NONE|STATIC|DYNAMIC|HIERARCHY)$")
            .withInitialValue("NONE"),
        new StringTextFormField(LdapCapabilityConfiguration.P_GROUP_SEARCH_BASE, messages.groupSearchBaseLabel(),
            messages.groupSearchBaseHelp(), FormField.OPTIONAL),
        new StringTextFormField(LdapCapabilityConfiguration.P_GROUP_OBJECT_CLASS, messages.groupObjectClassLabel(),
            messages.groupObjectClassHelp(), FormField.OPTIONAL).withInitialValue("groupOfNames"),
        new StringTextFormField(LdapCapabilityConfiguration.P_GROUP_ID_ATTRIBUTE, messages.groupIdAttributeLabel(),
            messages.groupIdAttributeHelp(), FormField.OPTIONAL).withInitialValue("cn"),
        new StringTextFormField(LdapCapabilityConfiguration.P_GROUP_MEMBER_ATTRIBUTE,
            messages.groupMemberAttributeLabel(), messages.groupMemberAttributeHelp(), FormField.OPTIONAL)
            .withInitialValue("member"),
        new StringTextFormField(LdapCapabilityConfiguration.P_GROUP_MEMBER_FORMAT, messages.groupMemberFormatLabel(),
            messages.groupMemberFormatHelp(), FormField.OPTIONAL).withInitialValue("${dn}"),
        new StringTextFormField(LdapCapabilityConfiguration.P_USER_MEMBER_OF_ATTRIBUTE,
            messages.userMemberOfAttributeLabel(), messages.userMemberOfAttributeHelp(), FormField.OPTIONAL)
            .withInitialValue("memberOf"),
        new NumberTextFormField(LdapCapabilityConfiguration.P_CONNECT_TIMEOUT, messages.connectTimeoutLabel(),
            messages.connectTimeoutHelp(), FormField.OPTIONAL).withInitialValue(10000).withMinimumValue(1),
        new NumberTextFormField(LdapCapabilityConfiguration.P_READ_TIMEOUT, messages.readTimeoutLabel(),
            messages.readTimeoutHelp(), FormField.OPTIONAL).withInitialValue(15000).withMinimumValue(1),
        new CheckboxFormField(LdapCapabilityConfiguration.P_USE_PAGED_RESULTS, messages.usePagedResultsLabel(),
            messages.usePagedResultsHelp(), FormField.OPTIONAL),
        new NumberTextFormField(LdapCapabilityConfiguration.P_PAGE_SIZE, messages.pageSizeLabel(),
            messages.pageSizeHelp(), FormField.OPTIONAL).withInitialValue(500).withMinimumValue(1),
        new NumberTextFormField(LdapCapabilityConfiguration.P_MAX_SEARCH_RESULTS, messages.maxSearchResultsLabel(),
            messages.maxSearchResultsHelp(), FormField.OPTIONAL).withInitialValue(300).withMinimumValue(1),
        new StringTextFormField(LdapCapabilityConfiguration.P_REFERRAL_STRATEGY, messages.referralStrategyLabel(),
            messages.referralStrategyHelp(), FormField.OPTIONAL, "^(follow|ignore|throw)$")
            .withInitialValue("follow"));
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
  protected LdapCapabilityConfiguration createConfig(final java.util.Map<String, String> properties) {
    return new LdapCapabilityConfiguration(properties);
  }

  @Override
  protected String renderAbout() {
    return render(TYPE_ID + "-about.vm");
  }

  @Override
  public java.util.Set<Tag> getTags() {
    return Tag.tags(Tag.categoryTag("Security"));
  }
}
