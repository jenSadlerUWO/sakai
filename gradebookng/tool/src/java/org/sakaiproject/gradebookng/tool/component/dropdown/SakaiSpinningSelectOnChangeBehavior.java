package org.sakaiproject.gradebookng.tool.component.dropdown;

import org.apache.wicket.ajax.AjaxChannel;
import org.apache.wicket.ajax.attributes.AjaxCallListener;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;

/**
 *
 * @author plukasew
 */
public abstract class SakaiSpinningSelectOnChangeBehavior extends AjaxFormComponentUpdatingBehavior
{	
	public SakaiSpinningSelectOnChangeBehavior()
	{
		super("onchange");
	}
	
	@Override
	protected void updateAjaxAttributes(AjaxRequestAttributes attributes)
	{
		super.updateAjaxAttributes(attributes);
		attributes.setChannel(new AjaxChannel("blocking", AjaxChannel.Type.ACTIVE));
		
		AjaxCallListener listener = new SakaiSpinningSelectAjaxCallListener(getComponent().getMarkupId(), false);
		attributes.getAjaxCallListeners().add(listener);
	}
}
