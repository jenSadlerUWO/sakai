/**********************************************************************************
 * $URL:  $
 * $Id:  $
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007, 2008, 2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.site.tool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.DefaultConfigurationBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.fonts.substitute.FontQualifier;
import org.apache.fop.fonts.substitute.FontSubstitution;
import org.apache.fop.fonts.substitute.FontSubstitutions;
import org.apache.fop.apps.MimeConstants;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.site.util.Participant;
import org.sakaiproject.site.util.SiteParticipantHelper;
import org.sakaiproject.thread_local.cover.ThreadLocalManager;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.util.BasicAuth;
import org.sakaiproject.util.RequestFilter;
import org.sakaiproject.util.ResourceLoader;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * this is the servlet to return the status of site copy thread based on the SessionState variable 
 * @author zqian
 *
 */
public class SiteInfoToolServlet extends HttpServlet
{
	private static final long serialVersionUID = 1L;
	
    private transient BasicAuth basicAuth;
    protected static final Logger log = LoggerFactory.getLogger(SiteInfoToolServlet.class);
    
    // create transformerFactory object needed by generatePDF
    private TransformerFactory transformerFactory = null;

    // create DocumentBuilder object needed by print PDF
	DocumentBuilder docBuilder =  null;

	// XML Node/Attribute Names
	protected static final String PARTICIPANTS_NODE_NAME = "PARTICIPANTS";
	protected static final String SITE_TITLE_NODE_NAME = "SITE_TITLE";
	protected static final String PARTICIPANT_NODE_NAME = "PARTICIPANT";
	protected static final String PARTICIPANT_NAME_NODE_NAME = "NAME";
	protected static final String PARTICIPANT_SECTIONS_NODE_NAME = "SECTIONS";
	protected static final String PARTICIPANT_SECTION_NODE_NAME = "SECTION";
	protected static final String PARTICIPANT_ID_NODE_NAME = "ID";
	protected static final String PARTICIPANT_CREDITS_NODE_NAME = "CREDITS";
	protected static final String PARTICIPANT_CREDIT_NODE_NAME = "CREDIT";
	protected static final String PARTICIPANT_ROLE_NODE_NAME = "ROLE";
	protected static final String PARTICIPANT_STATUS_NODE_NAME = "STATUS";
	
	/** Resource bundle using current language locale */
	private static ResourceLoader rb = new ResourceLoader("printParticipant");
    
	// --------------------------------------------------------- Public Methods

	/**
	 * Initialize this servlet.
	 */
	public void init() throws ServletException
	{
		super.init();
        try {
            basicAuth = new BasicAuth();
            basicAuth.init();
            
            transformerFactory = TransformerFactory.newInstance();
    		try
    		{
    			docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    		}
    		catch (ParserConfigurationException e)
    		{
    			log.warn(this + " cannot get DocumentBuilder " + e.getMessage());
    		}

        } catch (Exception e) {
            log.warn(this + "init " + e.getMessage());
        }
	}
	
	/**
	 * respond to an HTTP GET request
	 * 
	 * @param req
	 *        HttpServletRequest object with the client request
	 * @param res
	 *        HttpServletResponse object back to the client
	 * @exception ServletException
	 *            in case of difficulties
	 * @exception IOException
	 *            in case of difficulties
	 */
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
	{
		// process any login that might be present
		basicAuth.doLogin(req);
		
		// catch the login helper requests
		// 1: Get site status: request url is of format https://server_name/sakai-site-manage-tool/tool/sitecopystatus/toolId
		String option = req.getPathInfo();
		String[] parts = option.split("/");
		if ((parts.length == 3) && ("".equals(parts[0])) && ("sitecopystatus".equals(parts[1])))
		{
			getSiteCopyStatus(parts[2], res);
		}
		// 2: Print site participant list: request url if of format https://server_name/sakai-site-manage-tool/tool/printparticipant/siteId
		else if ((parts.length == 3) && ("".equals(parts[0])) && ("printparticipant".equals(parts[1])))
		{
			getSiteParticipantList(parts[2], res);
		}
	}
	
	/**
	 * 
	 * @param toolId
	 * @param res
	 */
	private void getSiteCopyStatus (String toolId, HttpServletResponse res)
	{
		// TODO
		
	}
	
