package org.sakaiproject.gradebookng.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBException;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.commons.lang.math.NumberUtils;

import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityAdvisor.SecurityAdvice;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.Membership;
import org.sakaiproject.coursemanagement.api.Section;
import org.sakaiproject.coursemanagement.api.exception.IdNotFoundException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.gradebookng.business.dto.AssignmentOrder;
import org.sakaiproject.gradebookng.business.exception.GbException;
import org.sakaiproject.gradebookng.business.finalgrades.GbStudentCourseGradeInfo;
import org.sakaiproject.gradebookng.business.model.GbCourseGrade;
import org.sakaiproject.gradebookng.business.model.GbGradeCell;
import org.sakaiproject.gradebookng.business.model.GbGradeInfo;
import org.sakaiproject.gradebookng.business.model.GbGradeLog;
import org.sakaiproject.gradebookng.business.model.GbGroup;
import org.sakaiproject.gradebookng.business.model.GbStudentGradeInfo;
import org.sakaiproject.gradebookng.business.model.GbStudentNameSortOrder;
import org.sakaiproject.gradebookng.business.model.GbUser;
import org.sakaiproject.gradebookng.business.util.CourseGradeFormatter;
import org.sakaiproject.gradebookng.business.util.FinalGradeFormatter;
import org.sakaiproject.gradebookng.business.util.FormatHelper;
import org.sakaiproject.gradebookng.business.util.GbStopWatch;
import org.sakaiproject.gradebookng.tool.model.GradebookUiSettings;
import org.sakaiproject.section.api.coursemanagement.CourseSection;
import org.sakaiproject.service.gradebook.shared.AssessmentNotFoundException;
import org.sakaiproject.service.gradebook.shared.Assignment;
import org.sakaiproject.service.gradebook.shared.CategoryDefinition;
import org.sakaiproject.service.gradebook.shared.CategoryScoreData;
import org.sakaiproject.service.gradebook.shared.CommentDefinition;
import org.sakaiproject.service.gradebook.shared.CourseGrade;
import org.sakaiproject.service.gradebook.shared.GradeDefinition;
import org.sakaiproject.service.gradebook.shared.GradebookExternalAssessmentService;
import org.sakaiproject.service.gradebook.shared.GradebookFrameworkService;
import org.sakaiproject.service.gradebook.shared.GradebookInformation;
import org.sakaiproject.service.gradebook.shared.GradebookNotFoundException;
import org.sakaiproject.service.gradebook.shared.GradebookPermissionService;
import org.sakaiproject.service.gradebook.shared.GradebookService;
import org.sakaiproject.service.gradebook.shared.GraderPermission;
import org.sakaiproject.service.gradebook.shared.InvalidGradeException;
import org.sakaiproject.service.gradebook.shared.PermissionDefinition;
import org.sakaiproject.service.gradebook.shared.SortType;
import org.sakaiproject.service.gradebook.shared.owl.anongrading.OwlAnonGradingID;
import org.sakaiproject.service.gradebook.shared.owl.finalgrades.OwlGradeApproval;
import org.sakaiproject.service.gradebook.shared.owl.finalgrades.OwlGradeSubmission;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.tool.gradebook.Gradebook;
import org.sakaiproject.tool.gradebook.GradingEvent;
import org.sakaiproject.user.api.CandidateDetailProvider;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.FormattedText;
import org.sakaiproject.util.ResourceLoader;

/**
 * Business service for GradebookNG
 *
 * This is not designed to be consumed outside of the application or supplied entityproviders. Use at your own risk.
 *
 * @author Steve Swinsburg (steve.swinsburg@gmail.com)
 *
 */

// TODO add permission checks! Remove logic from entityprovider if there is a
// double up
// TODO some of these methods pass in empty lists and its confusing. If we
// aren't doing paging, remove this.

@Slf4j
public class GradebookNgBusinessService {

	@Setter
	private SiteService siteService;

	@Setter
	private UserDirectoryService userDirectoryService;

	@Setter
	private ToolManager toolManager;

	@Setter
	private GradebookService gradebookService;

	@Setter
	private GradebookPermissionService gradebookPermissionService;

	@Setter
	private GradebookFrameworkService gradebookFrameworkService;

	@Setter
	private GradebookExternalAssessmentService gradebookExternalAssessmentService;

	@Setter
	private CourseManagementService courseManagementService;

	@Setter
	private SecurityService securityService;
	
	@Setter
	private CandidateDetailProvider candidateDetailProvider;

	public static final String ASSIGNMENT_ORDER_PROP = "gbng_assignment_order";

	/**
	 * Get a list of all users in the current site that can have grades
	 *
	 * @return a list of users as uuids or null if none
	 */
	public List<String> getGradeableUsers() {
		return this.getGradeableUsers(null);
	}

	/**
	 * Get a list of all users in the current site, filtered by the given group, that can have grades
	 *
	 * @param groupFilter GbGroupType to filter on
	 *
	 * @return a list of users as uuids or null if none
	 */
	public List<String> getGradeableUsers(final GbGroup groupFilter) {

		try {
			final String siteId = getCurrentSiteId();

			// note that this list MUST exclude TAs as it is checked in the
			// GradebookService and will throw a SecurityException if invalid
			// users are provided
			final Set<String> userUuids = this.siteService.getSite(siteId).getUsersIsAllowed(GbRole.STUDENT.getValue());

			// filter the allowed list based on membership
			if (groupFilter != null && groupFilter.getType() != GbGroup.Type.ALL) {

				final Set<String> groupMembers = new HashSet<>();

				/*
				 * groups handles both if(groupFilter.getType() == GbGroup.Type.SECTION) { Set<Membership> members =
				 * this.courseManagementService.getSectionMemberships( groupFilter.getId()); for(Membership m: members) {
				 * if(userUuids.contains(m.getUserId())) { groupMembers.add(m.getUserId()); } } }
				 */

				//if (groupFilter.getType() == GbGroup.Type.GROUP) {
					final Set<Member> members = this.siteService.getSite(siteId).getGroup(groupFilter.getId())
							.getMembers();
					for (final Member m : members) {
						if (userUuids.contains(m.getUserId())) {
							groupMembers.add(m.getUserId());
						}
					}
				//}

				// only keep the ones we identified in the group
				userUuids.retainAll(groupMembers);
			}

			// if TA, pass it through the gradebook permissions (only if there
			// are permissions)
			if (this.getUserRole(siteId) == GbRole.TA) {
				final User user = getCurrentUser();

				// if there are permissions, pass it through them
				// don't need to test TA access if no permissions
				final List<PermissionDefinition> perms = getPermissionsForUser(user.getId());
				if (!perms.isEmpty()) {

					final Gradebook gradebook = this.getGradebook(siteId);

					// get list of sections and groups this TA has access to
					final List courseSections = this.gradebookService.getViewableSections(gradebook.getUid());

					// get viewable students.
					final List<String> viewableStudents = this.gradebookPermissionService.getViewableStudentsForUser(
							gradebook.getUid(), user.getId(), new ArrayList<>(userUuids), courseSections);

					if (viewableStudents != null) {
						userUuids.retainAll(viewableStudents); // retain only
																// those that
																// are visible
																// to this TA
					} else {
						userUuids.clear(); // TA can't view anyone
					}
				}
			}

			return new ArrayList<>(userUuids);

		} catch (final IdUnusedException e) {
			log.debug(e.getMessage());
			return null;
		}
	}

	/**
	 * Given a list of uuids, get a list of Users
	 *
	 * @param userUuids list of user uuids
	 * @return
	 */
	public List<User> getUsers(final List<String> userUuids) throws GbException {
		try {
			final List<User> users = this.userDirectoryService.getUsers(userUuids);
			Collections.sort(users, new LastNameComparator()); // default sort // OWLTODO: remove this sort, it causes double sorting in various scenarios
			return users;
		} catch (final RuntimeException e) {
			// an LDAP exception can sometimes be thrown here, catch and rethrow
			throw new GbException("An error occurred getting the list of users.", e);
		}
	}
	
	public List<User> getUsers(final List<String> userUuids, final GradebookUiSettings settings)
	{
		// OWLTODO: for now we just grab the users from the previous method, ignoring the presort issue
		// and any filtering opportunities
		
		String studentFilter = settings.getStudentFilter();
		String studentNumberFilter = settings.getStudentNumberFilter();
		Optional<Site> site = getCurrentSite();
		if (studentFilter.isEmpty() && (studentNumberFilter.isEmpty() || !site.isPresent()))
		{
			return getUsers(userUuids);
		}
		
		// preauthorize current user for student number access to save checking for each user
		boolean preAuth = site.isPresent() && isStudentNumberVisible(getCurrentUser(), site.get());
		return getUsers(userUuids).stream().filter(u -> studentMatchesAnyFilter(u, site.orElse(null), studentFilter, studentNumberFilter, preAuth))
				.collect(Collectors.toList());
	}

	/**
	 * Gets a List of GbUsers for the specified userUuids, populating them with student numbers and anonIds without any filtering; sorting by last name
	 * Appropriate only for back end business like grade exports, statistics, etc.
	 * @param userUuids
	 * @return
	 */
	public List<GbUser> getGbUsers(final List<String> userUuids)
	{
		return getGbUsersFilteredIfAnonymous(userUuids, false);
	}

	/**
	 * Gets a List of GbUsers for the specified userUuids, populating them with student numbers and anonIds.
	 * Filters any users who do not have anonIds if filterForAnonymous is set. Sorts by last name.
	 * Inappropriate for the UI in most cases, because there is no section aware filtering; best suited for exports / statistics
	 * @param userUuids
	 * @param filterForAnonymous whether the returned users should be filtered to include only users who have anonymousIDs
	 * @return
	 */
	public List<GbUser> getGbUsersFilteredIfAnonymous(final List<String> userUuids, boolean filterForAnonymous)
	{
		List<GbUser> gbUsers = new ArrayList<>(userUuids.size());
		List<User> users = getUsers(userUuids);
		List<OwlAnonGradingID> anonIds = gradebookService.getAnonGradingIDsBySectionEIDs(getViewableSectionEids());
		Map<String, Map<String, Integer>> studentSectionAnonIdMap = getStudentSectionAnonIdMap(anonIds);
		if (filterForAnonymous)
		{
			users = users.stream().filter(user -> studentSectionAnonIdMap.keySet().contains(user.getEid())).collect(Collectors.toList());
		}

		Site site = getCurrentSite().orElse(null);
		// preauthorize current user for student number access to save checking for each user
		final boolean preAuth = site != null && isStudentNumberVisible(getCurrentUser(), site);
		for (User u : users)
		{
			String studentNumber = preAuth ? getStudentNumberPreAuthorized(u, site) : "";
			Map<String, Integer> sectionAnonIdMap = getMapForKey(studentSectionAnonIdMap, u.getEid());
			gbUsers.add(GbUser.fromUserWithStudentNumberAndAnonIdMap(u, studentNumber, sectionAnonIdMap));
		}

		return gbUsers;
	}

	/**
	 * Gets a List of GbUsers for the specified userUuids, populating them with student numbers / anonIds as appropriate, and also filtering in accordance with any ui search filters
	 * @param userUuids
	 * @param settings
	 * @return
	 */
	public List<GbUser> getGbUsersForUiSettings(final List<String> userUuids, final GradebookUiSettings settings)
	{
		List<User> users = getUsers(userUuids, settings);
		
		// Filter students for anon grading
		final Map<String, Map<String, Integer>> studentSectionAnonIdMap;
		if (settings.isContextAnonymous())
		{
			// Need to filter out users who don't have anonIDs
			List<OwlAnonGradingID> anonIDs = getAnonGradingIDsForUiSettings(settings);
			studentSectionAnonIdMap = getStudentSectionAnonIdMap(anonIDs);
			users.removeIf(user -> !studentSectionAnonIdMap.containsKey(user.getEid()));
		}
		else
		{
			studentSectionAnonIdMap = null;
		}

		List<GbUser> gbUsers = new ArrayList<>(users.size());
		Site site = getCurrentSite().orElse(null);
		// preauthorize current user for student number access to save checking for each user in loops
		final boolean preAuth = site != null && isStudentNumberVisible(getCurrentUser(), site);

		// Sort by name / student number
		if (!settings.isContextAnonymous())
		{
			if (settings.getStudentSortOrder() != null) {

				Comparator<User> comp = GbStudentNameSortOrder.FIRST_NAME.equals(settings.getNameSortOrder()) ?
						new FirstNameComparator() : new LastNameComparator();
				if (SortDirection.DESCENDING.equals(settings.getStudentSortOrder()))
				{
					comp = Collections.reverseOrder(comp);
				}
				Collections.sort(users, comp);
			}
			else if (settings.getStudentNumberSortOrder() != null)
			{
				if (site != null)
				{
					Comparator<User> comp = new StudentNumberComparator(site, preAuth);
					if (SortDirection.DESCENDING.equals(settings.getStudentNumberSortOrder()))
					{
						comp = Collections.reverseOrder(comp);
					}
					Collections.sort(users, comp);
				}
			}
		}

		for (User u : users)
		{
			String studentNumber = preAuth ? getStudentNumberPreAuthorized(u, site) : "";
			Map<String, Integer> sectionAnonIdMap = getMapForKey(studentSectionAnonIdMap, u.getEid());
			gbUsers.add(GbUser.fromUserWithStudentNumberAndAnonIdMap(u, studentNumber, sectionAnonIdMap));
		}

		return gbUsers;
	}

	/**
	 * Null safe method to get a map associated with an outer key in a Map<outterKey, Map<innerKey, innerValue>>.
	 * So, if the whole map, or the inner map is null, an empty map is created
	 */
	private static <T, U, V> Map<U, V> getMapForKey(Map<T, Map<U, V>> tripleMap, T key)
	{
		if (tripleMap == null)
		{
			return new LinkedHashMap<>();
		}
		Map<U, V> retMap = tripleMap.get(key);
		if (retMap == null)
		{
			return new LinkedHashMap<>();
		}
		return retMap;
	}

