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
import org.silverpeas.attachment.AttachmentServiceFactory;
import org.silverpeas.component.kmelia.mailbox.publication.AttachmentHolder;
import org.silverpeas.component.kmelia.mailbox.publication.PublicationFromMessageDocumentBuilder;
import org.silverpeas.component.kmelia.mailbox.publication.PublicationHolder;
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
    private static final String ATTACHMENT_TYPES_FILTER_PROP = "kmelia.mailbox.job.attachmentTypesFilter";
    private static final int PUBLI_IMPORTANCE = 1;
    private static final String USER_ID = "0";

    private final MessageProcessor messageProcessor;
    private final String subscribedComponentId;
    private final MailboxReader mailboxReader;
    private final Set<String> attachmentTypesFilter;

    @Inject
    private SubscribedComponentListener(ResourceLocator kmeliaSettings,
                                        MessageProcessor theMessageProcessor, MailboxReader theMailboxReader) {
        this.messageProcessor = theMessageProcessor;
        this.subscribedComponentId = kmeliaSettings.getString(SUBSCRIBED_COMP_PROP);
        final String[] filterArray = kmeliaSettings.getString(ATTACHMENT_TYPES_FILTER_PROP).split(",");
        this.attachmentTypesFilter = new HashSet<String>(Arrays.asList(filterArray));
        this.mailboxReader = theMailboxReader;
    }

    @Override
    public void onMailboxRead(MailboxReadEvent event) throws MailboxEventHandlingException {
        if(event.getMessages().length == 0){
            return;
        }
        Map<String, NodeDetail> foldersMapping = buildFoldersMapping();
        for (Message msg : event.getMessages()) {
            handleMessage(msg, foldersMapping);
        }
    }

    private void handleMessage(Message msg, Map<String, NodeDetail> foldersMapping) throws MailboxEventHandlingException {
        MessageDocument doc;
        try {
            doc = messageProcessor.processMessage(msg);
        } catch (MessagingException e) {
            throw new MailboxEventHandlingException(this.getClass().getName(), "An error occured while reading some message information",e);
        } catch (IOException e) {
            throw new MailboxEventHandlingException(this.getClass().getName(), "An error occured while reading some message content",e);
        }
        PublicationFromMessageDocumentBuilder builder = new PublicationFromMessageDocumentBuilder(attachmentTypesFilter, USER_ID, subscribedComponentId, doc);
        String pubId = storeMessageAsPublication(doc, builder, foldersMapping);
        storePublicationAttachments(pubId, builder);
    }

    private void storePublicationAttachments(String pubId, PublicationFromMessageDocumentBuilder builder) {
        Collection<AttachmentHolder> attachments = builder.buildAttachments(pubId);
        for (AttachmentHolder att : attachments) {
            storePubAttachment(att);
        }
    }

    private void storePubAttachment(AttachmentHolder att) {
        AttachmentServiceFactory.getAttachmentService().createAttachment(att.getSimpleDocument(), att.getInputStream());
    }

    private String storeMessageAsPublication(MessageDocument doc, PublicationFromMessageDocumentBuilder builder, Map<String, NodeDetail> foldersMapping) throws MailboxEventHandlingException {
        NodeDetail targetFolder = resolveTargetFolder(doc, foldersMapping);
        assert targetFolder != null;
        logTargetFolder(doc, targetFolder);
        PublicationHolder publicationHolder = builder.buildPublication(PUBLI_IMPORTANCE);
        String pubId = getKmeliaBm().createPublicationIntoTopic(publicationHolder.getPublicationDetail(), targetFolder.getNodePK());
        WysiwygController.save(publicationHolder.getWysiwygContent(), subscribedComponentId, pubId, USER_ID, null, true);
        return pubId;
    }

    private void logTargetFolder(MessageDocument doc, NodeDetail targetFolder){
        if(SilverTrace.TRACE_LEVEL_DEBUG >= SilverTrace.getTraceLevel("kmelia", true)){
            SilverTrace.debug("kmelia", this.getClass().getName(),
                    "Message " + doc.getSubject() + " will go into folder " + targetFolder.getName());
        }
    }

    private NodeDetail resolveTargetFolder(MessageDocument doc, Map<String, NodeDetail> foldersMapping) throws MailboxEventHandlingException {
        NodeDetail res = null;

        if (doc.getFromAddress() != null) {
            int indexOfArrob = doc.getFromAddress().indexOf('@');
            if (indexOfArrob != -1 && indexOfArrob < doc.getFromAddress().length() - 1) {
                String domainName = doc.getFromAddress().substring(indexOfArrob + 1);
                res = foldersMapping.get(domainName);
            }
        }
        if(res == null){
            return getDefaultTargetFolder(foldersMapping);
        }
        return res;
    }

    private NodeDetail getDefaultTargetFolder(Map<String, NodeDetail> foldersMapping) throws MailboxEventHandlingException {
        NodeDetail defaultTargetFolder = foldersMapping.get(null);
        if(defaultTargetFolder == null){
            throw new MailboxEventHandlingException(this.getClass().getName(), "Could not find default folder. " +
                    "Check that there is at least one folder with an empty description");
        }
        return defaultTargetFolder;
    }

    private Map<String, NodeDetail> buildFoldersMapping() {
        HashMap<String, NodeDetail> foldersByDescription = new HashMap<String, NodeDetail>();
        foldersByDescription.clear();
        ArrayList<NodeDetail> subTree = getNodeBm().getSubTree(new NodePK(NodePK.ROOT_NODE_ID, subscribedComponentId));
        for (NodeDetail node : subTree) {
            if(StringUtil.isBlank(node.getDescription())){
                NodeDetail defaultFolder = foldersByDescription.get(null);
                //the first folder with an empty description will be the default folder
                if(defaultFolder == null){
                    foldersByDescription.put(null, node);
                }
            } else {
                foldersByDescription.put(node.getDescription(), node);
            }
        }
        return foldersByDescription;
    }

    @PostConstruct
    private void registerThis() {
        // this listener registers itself as a listener for mailbox read events
        mailboxReader.registerListener(this);
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
