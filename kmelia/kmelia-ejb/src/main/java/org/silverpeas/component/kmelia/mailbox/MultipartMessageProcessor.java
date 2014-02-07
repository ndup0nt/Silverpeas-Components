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

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import java.io.IOException;

/**
 * An object that can deal with a multipart message to extract its main body text and its attachments.
 */
public class MultipartMessageProcessor {
    /**
     * Return the primary text content of the message.
     * Sourced from http://www.oracle.com/technetwork/java/javamail/faq/index.html#mainbody
     */
    public String getBodyText(Part p) throws
            MessagingException, IOException {
        if (p.isMimeType("text/*")) {
            String s = (String) p.getContent();
            //textIsHtml = p.isMimeType("text/html");
            return s;
        }

        if (p.isMimeType("multipart/alternative")) {
            // prefer html text over plain text
            Multipart mp = (Multipart) p.getContent();
            String text = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/plain")) {
                    if (text == null)
                        text = getBodyText(bp);
                    continue;
                } else if (bp.isMimeType("text/html")) {
                    String s = getBodyText(bp);
                    if (s != null)
                        return s;
                } else {
                    return getBodyText(bp);
                }
            }
            return text;
        } else if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = getBodyText(mp.getBodyPart(i));
                if (s != null)
                    return s;
            }
        }

        return null;
    }

//    public Collection<Part> findAttachments(Part message) throws IOException, MessagingException {
//        Object content = message.getContent();
//        if (content instanceof Multipart) {
//            processMultipart((Multipart) content, message);
//        }
//        return null;
//    }
//
//
//    public void processMultipart(Multipart multipart, Part message) throws MessagingException, IOException {
//        int partsNumber = multipart.getCount();
//        for (int i = 0; i < partsNumber; i++) {
//            Part part = multipart.getBodyPart(i);
//            processMailPart(part, message);
//        }
//    }
//
//    public void processMailPart(Part part, Message message)
//            throws MessagingException, IOException {
//        // Using isMimeType to determine the content type avoids fetching the actual content data until we need it.
//        if (!isMainTextPart(part)) {
//            Object content = part.getContent();
//            if (content instanceof Multipart) {
//                processMultipart((Multipart) content, message);
//            } else {
//                String fileName = getFileName(part);
//                if (fileName != null) {
//                    Attachment attachment = new Attachment();
//                    attachment.setSize(part.getSize());
//                    attachment.setFileName(fileName);
//                    attachment.setContentType(extractContentType(part.getContentType()));
//                    String attachmentPath = saveAttachment(part, message.getComponentId(), message.
//                            getMessageId());
//                    attachment.setPath(attachmentPath);
//                    message.getAttachments().add(attachment);
//                }
//            }
//        } else {
//            processBody((String) part.getContent(), extractContentType(part.getContentType()), message);
//        }
//    }
//
//    /**
//     * Analyze the part to check if it is an attachment, a base64 encoded file or some text.
//     *
//     * @param part the part to be analyzed.
//     * @return true if it is some text - false otherwise.
//     * @throws MessagingException
//     */
//    private boolean isMainTextPart(Part part) throws MessagingException {
//        String disposition = part.getDisposition();
//        if (!Part.ATTACHMENT.equals(disposition) &&
//                !Part.INLINE.equals(disposition)) {
//            final ContentType type;
//            try {
//                type = new ContentType(part.getContentType());
//            } catch (ParseException e) {
//                SilverTrace.warn("kmelia", this.getClass().getName(), "Unknown content type : " + part.getContentType()
//                        + ", we'll assume this is not a text part", null, e);
//                return false;
//            }
//            return "text".equalsIgnoreCase(type.getPrimaryType());
//
//        }
//        if (Part.INLINE.equals(disposition)) {
//            ContentType type;
//            try {
//                type = new ContentType(part.getContentType());
//            } catch (ParseException e) {
//                SilverTrace.warn("kmelia", this.getClass().getName(), "Unknown content type : " + part.getContentType()
//                        + ", we'll assume this is not a text part", null, e);
//                return false;
//            }
//            return "text".equalsIgnoreCase(type.getPrimaryType()) && getFileName(part) == null;
//        }
//        return false;
//    }
//
//    private String getFileName(Part part) throws MessagingException {
//        String fileName = part.getFileName();
//        if (fileName != null) {
//            return fileName;
//        }
//        final ContentType type;
//        try {
//            type = new ContentType(part.getContentType());
//        } catch (ParseException e) {
//            SilverTrace.warn("kmelia", this.getClass().getName(), "Unknown content type : " + part.getContentType()
//                    + ", therefore file name can't be resolved", null, e);
//            return null;
//        }
//        return type.getParameter("name");
//    }
//
//    private int level;
//    private int attnum;

//    public void dumpPart(Part p) throws Exception {
//
//        /** Dump input stream ..
//
//         InputStream is = p.getInputStream();
//         // If "is" is not already buffered, wrap a BufferedInputStream
//         // around it.
//         if (!(is instanceof BufferedInputStream))
//         is = new BufferedInputStream(is);
//         int c;
//         while ((c = is.read()) != -1)
//         System.out.write(c);
//
//         **/
//
//	/*
//     * Using isMimeType to determine the content type avoids
//	 * fetching the actual content data until we need it.
//	 */
//        if (p.isMimeType("text/plain")) {
//            return null;
//        } else if (p.isMimeType("multipart/*")) {
//            Multipart mp = (Multipart) p.getContent();
//            level++;
//            int count = mp.getCount();
//            for (int i = 0; i < count; i++)
//                dumpPart(mp.getBodyPart(i));
//            level--;
//        } else if (p.isMimeType("message/rfc822")) {
//            level++;
//            dumpPart((Part) p.getContent());
//            level--;
//        }
//
//        	/*
//	 * If we're saving attachments, write out anything that
//	 * looks like an attachment into an appropriately named
//	 * file.  Don't overwrite existing files to prevent
//	 * mistakes.
//	 */
//        if (level != 0 && p instanceof MimeBodyPart && !p.isMimeType("multipart/*")) {
//            String disp = p.getDisposition();
//            // many mailers don't include a Content-Disposition
//            if (disp == null || disp.equalsIgnoreCase(Part.ATTACHMENT)) {
//                String filename = p.getFileName();
//                if (filename == null)
//                    filename = "Attachment" + attnum++;
//                try {
//                    File f = new File(filename);
//                    if (f.exists())
//                        // XXX - could try a series of names
//                        throw new IOException("file exists");
//                    ((MimeBodyPart)p).saveFile(f);
//                } catch (IOException ex) {
//                    pr("Failed to save attachment: " + ex);
//                }
//                pr("---------------------------");
//            }
//        }
//
//    }
}