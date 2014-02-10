/**
 * Copyright (C) 2000 - 2013 Silverpeas
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * As a special exception to the terms and conditions of version 3.0 of the GPL, you may
 * redistribute this Program in connection with Free/Libre Open Source Software ("FLOSS")
 * applications as described in Silverpeas's FLOSS exception. You should have received a copy of the
 * text describing the FLOSS exception, and it is also available here:
 * "http://www.silverpeas.org/docs/core/legal/floss_exception.html"
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package org.silverpeas.component.kmelia.mailbox;

import com.silverpeas.util.AssertArgument;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Model object for an email.
 */
public class MessageDocument {

    private final Set<Attachment> attachments = new HashSet<Attachment>();
    @Getter
    private final String subject;
    @Getter
    private final String sender;
    private final Date sentDate;
    @Getter
    @Setter
    private String body;
    @Getter
    @Setter
    private String bodyContentType;

    public MessageDocument(String theSubject, String theSender, Date theSentDate) {
        this.subject = theSubject;
        this.sender = theSender;
        AssertArgument.assertNotNull(theSentDate, "sentDate is mandatory");
        this.sentDate = (Date) theSentDate.clone();
    }

    public Set<Attachment> getAttachments() {
        return Collections.unmodifiableSet(attachments);
    }

    public void addAttachment(Attachment anAttachment) {
        this.attachments.add(anAttachment);
    }

    public Date getSentDate() {
        if (sentDate == null) {
            return null;
        }
        return new Date(sentDate.getTime());
    }
}
