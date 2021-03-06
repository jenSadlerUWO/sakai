package org.sakaiproject.sitestats.tool.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.sakaiproject.sitestats.api.PrefsData;
import org.sakaiproject.sitestats.api.StatsManager;
import org.sakaiproject.sitestats.api.event.EventInfo;
import org.sakaiproject.sitestats.api.event.ToolInfo;
import org.sakaiproject.sitestats.api.parser.EventParserTip;
import org.sakaiproject.sitestats.api.report.ReportManager;
import org.sakaiproject.sitestats.tool.facade.Locator;

/**
 * @author plukasew, bjones86
 */
public class Tools
{
	public static List<String> getToolIds(String siteId, PrefsData pd)
	{
		List<String> toolIds = new ArrayList<>();
		for (ToolInfo ti : pd.getToolEventsDef())
		{
			if (ti.isSelected() && isToolSupported(siteId, ti, pd))
			{
				toolIds.add(ti.getToolId());
			}
		}

		return toolIds;
	}

	public static List<String> getEventsForToolFilter(String toolFilter, String siteId, PrefsData pd, boolean isForUserTracking)
	{
		if (ReportManager.WHAT_EVENTS_ALLTOOLS.equals(toolFilter) || ReportManager.WHAT_EVENTS_ALLTOOLS_EXCLUDE_CONTENT_READ.equals(toolFilter))
		{
			List<String> eventIDs = pd.getToolEventsStringList();
			if (isForUserTracking)
			{
				eventIDs.add(StatsManager.SITEVISIT_EVENTID);
			}
			if (ReportManager.WHAT_EVENTS_ALLTOOLS_EXCLUDE_CONTENT_READ.equals(toolFilter))
			{
				eventIDs.remove("content.read");
			}
			return eventIDs;
		}
		else if (isForUserTracking && StatsManager.PRESENCE_TOOLID.equals(toolFilter))
		{
			return Arrays.asList(StatsManager.SITEVISIT_EVENTID);
		}

		List<String> eventIds = new ArrayList<>();
		for (ToolInfo ti : pd.getToolEventsDef())
		{
			if (ti.isSelected() && ti.getToolId().equals(toolFilter) && isToolSupported(siteId, ti, pd))
			{
				for (EventInfo ei : ti.getEvents())
				{
					if (ei.isSelected())
					{
						eventIds.add(ei.getEventId());
					}
				}
			}
		}

		return eventIds;
	}

	public static boolean isToolSupported(String siteId, ToolInfo toolInfo, PrefsData pd)
	{
		if (Locator.getFacade().getStatsManager().isEventContextSupported())
		{
			return true;
		}
		else
		{
			List<ToolInfo> siteTools = Locator.getFacade().getEventRegistryService().getEventRegistry(siteId, pd.isListToolEventsOnlyAvailableInSite());
			for (ToolInfo t : siteTools)
			{
				if (t.getToolId().equals(toolInfo.getToolId()))
				{
					EventParserTip parserTip = t.getEventParserTip();
					return parserTip != null && StatsManager.PARSERTIP_FOR_CONTEXTID.equals(parserTip.getFor());
				}
			}
		}

		return false;
	}
}
