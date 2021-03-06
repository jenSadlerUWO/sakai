package org.sakaiproject.gradebookng.tool.panels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.extensions.markup.html.tabs.AbstractTab;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.sakaiproject.gradebookng.business.GradebookNgBusinessService;
import org.sakaiproject.gradebookng.tool.component.GbAjaxLink;
import org.sakaiproject.gradebookng.tool.model.GbModalWindow;
import org.sakaiproject.gradebookng.tool.model.GradebookUiSettings;
import org.sakaiproject.gradebookng.tool.pages.IGradesPage;

/**
 *
 * Wrapper for the student grade summary tabs
 *
 * @author Steve Swinsburg (steve.swinsburg@gmail.com)
 *
 */
public class StudentGradeSummaryPanel extends Panel {

	private static final long serialVersionUID = 1L;

	private final ModalWindow window;

	@SpringBean(name = "org.sakaiproject.gradebookng.business.GradebookNgBusinessService")
	protected GradebookNgBusinessService businessService;

	public StudentGradeSummaryPanel(final String id, final IModel<Map<String, Object>> model, final GbModalWindow window) {
		super(id, model);

		this.window = window;
		setOutputMarkupId(true);
	}

	@Override
	public void onInitialize() {
		super.onInitialize();

		// unpack model
		// final Map<String, Object> modelData = (Map<String, Object>) getDefaultModelObject();
		// final String eid = (String) modelData.get("eid");
		// final String displayName = (String) modelData.get("displayName");

		// done button
		add(new GbAjaxLink("done") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(final AjaxRequestTarget target) {
				StudentGradeSummaryPanel.this.window.close(target);
			}
		});

		GradebookUiSettings settings = ((IGradesPage) getPage()).getUiSettings();
		Label lblAnonymousBlurb = new Label("anonBlurb", Model.of(getString("label.studentsummary.anonymousblurb")));
		lblAnonymousBlurb.setVisible(settings.isGradebookMixed());
		add(lblAnonymousBlurb);

		final WebMarkupContainer studentNavigation = new WebMarkupContainer("studentNavigation");
		studentNavigation.setOutputMarkupPlaceholderTag(true);
		add(studentNavigation);

		final List tabs = new ArrayList();

		tabs.add(new AbstractTab(new Model<String>(getString("label.studentsummary.instructorviewtab"))) {
			@Override
			public Panel getPanel(final String panelId) {
				return new InstructorGradeSummaryGradesPanel(panelId, (IModel<Map<String, Object>>) getDefaultModel());
			}
		});
		tabs.add(new AbstractTab(new Model<String>(getString("label.studentsummary.studentviewtab"))) {
			@Override
			public Panel getPanel(final String panelId) {
				return new StudentGradeSummaryGradesPanel(panelId, (IModel<Map<String, Object>>) getDefaultModel());
			}
		});

		add(new AjaxBootstrapTabbedPanel("tabs", tabs) {
			@Override
			protected String getTabContainerCssClass() {
				return "nav nav-tabs";
			}

			@Override
			protected void onAjaxUpdate(final AjaxRequestTarget target) {
				super.onAjaxUpdate(target);

				final boolean showingInstructorView = (getSelectedTab() == 0);
				final boolean showingStudentView = (getSelectedTab() == 1);

				studentNavigation.setVisible(showingInstructorView);
				target.add(studentNavigation);

				target.appendJavaScript(
						String.format("new GradebookGradeSummary($(\"#%s\"), %s);",
							getParent().getMarkupId(),
							showingStudentView));
			}
		});
	}

}