	// simple or match across all filters
	private boolean studentMatchesAnyFilter(User user, Site site, String studentFilter, String studentNumberFilter, boolean preAuth)
	{
		boolean studentMatch = !studentFilter.isEmpty() && (StringUtils.containsIgnoreCase(user.getDisplayName(), studentFilter) || StringUtils.containsIgnoreCase(user.getEid(), studentFilter));		
		boolean numberMatch = site != null && !studentNumberFilter.isEmpty() && preAuth && StringUtils.containsIgnoreCase(getStudentNumberPreAuthorized(user, site), studentNumberFilter);
		
		return studentMatch || numberMatch;
	}

	/**
	 * Helper to get a reference to the gradebook for the current site
	 *
	 * @return the gradebook for the site
	 */
	public Gradebook getGradebook() {
		return getGradebook(getCurrentSiteId());
	}

	
	/**
	 * Helper to get a reference to the gradebook for the specified site
	 *
	 * @param siteId the siteId
	 * @return the gradebook for the site
	 */
	private Gradebook getGradebook(final String siteId) {
		try {
			final Gradebook gradebook = (Gradebook) this.gradebookService.getGradebook(siteId);
			return gradebook;
		} catch (final GradebookNotFoundException e) {
			log.error("No gradebook in site: {}", siteId);
			return null;
		}
	}

	/**
	 * Get a list of assignments in the gradebook in the current site that the current user is allowed to access
	 *
	 * @return a list of assignments or null if no gradebook
	 */
	public List<Assignment> getGradebookAssignments() {
		return getGradebookAssignments(getCurrentSiteId(), SortType.SORT_BY_SORTING);
	}

	/**
	 * Get a list of assignments in the gradebook in the current site that the current user is allowed to access
	 *
	 * @param siteId
	 * @return a list of assignments or null if no gradebook
	 */
	public List<Assignment> getGradebookAssignments(final String siteId) {
		return getGradebookAssignments(siteId, SortType.SORT_BY_SORTING);
	}

	/**
	 * Get a list of assignments in the gradebook in the current site that the current user is allowed to access sorted by the provided
	 * SortType
	 *
	 * @param sortBy
	 * @return a list of assignments or null if no gradebook
	 */
	public List<Assignment> getGradebookAssignments(final SortType sortBy) {
		return getGradebookAssignments(getCurrentSiteId(), sortBy);
	}

	/**
	 * Special operation to get a list of assignments in the gradebook that the specified student has access to. This taked into account
	 * externally defined assessments that may have grouping permissions applied.
	 *
	 * This should only be called if you are wanting to view the assignments that a student would see (ie if you ARE a student, or if you
	 * are an instructor using the student review mode)
	 *
	 * @param studentUuid
	 * @return a list of assignments or null if no gradebook
	 */
	public List<Assignment> getGradebookAssignmentsForStudent(final String studentUuid) {

		final Gradebook gradebook = getGradebook(getCurrentSiteId());
		final List<Assignment> assignments = getGradebookAssignments();

		// NOTE: cannot do a role check here as it assumes the current user but this could have been called by an instructor (unless we add
		// a new method to handle this)
		// in any case the role check would just be a confirmation that the user passed in was a student.

		// for each assignment we need to check if it is grouped externally and if the user has access to the group
		final Iterator<Assignment> iter = assignments.iterator();
		while (iter.hasNext()) {
			final Assignment a = iter.next();
			if (a.isExternallyMaintained()) {
				if (this.gradebookExternalAssessmentService.isExternalAssignmentGrouped(gradebook.getUid(), a.getExternalId()) &&
						!this.gradebookExternalAssessmentService.isExternalAssignmentVisible(gradebook.getUid(), a.getExternalId(),
								studentUuid)) {
					iter.remove();
				}
			}
		}
		return assignments;
	}

	/**
	 * Get a list of assignments in the gradebook in the specified site that the current user is allowed to access, sorted by sort order
	 *
	 * @param siteId the siteId
	 * @param sortBy
	 * @return a list of assignments or empty list if none/no gradebook
	 */
	public List<Assignment> getGradebookAssignments(final String siteId, final SortType sortBy) {

		final List<Assignment> assignments = new ArrayList<>();
		final Gradebook gradebook = getGradebook(siteId);
		if (gradebook != null) {
			// applies permissions (both student and TA) and default sort is
			// SORT_BY_SORTING
			assignments.addAll(this.gradebookService.getViewableAssignmentsForCurrentUserAnonAware(gradebook.getUid(), sortBy, true));
		}
		return assignments;
	}

	/**
	 * Get a list of categories in the gradebook in the current site
	 *
	 * @return list of categories or null if no gradebook
	 */
	public List<CategoryDefinition> getGradebookCategories() {
		return getGradebookCategories(getCurrentSiteId());
	}

	/**
	 * Get a list of categories in the gradebook in the specified site
	 *
	 * @param siteId the siteId
	 * @return a list of categories or empty if no gradebook
	 */
	public List<CategoryDefinition> getGradebookCategories(final String siteId) {
		final Gradebook gradebook = getGradebook(siteId);

		List<CategoryDefinition> rval = new ArrayList<>();

		if (gradebook != null && categoriesAreEnabled()) {
			rval = this.gradebookService.getCategoryDefinitions(gradebook.getUid());
		}

		// filter for TAs
		if (gradebook != null && this.getUserRole(siteId) == GbRole.TA) {
			final User user = getCurrentUser();

			// build a list of categoryIds
			final List<Long> allCategoryIds = new ArrayList<>();
			for (final CategoryDefinition cd : rval) {
				allCategoryIds.add(cd.getId());
			}

			if (allCategoryIds.isEmpty()) {
				return Collections.emptyList();
			}

			// get a list of category ids the user can actually view
			final List<Long> viewableCategoryIds = this.gradebookPermissionService
					.getCategoriesForUser(gradebook.getId(), user.getId(), allCategoryIds);

			// remove the ones that the user can't view
			final Iterator<CategoryDefinition> iter = rval.iterator();
			while (iter.hasNext()) {
				final CategoryDefinition categoryDefinition = iter.next();
				if (!viewableCategoryIds.contains(categoryDefinition.getId())) {
					iter.remove();
				}
			}

		}

		// Sort by categoryOrder
		Collections.sort(rval, CategoryDefinition.orderComparator);
		
		return rval;
	}
	
	/**
	 * Retrieve the categories visible to the given student.
	 * 
	 * This should only be called if you are wanting to view the assignments that a student would see (ie if you ARE a student, or if you
	 * are an instructor using the student review mode)
	 * 
	 * @param studentUuid
	 * @return 
	 */
	public List<CategoryDefinition> getGradebookCategoriesForStudent(String studentUuid)
	{	
		// find the categories that this student's visible assignments belong to
		List<Assignment> viewableAssignments = getGradebookAssignmentsForStudent(studentUuid);
		final List<Long> catIds = new ArrayList<>();
		for (Assignment a : viewableAssignments)
		{
			Long catId = a.getCategoryId();
			if (catId != null && !catIds.contains(catId))
			{
				catIds.add(a.getCategoryId());
			}
		}
		
		// get all the categories in the gradebook, use a security advisor in case the current user is the student
		SecurityAdvisor gbAdvisor = (String userId, String function, String reference)
				-> "gradebook.gradeAll".equals(function) ? SecurityAdvice.ALLOWED : SecurityAdvice.PASS;
		securityService.pushAdvisor(gbAdvisor);
		List<CategoryDefinition> catDefs = gradebookService.getCategoryDefinitions(getGradebook().getUid());
		securityService.popAdvisor(gbAdvisor);
		
		// filter out the categories that don't match the categories of the viewable assignments
		return catDefs.stream().filter(def -> catIds.contains(def.getId())).collect(Collectors.toList());
		
	}

	/**
	 * Get a map of course grades for the given users. key = studentUuid, value = course grade
	 *
	 * @param studentUuids uuids for the students
	 * @return the map of course grades for students, or an empty map
	 */
	public Map<String, CourseGrade> getCourseGrades(final List<String> studentUuids) {

		Map<String, CourseGrade> rval = new HashMap<>();

		final Gradebook gradebook = this.getGradebook();
		if (gradebook != null) {
			rval = this.gradebookService.getCourseGradeForStudents(gradebook.getUid(), studentUuids);
		}
		return rval;
	}

	/**
	 * Get the course grade for a student. Safe to call when logged in as a student.
	 *
	 * @param studentUuid
	 * @return coursegrade. May have null fields if the coursegrade has not been released
	 */
	public CourseGrade getCourseGrade(final String studentUuid) {

		GbStopWatch sw = new GbStopWatch("bus");
		final Gradebook gradebook = this.getGradebook();
		final CourseGrade courseGrade = this.gradebookService.getCourseGradeForStudent(gradebook.getUid(), studentUuid);

		// handle the special case in the gradebook service where totalPointsPossible = -1
		if (courseGrade != null && (courseGrade.getTotalPointsPossible() == null || courseGrade.getTotalPointsPossible() == -1)) {
			courseGrade.setTotalPointsPossible(null);
			courseGrade.setPointsEarned(null);
		}

		sw.time("getCourseGrade");
		return courseGrade;
	}

	/**
	 * Save the grade and comment for a student's assignment and do concurrency checking
	 *
	 * @param assignmentId id of the gradebook assignment
	 * @param studentUuid uuid of the user
	 * @param oldGrade old grade, passed in for concurrency checking/ If null, concurrency checking is skipped.
	 * @param newGrade new grade for the assignment/user
	 * @param comment optional comment for the grade. Can be null.
	 *
	 * @return
	 *
	 * 		TODO make the concurrency check a boolean instead of the null oldGrade
	 */
	public GradeSaveResponse saveGrade(final Long assignmentId, final String studentUuid, final String oldGrade,
			final String newGrade, final String comment) {

		final Gradebook gradebook = this.getGradebook();
		if (gradebook == null) {
			return GradeSaveResponse.ERROR;
		}

		// if newGrade is null, no change
		if (newGrade == null) {
			return GradeSaveResponse.NO_CHANGE;
		}

		// get current grade
		final String storedGrade = this.gradebookService.getAssignmentScoreString(gradebook.getUid(), assignmentId,
				studentUuid);

		// get assignment config
		final Assignment assignment = this.getAssignment(assignmentId);
		final Double maxPoints = assignment.getPoints();

		// check what grading mode we are in
		final GbGradingType gradingType = GbGradingType.valueOf(gradebook.getGrade_type());

		// if percentage entry type, reformat the grades, otherwise use points as is
		String newGradeAdjusted = newGrade;
		String oldGradeAdjusted = oldGrade;
		String storedGradeAdjusted = storedGrade;

		// Fix a problem when the grades comes from the old Gradebook API with locale separator, always compare the values using the same
		// separator
		if (StringUtils.isNotBlank(oldGradeAdjusted)) {
			oldGradeAdjusted = oldGradeAdjusted.replace(",".equals(FormattedText.getDecimalSeparator()) ? "." : ",",
					",".equals(FormattedText.getDecimalSeparator()) ? "," : ".");
		}
		if (StringUtils.isNotBlank(storedGradeAdjusted)) {
			storedGradeAdjusted = storedGradeAdjusted.replace(",".equals(FormattedText.getDecimalSeparator()) ? "." : ",",
					",".equals(FormattedText.getDecimalSeparator()) ? "," : ".");
		}

		if (gradingType == GbGradingType.PERCENTAGE) {
			// the passed in grades represents a percentage so the number needs to be adjusted back to points
			Double newGradePercentage = new Double("0.0");

			if(StringUtils.isNotBlank(newGrade)){
				newGradePercentage = FormatHelper.validateDouble(newGrade);
			}

			final Double newGradePointsFromPercentage = (newGradePercentage / 100) * maxPoints;
			newGradeAdjusted = FormatHelper.formatDoubleToDecimal(newGradePointsFromPercentage);

			// only convert if we had a previous value otherwise it will be out of sync
			if (StringUtils.isNotBlank(oldGradeAdjusted)) {
				// To check if our data is out of date, we first compare what we think
				// is the latest saved score against score stored in the database. As the score
				// is stored as points, we must convert this to a percentage. To be sure we're
				// comparing apples with apples, we first determine the number of decimal places
				// on the score, so the converted points-as-percentage is in the expected format.

				final Double oldGradePercentage = FormatHelper.validateDouble(oldGradeAdjusted);
				final Double oldGradePointsFromPercentage = (oldGradePercentage / 100) * maxPoints;

				oldGradeAdjusted = FormatHelper.formatDoubleToMatch(oldGradePointsFromPercentage, storedGradeAdjusted);

				oldGradeAdjusted = oldGradeAdjusted.replace(",".equals(FormattedText.getDecimalSeparator()) ? "." : ",",
					",".equals(FormattedText.getDecimalSeparator()) ? "," : ".");
			}

			// we dont need processing of the stored grade as the service does that when persisting.
		}

		// trim the .0 from the grades if present. UI removes it so lets standardise
		// trim to null so we can better compare against no previous grade being recorded (as it will be null)
		// Note that we also trim newGrade so that don't add the grade if the new grade is blank and there was no grade previously
		storedGradeAdjusted = StringUtils.trimToNull(StringUtils.removeEnd(storedGradeAdjusted, ".0"));
		oldGradeAdjusted = StringUtils.trimToNull(StringUtils.removeEnd(oldGradeAdjusted, ".0"));
		newGradeAdjusted = StringUtils.trimToNull(StringUtils.removeEnd(newGradeAdjusted, ".0"));

		storedGradeAdjusted = StringUtils.trimToNull(StringUtils.removeEnd(storedGradeAdjusted, ",0"));
		oldGradeAdjusted = StringUtils.trimToNull(StringUtils.removeEnd(oldGradeAdjusted, ",0"));
		newGradeAdjusted = StringUtils.trimToNull(StringUtils.removeEnd(newGradeAdjusted, ",0"));

		if (log.isDebugEnabled()) {
			log.debug("storedGradeAdjusted: " + storedGradeAdjusted);
			log.debug("oldGradeAdjusted: " + oldGradeAdjusted);
			log.debug("newGradeAdjusted: " + newGradeAdjusted);
		}

		// no change
		if (StringUtils.equals(storedGradeAdjusted, newGradeAdjusted)) {
			final Double storedGradePoints = FormatHelper.validateDouble(storedGradeAdjusted);
			if (storedGradePoints != null && storedGradePoints.compareTo(maxPoints) > 0) {
				return GradeSaveResponse.OVER_LIMIT;
			} else {
				return GradeSaveResponse.NO_CHANGE;
			}
		}

		// concurrency check, if stored grade != old grade that was passed in,
		// someone else has edited.
		// if oldGrade == null, ignore concurrency check
		if (oldGrade != null && !StringUtils.equals(storedGradeAdjusted, oldGradeAdjusted)) {
			return GradeSaveResponse.CONCURRENT_EDIT;
		}

		GradeSaveResponse rval = null;

		if (StringUtils.isNotBlank(newGradeAdjusted)) {
			final Double newGradePoints = FormatHelper.validateDouble(newGradeAdjusted);

			// if over limit, still save but return the warning
			if (newGradePoints != null && newGradePoints.compareTo(maxPoints) > 0) {
				log.debug("over limit. Max: {}", maxPoints);
				rval = GradeSaveResponse.OVER_LIMIT;
			}
		}

		// save
		try {
			// note, you must pass in the comment or it will be nulled out by the GB service
			// also, must pass in the raw grade as the service does conversions between percentage etc
			this.gradebookService.saveGradeAndCommentForStudent(gradebook.getUid(), assignmentId, studentUuid,
					newGrade, comment);
			if (rval == null) {
				// if we don't have some other warning, it was all OK
				rval = GradeSaveResponse.OK;
			}
		} catch (InvalidGradeException | GradebookNotFoundException | AssessmentNotFoundException e) {
			log.error("An error occurred saving the grade. {}: {}", e.getClass(), e.getMessage());
			rval = GradeSaveResponse.ERROR;
		}
		return rval;
	}

