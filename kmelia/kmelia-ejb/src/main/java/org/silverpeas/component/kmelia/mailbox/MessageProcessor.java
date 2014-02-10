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

import com.stratelia.silverpeas.silvertrace.SilverTrace;
import org.springframework.util.Assert;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.ParseException;
import java.io.IOException;

@Named
@Singleton
public class MessageProcessor {

    /**
     * Process a part.
     *
     * @param part            the part to be processed.
     * @param messageDocument the message document that is being built
     * @throws javax.mail.MessagingException
     * @throws java.io.IOException
     */
    private void processMailPart(Part part, MessageDocument messageDocument)
            throws MessagingException, IOException {
        if (isTextPart(part)) {
            processBody(part, messageDocument);
        } else {
            Object content = part.getContent();
            if (content instanceof Multipart) {
                processMultipart((Multipart) content, messageDocument);
            } else if (content instanceof Part) { // such as message/rfc822
                processMailPart((Part) content, messageDocument);
            } else {
                String fileName = getFileName(part);
                if (fileName != null) {
                    Attachment attachment = new Attachment(part.getSize(), fileName, extractContentType(part.getContentType()), part.getInputStream());
                    messageDocument.addAttachment(attachment);
                }
            }
        }
    }

    /**
     * Process the text part of an email.
     *
     * @param part            the part that is being processed.
     * @param messageDocument the message document that is being built
     * @throws java.io.IOException
     * @throws javax.mail.MessagingException
     */
    private void processBody(Part part, MessageDocument messageDocument)
            throws IOException, MessagingException {
        //if there are many candidates for main text part, we only keep the first one
        if (messageDocument.getBody() != null) {
            return;
        }
        Assert.state(part.isMimeType("text/*"));
        String contentType = extractContentType(part.getContentType());
        messageDocument.setBodyContentType(contentType);
        messageDocument.setBody((String) part.getContent());
    }

    /**
     * Process an email, extracting attachments and body to construct a MessageDocument.
     *
     * @param mail the email to be processed.
     * @throws javax.mail.MessagingException
     * @throws java.io.IOException
     */
    public MessageDocument processMessage(Message mail) throws MessagingException, IOException {
        String sender = ((InternetAddress[]) mail.getFrom())[0].getAddress();
        MessageDocument messageDocument = new MessageDocument();
        messageDocument.setSender(sender);
        messageDocument.setSentDate(mail.getSentDate());
        messageDocument.setTitle(mail.getSubject());
        SilverTrace.info("kmelia", this.getClass().getName() + ".processMessage()",
                "Processing message " + mail.getSubject());
        processMailPart(mail, messageDocument);
        return messageDocument;
    }

    private static String extractContentType(String contentType) {
        try {
            ContentType type = new ContentType(contentType);
            return type.getBaseType();
        } catch (ParseException e) {
            SilverTrace.warn("kmelia", MessageProcessor.class.getName() + ".extractContentType()",
                    "Could not extract base type for " + contentType, e);
            return contentType;
        }
    }

    private String getFileName(Part part) throws MessagingException {
        String fileName = part.getFileName();
        if (fileName != null) {
            return fileName;
        }
        final ContentType type;
        try {
            type = new ContentType(part.getContentType());
        } catch (ParseException e) {
            SilverTrace.warn("kmelia", this.getClass().getName(), "Unknown content type : " + part.getContentType()
                    + ", therefore file name can't be resolved", null, e);
            return null;
        }
        return type.getParameter("name");
    }

    /**
     * Check if a part is an attachment, a base64 encoded file or some text.
     *
     * @param part the part to be analyzed.
     * @return true if it is some text - false otherwise.
     * @throws MessagingException
     */
    private boolean isTextPart(Part part) throws MessagingException {
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equals(disposition)) {
            return false;
        }

        if (Part.INLINE.equals(disposition)) {
            return part.isMimeType("text/*") && getFileName(part) == null;
        }

        return part.isMimeType("text/*");
    }

    private void processMultipart(Multipart multipart, MessageDocument messageDocument)
            throws MessagingException, IOException {
        int partsNumber = multipart.getCount();
        for (int i = 0; i < partsNumber; i++) {
            Part part = multipart.getBodyPart(i);
            processMailPart(part, messageDocument);
        }
    }
}
