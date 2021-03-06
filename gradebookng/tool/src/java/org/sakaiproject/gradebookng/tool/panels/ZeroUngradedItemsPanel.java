package org.sakaiproject.gradebookng.tool.panels;

import java.util.List;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.spring.injection.annot.SpringBean;

import org.sakaiproject.gradebookng.business.GradebookNgBusinessService;
import org.sakaiproject.gradebookng.tool.component.SakaiAjaxButton;
import org.sakaiproject.gradebookng.tool.pages.IGradesPage;
import org.sakaiproject.service.gradebook.shared.Assignment;

/**
 *
 * Panel for the modal window that allows an instructor to zero the ungraded scores for all gradebook items
 *
 */
public class ZeroUngradedItemsPanel extends Panel {

	@SpringBean(name = "org.sakaiproject.gradebookng.business.GradebookNgBusinessService")
	protected GradebookNgBusinessService businessService;

	private final ModalWindow window;

	private static final double ZERO_GRADE = 0;

	public ZeroUngradedItemsPanel(final String id, final ModalWindow window) {
		super(id);
		this.window = window;
	}

	@Override
	public void onInitialize() {
		super.onInitialize();

		final SakaiAjaxButton submit = new SakaiAjaxButton("submit") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit(final AjaxRequestTarget target, final Form<?> form) {

				// fetch all assignments
				final List<Assignment> assignments = ZeroUngradedItemsPanel.this.businessService.getGradebookAssignments();

				for (final Assignment assignment : assignments) {
					final long assignmentId = assignment.getId();
					ZeroUngradedItemsPanel.this.businessService.updateUngradedItems(assignmentId, ZERO_GRADE);
				}

				// refresh
				ZeroUngradedItemsPanel.this.window.close(target);
				final IGradesPage gradebookPage = (IGradesPage) getPage();
				gradebookPage.redrawSpreadsheet(target);
			}
		};
		add(submit);

		final SakaiAjaxButton cancel = new SakaiAjaxButton("cancel") {

			@Override
			public void onSubmit(final AjaxRequestTarget target, final Form<?> form) {
				ZeroUngradedItemsPanel.this.window.close(target);
			}
		};
		cancel.setDefaultFormProcessing(false);
		add(cancel);
	}
}