	public GradeSaveResponse saveGradesAndCommentsForImport(final Gradebook gradebook, final Assignment assignment, final List<GradeDefinition> gradeDefList) {
		if (gradebook == null) {
			return GradeSaveResponse.ERROR;
		}

		// save
		try {
			gradebookService.saveGradesAndComments(gradebook.getUid(), assignment.getId(), gradeDefList);
			return GradeSaveResponse.OK;
		} catch (InvalidGradeException | GradebookNotFoundException | AssessmentNotFoundException e) {
			log.error("An error occurred saving the grade. {}: {}", e.getClass(), e.getMessage());
			return GradeSaveResponse.ERROR;
		}
	}

	/**
	 * Build the matrix of assignments, students and grades for all students
	 *
	 * @param assignments list of assignments
	 * @return
	 */
	public List<GbStudentGradeInfo> buildGradeMatrix(final List<Assignment> assignments) throws GbException {
		return this.buildGradeMatrix(assignments, this.getGradeableUsers());
	}

	/**
	 * Build the matrix of assignments and grades for the given users. In general this is just one, as we use it for the instructor view
	 * student summary but could be more for paging etc
	 *
	 * @param assignments list of assignments
	 * @param studentUuids of uuids
	 * @return
	 */
	public List<GbStudentGradeInfo> buildGradeMatrix(final List<Assignment> assignments,
			final List<String> studentUuids) throws GbException {
		return this.buildGradeMatrix(assignments, studentUuids, null);
	}

	/**
	 * Build the matrix of assignments, students and grades for all students, with the specified sortOrder
	 *
	 * @param assignments list of assignments
	 * @param uiSettings the UI settings. Wraps sort order and group filter (sort = null for no sort, filter = null for all groups)
	 * @return
	 */
	public List<GbStudentGradeInfo> buildGradeMatrix(final List<Assignment> assignments,
			final GradebookUiSettings uiSettings) throws GbException {
		return this.buildGradeMatrix(assignments, this.getGradeableUsers(uiSettings.getGroupFilter()), uiSettings);
	}

	/**
	 * Build the matrix of assignments and grades for the given users with the specified sort order
	 *
	 * @param assignments list of assignments
	 * @param studentUuids student uuids
	 * @param uiSettings the settings from the UI that wraps up preferences
	 * @return
	 *
	 * TODO refactor this into a hierarchical method structure
	 */
	public List<GbStudentGradeInfo> buildGradeMatrix(final List<Assignment> assignments,
			final List<String> studentUuids, final GradebookUiSettings uiSettings) throws GbException {

		// TODO move GradebookUISettings to business

		// ------------- Initialization -------------

		// settings could be null depending on constructor so it needs to be corrected
		final GradebookUiSettings settings = (uiSettings != null) ? uiSettings : new GradebookUiSettings();

		final GbStopWatch stopwatch = new GbStopWatch("buildGradeMatrix");
		stopwatch.time("buildGradeMatrix start");

		final Gradebook gradebook = this.getGradebook();
		if (gradebook == null) {
			return null;
		}
		stopwatch.time("getGradebook");

		final boolean categoriesEnabled = categoriesAreEnabled();
		stopwatch.time("categoriesAreEnabled");

		// get current user
		final String currentUserUuid = getCurrentUser().getId();

		// get role for current user
		final GbRole role = this.getUserRole();
		
		Site site = getCurrentSite().orElse(null);

		// ------------- Get Users -------------

		final List<GbUser> gbStudents = getGbUsersForUiSettings(studentUuids, settings);
		stopwatch.time("getGbUsersForUiSettings");

		// ------------- Course Grades -------------

		final Map<String, GbStudentGradeInfo> matrix = new LinkedHashMap<>();

		// Add course grades only if isContextAnonymous matches whether the course grade is pure anonymous
		if (settings.isContextAnonymous() == isCourseGradePureAnonForAllAssignments(assignments))
		{
			putCourseGradesInMatrix(matrix, gbStudents, gradebook, role, isCourseGradeVisible(currentUserUuid), settings);
		}
		stopwatch.time("putCourseGradesInMatrix");

		// ------------- Assignments & Categories -------------

		putAssignmentsAndCategoryItemsInMatrix(matrix, gbStudents, assignments, gradebook, currentUserUuid, role, settings);
		stopwatch.time("putAssignmentAndCategoryItemsInMatrix");

		// ------------- Sorting -------------

		List<GbStudentGradeInfo> items = sortGradeMatrix(matrix, settings);
		stopwatch.time("sortGradeMatrix");

		return items;
	}
	
	public List<GbStudentGradeInfo> buildGradeMatrixForFinalGrades(final GradebookUiSettings uiSettings) throws GbException
	{
		// ------------- Initialization -------------

		// settings could be null depending on constructor so it needs to be corrected
		final GradebookUiSettings settings = (uiSettings != null) ? uiSettings : new GradebookUiSettings();

		final GbStopWatch stopwatch = new GbStopWatch("buildGradeMatrixForFinalGrades");
		stopwatch.time("buildGradeMatrixForFinalGrades start");

		final Gradebook gradebook = getGradebook();
		if (gradebook == null) {
			return Collections.emptyList();
		}
		stopwatch.time("getGradebook");

		// get current user
		final String currentUserUuid = getCurrentUser().getId();

		// get role for current user
		final GbRole role = getUserRole();

		// ------------- Get Users -------------

		final List<GbUser> gbStudents = getGbUsersForUiSettings(getGradeableUsers(settings.getGroupFilter()), settings);
		stopwatch.time("getGbUsersForUiSettings");

		// ------------- Course Grades -------------

		final Map<String, GbStudentGradeInfo> matrix = new LinkedHashMap<>();

		putCourseGradesInMatrix(matrix, gbStudents, gradebook, role, isCourseGradeVisible(currentUserUuid), settings);
		stopwatch.time("putCourseGradesInMatrix");

		// ------------- Sorting -------------

		List<GbStudentGradeInfo> items = sortGradeMatrix(matrix, settings);
		stopwatch.time("sortGradeMatrix");

		return items;
	}

	/**
	 * Build the matrix of assignments and grades for the Export process
	 *
	 * @param assignments list of assignments
	 * @param isContextAnonymous
	 * @param groupFilter
	 * @return
	 */
	public List<GbStudentGradeInfo> buildGradeMatrixForImportExport(final List<Assignment> assignments, boolean isContextAnonymous, GbGroup groupFilter) throws GbException
	{
		// ------------- Initialization -------------
		final GbStopWatch stopwatch = new GbStopWatch("buildGradeMatrixForImportExport");
		stopwatch.time("buildGradeMatrix start");

		final Gradebook gradebook = this.getGradebook();
		if (gradebook == null) {
			return Collections.EMPTY_LIST;
		}
		stopwatch.time("getGradebook");

		// get current user
		final String currentUserUuid = getCurrentUser().getId();

		// get role for current user
		final GbRole role = this.getUserRole();

		final GradebookUiSettings settings = new GradebookUiSettings();
		if (isContextAnonymous)
		{
			settings.setContextAnonymous(true);
			settings.setAnonIdSortOrder(SortDirection.ASCENDING);
		}

		// ------------- Get Users -------------

		final List<String> studentUUIDs = getGradeableUsers(groupFilter);
		final List<GbUser> gbStudents = getGbUsersFilteredIfAnonymous(studentUUIDs, isContextAnonymous);
		stopwatch.time("getGbUsersForUiSettings");

		// ------------- Course Grades -------------
		final Map<String, GbStudentGradeInfo> matrix = new LinkedHashMap<>();
		putCourseGradesInMatrix(matrix, gbStudents, gradebook, role, isCourseGradeVisible(currentUserUuid), settings);
		stopwatch.time("putCourseGradesInMatrix");

		// ------------- Assignments & Categories -------------
		putAssignmentsInMatrixForExport(matrix, gbStudents, assignments, gradebook, currentUserUuid, role);
		stopwatch.time("putAssignmentsInMatrix");

		// ------------- Sorting -------------
		List<GbStudentGradeInfo> items = sortGradeMatrix(matrix, settings);
		stopwatch.time("sortGradeMatrix");

		return items;
	}

	/**
	 * Convenience method; use isCourseGradePureAnonForAllAssignments if you already have access to the assignment list
	 * @return
	 */
	public boolean isCourseGradePureAnon()
	{
		return isCourseGradePureAnonForAllAssignments(getGradebookAssignments());
	}

	/**
	 * Returns true if all items counting toward the course grade are anonymous in the specified list of assignments. If no assignments count toward the course grade, it is not considered pure anonymous.
	 * @param allAssignments for performance purposes; it is expected to be the complete list of assignments in the course (or at least the entire list of assignments that count toward the course grade).
	 * To guarantee accuracy, pass the complete unfiltered list of assignments in the course
	 * @return
	 */
	public boolean isCourseGradePureAnonForAllAssignments(List<Assignment> allAssignments)
	{
		// Return true if there exists at least one anonymous counting assignment and no normal counting assignments
		boolean normalFound = false;
		boolean anonFound = false;
		for (Assignment assignment : allAssignments)
		{
			if (assignment.isCounted())
			{
				if (assignment.isAnon())
				{
					anonFound = true;
				}
				else
				{
					normalFound = true;
					break;
				}
			}
		}

		// Return true iff there is at least one assignment and all assignments are anonymous
		return anonFound && !normalFound;
	}

	/**
	 * Visits all given assignments and populates the uiSettings.getAnonAwareAssignmentIDsForContext and getAnonAwareCategoryIDsForContext lists, representing which assignments and categories we need to display scores for
	 * @param uiSettings used to determine if the context is anonymous
	 * @param allAssignments the list of all assignments in this gradebook
	 */
	public void setupAnonAwareAssignmentIDsAndCategoryIDsForContext(GradebookUiSettings uiSettings, Collection<Assignment> allAssignments)
	{
		Set<Long> assignmentIDsToInclude = new HashSet<>();
		Set<Long> categoryIDsToIncludeScores = new HashSet<>();
		uiSettings.setAnonAwareAssignmentIDsForContext(assignmentIDsToInclude);
		uiSettings.setAnonAwareCategoryIDsForContext(categoryIDsToIncludeScores);
		Set<Long> categoriesContainingNormal = new HashSet<>();
		Set<Long> categoriesContainingAnonymous = new HashSet<>();
		for (Assignment assignment : allAssignments)
		{
			Long categoryId = assignment.getCategoryId();
			if (categoryId != null)
			{
				if (assignment.isAnon())
				{
					categoriesContainingAnonymous.add(categoryId);
				}
				else
				{
					categoriesContainingNormal.add(categoryId);
				}
			}
			if (assignment.isAnon() == uiSettings.isContextAnonymous())
			{
				assignmentIDsToInclude.add(assignment.getId());
			}
		}
		if (uiSettings.isContextAnonymous())
		{
			// show grades for pure anonymous categories; if there's one normal item, it's mixed and should be displayed only in the normal context
			categoryIDsToIncludeScores.addAll(categoriesContainingAnonymous);
			categoryIDsToIncludeScores.removeAll(categoriesContainingNormal);
		}
		else
		{
			// If there are any normal items, we display the category score
			categoryIDsToIncludeScores.addAll(categoriesContainingNormal);
		}
	}

	// If a use case exists, feel free to provide a convenience method to call this passing in getGradebook(), getRole(), isCourseGradeVisible(getCurrentSite()) etc.
	/**
	 * Adds course grade info into the matrix specified in the first param
	 * @param matrix mapping of student uids to GbStudentGradeInfo in which to store course grades
	 * @param gbStudents list of student for whom to retrieve course grades
	 * @param gradebook current site's gradebook
	 * @param role current user's GbRole in the site
	 * @param isCourseGradeVisible whether the current user can see course grades in this site
	 * @param settings GradebookUiSettings instance
	 */
	public void putCourseGradesInMatrix(Map<String, GbStudentGradeInfo> matrix, List<GbUser> gbStudents, Gradebook gradebook, GbRole role, boolean isCourseGradeVisible, GradebookUiSettings settings)
	{
		// Collect studentUuids for getCourseGrades
		List<String> studentUuids = gbStudents.stream().map(GbUser::getUserUuid).collect(Collectors.toList());

		// get course grades
		final Map<String, CourseGrade> courseGrades = getCourseGrades(studentUuids);

		// setup the course grade formatter
		// TODO we want the override except in certain cases. Can we hard code this?
		final CourseGradeFormatter.FormatterConfig config = new CourseGradeFormatter.FormatterConfig();
		config.isCourseGradeVisible = isCourseGradeVisible;
		config.showPoints = settings.getShowPoints();
		config.showOverride = false;
		config.showLetterGrade = false;
		final CourseGradeFormatter courseGradeFormatter = new CourseGradeFormatter(
				gradebook,
				role,
				config);

		for (final GbUser student : gbStudents)
		{
			// create and add the user info
			final GbStudentGradeInfo sg = new GbStudentGradeInfo(student);

			// add the course grade, including the display
			String uid = student.getUserUuid();
			final CourseGrade courseGrade = courseGrades.get(uid);
			final GbCourseGrade gbCourseGrade = new GbCourseGrade(courseGrades.get(uid));
			// OWLTODO: do we still need to set the display string now that CourseGradeColumn does it? File exports?
			gbCourseGrade.setDisplayString(courseGradeFormatter.format(courseGrade));
			sg.setCourseGrade(gbCourseGrade);

			// add to map so we can build on it later
			matrix.put(uid, sg);
		}
	}

