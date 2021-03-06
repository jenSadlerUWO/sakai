package org.sakaiproject.gradebookng.tool.panels;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxCheckBox;
import org.apache.wicket.extensions.markup.html.form.DateTextField;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.validation.IErrorMessageSource;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidationError;
import org.apache.wicket.validation.IValidator;
import org.apache.wicket.validation.ValidationError;

import org.sakaiproject.gradebookng.business.GbCategoryType;
import org.sakaiproject.gradebookng.business.GbGradingType;
import org.sakaiproject.gradebookng.business.GradebookNgBusinessService;
import org.sakaiproject.gradebookng.business.util.FormatHelper;
import org.sakaiproject.service.gradebook.shared.Assignment;
import org.sakaiproject.service.gradebook.shared.CategoryDefinition;
import org.sakaiproject.service.gradebook.shared.GradebookService;
import org.sakaiproject.tool.gradebook.Gradebook;

/**
 * The panel for the add grade item window
 *
 * @author Steve Swinsburg (steve.swinsburg@gmail.com)
 *
 */
@Slf4j
public class AddOrEditGradeItemPanelContent extends Panel {

	@SpringBean(name = "org.sakaiproject.gradebookng.business.GradebookNgBusinessService")
	private GradebookNgBusinessService businessService;

	private AjaxCheckBox counted;
	private AjaxCheckBox released;
	private AjaxCheckBox anonymous;

	private boolean categoriesEnabled;

	private boolean isAnonymousLocked = false;

	public AddOrEditGradeItemPanelContent(final String id, final Model<Assignment> assignmentModel) {
		super(id, assignmentModel);
	}
	
