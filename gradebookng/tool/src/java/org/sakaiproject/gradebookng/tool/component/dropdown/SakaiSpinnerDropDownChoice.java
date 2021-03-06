package org.sakaiproject.gradebookng.tool.component.dropdown;

import java.util.List;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.panel.GenericPanel;
import org.apache.wicket.model.IModel;

/**
 * Wraps a DropDownChoice component in a panel that allows the overlay spinner to be shown.
 * Attach to a <span> in your markup. It will provide a <label> and <select>.
 * This panel shares its model with the DropDownChoice so you can set the model object directly on this
 * component. Be careful if replacing the models themselves.
 * 
 * @author plukasew
 */
public class SakaiSpinnerDropDownChoice<T> extends GenericPanel<T>
{	
	public final DropDownChoice<T> select;
	
	public SakaiSpinnerDropDownChoice(String id, IModel<T> choiceModel, List<T> choices, IChoiceRenderer<T> renderer,
			IModel<String> labelModel, SakaiSpinningSelectOnChangeBehavior behavior)
	{
		super(id, choiceModel);
		select = new DropDownChoice<>("spinnerSelect", choiceModel, choices, renderer);
		select.setLabel(labelModel);
		select.add(behavior);
		add(select);
	}
}