	/**
	 * Convenience method
	 * @param matrix
	 * @param assignments
	 */
	public void putAssignmentsAndCategoryItemsInMatrix(Map<String, GbStudentGradeInfo> matrix, List<Assignment> assignments)
	{
		List<GbUser> gbStudents = getGbUsers(getGradeableUsers());
		putAssignmentsAndCategoryItemsInMatrix(matrix, gbStudents, assignments, getGradebook(), getCurrentUser().getId(), getUserRole(), null);
	}

	/**
	 * Builds up the matrix (a map<userUid, GbStudentGradeInfo>) for the specified students / assignments.a
	 * @param matrix output parameter; a map of studentUuids to GbStudentGradeInfo objects which will contain grade data for the specified assignments
	 * @param gbStudents list of GbUsers for whom to retrieve grading data
	 * @param assignments the list of assignments for which to retrieve grading data. Computes category scores associated with these assignments as appropriate
	 * @param gradebook the gradebook containing the assignments, etc.
	 * @param currentUserUuid
	 * @param role the current user's role
	 * @param settings the GradebookUiSettings instance associated with the user's session; used to determine whether the context is anonymous. If null, all grading data will be retrieved without any anonymous aware filtering
	 */
	public void putAssignmentsAndCategoryItemsInMatrix(Map<String, GbStudentGradeInfo> matrix, List<GbUser> gbStudents, List<Assignment> assignments, Gradebook gradebook, String currentUserUuid, GbRole role, GradebookUiSettings settings)
	{
		// Collect list of studentUuids, and ensure the matrix is populated with GbStudentGradeInfo instances for each student
		final List<String> studentUuids = new ArrayList<>(gbStudents.size());
		gbStudents.stream().forEach(gbStudent ->
		{
			String userUuid = gbStudent.getUserUuid();
			studentUuids.add(userUuid);
			GbStudentGradeInfo info = matrix.get(userUuid);
			if (info == null)
			{
				matrix.put(userUuid, new GbStudentGradeInfo(gbStudent));
			}
		});

		// get categories. This call is filtered for TAs as well.
		final List<CategoryDefinition> categories = this.getGradebookCategories();

		// for TA's, build a lookup map of visible categoryIds so we can filter
		// the assignment list to not fetch grades
		// for assignments we don't have category level access to.
		// for everyone else this will just be an empty list that is unused
		final List<Long> categoryIds = new ArrayList<>();

		if (role == GbRole.TA) {
			for (final CategoryDefinition category : categories) {
				categoryIds.add(category.getId());
			}
		}

		// this holds a map of categoryId and the list of assignment ids in each
		// we build this whilst iterating below to save further iterations when
		// building the category list
		final Map<Long, Set<Long>> categoryAssignments = new TreeMap<>();

		// Determine only which assignments / categories we're interested in wrt isContextAnonymous
		Set<Long> assignmentIDsToInclude = new HashSet<>();
		Set<Long> categoryIDsToIncludeScores = new HashSet<>();
		if (settings != null)
		{
			assignmentIDsToInclude = settings.getAnonAwareAssignmentIDsForContext();
			categoryIDsToIncludeScores = settings.getAnonAwareCategoryIDsForContext();
		}

		// iterate over assignments and get the grades for each
		// note, the returned list only includes entries where there is a grade
		// for the user
		// we also build the category lookup map here
		for (final Assignment assignment : assignments) {

			final Long categoryId = assignment.getCategoryId();
			final Long assignmentId = assignment.getId();

			if (settings != null && !categoryIDsToIncludeScores.contains(categoryId) && !assignmentIDsToInclude.contains(assignmentId))
			{
				// we don't need any info from this assignment; skip
				continue;
			}

			// TA permission check. If there are categories and they don't have
			// access to this one, skip it
			if (role == GbRole.TA) {
				log.debug("TA processing category: {}", categoryId);

				if (!categoryIds.isEmpty() && categoryId != null && !categoryIds.contains(categoryId)) {
					continue;
				}

				// TA stub out. So that we can support 'per grade' permissions for a
				// TA, we need a stub record for every student
				// This is because getGradesForStudentsForItem only returns records
				// where there is a grade (even if blank)
				// So this iteration for TAs allows the matrix to be fully
				// populated.
				// This is later updated to be a real grade enry if there is one.
				for (final GbUser student : gbStudents) {
					final GbStudentGradeInfo sg = matrix.get(student.getUserUuid());
					sg.addGrade(assignment.getId(), new GbGradeInfo(null));
				}
			}

			// build the category map (if assignment is categorised)
			if (categoryId != null) {
				Set<Long> values;
				if (categoryAssignments.containsKey(categoryId)) {
					values = categoryAssignments.get(categoryId);
					values.add(assignmentId);
				}
				else {
					values = new HashSet<>();
					values.add(assignmentId);
				}
				categoryAssignments.put(categoryId, values);
			}

			// get grades
			final List<GradeDefinition> defs = this.gradebookService.getGradesForStudentsForItem(gradebook.getUid(),
					assignment.getId(), studentUuids);

			// iterate the definitions returned and update the record for each
			// student with the grades
			for (final GradeDefinition def : defs) {
				final GbStudentGradeInfo sg = matrix.get(def.getStudentUid());

				if (sg == null) {
					log.warn("No matrix entry seeded for: {}. This user may have been removed from the site", def.getStudentUid());
				}
				else {
					// this will overwrite the stub entry for the TA matrix if
					// need be
					sg.addGrade(assignment.getId(), new GbGradeInfo(def));
				}
			}
		}

		// build category columns
		for (final CategoryDefinition category : categories) {

			Long categoryId = category.getId();
			if (settings != null && !categoryIDsToIncludeScores.contains(categoryId))
			{
				// Nothing of interest in this category; skip
				continue;
			}

			// use the category mappings for faster lookup of the assignmentIds
			// and grades in the category
			final Set<Long> categoryAssignmentIds = categoryAssignments.get(categoryId);

			// if there are no assignments in the category (ie its a new category) this will be null, so skip
			if (categoryAssignmentIds != null) {
				for (final GbUser student : gbStudents) {
					final GbStudentGradeInfo sg = matrix.get(student.getUserUuid());
					
					// get grades
					final Map<Long, GbGradeInfo> grades = sg.getGrades();

					// build map of just the grades we want
					final Map<Long, String> gradeMap = new HashMap<>();
					for (final Long assignmentId : categoryAssignmentIds) {
						final GbGradeInfo gradeInfo = grades.get(assignmentId);
						if (gradeInfo != null) {
							gradeMap.put(assignmentId, gradeInfo.getGrade());
						}
					}

					Double score = null;
					final Optional<CategoryScoreData> categoryScore = gradebookService.calculateCategoryScore(gradebook,
							student.getUserUuid(), category, category.getAssignmentList(), gradeMap);
					if (categoryScore.isPresent())
					{
						CategoryScoreData data = categoryScore.get();
						for (Long item : gradeMap.keySet())
						{
							if (!data.includedItems.contains(item))
							{
								grades.get(item).setDroppedFromCategoryScore(true);
							}
						}
						score = data.score;
					}
					
					// add to GbStudentGradeInfo
					// OWLTODO: allowing score to be null to preserve original logic
					// however, this just gets put into a map as a null so probably could
					// just omit it
					sg.addCategoryAverage(category.getId(), score);

					// TODO the TA permission check could reuse this iteration... check performance.
				}
			}
		}

		// Remove grades for assignments that are out of context (they would exist in the case of mixed categories)
		if (settings != null)
		{
			for (GbStudentGradeInfo sg : matrix.values())
			{
				sg.getGrades().keySet().retainAll(assignmentIDsToInclude);
			}
		}

		// for a TA, apply the permissions to each grade item to see if we can render it
		// the list of students, assignments and grades is already filtered to those that can be viewed
		// so we are only concerned with the gradeable permission
		if (role == GbRole.TA) 
		{
			// get permissions
			final List<PermissionDefinition> permissions = getPermissionsForUser(currentUserUuid);

			log.debug("All permissions: {}", permissions.size());

			// only need to process this if some are defined
			// again only concerned with grade permission, so parse the list to
			// remove those that aren't GRADE
			permissions.removeIf(permission -> !StringUtils.equalsIgnoreCase(GraderPermission.GRADE.toString(), permission.getFunction()));

			log.debug("Filtered permissions: {}", permissions.size());

			// if we still have permissions, they will be of type grade, so we
			// need to enrich the students grades
			if (!permissions.isEmpty()) 
			{
				// first need a lookup map of assignment id to category, so we
				// can link up permissions by category
				final Map<Long, Long> assignmentCategoryMap = new HashMap<>();
				for (final Assignment assignment : assignments) 
				{
					assignmentCategoryMap.put(assignment.getId(), assignment.getCategoryId());
				}

				// get the group membership for the students
				final Map<String, List<String>> groupMembershipsMap = getGroupMemberships();

				// for every student
				for (final GbUser student : gbStudents)
				{
					log.debug("Processing student: {}", student.getEid());

					final GbStudentGradeInfo sg = matrix.get(student.getUserUuid());

					// get their assignment/grade list
					final Map<Long, GbGradeInfo> gradeMap = sg.getGrades();

					// for every assignment that has a grade
					for (final Map.Entry<Long, GbGradeInfo> entry : gradeMap.entrySet())
					{
						// categoryId
						final Long gradeCategoryId = assignmentCategoryMap.get(entry.getKey());

						log.debug("Grade: {}", entry.getValue());

						// iterate the permissions
						// if category, compare the category,
						// then check the group and find the user in the group
						// if all ok, mark it as GRADEABLE

						boolean gradeable = false;

						for (final PermissionDefinition permission : permissions) {
							// we know they are all GRADE so no need to check here

							boolean categoryOk = false;
							boolean groupOk = false;

							final Long permissionCategoryId = permission.getCategoryId();
							final String permissionGroupReference = permission.getGroupReference();

							log.debug("permissionCategoryId: {}", permissionCategoryId);
							log.debug("permissionGroupReference: {}", permissionGroupReference);

							// if permissions category is null (can grade all
							// categories) or they match (can grade this
							// category)
							if (permissionCategoryId == null || permissionCategoryId.equals(gradeCategoryId)) {
								categoryOk = true;
								log.debug("Category check passed");
							}

							// if group reference is null (can grade all groups)
							// or group membership contains student (can grade
							// this group)
							if (StringUtils.isBlank(permissionGroupReference)) {
								groupOk = true;
								log.debug("Group check passed #1");
							} else {
								final List<String> groupMembers = groupMembershipsMap.get(permissionGroupReference);
								log.debug("groupMembers: {}", groupMembers);

								if (groupMembers != null && groupMembers.contains(student.getUserUuid())) {
									groupOk = true;
									log.debug("Group check passed #2");
								}
							}

							if (categoryOk && groupOk) {
								gradeable = true;
								break;
							}
						}

						// set the gradeable flag on this grade instance
						final GbGradeInfo gradeInfo = entry.getValue();
						gradeInfo.setGradeable(gradeable);
					}
				}
			}
		}
	}

