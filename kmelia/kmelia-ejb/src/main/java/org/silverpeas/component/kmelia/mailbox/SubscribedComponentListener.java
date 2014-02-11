package org.silverpeas.component.kmelia.mailbox;

import com.silverpeas.util.StringUtil;
import com.stratelia.silverpeas.silvertrace.SilverTrace;
import com.stratelia.webactiv.kmelia.control.ejb.KmeliaBm;
import com.stratelia.webactiv.util.EJBUtilitaire;
import com.stratelia.webactiv.util.JNDINames;
import com.stratelia.webactiv.util.ResourceLocator;
import com.stratelia.webactiv.util.node.control.NodeBm;
import com.stratelia.webactiv.util.node.model.NodeDetail;
import com.stratelia.webactiv.util.node.model.NodePK;
import com.stratelia.webactiv.util.node.model.NodeRuntimeException;
import com.stratelia.webactiv.util.publication.model.PublicationDetail;
import com.stratelia.webactiv.util.publication.model.PublicationPK;
import org.silverpeas.attachment.AttachmentServiceFactory;
import org.silverpeas.attachment.model.SimpleAttachment;
import org.silverpeas.attachment.model.SimpleDocument;
import org.silverpeas.attachment.model.SimpleDocumentPK;
import org.silverpeas.wysiwyg.control.WysiwygController;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.IOException;
import java.util.*;

/**
 * A message listener that stores read messages into a given kmelia component, resolving target folder from
 * the message sender email domain.
 */
@Named
@Singleton
public class SubscribedComponentListener implements MailboxReadEventListener {
    private static final String SUBSCRIBED_COMP_PROP = "kmelia.mailbox.job.subscribedComponentId";
    private static final String DEFAULT_TARGET_FOLDER_PROP = "kmelia.mailbox.job.defaultTargetFolderId";
    private static final String ATTACHMENT_TYPES_FILTER_PROP = "kmelia.mailbox.job.attachmentTypesFilter";
    private static final int PUBLI_IMPORTANCE = 1;
    private static final String USER_ID = "0";

    private final Map<String, NodeDetail> foldersByDescription = new HashMap<String, NodeDetail>();
    private final MessageProcessor messageProcessor;
    private final String subscribedComponentId;
    private final String defaultTargetFolderId;
    private final MailboxReader mailboxReader;
    private final Set<String> attachmentTypesFilter;

    @Inject
    private SubscribedComponentListener(ResourceLocator kmeliaSettings,
                                        MessageProcessor theMessageProcessor, MailboxReader theMailboxReader) {
        this.messageProcessor = theMessageProcessor;
        this.subscribedComponentId = kmeliaSettings.getString(SUBSCRIBED_COMP_PROP);
        this.defaultTargetFolderId = kmeliaSettings.getString(DEFAULT_TARGET_FOLDER_PROP);
        final String[] filterArray = kmeliaSettings.getString(ATTACHMENT_TYPES_FILTER_PROP).split(",");
        this.attachmentTypesFilter = new HashSet<String>(Arrays.asList(filterArray));
        this.mailboxReader = theMailboxReader;
    }

    @Override
    public void onMailboxRead(MailboxReadEvent event) throws MailboxEventHandlingException {
        if(event.getMessages().length > 0){
            refreshFoldersMapping();
        }
        for (Message msg : event.getMessages()) {
            handleMessage(msg);
        }
    }

    private void handleMessage(Message msg) throws MailboxEventHandlingException {
        MessageDocument doc;
        try {
            doc = messageProcessor.processMessage(msg);
        } catch (MessagingException e) {
            throw new MailboxEventHandlingException(this.getClass().getName(), "An error occured while reading some message information",e);
        } catch (IOException e) {
            throw new MailboxEventHandlingException(this.getClass().getName(), "An error occured while reading some message content",e);
        }
        String pubId = storeMessageAsPublication(doc);
        storeMessageAttachmentsAsPublicationAttachments(pubId, doc);
    }

    private void storeMessageAttachmentsAsPublicationAttachments(String pubId, MessageDocument doc) {
        for (MessageAttachment att : doc.getAttachments()) {
            if(SilverTrace.TRACE_LEVEL_DEBUG >= SilverTrace.getTraceLevel("kmelia", true)){
                SilverTrace.debug("kmelia", this.getClass().getName(),
                        "Handling attachment with name " + att.getFileName() + " and content type " + att.getContentType());
            }
            if(attachmentTypesFilter.contains(att.getContentType())){
                storeMsgAttachmentAsPubAttachment(att, pubId);
            }
        }
    }

