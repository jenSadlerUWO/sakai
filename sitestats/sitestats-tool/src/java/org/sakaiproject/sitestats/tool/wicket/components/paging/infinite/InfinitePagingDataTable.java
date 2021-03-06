// Adapted from https://github.com/apache/wicket/blob/wicket-6.x/wicket-extensions/src/main/java/org/apache/wicket/extensions/markup/html/repeater/data/table/DataTable.java

package org.sakaiproject.sitestats.tool.wicket.components.paging.infinite;

import java.util.List;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IStyledColumn;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.IItemReuseStrategy;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.util.lang.Args;
import org.apache.wicket.util.string.Strings;
import org.apache.wicket.util.visit.IVisit;
import org.apache.wicket.util.visit.IVisitor;
import org.sakaiproject.sitestats.tool.wicket.providers.infinite.InfiniteDataProvider;

public class InfinitePagingDataTable<T, S> extends Panel
{
	static abstract class CssAttributeBehavior extends Behavior
	{
		private static final long serialVersionUID = 1L;

		protected abstract String getCssClass();

		/**
		 * @see Behavior#onComponentTag(Component, ComponentTag)
		 */
		@Override
		public void onComponentTag(final Component component, final ComponentTag tag)
		{
			String className = getCssClass();
			if (!Strings.isEmpty(className))
			{
				tag.append("class", className, " ");
			}
		}
	}

	private final WebMarkupContainer body;
	private final List<? extends IColumn<T, S>> columns;
	private final ToolbarsContainer topToolbars;
	private final ToolbarsContainer bottomToolbars;
	private final Caption caption;
	private long toolbarIdCounter;

	private final InfinitePagingDataGridView<T> datagrid;

	public InfinitePagingDataTable(String id, final List<? extends IColumn<T, S>> columns, final InfiniteDataProvider<T> dataProvider, final long rowsPerPage)
	{
		super(id);

		Args.notEmpty(columns, "columns");

		this.columns = columns;
		this.caption = new Caption("caption", getCaptionModel());
		add(caption);
		body = newBodyContainer("body");
		datagrid = newInfinitePagingDataGridView("rows", columns, dataProvider);
		datagrid.setItemsPerPage(rowsPerPage);
		body.add(datagrid);
		add(body);
		topToolbars = new ToolbarsContainer("topToolbars");
		bottomToolbars = new ToolbarsContainer("bottomToolbars");
		add(topToolbars);
		add(bottomToolbars);
	}

	protected InfinitePagingDataGridView<T> newInfinitePagingDataGridView(String id, List<? extends IColumn<T, S>> columns, InfiniteDataProvider<T> dataProvider)
	{
		return new DefaultInfinitePagingDataGridView(id, columns, dataProvider);
	}

	protected IModel<String> getCaptionModel()
	{
		return null;
	}

	protected WebMarkupContainer newBodyContainer(final String id)
	{
		return new WebMarkupContainer(id);
	}

	public final void setTableBodyCss(final String cssStyle)
	{
		body.add(AttributeModifier.replace("class", cssStyle));
	}

	public void addBottomToolbar(final InfinitePagingDataTableToolbar toolbar)
	{
		addToolbar(toolbar, bottomToolbars);
	}

	public void addTopToolbar(final InfinitePagingDataTableToolbar toolbar)
	{
		addToolbar(toolbar, topToolbars);
	}

	public final ToolbarsContainer getTopToolbars()
	{
		return topToolbars;
	}

	public final ToolbarsContainer getBottomToolbars()
	{
		return bottomToolbars;
	}

	public final WebMarkupContainer getBody()
	{
		return body;
	}

	public final Caption getCaption()
	{
		return caption;
	}

	public final InfiniteDataProvider<T> getDataProvider()
	{
		return datagrid.getDataProvider();
	}

	public final List<? extends IColumn<T, S>> getColumns()
	{
		return columns;
	}

	public final void nextPage()
	{
		datagrid.nextPage();
	}

	public final void prevPage()
	{
		datagrid.prevPage();
	}

	public final boolean hasNextPage()
	{
		return datagrid.hasNextPage();
	}

	public final boolean hasPrevPage()
	{
		return datagrid.hasPrevPage();
	}

	public final long getItemsPerPage()
	{
		return datagrid.getItemsPerPage();
	}

	public final void setOffset(long value)
	{
		datagrid.setOffset(value);
	}

	public final long getOffset()
	{
		return datagrid.getOffset();
	}

