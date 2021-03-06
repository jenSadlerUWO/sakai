package org.sakaiproject.sitemanage.api;

public interface SiteHelper {

	/**
	 * This site ID selected from the helper.
	 */
	static final String SITE_PICKER_SITE_ID = "sakaiproject.sitepicker.siteid";
	
	/**
	 * Permission needed for the current user over the selected site.
	 * @see SiteService.SiteType
	 */
	static final String SITE_PICKER_PERMISSION = "sakaiproject.sitepicker.permission";
	
	/**
	 * Property needing to be set on the requested site.
	 */
	static final String SITE_PICKER_PROPERTY = "sakaiproject.sitepicker.property";
	
	/**
	 * The selection of a site ID was cancelled.
	 */
	static final String SITE_PICKER_CANCELLED = "sakaiproject.sitepicker.cancelled";
	
	
	// For creation of a new site


	/**
	 * Attribute to indicate that the site creation helper should start from the beginning.
	 * Example: Boolean.TRUE.
	 */
	static final String SITE_CREATE_START = "sakaiproject.sitecreate.start";
	
	/**
	 * Attribute to tell set creation helper what site types should be available.
	 * Example: "project,course".
	 */
	static final String SITE_CREATE_SITE_TYPES = "sakaiproject.sitecreate.types";
	
	/**
	 * The title of the site to create, if present the user won't be able to edit the site title in the helper.
	 * Example: "My Test Site".
	 */
	static final String SITE_CREATE_SITE_TITLE = "sakaiproject.sitecreate.title";
	
	/**
	 * ID of the created site returned by the helper.
	 * Example: "32mds8slslaid-s7skj-s78sj"
	 */
	static final String SITE_CREATE_SITE_ID = "sakaiproject.sitecreate.siteid";
	
	/**
	 * Precence indicated user cancelled the helper.
	 * Example: Boolean.TRUE.
	 */
	static final String SITE_CREATE_CANCELLED = "sakaiproject.sitecreate.cancelled";
	
	/**
	 * this is a property name to indicate whether the Site Info tool should log the following user membership change events
	 */
	static final String WSETUP_TRACK_USER_MEMBERSHIP_CHANGE = "wsetup.track.user.membership.change";

	/**
	 * this is a property name to indicate whether the Site Info tool should log the roster change events
	 */
	static final String WSETUP_TRACK_ROSTER_CHANGE = "wsetup.track.roster.change";

	/**
	 * When a section is added to a group, it will take up to 2 minutes for the memberships to be populated by the scheduled RefreshAuthzGroupTask thread.
	 * This property determines whether to force section memberships to be refreshed immediately when a group's provided sections are modified
	 */
	static final String WSETUP_FORCE_REFRESH_GROUP_ON_SECTION_CHANGE = "wsetup.force.refresh.group.on.section.change";
	
}