	@Override
	protected void onInitialize()
	{
		super.onInitialize();
		
		final Gradebook gradebook = businessService.getGradebook();
		final GbGradingType gradingType = GbGradingType.valueOf(gradebook.getGrade_type());

		final Model<Assignment> assignmentModel = (Model<Assignment>) getDefaultModel();
		final Assignment assignment = assignmentModel.getObject();

		this.categoriesEnabled = true;
		if (gradebook.getCategory_type() == GbCategoryType.NO_CATEGORY.getValue()) {
			categoriesEnabled = false;
		}

		// title
		final TextField<String> title = new TextField<String>("title", new PropertyModel<>(assignmentModel, "name")) {

			@Override
			public boolean isEnabled() {
				return !assignment.isExternallyMaintained();
			}

			@Override
			public boolean isRequired() {
				return true;
			}

			@Override
			public void error(IValidationError error)
			{
				Serializable msg;
				if (error instanceof GradeItemValidationError)
				{
					msg = error.getErrorMessage(new ResourceErrorMessageSource());
				}
				else
				{
					msg = getString(GradeItemValidationError.TITLE_ERROR_KEY);
				}
				
				error(msg);
			}
		};
		title.add(new IValidator<String>()
		{ 
			@Override
			public void validate(IValidatable<String> validatable)
			{
				String titleValue = validatable.getValue();
				String errorKey = "";
				if ("Course Grade".equalsIgnoreCase(titleValue))
				{
					errorKey = GradeItemValidationError.COURSE_GRADE_ERROR_KEY;
				}
				else
				{
					// new assignment, or existing assignment but name could have changed, check for other with same title
					Assignment other = businessService.getAssignment(titleValue);
					if (other != null && !other.getId().equals(assignment.getId()))
					{
						errorKey = GradeItemValidationError.TITLE_ERROR_KEY;
					}
				}
				
				if (!errorKey.isEmpty())
				{
					IValidationError error = new GradeItemValidationError(errorKey);
					validatable.error(error);
				}
			}
		});
		add(title);

		// points
		final Label pointsLabel = new Label("pointsLabel");
		if (gradingType == GbGradingType.PERCENTAGE) {
			pointsLabel.setDefaultModel(new ResourceModel("label.addgradeitem.percentage"));
		} else {
			pointsLabel.setDefaultModel(new ResourceModel("label.addgradeitem.points"));
		}
		add(pointsLabel);
		final TextField<Double> points = new TextField<Double>("points", new PropertyModel<>(assignmentModel, "points")) {

			@Override
			public boolean isEnabled() {
				return !assignment.isExternallyMaintained();
			}

			@Override
			public boolean isRequired() {
				return true;
			}

			@Override
			public void error(IValidationError error) {
				// Use our fancy error message for all validation errors
				error(getString("error.addgradeitem.points"));
			}
		};
		points.add(new IValidator<Double>()
		{ 
			@Override
			public void validate(IValidatable<Double> validatable)
			{
				Double pointsValue = validatable.getValue();
				if (pointsValue == null || pointsValue < 0)
				{
					IValidationError error = new ValidationError();
					validatable.error(error);
				}
			}
		});
		add(points);

		// due date
		// TODO date format needs to come from i18n
		final DateTextField dueDate = new DateTextField("duedate", new PropertyModel<>(assignmentModel, "dueDate"), getString("format.date")) {

			@Override
			public boolean isEnabled() {
				return !assignment.isExternallyMaintained();
			}
		};
		add(dueDate);

		// category
		final List<CategoryDefinition> categories = new ArrayList<>();
		final Map<Long, CategoryDefinition> categoryMap = new LinkedHashMap<>();

		if (categoriesEnabled) {
			categories.addAll(this.businessService.getGradebookCategories());

			for (final CategoryDefinition category : categories) {
				categoryMap.put(category.getId(), category);
			}
		}

		// wrapper for category section. It doesnt get shown at all if
		// categories are not enabled.
		final WebMarkupContainer categoryWrap = new WebMarkupContainer("categoryWrap");
		categoryWrap.setVisible(categoriesEnabled);

		final DropDownChoice<Long> categoryDropDown = new DropDownChoice<Long>("category",
				new PropertyModel<>(assignmentModel, "categoryId"), new ArrayList<>(categoryMap.keySet()),
				new IChoiceRenderer<Long>() {

					@Override
					public Object getDisplayValue(final Long value) {
						final CategoryDefinition category = categoryMap.get(value);
						if (GradebookService.CATEGORY_TYPE_WEIGHTED_CATEGORY == gradebook.getCategory_type()) {
							final String weight = FormatHelper.formatDoubleAsPercentage(category.getWeight() * 100);
							return MessageFormat.format(getString("label.addgradeitem.categorywithweight"), category.getName(), weight);
						} else {
							return category.getName();
						}
					}

					@Override
					public String getIdValue(final Long object, final int index) {
						return object.toString();
					}
				}) {

			@Override
			protected String getNullValidDisplayValue() {
				return getString("gradebookpage.uncategorised");
			}
		};

		// always allow an assignment to be set as uncategorized
		categoryDropDown.setNullValid(true);
		categoryDropDown.setVisible(!categories.isEmpty());
		categoryWrap.add(categoryDropDown);

		categoryWrap.add(new WebMarkupContainer("noCategoriesMessage") {

			@Override
			public boolean isVisible() {
				return categories.isEmpty();
			}
		});

		add(categoryWrap);

		// extra credit
		// if an extra credit category is selected, this will be unchecked and
		// disabled
		final AjaxCheckBox extraCredit = new AjaxCheckBox("extraCredit", new PropertyModel<>(assignmentModel, "extraCredit")) {

			@Override
			protected void onUpdate(final AjaxRequestTarget target) {
				// nothing required
			}
		};
		extraCredit.setOutputMarkupId(true);
		extraCredit.setEnabled(!assignment.isCategoryExtraCredit());
		add(extraCredit);

		// released
		released = new AjaxCheckBox("released", new PropertyModel<>(assignmentModel, "released")) {

			@Override
			protected void onUpdate(final AjaxRequestTarget target) {
				if (!getModelObject()) {
					AddOrEditGradeItemPanelContent.this.counted.setModelObject(false);
					target.add(AddOrEditGradeItemPanelContent.this.counted);
				}
			}
		};
		released.setOutputMarkupId(true);
		add(released);

		// counted
		// if checked, release must also be checked and then disabled
		counted = new AjaxCheckBox("counted", new PropertyModel<>(assignmentModel, "counted")) {

			@Override
			protected void onUpdate(final AjaxRequestTarget target) {
				if (getModelObject()) {
					AddOrEditGradeItemPanelContent.this.released.setModelObject(true);
				}
				target.add(AddOrEditGradeItemPanelContent.this.released);
			}
		};

		if (businessService.categoriesAreEnabled())
		{
			// validate counted state
			// make sure uncategorized items are not displayed as counted even if they are set that way
			// (categories could have been disabled previously and enabling categories does not affect the counted setting)
			boolean categorized = assignment.getCategoryId() != null;
			if (!categorized)
			{
				counted.setEnabled(false);
				counted.setModelObject(false);
			}
		}

		add(counted);

		// anonymous (OWL-2545)  --bbailla2
		WebMarkupContainer anonymousContainer = new WebMarkupContainer("anonymousContainer") {
			@Override
			protected void onInitialize() {
				super.onInitialize();
				setOutputMarkupId(true);
			}
		};

		anonymous = new AjaxCheckBox("anonymous", new PropertyModel<>(assignmentModel, "anon")) {

			@Override
			protected void onInitialize() {
				super.onInitialize();
				setOutputMarkupId(true);
				if (isAnonymousLocked())
				{
					setEnabled(false);
				}
				else
				{
					setEnabled(assignment == null || assignment.getId() == null);
				}
			}

			@Override
			protected void onUpdate(final AjaxRequestTarget target) {
				if (getModelObject()) {
					AddOrEditGradeItemPanelContent.this.anonymous.setModelObject(true);
				}
				target.add(AddOrEditGradeItemPanelContent.this.anonymous);
			}
		};
		anonymousContainer.add(anonymous);
		// Only display the anonymous toggle if there are anonIDs in the site
		anonymousContainer.setVisible(!businessService.getAnonGradingIDsForCurrentSite().isEmpty());
		add(anonymousContainer);

		// behaviour for when a category is chosen. If the category is extra
		// credit, deselect and disable extra credit checkbox
		categoryDropDown.add(new AjaxFormComponentUpdatingBehavior("onchange") {

			@Override
			protected void onUpdate(final AjaxRequestTarget target) {

				final Long selected = categoryDropDown.getModelObject();

				// if extra credit, deselect and disable the 'extraCredit'
				// checkbox
				final CategoryDefinition category = categoryMap.get(selected);

				if (category != null && category.isExtraCredit()) {
					extraCredit.setModelObject(false);
					extraCredit.setEnabled(false);
				} else {
					extraCredit.setEnabled(true);
				}
				target.add(extraCredit);

				if (AddOrEditGradeItemPanelContent.this.businessService.categoriesAreEnabled()) {
					if (category == null) {
						AddOrEditGradeItemPanelContent.this.counted.setEnabled(false);
						AddOrEditGradeItemPanelContent.this.counted.setModelObject(false);
					} else {
						AddOrEditGradeItemPanelContent.this.counted.setEnabled(true);
						AddOrEditGradeItemPanelContent.this.counted.setModelObject(true);
						AddOrEditGradeItemPanelContent.this.released.setModelObject(true);
					}

					target.add(AddOrEditGradeItemPanelContent.this.counted);
					target.add(AddOrEditGradeItemPanelContent.this.released);
				}
			}
		});
		
	}
	
	public boolean isAnonymousLocked()
	{
		return isAnonymousLocked;
	}

	/**
	 * Use case: newly imported items. The anonymity needs to match the type of spreadsheet that was imported.
	 */
	public void lockAnonymousToValue(boolean isItemAnonymous)
	{
		Assignment assignment = (Assignment) getDefaultModel().getObject();
		assignment.setAnon(isItemAnonymous);
		isAnonymousLocked = true;
	}
	
	public static class GradeItemValidationError implements IValidationError
	{
		private static final String COURSE_GRADE_ERROR_KEY = "error.addgradeitem.coursegrade";
		private static final String TITLE_ERROR_KEY = "error.addgradeitem.title";

		private String key = "";
		
		public GradeItemValidationError(String key)
		{
			this.key = key;
		}
		
		@Override
		public Serializable getErrorMessage(IErrorMessageSource iems)
		{
			return iems.getMessage(key, Collections.emptyMap());
		}
	}
	
	public static class ResourceErrorMessageSource implements IErrorMessageSource
	{
		@Override
		public String getMessage(String key, Map<String, Object> vars)
		{
			return new ResourceModel(key).getObject();
		}
	}
}