	private void getSiteParticipantList(String siteId, HttpServletResponse res)
	{
		// get the user id
		String userId = SessionManager.getCurrentSessionUserId();
		
		if (userId == null)
		{
			// fail the request, user not logged in yet.
			log.warn(this + " HttpAccess for printing participant of site id =" + siteId + " without user loggin. ");
		}
		else
		{
			String siteReference = SiteService.siteReference(siteId);
			// check whether the user has permission to view the site roster or is super user
			if (SecurityService.unlock(userId, SiteService.SECURE_VIEW_ROSTER, siteReference) || SecurityService.isSuperUser())
			{
				print_participant(siteId);
			}
			else
			{
				log.warn(this + " HttpAccess for printing participant of site id =" + siteId + " with user id = " + userId + ": user does not have permission to view roster. " );
			}
		}
	}
	
	/**
	 * generate PDF file containing all site participant
	 * @param data
	 */
	public void print_participant(String siteId)
	{
		
		HttpServletResponse res = (HttpServletResponse) ThreadLocalManager.get(RequestFilter.CURRENT_HTTP_RESPONSE);
		
		ByteArrayOutputStream outByteStream = new ByteArrayOutputStream();

		res.addHeader("Content-Disposition", "inline; filename=\"participants.pdf\"");
		res.setContentType("application/pdf");
		
		Document document = docBuilder.newDocument();
		
		// get the participant xml document
		generateParticipantXMLDocument(document, siteId);

		generatePDF(document, outByteStream);
		res.setContentLength(outByteStream.size());
		if (outByteStream.size() > 0)
		{
			// Increase the buffer size for more speed.
			res.setBufferSize(outByteStream.size());
		}

		/*
		// output xml for debugging purpose
		try
		{
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
	        Transformer transformer = transformerFactory.newTransformer();
	        DOMSource source = new DOMSource(document);
	        StreamResult result =  new StreamResult(System.out);
	        transformer.transform(source, result);
		}
		catch (Exception e)
		{
			
		}*/
        
		OutputStream out = null;
		try
		{
			out = res.getOutputStream();
			if (outByteStream.size() > 0)
			{
				outByteStream.writeTo(out);
			}
			res.setHeader("Refresh", "0");

			out.flush();
			out.close();
		}
		catch (Throwable ignore)
		{
		}
		finally
		{
			if (out != null)
			{
				try
				{
					out.close();
				}
				catch (Throwable ignore)
				{
				}
			}
		}
	}
	
	/**
	 * Generate participant document
	 * @param doc
	 * @param siteId
	 */
	protected void generateParticipantXMLDocument(Document doc, String siteId)
	{

		List<String> providerCourseList = SiteParticipantHelper.getProviderCourseList(siteId);
		Collection<Participant> participants = SiteParticipantHelper.prepareParticipants(siteId, providerCourseList, "", "");
		
		// Create Root Element
		Element root = doc.createElement(PARTICIPANTS_NODE_NAME);

		String siteTitle;
		
		if (siteId != null)
		{
			try
			{
				Site site = SiteService.getSite(siteId);
				siteTitle = site.getTitle();
	    		
				// site title
				writeStringNodeToDom(doc, root, SITE_TITLE_NODE_NAME, rb.getFormattedMessage("participant_pdf_title", new Object[] {siteTitle}));
			}
			catch (Exception e)
			{
				log.warn(this + ":generateParticipantXMLDocument: Cannot find site with id =" + siteId);
			}
		}

		// Add the Root Element to Document
		doc.appendChild(root);


		if (participants != null)
		{
		
			// Go through all the time ranges (days)
			for (Participant participant : participants)
			{
				// Create Participant Element
				Element participantNode = doc.createElement(PARTICIPANT_NODE_NAME);
				
				// participant name
				writeStringNodeToDom(doc, participantNode, PARTICIPANT_NAME_NODE_NAME, StringUtils.trimToEmpty(participant.getName()));

				// display id
				writeStringNodeToDom(doc, participantNode, PARTICIPANT_ID_NODE_NAME, StringUtils.trimToEmpty(participant.getDisplayId()));

				// sections
				Element sectionsNode = doc.createElement(PARTICIPANT_SECTIONS_NODE_NAME);
				for ( Iterator iSections = participant.getSectionEidList().iterator(); iSections.hasNext();)
				{
					String section = (String) iSections.next();
					writeStringNodeToDom(doc, sectionsNode, PARTICIPANT_SECTION_NODE_NAME, StringUtils.trimToEmpty(section));
				}
				participantNode.appendChild(sectionsNode);

				// credits
				String[] credits = StringUtils.trimToEmpty(participant.getCredits()).replaceAll("<br />", "").split(",");
				Element creditsNode = doc.createElement(PARTICIPANT_CREDITS_NODE_NAME);
				for (String credit : credits)
				{
					writeStringNodeToDom(doc, creditsNode, PARTICIPANT_CREDIT_NODE_NAME, credit.trim());
				}
				participantNode.appendChild(creditsNode);

				// role id
				writeStringNodeToDom(doc, participantNode, PARTICIPANT_ROLE_NODE_NAME, StringUtils.trimToEmpty(participant.getRole()));
				
				// status
				writeStringNodeToDom(doc, participantNode, PARTICIPANT_STATUS_NODE_NAME, StringUtils.trimToEmpty(participant.active?rb.getString("sitegen.siteinfolist.active"):rb.getString("sitegen.siteinfolist.inactive")));
			
				// add participant node to participants node
				root.appendChild(participantNode);
			}
		}
	}
	