	/**
	 * Builds up the matrix (a map<userUid, GbStudentGradeInfo>) for the specified students / assignments.a
	 * @param matrix output parameter; a map of studentUuids to GbStudentGradeInfo objects which will contain grade data for the specified assignments
	 * @param gbStudents list of GbUsers for whom to retrieve grading data
	 * @param assignments the list of assignments for which to retrieve grading data. Computes category scores associated with these assignments as appropriate
	 * @param gradebook the gradebook containing the assignments, etc.
	 * @param currentUserUuid
	 * @param role the current user's role
	 */
	public void putAssignmentsInMatrixForExport(Map<String, GbStudentGradeInfo> matrix, List<GbUser> gbStudents, List<Assignment> assignments,
													Gradebook gradebook, String currentUserUuid, GbRole role)
	{
		// Collect list of studentUuids, and ensure the matrix is populated with GbStudentGradeInfo instances for each student
		final List<String> studentUuids = new ArrayList<>(gbStudents.size());
		gbStudents.stream().forEach(gbStudent ->
		{
			String userUuid = gbStudent.getUserUuid();
			studentUuids.add(userUuid);
			GbStudentGradeInfo info = matrix.get(userUuid);
			if (info == null)
			{
				matrix.put(userUuid, new GbStudentGradeInfo(gbStudent));
			}
		});

		// iterate over assignments and get the grades for each
		// note, the returned list only includes entries where there is a grade
		// for the user
		// we also build the category lookup map here
		for (final Assignment assignment : assignments) {

			// get grades
			final List<GradeDefinition> defs = this.gradebookService.getGradesForStudentsForItem(gradebook.getUid(), assignment.getId(), studentUuids);

			// iterate the definitions returned and update the record for each
			// student with the grades
			for (final GradeDefinition def : defs) {
				final GbStudentGradeInfo sg = matrix.get(def.getStudentUid());

				if (sg == null) {
					log.warn("No matrix entry seeded for: {}. This user may have been removed from the site", def.getStudentUid());
				} else {
					// this will overwrite the stub entry for the TA matrix if
					// need be
					sg.addGrade(assignment.getId(), new GbGradeInfo(def));
				}
			}
		}

		// for a TA, apply the permissions to each grade item to see if we can export it
		// the list of students, assignments and grades is already filtered to those that can be viewed
		// so we are only concerned with the gradeable permission
		if (role == GbRole.TA) {

			// get permissions
			final List<PermissionDefinition> permissions = getPermissionsForUser(currentUserUuid);

			log.debug("All permissions: {}", permissions.size());

			// only need to process this if some are defined
			// again only concerned with grade permission, so parse the list to
			// remove those that aren't GRADE
			permissions.removeIf(permission -> !StringUtils.equalsIgnoreCase(GraderPermission.GRADE.toString(), permission.getFunction()));

			log.debug("Filtered permissions: {}", permissions.size());

			// if we still have permissions, they will be of type grade, so we
			// need to enrich the students grades
			if (!permissions.isEmpty()) {

				// first need a lookup map of assignment id to category, so we
				// can link up permissions by category
				final Map<Long, Long> assignmentCategoryMap = new HashMap<>();
				for (final Assignment assignment : assignments) {
					assignmentCategoryMap.put(assignment.getId(), assignment.getCategoryId());
				}

				// get the group membership for the students
				final Map<String, List<String>> groupMembershipsMap = getGroupMemberships();

				// for every student
				for (final GbUser student : gbStudents) {
					log.debug("Processing student: {}", student.getEid());

					final GbStudentGradeInfo sg = matrix.get(student.getUserUuid());

					// get their assignment/grade list
					final Map<Long, GbGradeInfo> gradeMap = sg.getGrades();

					// for every assignment that has a grade
					for (final Map.Entry<Long, GbGradeInfo> entry : gradeMap.entrySet()) {
						// categoryId
						final Long gradeCategoryId = assignmentCategoryMap.get(entry.getKey());

						log.debug("Grade: {}", entry.getValue());

						// iterate the permissions
						// if category, compare the category,
						// then check the group and find the user in the group
						// if all ok, mark it as GRADEABLE

						boolean gradeable = false;

						for (final PermissionDefinition permission : permissions) {
							// we know they are all GRADE so no need to check here

							boolean categoryOk = false;
							boolean groupOk = false;

							final Long permissionCategoryId = permission.getCategoryId();
							final String permissionGroupReference = permission.getGroupReference();

							log.debug("permissionCategoryId: {}", permissionCategoryId);
							log.debug("permissionGroupReference: {}", permissionGroupReference);

							// if permissions category is null (can grade all
							// categories) or they match (can grade this
							// category)
							if (permissionCategoryId == null || permissionCategoryId.equals(gradeCategoryId)) {
								categoryOk = true;
								log.debug("Category check passed");
							}

							// if group reference is null (can grade all groups)
							// or group membership contains student (can grade
							// this group)
							if (StringUtils.isBlank(permissionGroupReference)) {
								groupOk = true;
								log.debug("Group check passed #1");
							} else {
								final List<String> groupMembers = groupMembershipsMap.get(permissionGroupReference);
								log.debug("groupMembers: {}", groupMembers);

								if (groupMembers != null && groupMembers.contains(student.getUserUuid())) {
									groupOk = true;
									log.debug("Group check passed #2");
								}
							}

							if (categoryOk && groupOk) {
								gradeable = true;
								break;
							}
						}

						// set the gradeable flag on this grade instance
						final GbGradeInfo gradeInfo = entry.getValue();
						gradeInfo.setGradeable(gradeable);
					}
				}
			}
		}
	}

	/**
	 * Takes the value set of the matrix (a map<studentUuid, GbStudentGradeInfo>), and sorts the value set appropriately wrt the GradebookUiSettings
	 * @param matrix
	 * @param settings
	 * @return the valueSet of the matrix as an appropriately sorted List
	 */
	public List<GbStudentGradeInfo> sortGradeMatrix(Map<String, GbStudentGradeInfo> matrix, GradebookUiSettings settings)
	{
		// Get the matrix as a list of GbStudentGradeInfo
		final List<GbStudentGradeInfo> items = new ArrayList<>(matrix.values());

		// sort the matrix based on the supplied assignment sort order (if any)
		if (settings.getAssignmentSortOrder() != null)
		{
			Comparator<GbStudentGradeInfo> comparator = new AssignmentGradeComparator(settings.getAssignmentSortOrder().getAssignmentId());

			final SortDirection direction = settings.getAssignmentSortOrder().getDirection();

			// reverse if required
			if (direction == SortDirection.DESCENDING) {
				comparator = Collections.reverseOrder(comparator);
			}

			// sort
			Collections.sort(items, comparator);
		}

		// sort the matrix based on the supplied category sort order (if any)
		if (settings.getCategorySortOrder() != null)
		{
			Comparator comparator = new CategorySubtotalComparator(settings.getCategorySortOrder().getCategoryId());

			final SortDirection direction = settings.getCategorySortOrder().getDirection();

			// reverse if required
			if (direction == SortDirection.DESCENDING) {
				comparator = Collections.reverseOrder(comparator);
			}

			// sort
			Collections.sort(items, comparator);
		}

		if (settings.getCourseGradeSortOrder() != null)
		{
			Comparator comp = new CourseGradeComparator(getGradebookSettings());

			// reverse if required
			if (settings.getCourseGradeSortOrder() == SortDirection.DESCENDING) {
				comp = Collections.reverseOrder(comp);
			}

			// sort
			Collections.sort(items, comp);
		}

		if (settings.getFinalGradeSortOrder() != null)
		{
			Comparator<GbStudentGradeInfo> comp = new FinalGradeComparator();

			// reverse if required
			if (settings.getFinalGradeSortOrder() == SortDirection.DESCENDING) {
				comp = Collections.reverseOrder(comp);
			}

			// sort
			Collections.sort(items, comp);
		}
		
		if (settings.getCalculatedSortOrder() != null)
		{
			Comparator<GbStudentGradeInfo> comp = new CalculatedCourseGradeComparator();

			// reverse if required
			if (settings.getCalculatedSortOrder() == SortDirection.DESCENDING) {
				comp = Collections.reverseOrder(comp);
			}

			// sort
			Collections.sort(items, comp);
		}

		if (settings.getAnonIdSortOrder() != null) {
			GbGroup group = settings.getGroupFilter();
			String section = group == null ? null : group.getProviderId();
			Comparator<GbStudentGradeInfo> comp = new AnonIDComparator(section);

			if (settings.getAnonIdSortOrder() == SortDirection.DESCENDING) {
				comp = Collections.reverseOrder(comp);
			}

			// sort
			Collections.sort(items, comp);
		}

		return items;
	}

	/**
	 * Get a list of sections and groups in a site
	 *
	 * @return
	 */
	public List<GbGroup> getSiteSectionsAndGroups() {
		final String siteId = getCurrentSiteId();

		final List<GbGroup> rval = new ArrayList<>();

		// get groups (handles both groups and sections)
		try {
			final Site site = this.siteService.getSite(siteId);
			final Collection<Group> groups = site.getGroups();

			for (final Group group : groups) {
				//rval.add(new GbGroup(group.getId(), group.getTitle(), group.getReference(), GbGroup.Type.GROUP, group.getProviderGroupId()));
				rval.add(GbGroup.fromGroup(group));
			}

		} catch (final IdUnusedException e) {
			// essentially ignore and use what we have
			log.error("Error retrieving groups", e);
		}

		// if user is a TA, get the groups they can see and filter the GbGroup
		// list to keep just those
		if (this.getUserRole(siteId) == GbRole.TA) {
			final Gradebook gradebook = this.getGradebook(siteId);
			final User user = getCurrentUser();

			// need list of all groups as REFERENCES (not ids)
			final List<String> allGroupIds = new ArrayList<>();
			for (final GbGroup group : rval) {
				allGroupIds.add(group.getReference());
			}

			// get the ones the TA can actually view
			// note that if a group is empty, it will not be included.
			final List<String> viewableGroupIds = this.gradebookPermissionService
					.getViewableGroupsForUser(gradebook.getId(), user.getId(), allGroupIds);

			// remove the ones that the user can't view
			final Iterator<GbGroup> iter = rval.iterator();
			while (iter.hasNext()) {
				final GbGroup group = iter.next();
				if (!viewableGroupIds.contains(group.getReference())) {
					iter.remove();
				}
			}

		}

		Collections.sort(rval);

		return rval;
	}
	
	public List<GbGroup> getSiteSections()
	{
		List<GbGroup> providedGroups = getSiteSectionsAndGroups().stream().filter(GbGroup::isSection).collect(Collectors.toList());
		
		Set<String> secEids = providedGroups.stream().map(GbGroup::getProviderId).collect(Collectors.toSet());
		List<String> secTitles = new ArrayList<>(secEids.size());
		List<String> realSecEids = new ArrayList<>(secEids.size());
		
		for (String eid : secEids)
		{
			try
			{
				Section sec = courseManagementService.getSection(eid);
				realSecEids.add(eid);
				secTitles.add(sec.getTitle());
			}
			catch (IdNotFoundException e)
			{
				// crosslist or invalid eid, ignore and continue
			}
		}
		
		// filter out the non-section provided groups based on a simple title comparison
		return providedGroups.stream().filter(g -> realSecEids.contains(g.getProviderId()) && secTitles.contains(g.getTitle())).collect(Collectors.toList());
	}

	/**
	 * Helper to get siteid. This will ONLY work in a portal site context, it will return null otherwise (ie via an entityprovider).
	 *
	 * @return
	 */
	public String getCurrentSiteId() {
		try {
			return this.toolManager.getCurrentPlacement().getContext();
		} catch (final Exception e) {
			return null;
		}
	}
	
	public Optional<Site> getCurrentSite()
	{
		String siteId = getCurrentSiteId();
		if (siteId != null)
		{
			try
			{
				return Optional.of(siteService.getSite(siteId));
			}
			catch (IdUnusedException e)
			{
				// do nothing
			}
		}
		
		return Optional.empty();
	}

	/**
	 * Helper to get user
	 *
	 * @return
	 */
	public User getCurrentUser() {
		return this.userDirectoryService.getCurrentUser();
	}

	/**
	 * Determine if the current user is an admin user.
	 *
	 * @return true if the current user is admin, false otherwise.
	 */
	public boolean isSuperUser() {
		return securityService.isSuperUser();
	}

	/**
	 * Add a new assignment definition to the gradebook
	 *
	 * @param assignment
	 * @return id of the newly created assignment or null if there were any errors
	 */
	public Long addAssignment(final Assignment assignment) {

		final Gradebook gradebook = getGradebook();

		if (gradebook != null) {
			final String gradebookId = gradebook.getUid();

			final Long assignmentId = this.gradebookService.addAssignment(gradebookId, assignment);

			// Force the assignment to sit at the end of the list
			if (assignment.getSortOrder() == null) {
				final List<Assignment> allAssignments = this.gradebookService.getAssignmentsAnonAware(gradebookId, true);
				int nextSortOrder = allAssignments.size();
				for (final Assignment anotherAssignment : allAssignments) {
					if (anotherAssignment.getSortOrder() != null && anotherAssignment.getSortOrder() >= nextSortOrder) {
						nextSortOrder = anotherAssignment.getSortOrder() + 1;
					}
				}
				updateAssignmentOrder(assignmentId, nextSortOrder);
			}

			// also update the categorized order
			updateAssignmentCategorizedOrder(gradebook.getUid(), assignment.getCategoryId(), assignmentId,
					Integer.MAX_VALUE);

			return assignmentId;

			// TODO wrap this so we can catch any runtime exceptions
		}
		return null;
	}

	/**
	 * Update the order of an assignment for the current site.
	 *
	 * @param assignmentId
	 * @param order
	 */
	public void updateAssignmentOrder(final long assignmentId, final int order) {

		final String siteId = getCurrentSiteId();
		this.updateAssignmentOrder(siteId, assignmentId, order);
	}

	/**
	 * Update the order of an assignment. If calling outside of GBNG, use this method as you can provide the site id.
	 *
	 * @param siteId the siteId
	 * @param assignmentId the assignment we are reordering
	 * @param order the new order
	 */
	public void updateAssignmentOrder(final String siteId, final long assignmentId, final int order) {

		final Gradebook gradebook = this.getGradebook(siteId);
		this.gradebookService.updateAssignmentOrder(gradebook.getUid(), assignmentId, order);
	}

	/**
	 * Update the categorized order of an assignment.
	 *
	 * @param assignmentId the assignment we are reordering
	 * @param order the new order
	 * @throws JAXBException
	 * @throws IdUnusedException
	 * @throws PermissionException
	 */
	public void updateAssignmentCategorizedOrder(final long assignmentId, final int order)
			throws JAXBException, IdUnusedException, PermissionException {
		final String siteId = getCurrentSiteId();
		updateAssignmentCategorizedOrder(siteId, assignmentId, order);
	}

	/**
	 * Update the categorized order of an assignment.
	 *
	 * @param siteId the site's id
	 * @param assignmentId the assignment we are reordering
	 * @param order the new order
	 * @throws IdUnusedException
	 * @throws PermissionException
	 */
	public void updateAssignmentCategorizedOrder(final String siteId, final long assignmentId, final int order)
			throws IdUnusedException, PermissionException {

		// validate site
		try {
			this.siteService.getSite(siteId);
		} catch (final IdUnusedException e) {
			// TODO Auto-generated catch block
			log.debug(e.getMessage());
			return;
		}

		final Gradebook gradebook = (Gradebook) this.gradebookService.getGradebook(siteId);

		if (gradebook == null) {
			log.error(String.format("Gradebook not in site %s", siteId));
			return;
		}

		final Assignment assignmentToMove = this.gradebookService.getAssignment(gradebook.getUid(), assignmentId);

		if (assignmentToMove == null) {
			// TODO Handle assignment not in gradebook
			log.error(String.format("Assignment %d not in site %s", assignmentId, siteId));
			return;
		}

		updateAssignmentCategorizedOrder(gradebook.getUid(), assignmentToMove.getCategoryId(), assignmentToMove.getId(),
				order);
	}

	/**
	 * Update the categorized order of an assignment via the gradebook service.
	 *
	 * @param gradebookId the gradebook's id
	 * @param categoryId the id for the cataegory in which we are reordering
	 * @param assignmentId the assignment we are reordering
	 * @param order the new order
	 */
	private void updateAssignmentCategorizedOrder(final String gradebookId, final Long categoryId,
			final Long assignmentId, final int order) {
		this.gradebookService.updateAssignmentCategorizedOrder(gradebookId, categoryId, assignmentId, order);
	}

	/**
	 * Comparator class for sorting a list of users by last name Secondary sort is on first name to maintain consistent order for those with
	 * the same last name
	 */
	class LastNameComparator implements Comparator<User> {
		@Override
		public int compare(final User u1, final User u2) {
			return new CompareToBuilder().append(u1.getLastName(), u2.getLastName())
					.append(u1.getFirstName(), u2.getFirstName()).toComparison();
		}
	}

