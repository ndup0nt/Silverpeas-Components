package org.silverpeas.component.kmelia.mailbox.publication;

import com.silverpeas.util.EncodeHelper;
import com.stratelia.silverpeas.silvertrace.SilverTrace;
import com.stratelia.webactiv.util.publication.model.PublicationDetail;
import com.stratelia.webactiv.util.publication.model.PublicationPK;
import lombok.RequiredArgsConstructor;
import org.silverpeas.attachment.model.SimpleAttachment;
import org.silverpeas.attachment.model.SimpleDocument;
import org.silverpeas.attachment.model.SimpleDocumentPK;
import org.silverpeas.component.kmelia.mailbox.MessageAttachment;
import org.silverpeas.component.kmelia.mailbox.MessageDocument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Set;

/**
 * A builder that can prepare a PublicationDetail, its WYSIWYG content and attachments ready to be saved in a given
 * kmelia component in Silverpeas.
 */
@RequiredArgsConstructor
public class PublicationFromMessageDocumentBuilder {
    private final Set<String> attachmentTypesFilter;
    private final String userId;
    private final String componentId;
    private final MessageDocument messageDocument;

    /**
     * Prepare a PublicationDetail and its WYSIWYG content ready to be saved in the kmelia component in Silverpeas.
     *
     * @param importance
     * @return
     */
    public PublicationHolder buildPublication(int importance) {
        PublicationDetail publicationDetail = buildPublicationDetail(importance);
        String wysiwygContent = resolveWysiwygContentFromMessage();
        return new PublicationHolder(publicationDetail, wysiwygContent);
    }

    /**
     * Prepare a publication attachments ready to be saved in the kmelia component in Silverpeas
     *
     * @param publicationId the id of the publication the attachments will be linked to
     * @return
     */
    public Collection<AttachmentHolder> buildAttachments(String publicationId) {
        Collection<AttachmentHolder> res = new ArrayList<AttachmentHolder>();
        for (MessageAttachment att : messageDocument.getAttachments()) {
            if (SilverTrace.TRACE_LEVEL_DEBUG >= SilverTrace.getTraceLevel("kmelia", true)) {
                SilverTrace.debug("kmelia", this.getClass().getName(),
                        "Handling attachment with name " + att.getFileName() + " and content type " + att.getContentType());
            }
            if (attachmentTypesFilter.contains(att.getContentType())) {
                res.add(buildAttachment(att, publicationId));
            }
        }
        return res;
    }

    private AttachmentHolder buildAttachment(MessageAttachment att, String pubId) {
        SimpleDocumentPK attachmentPk = new SimpleDocumentPK(null, componentId);
        SimpleDocument attachmentDocument = new SimpleDocument(attachmentPk, pubId, -1, false,
                new SimpleAttachment(att.getFileName(), null, att.getFileName(), null, att.getSize(),
                        att.getContentType(), userId, new Date(), null));
        return new AttachmentHolder(attachmentDocument, att.getInputStream());
    }

    private PublicationDetail buildPublicationDetail(int importance) {
        logDebug();
        Date creationDate = new Date();
        PublicationDetail publication = new PublicationDetail(
                new PublicationPK("dummy", componentId), messageDocument.getSubject(), null, creationDate,
                creationDate, null, userId, importance, null, null, null, PublicationDetail.VALID);
        return publication;
    }

    private void logDebug() {
        if (SilverTrace.TRACE_LEVEL_DEBUG >= SilverTrace.getTraceLevel("kmelia", true)) {
            SilverTrace.debug("kmelia", this.getClass().getName(),
                    "Message " + messageDocument.getSubject() + " has a body with content type "
                            + messageDocument.getBodyContentType());
        }
    }

    private String resolveWysiwygContentFromMessage() {
        if ("text/plain".equals(messageDocument.getBodyContentType())) {
            return EncodeHelper.javaStringToHtmlParagraphe(messageDocument.getBody());
        }
        return messageDocument.getBody();
    }

}