	/**
	 * Utility routine to write a string node to the DOM.
	 */
	protected Element writeStringNodeToDom(Document doc, Element parent, String nodeName, String nodeValue)
	{
		if (nodeValue != null)
		{
			Element name = doc.createElement(nodeName);
			name.appendChild(doc.createTextNode(nodeValue));
			parent.appendChild(name);
			return name;
		}

		return null;
	}
	/**
	 * Takes a DOM structure and renders a PDF
	 * 
	 * @param doc
	 *        DOM structure
	 * @param xslFileName
	 *        XSL file to use to translate the DOM document to FOP
	 */
	@SuppressWarnings("unchecked")
	protected void generatePDF(Document doc, OutputStream streamOut)
	{
		String xslFileName = "participants-all-attrs.xsl";
		Locale currentLocale = rb.getLocale();
		if (currentLocale!=null){
			String fullLocale = currentLocale.toString();
			xslFileName = "participants-all-attrs_" + fullLocale + ".xsl";
			InputStream inputStream = getClass().getClassLoader().getResourceAsStream(xslFileName);
			if (inputStream == null){
				xslFileName = "participants-all-attrs_" + currentLocale.getCountry() + ".xsl";
				inputStream = getClass().getClassLoader().getResourceAsStream(xslFileName);
				if (inputStream == null){
					//We use the default file
					xslFileName = "participants-all-attrs.xsl";
				}
			}

			IOUtils.closeQuietly(inputStream);
		}
		String configFileName = "userconfig.xml";
		DefaultConfigurationBuilder cfgBuilder = new DefaultConfigurationBuilder();
		InputStream configInputStream = null;
		try 
		{
			configInputStream = getClass().getClassLoader().getResourceAsStream(configFileName);
			Configuration cfg = cfgBuilder.build(configInputStream);
			
			FopFactory fopFactory = FopFactory.newInstance();
			fopFactory.setUserConfig(cfg);
			fopFactory.setStrictValidation(false);
			FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
			if (!StringUtils.isEmpty(ServerConfigurationService.getString("pdf.default.font"))) {
			    // this allows font substitution to support i18n chars in PDFs - SAK-21909
			    FontQualifier fromQualifier = new FontQualifier();
			    fromQualifier.setFontFamily("DEFAULT_FONT");
			    FontQualifier toQualifier = new FontQualifier();
			    toQualifier.setFontFamily(ServerConfigurationService.getString("pdf.default.font", "Helvetica"));
			    FontSubstitutions result = new FontSubstitutions();
			    result.add(new FontSubstitution(fromQualifier, toQualifier));
			    fopFactory.getFontManager().setFontSubstitutions(result);
			}
			Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, streamOut);
			InputStream in = getClass().getClassLoader().getResourceAsStream(xslFileName);
			Transformer transformer = transformerFactory.newTransformer(new StreamSource(in));
			transformer.setParameter("titleName", rb.getString("sitegen.siteinfolist.title.name"));
			transformer.setParameter("titleId", rb.getString("sitegen.siteinfolist.title.id"));
			transformer.setParameter("titleSection", rb.getString("sitegen.siteinfolist.title.section"));
			transformer.setParameter("titleCredit", rb.getString("sitegen.siteinfolist.title.credit"));
			transformer.setParameter("titleRole", rb.getString("sitegen.siteinfolist.title.role"));
			transformer.setParameter("titleStatus", rb.getString("sitegen.siteinfolist.title.status"));

			Source src = new DOMSource(doc);
			transformer.transform(src, new SAXResult(fop.getDefaultHandler()));
		}
		catch (Exception e)
		{
			log.warn(this+".generatePDF(): " + e);
		}
		finally
		{
			IOUtils.closeQuietly(configInputStream);
		}
	}
}