	/**
	 * Comparator class for sorting a list of users by first name Secondary sort is on last name to maintain consistent order for those with
	 * the same first name
	 */
	class FirstNameComparator implements Comparator<User> {
		@Override
		public int compare(final User u1, final User u2) {
			return new CompareToBuilder().append(u1.getFirstName(), u2.getFirstName())
					.append(u1.getLastName(), u2.getLastName()).toComparison();
		}
	}
	
	/**
	 * Comparator class for sorting a list of users by student number
	 */
	@RequiredArgsConstructor
	class StudentNumberComparator implements Comparator<User>
	{
		private final Site site;
		private final boolean preAuth;
		
		@Override
		public int compare(final User u1, final User u2)
		{
			if (!preAuth)
			{
				return 0;
			}
			String stunum1 = getStudentNumberPreAuthorized(u1, site);
			String stunum2 = getStudentNumberPreAuthorized(u2, site);
			return stunum1.compareTo(stunum2);
		}
	}

	/**
	 * Comparator class for sorting a list of users by anonymous grading IDs
	 */
	class AnonIDComparator implements Comparator<GbStudentGradeInfo>
	{
		private final String sectionId;
		public AnonIDComparator(String sectionId)
		{
			this.sectionId = sectionId;
		}

		@Override
		public int compare(final GbStudentGradeInfo s1, final GbStudentGradeInfo s2)
		{
			String anonId1 = StringUtils.trimToEmpty(s1.getStudent().getAnonId(sectionId));
			String anonId2 = StringUtils.trimToEmpty(s2.getStudent().getAnonId(sectionId));
			return anonId1.compareTo(anonId2);
		}
	}

	/**
	 * Get a list of edit events for this gradebook. Excludes any events for the current user
	 *
	 * @param gradebookUid the gradebook that we are interested in
	 * @param since the time to check for changes from
	 * @return
	 */
	public List<GbGradeCell> getEditingNotifications(final String gradebookUid, final Date since) {

		final User currentUser = getCurrentUser();

		final List<GbGradeCell> rval = new ArrayList<>();

		final List<Assignment> assignments = this.gradebookService.getViewableAssignmentsForCurrentUserAnonAware(gradebookUid,
				SortType.SORT_BY_SORTING, true);
		final List<Long> assignmentIds = assignments.stream().map(a -> a.getId()).collect(Collectors.toList());
		if (CollectionUtils.isEmpty(assignmentIds)) {
			return rval;
		}

		// keep a hash of all users so we don't have to hit the service each time
		final Map<String, GbUser> users = new HashMap<>();

		// filter out any events made by the current user
		final List<GradingEvent> events = this.gradebookService.getGradingEvents(assignmentIds, since);
		for (final GradingEvent event : events) {
			if (!event.getGraderId().equals(currentUser.getId())) {
				// update cache (if required)
				users.putIfAbsent(event.getGraderId(), getUser(event.getGraderId()));

				// pull user from the cache
				final GbUser updatedBy = users.get(event.getGraderId());
				rval.add(
						new GbGradeCell(
								event.getStudentId(),
								event.getGradableObject().getId(),
								updatedBy.getDisplayName()));
			}
		}

		return rval;
	}

	/**
	 * Get an Assignment in the current site given the assignment id
	 *
	 * @param assignmentId
	 * @return
	 */
	public Assignment getAssignment(final long assignmentId) {
		return this.getAssignment(getCurrentSiteId(), assignmentId);
	}

	/**
	 * Get an Assignment in the specified site given the assignment id
	 *
	 * @param siteId
	 * @param assignmentId
	 * @return
	 */
	public Assignment getAssignment(final String siteId, final long assignmentId) {
		final Gradebook gradebook = getGradebook(siteId);
		if (gradebook != null) {
			return this.gradebookService.getAssignment(gradebook.getUid(), assignmentId);
		}
		return null;
	}

	/**
	 * Get an Assignment in the current site given the assignment name
	 * This should be avoided where possible but is required for the import process to allow modification of assignment point values
	 *
	 * @param assignmentName
	 * @return
	 */
	public Assignment getAssignment(final String assignmentName) {
		return this.getAssignment(getCurrentSiteId(), assignmentName);
	}

	/**
	 * Get an Assignment in the specified site given the assignment name
	 * This should be avoided where possible but is required for the import process to allow modification of assignment point values
	 *
	 * @param siteId
	 * @param assignmentName
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public Assignment getAssignment(final String siteId, final String assignmentName) {
		final Gradebook gradebook = getGradebook(siteId);
		if (gradebook != null) {
			return this.gradebookService.getAssignment(gradebook.getUid(), assignmentName);
		}
		return null;
	}

	/**
	 * Get the sort order of an assignment. If the assignment has a sort order, use that. Otherwise we determine the order of the assignment
	 * in the list of assignments
	 *
	 * This means that we can always determine the most current sort order for an assignment, even if the list has never been sorted.
	 *
	 *
	 * @param assignmentId
	 * @return sort order if set, or calculated, or -1 if cannot determine at all.
	 */
	public int getAssignmentSortOrder(final long assignmentId) {
		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		if (gradebook != null) {
			final Assignment assignment = this.gradebookService.getAssignment(gradebook.getUid(), assignmentId);

			// if the assignment has a sort order, return that
			if (assignment.getSortOrder() != null) {
				return assignment.getSortOrder();
			}

			// otherwise we need to determine the assignment sort order within
			// the list of assignments
			final List<Assignment> assignments = this.getGradebookAssignments(siteId);

			for (int i = 0; i < assignments.size(); i++) {
				final Assignment a = assignments.get(i);
				if (assignmentId == a.getId() && a.getSortOrder() != null) {
					return a.getSortOrder();
				}
			}
		}

		return -1;
	}

	/**
	 * Update the details of an assignment
	 *
	 * @param assignment
	 * @return
	 */
	public boolean updateAssignment(final Assignment assignment) {
		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		// need the original name as the service needs that as the key...
		final Assignment original = this.getAssignment(assignment.getId());

		try {
			this.gradebookService.updateAssignment(gradebook.getUid(), original.getId(), assignment);
			if (original.getCategoryId() != null && assignment.getCategoryId() != null
					&& original.getCategoryId().longValue() != assignment.getCategoryId().longValue()) {
				updateAssignmentCategorizedOrder(gradebook.getUid(), assignment.getCategoryId(), assignment.getId(),
						Integer.MAX_VALUE);
			}
			return true;
		} catch (final Exception e) {
			log.error("An error occurred updating the assignment", e);
		}

		return false;
	}

	/**
	 * Updates ungraded items in the given assignment with the given grade
	 *
	 * @param assignmentId
	 * @param grade
	 * @return
	 */
	public boolean updateUngradedItems(final long assignmentId, final double grade) {
		return updateUngradedItems(assignmentId, grade, null);
	}

	/**
	 * Updates ungraded items in the given assignment for students within a particular group and with the given grade
	 *
	 * @param assignmentId
	 * @param grade
	 * @param group
	 * @return
	 */
	public boolean updateUngradedItems(final long assignmentId, final double grade, final GbGroup group) {
		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		// get students
		final List<String> studentUuids = (group == null) ? this.getGradeableUsers() : this.getGradeableUsers(group);

		// get grades (only returns those where there is a grade)
		final List<GradeDefinition> defs = this.gradebookService.getGradesForStudentsForItem(gradebook.getUid(),
				assignmentId, studentUuids);

		// iterate and trim the studentUuids list down to those that don't have
		// grades
		for (final GradeDefinition def : defs) {

			// don't remove those where the grades are blank, they need to be
			// updated too
			if (StringUtils.isNotBlank(def.getGrade())) {
				studentUuids.remove(def.getStudentUid());
			}
		}

		if (studentUuids.isEmpty()) {
			log.debug("Setting default grade. No students are ungraded.");
		}

		try {
			// for each student remaining, add the grade
			for (final String studentUuid : studentUuids) {

				log.debug("Setting default grade. Values of assignmentId: {}, studentUuid: {}, grade: {}", assignmentId, studentUuid, grade);

				// TODO if this is slow doing it one by one, might be able to
				// batch it
				this.gradebookService.saveGradeAndCommentForStudent(gradebook.getUid(), assignmentId, studentUuid, String.valueOf(grade), null);
			}
			return true;
		} catch (final Exception e) {
			log.error("An error occurred updating the assignment", e);
		}

		return false;
	}

	/**
	 * Get the grade log for the given student and assignment
	 *
	 * @param studentUuid
	 * @param assignmentId
	 * @return
	 */
	public List<GbGradeLog> getGradeLog(final String studentUuid, final long assignmentId) {
		final List<GradingEvent> gradingEvents = this.gradebookService.getGradingEvents(studentUuid, assignmentId);

		final List<GbGradeLog> rval = new ArrayList<>();
		for (final GradingEvent ge : gradingEvents) {
			rval.add(new GbGradeLog(ge));
		}

		Collections.reverse(rval);

		return rval;
	}

	/**
	 * Get the user given a uuid. Acquires student number but not anon id.
	 * Better to use getUserPreAuthorized() if possible for performance reasons
	 *
	 * @param userUuid
	 * @return GbUser or null if cannot be found
	 */
	public GbUser getUser(final String userUuid) {
		try {
			final User u = this.userDirectoryService.getUser(userUuid);
			return GbUser.fromUserAcquiringStudentNumber(u, this);
		} catch (final UserNotDefinedException e) {
			return null;
		}
	}
	
	/**
	 * Gets the user given a uuid. Acquires student number but not anon id.
	 * Assumes that the current user is allowed to see student numbers so skips the check.
	 * @param userUuid the uuid of the user to get
	 * @param site the site the user belongs to
	 * @return GbUser populated with student number, or null if cannot be found 
	 */
	public GbUser getUserPreAuthorized(final String userUuid, Site site)
	{
		try {
			final User u = this.userDirectoryService.getUser(userUuid);
			return GbUser.fromUserAcquiringStudentNumberPreAuthorized(u, site, this);
		} catch (final UserNotDefinedException e) {
			return null;
		}
	}

	/**
	 * Get the user given a uuid. Acquires student number and anon id map for the user.
	 * This method is slow, use only when needed.
	 * 
	 * @param userUuid
	 * @return GbUser with student number and anon ids, or null if cannot be found
	 */
	public GbUser getUserWithAnonId(final String userUuid)
	{
		try {
			final User u = this.userDirectoryService.getUser(userUuid);
			return GbUser.fromUserAcquiringStudentNumberAndAnonIdMap(u, this);
		} catch (final UserNotDefinedException e) {
			return null;
		}
	}
	
	/**
	 * Get the user given an eid. Does not attempt to acquire student number or anon id.
	 *
	 * @param eid
	 * @return Optional<GbUser>, empty if not found
	 */
	public Optional<GbUser> getNonStudentUserByEid(final String eid)
	{
		try
		{
			final User u = userDirectoryService.getUserByEid(eid);
			return Optional.of(GbUser.fromUser(u));
		}
		catch (final UserNotDefinedException e)
		{
			return Optional.empty();
		}
	}

	/**
	 * Get the comment for a given student assignment grade
	 *
	 * @param assignmentId id of assignment
	 * @param studentUuid uuid of student
	 * @return the comment or null if none
	 */
	public String getAssignmentGradeComment(final long assignmentId, final String studentUuid) {

		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		try {
			final CommentDefinition def = this.gradebookService.getAssignmentScoreComment(gradebook.getUid(),
					assignmentId, studentUuid);
			if (def != null) {
				return def.getCommentText();
			}
		} catch (GradebookNotFoundException | AssessmentNotFoundException e) {
			log.error("An error occurred retrieving the comment. {}: {}", e.getClass(), e.getMessage());
		}
		return null;
	}

	/**
	 * Update (or set) the comment for a student's assignment
	 *
	 * @param assignmentId id of assignment
	 * @param studentUuid uuid of student
	 * @param comment the comment
	 * @return true/false
	 */
	public boolean updateAssignmentGradeComment(final long assignmentId, final String studentUuid,
			final String comment) {

		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		try {
			// could do a check here to ensure we aren't overwriting someone
			// else's comment that has been updated in the interim...
			this.gradebookService.setAssignmentScoreComment(gradebook.getUid(), assignmentId, studentUuid, comment);
			return true;
		} catch (GradebookNotFoundException | AssessmentNotFoundException | IllegalArgumentException e) {
			log.error("An error occurred saving the comment. {}: {}", e.getClass(), e.getMessage());
		}

		return false;
	}

	/**
	 * Get the role of the current user in the current site
	 *
	 * @return Role
	 */
	public GbRole getUserRole() {
		final String siteId = getCurrentSiteId();
		return this.getUserRole(siteId);
	}

	/**
	 * Get the role of the current user in the given site
	 *
	 * @param siteId the siteId to check
	 * @return Role
	 */
	public GbRole getUserRole(final String siteId) {

		final String userId = getCurrentUser().getId();

		String siteRef;
		try {
			siteRef = this.siteService.getSite(siteId).getReference();
		} catch (final IdUnusedException e) {
			log.debug(e.getMessage());
			return null;
		}

		GbRole rval;

		if (this.securityService.unlock(userId, GbRole.INSTRUCTOR.getValue(), siteRef)) {
			rval = GbRole.INSTRUCTOR;
		} else if (this.securityService.unlock(userId, GbRole.TA.getValue(), siteRef)) {
			rval = GbRole.TA;
		} else if (this.securityService.unlock(userId, GbRole.STUDENT.getValue(), siteRef)) {
			rval = GbRole.STUDENT;
		} else {
			rval = GbRole.NONE;
		}

		return rval;
	}

	/**
	 * Return true if the current user has the Instructor section role and/or the gradebook.editAssessment permission.
	 *
	 * @param gradebookID the ID of the gradebook in question (site ID)
	 * @return true if the user has the ability, false otherwise
	 */
	public boolean currentUserHasEditPermission(final String gradebookID)
	{
		if (StringUtils.isBlank(gradebookID))
		{
			return false;
		}

		return gradebookService.currentUserHasEditPerm(gradebookID);
	}