	public final int getRowCount()
	{
		return datagrid.getRowCount();
	}

	public final InfinitePagingDataTable<T, S> setItemReuseStrategy(final IItemReuseStrategy strategy)
	{
		datagrid.setItemReuseStrategy(strategy);
		return this;
	}

	public void setItemsPerPage(final long items)
	{
		datagrid.setItemsPerPage(items);
	}

	private void addToolbar(final InfinitePagingDataTableToolbar toolbar, final ToolbarsContainer container)
	{
		Args.notNull(toolbar, "toolbar");
		container.getRepeatingView().add(toolbar);
	}

	protected Item<IColumn<T, S>> newCellItem(final String id, final int index, final IModel<IColumn<T, S>> model)
	{
		return new Item<>(id, index, model);
	}

	protected Item<T> newRowItem(final String id, final int index, final IModel<T> model)
	{
		return new Item<>(id, index, model);
	}

	@Override
	protected void onDetach()
	{
		super.onDetach();

		for (IColumn<T, S> column : columns)
		{
			column.detach();
		}
	}

	protected void onPageChanged()
	{
		// noop
	}

	String newToolbarId()
	{
		toolbarIdCounter++;
		return String.valueOf(toolbarIdCounter).intern();
	}

	@Override
	protected void onComponentTag(ComponentTag tag)
	{
		checkComponentTag(tag, "table");
		super.onComponentTag(tag);
	}

	/**
	 * This class acts as a repeater that will contain the toolbar. It makes sure that the table row
	 * group (e.g. thead) tags are only visible when they contain rows in accordance with the HTML
	 * specification.
	 *
	 * @author igor.vaynberg
	 */
	private static class ToolbarsContainer extends WebMarkupContainer
	{
		private static final long serialVersionUID = 1L;

		private final RepeatingView toolbars;

		/**
		 * Constructor
		 *
		 * @param id
		 */
		private ToolbarsContainer(final String id)
		{
			super(id);
			toolbars = new RepeatingView("toolbars");
			add(toolbars);
		}

		public RepeatingView getRepeatingView()
		{
			return toolbars;
		}

		@Override
		public void onConfigure()
		{
			super.onConfigure();
			toolbars.configure();

			Boolean visible = toolbars.visitChildren(new IVisitor<Component, Boolean>()
			{
				@Override
				public void component(Component object, IVisit<Boolean> visit)
				{
					object.configure();
					if (object.isVisible())
					{
						visit.stop(Boolean.TRUE);
					}
					else
					{
						visit.dontGoDeeper();
					}
				}
			});

			if (visible == null)
			{
				visible = false;
			}

			setVisible(visible);
		}
	}

	/**
	 * A caption for the table. It renders itself only if {@link DataTable#getCaptionModel()} has
	 * non-empty value.
	 */
	private static class Caption extends Label
	{
		private static final long serialVersionUID = 1L;

		/**
		 * Construct.
		 *
		 * @param id
		 *            the component id
		 * @param model
		 *            the caption model
		 */
		public Caption(String id, IModel<String> model)
		{
			super(id, model);
		}

		@Override
		protected void onConfigure()
		{
			setRenderBodyOnly(Strings.isEmpty(getDefaultModelObjectAsString()));
			super.onConfigure();
		}

		@Override
		protected IModel<String> initModel()
		{
			// don't try to find the model in the parent
			return null;
		}
	}

	private class DefaultInfinitePagingDataGridView extends InfinitePagingDataGridView<T>
	{
		public DefaultInfinitePagingDataGridView(String id, List<? extends IColumn<T, S>> columns, InfiniteDataProvider<T> dataProvider)
		{
			super(id, columns, dataProvider);
		}

		@Override
		protected Item newCellItem(final String id, final int index, final IModel model)
		{
			Item item = InfinitePagingDataTable.this.newCellItem(id, index, model);
			final IColumn<T, S> column = InfinitePagingDataTable.this.columns.get(index);
			if (column instanceof IStyledColumn)
			{
				item.add(new CssAttributeBehavior()
				{
					private static final long serialVersionUID = 1L;

					@Override
					protected String getCssClass()
					{
						return ((IStyledColumn<T, S>)column).getCssClass();
					}
				});
			}

			return item;
		}

		@Override
		protected Item<T> newRowItem(final String id, final int index, final IModel<T> model)
		{
			return InfinitePagingDataTable.this.newRowItem(id, index, model);
		}
	}
}