    private void storeMsgAttachmentAsPubAttachment(MessageAttachment att, String pubId) {
        SimpleDocumentPK attachmentPk = new SimpleDocumentPK(null, subscribedComponentId);
        SimpleDocument attachmentDocument = new SimpleDocument(attachmentPk, pubId, -1, false,
                new SimpleAttachment(att.getFileName(), null, att.getFileName(), null, att.getSize(),
                        att.getContentType(), USER_ID, new Date(), null));

        AttachmentServiceFactory.getAttachmentService().createAttachment(attachmentDocument, att.getInputStream());
    }

    /**
     * @param doc the message model
     * @return the new publication id
     */
    private String storeMessageAsPublication(MessageDocument doc) throws MailboxEventHandlingException {
        NodeDetail targetFolder = resolveTargetFolder(doc);
        assert targetFolder != null;
        if(SilverTrace.TRACE_LEVEL_DEBUG >= SilverTrace.getTraceLevel("kmelia", true)){
            SilverTrace.debug("kmelia", this.getClass().getName(),
                    "Message  " + doc.getSubject() + " will go into folder " + targetFolder.getName());
        }
        Date creationDate = new Date();
        PublicationDetail publication = new PublicationDetail(
                new PublicationPK("dummy", subscribedComponentId), doc.getSubject(), null, creationDate,
                creationDate, null, USER_ID, PUBLI_IMPORTANCE, null, null, null, PublicationDetail.VALID);
        String pubId = getKmeliaBm().createPublicationIntoTopic(publication, targetFolder.getNodePK());
        WysiwygController.save(doc.getBody(), subscribedComponentId, pubId, USER_ID, null, true);
        return pubId;
    }

    private NodeDetail resolveTargetFolder(MessageDocument doc) {
        NodeDetail res = null;
        if (doc.getSender() != null) {
            int indexOfArrob = doc.getSender().indexOf('@');
            if (indexOfArrob != -1 && indexOfArrob < doc.getSender().length() - 1) {
                String domainName = doc.getSender().substring(indexOfArrob + 1);
                res = resolveFolderFromDomain(domainName);
            }
        }
        if(res == null){
            return getNodeBm().getDetail(new NodePK(defaultTargetFolderId, subscribedComponentId));
        }
        return res;
    }

    private NodeDetail getDefaultTargetFolder() throws MailboxEventHandlingException {
        try{
            return getNodeBm().getDetail(new NodePK(defaultTargetFolderId, subscribedComponentId));
        } catch (NodeRuntimeException ex){
            throw new MailboxEventHandlingException(this.getClass().getName(), "Could not find default folder. " +
                    "Check that " + DEFAULT_TARGET_FOLDER_PROP + " is well set up", ex);
        }
    }

    private NodeDetail resolveFolderFromDomain(String domainName) {
        return foldersByDescription.get(domainName);
    }

    @PostConstruct
    private void registerThis() {
        // this listener registers itself as a listener for mailbox read events
        mailboxReader.registerListener(this);
    }

    private void refreshFoldersMapping() {
        foldersByDescription.clear();
        ArrayList<NodeDetail> subTree = getNodeBm().getSubTree(new NodePK(NodePK.ROOT_NODE_ID, subscribedComponentId));
        for (NodeDetail node : subTree) {
            if (StringUtil.isNotBlank(node.getDescription())) {
                foldersByDescription.put(node.getDescription(), node);
            }
        }
    }

    private static class SingletonsHolder{
        private final static NodeBm nodeBm = EJBUtilitaire.getEJBObjectRef(JNDINames.NODEBM_EJBHOME, NodeBm.class);
        private final static KmeliaBm kmeliaBm = EJBUtilitaire.getEJBObjectRef(JNDINames.KMELIABM_EJBHOME, KmeliaBm.class);
    }

    private NodeBm getNodeBm() {
        return SingletonsHolder.nodeBm;
    }

    private KmeliaBm getKmeliaBm() {
        return SingletonsHolder.kmeliaBm;
    }
}