	/**
	 * Return true if the current user has the submit or approve permissions. This is NOT complete check
	 * of final submitter/approver status because we can't perform the full check with no section selected.
	 * @param siteId
	 * @return 
	 */
	public boolean currentUserCanSeeFinalGradesPage(final String siteId)
	{
		if (StringUtils.isBlank(siteId))
		{
			return false;
		}
		
		final String userId = getCurrentUser().getId();

		String siteRef;
		try {
			siteRef = this.siteService.getSite(siteId).getReference();
		} catch (final IdUnusedException e) {
			log.debug(e.getMessage());
			return false;
		}
		
		// OWLTODO: these OWL permissions have a public constant in AuthzSakai2Impl but it is not visible from NG
		return securityService.unlock(userId, GbRole.INSTRUCTOR.getValue(), siteRef) 
				&& (securityService.unlock(userId, "gradebook.submitCourseGrades", siteRef)
				|| securityService.unlock(userId, "gradebook.approveCourseGrades", siteRef));
	}

	/**
	 * Get a map of grades for the given student. Safe to call when logged in as a student.
	 *
	 * @param studentUuid
	 * @return map of assignment to GbGradeInfo
	 */
	public Map<Long, GbGradeInfo> getGradesForStudent(final String studentUuid) {

		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		// will apply permissions and only return those the student can view
		final List<Assignment> assignments = getGradebookAssignmentsForStudent(studentUuid);

		final Map<Long, GbGradeInfo> rval = new LinkedHashMap<>();

		// iterate all assignments and get the grades
		// if student, only proceed if grades are released for the site
		// if instructor or TA, skip this check
		// permission checks are still applied at the assignment level in the
		// GradebookService
		final GbRole role = this.getUserRole(siteId);

		if (role == GbRole.STUDENT) {
			final boolean released = gradebook.isAssignmentsDisplayed();
			if (!released) {
				return rval;
			}
		}

		for (final Assignment assignment : assignments) {
			final GradeDefinition def = this.gradebookService.getGradeDefinitionForStudentForItem(gradebook.getUid(),
					assignment.getId(), studentUuid);
			rval.put(assignment.getId(), new GbGradeInfo(def));
		}

		return rval;
	}

	/**
	 * Get the category score for the given student. Safe to call when logged in as a student.
	 *
	 * @param categoryId id of category
	 * @param studentUuid uuid of student
	 * @return
	 */
	public Optional<CategoryScoreData> getCategoryScoreForStudent(final Long categoryId, final String studentUuid) {

		final Gradebook gradebook = getGradebook();

		final Optional<CategoryScoreData> result = gradebookService.calculateCategoryScore(gradebook.getId(), studentUuid, categoryId);
		log.info("Category score for category: {}, student: {}:{}", categoryId, studentUuid, result.map(r -> r.score).orElse(null));

		return result;
	}

	/**
	 * Get the settings for this gradebook. Note that this CANNOT be called by a student.
	 *
	 * @return
	 */
	public GradebookInformation getGradebookSettings() {
		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		final GradebookInformation settings = this.gradebookService.getGradebookInformation(gradebook.getUid());

		Collections.sort(settings.getCategories(), CategoryDefinition.orderComparator);

		return settings;
	}

	/**
	 * Update the settings for this gradebook
	 *
	 * @param settings GradebookInformation settings
	 */
	public void updateGradebookSettings(final GradebookInformation settings) {

		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		this.gradebookService.updateGradebookSettings(gradebook.getUid(), settings);
	}

	/**
	 * Remove an assignment from its gradebook
	 *
	 * @param assignmentId the id of the assignment to remove
	 */
	public void removeAssignment(final Long assignmentId) {
		this.gradebookService.removeAssignment(assignmentId);
	}

	/**
	 * Get a list of teaching assistants in the current site
	 *
	 * @return
	 */
	public List<GbUser> getTeachingAssistants() {

		final String siteId = getCurrentSiteId();
		final List<GbUser> rval = new ArrayList<>();

		try {
			final Set<String> userUuids = this.siteService.getSite(siteId).getUsersIsAllowed(GbRole.TA.getValue());
			for (final String userUuid : userUuids) {
				rval.add(getUser(userUuid));
			}
		} catch (final IdUnusedException e) {
			log.debug(e.getMessage());
		}

		return rval;
	}

	/**
	 * Get a list of permissions defined for the given user. Note: These are currently only defined/used for a teaching assistant.
	 *
	 * @param userUuid
	 * @return list of permissions or empty list if none
	 */
	public List<PermissionDefinition> getPermissionsForUser(final String userUuid) {
		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		final List<PermissionDefinition> permissions = this.gradebookPermissionService
				.getPermissionsForUser(gradebook.getUid(), userUuid);
		if (permissions == null) {
			return new ArrayList<>();
		}
		return permissions;
	}

	/**
	 * Update the permissions for the user. Note: These are currently only defined/used for a teaching assistant.
	 *
	 * @param userUuid
	 * @param permissions
	 */
	public void updatePermissionsForUser(final String userUuid, final List<PermissionDefinition> permissions) {
		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		this.gradebookPermissionService.updatePermissionsForUser(gradebook.getUid(), userUuid, permissions);
	}

	/**
	 * Check if the course grade is visible to the user
	 *
	 * For TA's, the students are already filtered by permission so the TA won't see those they don't have access to anyway However if there
	 * are permissions and the course grade checkbox is NOT checked, then they explicitly do not have access to the course grade. So this
	 * method checks if the TA has any permissions assigned for the site, and if one of them is the course grade permission, then they have
	 * access.
	 *
	 * @param userUuid user to check
	 * @return boolean
	 */
	public boolean isCourseGradeVisible(final String userUuid) {

		final String siteId = getCurrentSiteId();

		final GbRole role = this.getUserRole(siteId);

		// if instructor, allowed
		if (role == GbRole.INSTRUCTOR) {
			return true;
		}

		// if TA, permission checks
		if (role == GbRole.TA) {

			// if no defs, implicitly allowed
			final List<PermissionDefinition> defs = getPermissionsForUser(userUuid);
			if (defs.isEmpty()) {
				return true;
			}

			// if defs and one is the view course grade, explicitly allowed
			for (final PermissionDefinition def : defs) {
				if (StringUtils.equalsIgnoreCase(def.getFunction(), GraderPermission.VIEW_COURSE_GRADE.toString())) {
					return true;
				}
			}
			return false;
		}

		// if student, check the settings
		// this could actually get the settings but it would be more processing
		if (role == GbRole.STUDENT) {
			final Gradebook gradebook = this.getGradebook(siteId);

			if (gradebook.isCourseGradeDisplayed()) {
				return true;
			}
		}

		// other roles not yet catered for, catch all.
		return false;
	}
	
	// true if student numbers are visible to the current user
	// doesn't take into account suppression of student number for individial students due to account type
	public boolean isStudentNumberVisible()
	{
		User user = getCurrentUser();
		Optional<Site> site = getCurrentSite();
		return isStudentNumberVisible(user, site.orElse(null));
	}
	
	public boolean isStudentNumberVisible(User user, Site site)
	{
		return user != null && site != null && candidateDetailProvider.isInstitutionalNumericIdEnabled()
				&& candidateDetailProvider.canUserViewInstitutionalNumericIds(user, site);
	}
	
	public String getStudentNumber(User u, Site site)
	{
		if (site == null || !isStudentNumberVisible(getCurrentUser(), site))
		{
			return "";
		}

		return getStudentNumberPreAuthorized(u, site);
	}

	/**
	 * Gets student number without checking if the current user has permissions.
	 * Use only if you have already checked isStudentNumberVisible().
	 * @param u the user
	 * @param site cannot be null
	 * @return the student number
	 */
	public String getStudentNumberPreAuthorized(User u, Site site)
	{
		return candidateDetailProvider.getInstitutionalNumericId(u, site)
				.orElseGet(() ->
				{
					String num = revealStudentNumber(u, site);
					if (!num.isEmpty() && isStudentInARoster(u, getSiteSections())) // check for presence of number before hitting CM tables
					{
						return num;
					}
					
					return "";
				});
	}
	
	/**
	 * Retrieves the student number for this student, regardless of the student's number visibility permissions
	 * @param user the student
	 * @return the student number, or empty string if not found
	 */
	public String revealStudentNumber(GbUser user)
	{	
		Optional<Site> site = getCurrentSite();
		if (!site.isPresent())
		{
			return "";
		}
		
		try
		{
			User u = userDirectoryService.getUser(user.getUserUuid());
			return revealStudentNumber(u, site.get());
		}
		catch (UserNotDefinedException e)
		{
			return "";
		}
	}
	
	private String revealStudentNumber(User user, Site site)
	{
		return candidateDetailProvider.getInstitutionalNumericIdIgnoringCandidatePermissions(user, site).orElse("");
	}
	
	private boolean isStudentInARoster(User user, List<GbGroup> sections)
	{	
		for (GbGroup sec : sections)
		{
			Set<Membership> members = courseManagementService.getSectionMemberships(sec.getProviderId());
			return members.stream().anyMatch(m -> m.getUserId().equals(user.getEid()) && "S".equals(m.getRole()));
		}
		
		return false;
	}

	/**
	 * Build a list of group references to site membership (as uuids) for the groups that are viewable for the current user.
	 *
	 * @return
	 */
	public Map<String, List<String>> getGroupMemberships() {

		final String siteId = getCurrentSiteId();

		Site site;
		try {
			site = this.siteService.getSite(siteId);
		} catch (final IdUnusedException e) {
			log.error("Error looking up site: {}", siteId, e);
			return null;
		}

		// filtered for the user
		final List<GbGroup> viewableGroups = getSiteSectionsAndGroups();

		final Map<String, List<String>> rval = new HashMap<>();

		for (final GbGroup gbGroup : viewableGroups) {
			final String groupReference = gbGroup.getReference();
			final List<String> memberUuids = new ArrayList<>();

			final Group group = site.getGroup(groupReference);
			if (group != null) {
				final Set<Member> members = group.getMembers();

				for (final Member m : members) {
					memberUuids.add(m.getUserId());
				}
			}

			rval.put(groupReference, memberUuids);
		}

		return rval;
	}

	/**
	 * Have categories been enabled for the gradebook?
	 *
	 * @return if the gradebook is setup for either "Categories Only" or "Categories & Weighting"
	 */
	public boolean categoriesAreEnabled() {
		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		return GbCategoryType.ONLY_CATEGORY.getValue() == gradebook.getCategory_type()
				|| GbCategoryType.WEIGHTED_CATEGORY.getValue() == gradebook.getCategory_type();
	}

	/**
	 * Get the currently configured gradebook category type
	 *
	 * @return GbCategoryType wrapper around the int value
	 */
	public GbCategoryType getGradebookCategoryType() {
		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		final int configuredType = gradebook.getCategory_type();

		return GbCategoryType.valueOf(configuredType);
	}

	/**
	 * Update the course grade (override) for this student
	 *
	 * @param studentUuid uuid of the student
	 * @param grade the new grade
	 * @return
	 */
	public boolean updateCourseGrade(final String studentUuid, final String grade) {

		GbStopWatch sw = new GbStopWatch("bus");
		final String siteId = getCurrentSiteId();
		final Gradebook gradebook = getGradebook(siteId);

		try {
			this.gradebookService.updateCourseGradeForStudent(gradebook.getUid(), studentUuid, grade);
			sw.time("updateCourseGrade");
			return true;
		} catch (final Exception e) {
			log.error("An error occurred saving the course grade. {}: {}", e.getClass(), e.getMessage());
		}

		return false;
	}

	/**
	 * Get the user's preferred locale from the Sakai resource loader
	 *
	 * @return
	 */
	public Locale getUserPreferredLocale() {
		final ResourceLoader rl = new ResourceLoader();
		return rl.getLocale();
	}

	/**
	 * Helper to check if a user is roleswapped
	 *
	 * @return true if ja, false if nay.
	 */
	public boolean isUserRoleSwapped() {

		final String siteId = getCurrentSiteId();

		try {
			final Site site = this.siteService.getSite(siteId);

			// they are roleswapped if they have an 'effective role'
			final String effectiveRole = this.securityService.getUserEffectiveRole(site.getReference());
			if (StringUtils.isNotBlank(effectiveRole)) {
				return true;
			}
		} catch (final IdUnusedException e) {
			// something has happened between getting the siteId and getting the site.
			throw new GbException("An error occurred checking some bits and pieces, please try again.", e);
		}
		return false;
	}
	
	/**
	 * Returns true if the given grade is numeric and meets the gradebook requirements (10 digits/2 decimal places max)
	 * @param grade the grade to be validated, expected to be numeric
	 * @return true if the grade is numeric and meets the gradebook requirements
	 */
	public boolean isValidNumericGrade(String grade)
	{
		return gradebookService.isValidNumericGrade(grade);
	}

	// -------------------- Begin Course Grade Submission methods --------------------

	public List getViewableSections()
	{
		return gradebookService.getViewableSections(getCurrentSiteId());
	}

	public Set<String> getViewableSectionEids()
	{
		List<CourseSection> vSections = getViewableSections();
		Set<String> viewableSectionEids = new HashSet<>();
		for (CourseSection s : vSections)
		{
			if (s != null && StringUtils.isNotBlank(s.getEid()))
			{
				viewableSectionEids.add(s.getEid());
			}
		}

		return viewableSectionEids;
	}

	// -------------------- End Course Grade Submission methods --------------------

	// -------------------- Begin anonymous grading methods --------------------

	/**
	 * Gets anonymousIds for the specified sectionEids
	 * @param sectionEids
	 * @return
	 */
	public List<OwlAnonGradingID> getAnonGradingIDsBySectionEIDs(Set<String> sectionEids)
	{
		return gradebookService.getAnonGradingIDsBySectionEIDs(sectionEids);
	}

	public List<OwlAnonGradingID> getAnonGradingIDsForCurrentSite()
	{
		return gradebookService.getAnonGradingIDsBySectionEIDs(getViewableSectionEids());
	}

	/**
	 * Gets anonymousIds for the group filter provided by GradebookUiSettings
	 * @param settings
	 * @return
	 */
	public List<OwlAnonGradingID> getAnonGradingIDsForUiSettings(GradebookUiSettings settings)
	{
		// The sections we'll be looking anonIDs up for
		Set<String> sections = new HashSet<>();

		// Get the group filter's selected sectionId; if none selected, get all viewable sections
		GbGroup group = settings.getGroupFilter();
		String section = group == null ? null : group.getProviderId();
		if (StringUtils.isBlank(section))
		{
			sections.addAll(getViewableSectionEids());
		}
		else
		{
			sections.add(section);
		}
		
		return gradebookService.getAnonGradingIDsBySectionEIDs(sections);
	}

