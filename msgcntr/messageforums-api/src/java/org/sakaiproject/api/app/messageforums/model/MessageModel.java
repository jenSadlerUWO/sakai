/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2005 The Regents of the University of Michigan, Trustees of Indiana University,
 *                  Board of Trustees of the Leland Stanford, Jr., University, and The MIT Corporation
 * 
 * Licensed under the Educational Community License Version 1.0 (the "License");
 * By obtaining, using and/or copying this Original Work, you agree that you have read,
 * understand, and will comply with the terms and conditions of the Educational Community License.
 * You may obtain a copy of the License at:
 * 
 *      http://cvs.sakaiproject.org/licenses/license_1_0.html
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 **********************************************************************************/

package org.sakaiproject.api.app.messageforums.model;

import java.util.List;

import org.sakaiproject.api.app.messageforums.Message;

public interface MessageModel extends MutableEntityModel {

    public Message createPersistible();

    public Boolean getApproved();

    public void setApproved(Boolean approved);

    public List getAttachments();

    public void setAttachments(List attachments);

    public String getAuthor();

    public void setAuthor(String author);

    public String getBody();

    public void setBody(String body);

    public String getGradebook();

    public void setGradebook(String gradebook);

    public String getGradebookAssignment();

    public void setGradebookAssignment(String gradebookAssignment);

    public MessageModel getInReplyTo();

    public void setInReplyTo(MessageModel inReplyTo);

    public String getLabel();

    public void setLabel(String label);

    public String getTitle();

    public void setTitle(String title);

    //public Type getType();

    //public void setType(Type type);

}