	/**
	 * For performance, use only in contexts where data is presented for one user
	 * @param userEid
	 * @return
	 */
	public Map<String, Integer> getSectionAnonIdMapForUser(String userEid)
	{
		return getStudentSectionAnonIdMap(getAnonGradingIDsForCurrentSite()).get(userEid);
	}

	/**
	 * Builds a Map<studentUid, Map<section, anonId>> from the specified OwlAnonGradingID list
	 * @param anonIds
	 * @return
	 */
	public Map<String, Map<String, Integer>> getStudentSectionAnonIdMap(List<OwlAnonGradingID> anonIds)
	{
		Map<String, Map<String, Integer>> studentSectionAnonIdMap = new LinkedHashMap<>();
		Collections.sort(anonIds, defaultOwlAnonGradingIDOrdering);
		for (OwlAnonGradingID anonId : anonIds)
		{
			addTripleToMap(studentSectionAnonIdMap, anonId.getUserEid(), anonId.getSectionEid(), anonId.getAnonGradingID());
		}

		return studentSectionAnonIdMap;
	}

	/**
	 * Builds a Map<section, Map<studentUid, anonId>> from the specified OwlAnonGradingID list
	 * @param anonIds
	 * @return
	 */
	public Map<String, Map<String, Integer>> getSectionStudentAnonIdMap(List<OwlAnonGradingID> anonIds)
	{
		Map<String, Map<String, Integer>> sectionStudentAnonIdMap = new LinkedHashMap<>();
		Collections.sort(anonIds, defaultOwlAnonGradingIDOrdering);
		for (OwlAnonGradingID anonId : anonIds)
		{
			addTripleToMap(sectionStudentAnonIdMap, anonId.getSectionEid(), anonId.getUserEid(), anonId.getAnonGradingID());
		}

		return sectionStudentAnonIdMap;
	}

	/**
	 * For any Map<T, Map<U, V>>, maps key1->(key2, value); constructing the inner map for key1 if it doesn't exist
	 */
	private static <T, U, V> void addTripleToMap(Map<T, Map<U, V>> map, T key1, U key2, V value)
	{
		Map<U, V> key1Map = map.get(key1);
		if (key1Map == null)
		{
			key1Map = new LinkedHashMap<>();
			map.put(key1, key1Map);
		}
		key1Map.put(key2, value);
	}

	/**
	 * Orders OwlAnonGradingIDs: primary sort by sections, secondary sort by userEids. This is mainly used to ensure the order in which OwlAnonGradingID data is added into LinkedHashMaps is deterministic.
	 */
	public static Comparator<OwlAnonGradingID> defaultOwlAnonGradingIDOrdering = new Comparator<OwlAnonGradingID>()
	{
		@Override
		public int compare(final OwlAnonGradingID id1, final OwlAnonGradingID id2)
		{
			int sectionComparison = id1.getSectionEid().compareTo(id2.getSectionEid());
			if (sectionComparison == 0)
			{
				int userIdComparison = id1.getUserEid().compareTo(id2.getUserEid());
				// if userIdComparison is 0, there's no need to go further with anonIds; they should be unique per (sectionId, userId) pair
				return userIdComparison;
			}
			return sectionComparison;
		}
	};

	 // -------------------- End anonymous grading methods --------------------

	/**
	 * Comparator class for sorting a list of AssignmentOrders
	 */
	class AssignmentOrderComparator implements Comparator<AssignmentOrder> {
		@Override
		public int compare(final AssignmentOrder ao1, final AssignmentOrder ao2) {
			// Deal with uncategorized assignments (nulls!)
			if (ao1.getCategory() == null && ao2.getCategory() == null) {
				return ((Integer) ao1.getOrder()).compareTo(ao2.getOrder());
			} else if (ao1.getCategory() == null) {
				return 1;
			} else if (ao2.getCategory() == null) {
				return -1;
			}
			// Deal with friendly categorized assignments
			if (ao1.getCategory().equals(ao2.getCategory())) {
				return ((Integer) ao1.getOrder()).compareTo(ao2.getOrder());
			} else {
				return ao1.getCategory().compareTo(ao2.getCategory());
			}
		}
	}

	/**
	 * Comparator class for sorting an assignment by the grades.
	 *
	 * Note that this must have the assignmentId set into it so we can extract the appropriate grade entry from the map that each student
	 * has.
	 *
	 */
	@RequiredArgsConstructor
	class AssignmentGradeComparator implements Comparator<GbStudentGradeInfo> {

		private final long assignmentId;

		@Override
		public int compare(final GbStudentGradeInfo g1, final GbStudentGradeInfo g2) {

			final GbGradeInfo info1 = g1.getGrades().get(this.assignmentId);
			final GbGradeInfo info2 = g2.getGrades().get(this.assignmentId);

			// for proper number ordering, these have to be numerical
			final Double grade1 = (info1 != null) ? NumberUtils.toDouble(info1.getGrade()) : null;
			final Double grade2 = (info2 != null) ? NumberUtils.toDouble(info2.getGrade()) : null;

			return new CompareToBuilder().append(grade1, grade2).toComparison();
		}
	}

	/**
	 * Comparator class for sorting a category by the subtotals
	 *
	 * Note that this must have the categoryId set into it so we can extract the appropriate grade entry from the map that each student has.
	 *
	 */
	@RequiredArgsConstructor
	class CategorySubtotalComparator implements Comparator<GbStudentGradeInfo> {

		private final long categoryId;

		@Override
		public int compare(final GbStudentGradeInfo g1, final GbStudentGradeInfo g2) {

			final Double subtotal1 = g1.getCategoryAverages().get(this.categoryId);
			final Double subtotal2 = g2.getCategoryAverages().get(this.categoryId);

			return new CompareToBuilder().append(subtotal1, subtotal2).toComparison();
		}
	}

	/**
	 * Comparator class for sorting by course grade, first by the letter grade's index in the gradebook's grading scale and then by the
	 * number of points the student has earned.
	 *
	 */
	class CourseGradeComparator implements Comparator<GbStudentGradeInfo> {

		private List<String> ascendingGrades;

		public CourseGradeComparator(final GradebookInformation gradebookInformation) {
			final Map<String, Double> gradeMap = gradebookInformation.getSelectedGradingScaleBottomPercents();
			this.ascendingGrades = new ArrayList<>(gradeMap.keySet());
			this.ascendingGrades.sort(new Comparator<String>() {
				@Override
				public int compare(final String a, final String b) {
					return new CompareToBuilder()
							.append(gradeMap.get(a), gradeMap.get(b))
							.toComparison();
				}
			});
		}

		@Override
		public int compare(final GbStudentGradeInfo g1, final GbStudentGradeInfo g2) {
			final CourseGrade cg1 = g1.getCourseGrade().getCourseGrade();
			final CourseGrade cg2 = g2.getCourseGrade().getCourseGrade();

			String letterGrade1 = cg1.getMappedGrade();
			if (cg1.getEnteredGrade() != null) {
				letterGrade1 = cg1.getEnteredGrade();
			}
			String letterGrade2 = cg2.getMappedGrade();
			if (cg2.getEnteredGrade() != null) {
				letterGrade2 = cg2.getEnteredGrade();
			}

			final int gradeIndex1 = this.ascendingGrades.indexOf(letterGrade1);
			final int gradeIndex2 = this.ascendingGrades.indexOf(letterGrade2);

			final Double calculatedGrade1 = cg1.getCalculatedGrade() == null ? null : Double.valueOf(cg1.getCalculatedGrade());
			final Double calculatedGrade2 = cg2.getCalculatedGrade() == null ? null : Double.valueOf(cg2.getCalculatedGrade());

			return new CompareToBuilder()
					.append(gradeIndex1, gradeIndex2)
					.append(calculatedGrade1, calculatedGrade2)
					.toComparison();
		}
	}
	
	/**
	 * Comparator class for sorting by OWL final grade (course sites)
	 *
	 */
	class FinalGradeComparator implements Comparator<GbStudentGradeInfo>
	{	
		@Override
		public int compare(final GbStudentGradeInfo g1, final GbStudentGradeInfo g2)
		{
			String fg1 = FinalGradeFormatter.formatForRegistrar(g1.getCourseGrade());
			String fg2 = FinalGradeFormatter.formatForRegistrar(g2.getCourseGrade());
			
			return fg1.compareTo(fg2);
		}
	}
	
	/**
	 * Comparator class for sorting by calculated course grade only
	 *
	 */
	class CalculatedCourseGradeComparator implements Comparator<GbStudentGradeInfo>
	{	
		@Override
		public int compare(final GbStudentGradeInfo g1, final GbStudentGradeInfo g2)
		{
			Double cg1 = g1.getCourseGrade().getCalculatedGrade().orElse(Double.NEGATIVE_INFINITY);
			Double cg2 = g2.getCourseGrade().getCalculatedGrade().orElse(Double.NEGATIVE_INFINITY);
			
			return cg1.compareTo(cg2);
		}
	}
	
	/************************* Begin Course Grade Submission methods  --plukasew *****************************/
	
	public List<OwlGradeSubmission> getAllCourseGradeSubmissionsForSection(final String sectionEid) throws IllegalArgumentException
	{
		return gradebookService.getAllCourseGradeSubmissionsForSectionInSite(sectionEid, getCurrentSiteId());
	}
	
	public OwlGradeSubmission getMostRecentCourseGradeSubmissionForSection(final String sectionEid) throws IllegalArgumentException
	{
		return gradebookService.getMostRecentCourseGradeSubmissionForSectionInSite(sectionEid, getCurrentSiteId());
	}

	// OWL-1228  --plukasew
	public boolean isSectionApproved(final String sectionEid) throws IllegalArgumentException
	{
		return gradebookService.isSectionInSiteApproved(sectionEid, getCurrentSiteId());
	}

	public boolean areAllSectionsApproved(final Set<String> sectionEids) throws IllegalArgumentException
	{
		return gradebookService.areAllSectionsInSiteApproved(sectionEids, getCurrentSiteId());
	}

	public Long createSubmission(final OwlGradeSubmission sub) throws IllegalArgumentException
	{
		return gradebookService.createSubmission(sub);
	}

	public void updateSubmission(final OwlGradeSubmission sub) throws IllegalArgumentException
	{
		gradebookService.updateSubmission(sub);
	}

	public Long createApproval(final OwlGradeApproval approval) throws IllegalArgumentException
	{
		return gradebookService.createApproval(approval);
	}

	public boolean isOfficialRegistrarGradingSchemeInUse( final Long gradebookID )
	{
		return gradebookService.isOfficialRegistrarGradingSchemeInUse(gradebookID);
	}
	
	public Section getSectionByEid(String eid)
	{
		return courseManagementService.getSection(eid);
	}
	
	public Set<Membership> getSectionMemberships(String sectionEid)
	{
		return courseManagementService.getSectionMemberships(sectionEid);
	}
	
	public List<GbStudentCourseGradeInfo> getSectionCourseGrades(GbGroup group)
	{
		if (group.getType() == GbGroup.Type.SECTION)
		{
			GbStopWatch sw = new GbStopWatch("bus.getSectionCourseGrades");
			List<String> users = getGradeableUsers(group);
			sw.time("getGradableUsers");
			Map<String, CourseGrade> courseGrades = getCourseGrades(users);
			sw.time("getCourseGrades");
			List<GbStudentCourseGradeInfo> secCourseGrades = new ArrayList<>(courseGrades.size());
			// before looping, get the current site and preauthorize the current user
			Site site = getCurrentSite().orElse(null);
			boolean canView = isStudentNumberVisible(getCurrentUser(), site);
			if (site != null && canView)
			{
				for (Entry<String, CourseGrade> entry : courseGrades.entrySet())
				{
					GbUser student = getUserPreAuthorized(entry.getKey(), site);
					GbStudentCourseGradeInfo cgi = new GbStudentCourseGradeInfo(student);
					cgi.setCourseGrade(new GbCourseGrade(entry.getValue()));
					secCourseGrades.add(cgi);
				}
				sw.time("create course grade infos");

				return secCourseGrades;
			}
		}
		
		return Collections.emptyList();
	}

	/* End Course Grade Submission methods  --plukasew */

	/**
	 * Create a map so that we can use the user's EID (from the imported file) to lookup their UUID (used to store the grade by the backend service).
	 *
	 * @return Map where the user's EID is the key and the {@link GbUser} object is the value
	 */
	public Map<String, GbUser> getUserEidMap() {
		final List<GbUser> users = getGbUsers(getGradeableUsers());
		final Map<String, GbUser> userEidMap = new HashMap<>();
		for (GbUser user : users) {
			String eid = user.getEid();
			if (StringUtils.isNotBlank(eid)) {
				userEidMap.put(eid, user);
			}
		}

		return userEidMap;
	}

	/**
	 * Create a map so that we can use the user's student number (from the imported file) to lookup their UUID (used to store the grade
	 * by the backend service).
	 *
	 * @return Map where the user's student number is the key and the {@link GbUser} object is the value
	 */
	public Map<String, GbUser> getUserStudentNumMap() {
		final List<GbUser> users = getGbUsers(getGradeableUsers());
		final Map<String, GbUser> userStudentNumMap = new HashMap<>();
		for (GbUser user : users) {
			String studentNum = user.getStudentNumber();
			if (StringUtils.isNotBlank(studentNum)) {
				userStudentNumMap.put(studentNum, user);
			}
		}

		return userStudentNumMap;
	}

	/**
	 * Create a map of GradingIDs to their associated GbUser objects. 
	 * NB: This should be avoided as GradingIDs can collide in crosslisted sections.
	 * But there are scenarios (Ie. anonymous spreadsheet imports) where it is necessary
	 * @return
	 */
	public Map<String, GbUser> getAnonIDUserMap()
	{
		final List<GbUser> users = getGbUsersFilteredIfAnonymous(getGradeableUsers(), true);
		final Map<String, GbUser> anonIdUserMap = new HashMap<>();
		users.stream().forEach(user -> anonIdUserMap.put(String.valueOf(user.getAnonId()), user));

		return anonIdUserMap;
	}
}
