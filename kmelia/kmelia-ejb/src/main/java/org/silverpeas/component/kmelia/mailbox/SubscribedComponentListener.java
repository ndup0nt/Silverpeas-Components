package org.silverpeas.component.kmelia.mailbox;

import com.silverpeas.util.StringUtil;
import com.stratelia.silverpeas.silvertrace.SilverTrace;
import com.stratelia.webactiv.util.EJBUtilitaire;
import com.stratelia.webactiv.util.JNDINames;
import com.stratelia.webactiv.util.ResourceLocator;
import com.stratelia.webactiv.util.node.control.NodeBm;
import com.stratelia.webactiv.util.node.model.NodeDetail;
import com.stratelia.webactiv.util.node.model.NodePK;
import org.apache.commons.io.IOUtils;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * A message listener that stores read messages into a given kmelia component, resolving target folder from
 * the message sender email domain.
 */
@Named
@Singleton
//TODO remove all System.out
public class SubscribedComponentListener implements MessageListener{
    private static final String SUBSCRIBED_COMP_PROP = "kmelia.mailbox.job.subscribingComponentId";
    private static final String DEFAULT_TARGET_FOLDER_PROP = "kmelia.mailbox.job.defaultTargetFolderId";

    private NodeBm nodeBm;
    private final Map<String, NodeDetail> foldersByDescription = new HashMap<String, NodeDetail>();
    private final MessageProcessor messageProcessor;
    private final String subscribedComponentId;
    private final String defaultTargetFolderId;

    @Inject
    private SubscribedComponentListener(ResourceLocator kmeliaSettings,
                                        MessageProcessor theMessageProcessor) {
        //initFoldersMapping();
        this.messageProcessor = theMessageProcessor;
        this.subscribedComponentId = kmeliaSettings.getString(SUBSCRIBED_COMP_PROP);
        this.defaultTargetFolderId = kmeliaSettings.getString(DEFAULT_TARGET_FOLDER_PROP);
    }

    @Override
    public void onMailboxRead(ReadMailboxEvent event) throws MessagingException, IOException {
        for (Message msg : event.getMessages()) {
            MessageDocument doc = messageProcessor.processMessage(msg);
            NodeDetail targetFolder = resolveTargetFolder(doc);
            System.out.println("Target folder : " + targetFolder.getName());
            System.out.println("Message subject : " + doc.getSubject());
            System.out.println("Message content : " + doc.getBody());
            System.out.println("Message attachments count : " + doc.getAttachments().size());
            for (MessageAttachment att : doc.getAttachments()) {
                StringWriter writer = new StringWriter();
                IOUtils.copy(att.getInputStream(), writer);
                System.out.println(att.getFileName() + " : ");
                System.out.println(writer.toString());
            }
        }
    }

    private NodeDetail resolveTargetFolder(MessageDocument doc){
        if(doc.getSender() != null){
            int indexOfArrob = doc.getSender().indexOf('@');
            if(indexOfArrob != -1 && indexOfArrob < doc.getSender().length() - 1){
                String domainName = doc.getSender().substring(indexOfArrob + 1);
                return resolveFolderFromDomain(domainName);
            }
        }
        return getNodeBm().getDetail(new NodePK(defaultTargetFolderId, subscribedComponentId));
    }

    private NodeDetail resolveFolderFromDomain(String domainName) {
        return foldersByDescription.get(domainName);
    }

    @PostConstruct
    private void initFoldersMapping(){
        ArrayList<NodeDetail> subTree = getNodeBm().getSubTree(new NodePK(NodePK.ROOT_NODE_ID, subscribedComponentId));
        for(NodeDetail node : subTree){
            if(StringUtil.isNotBlank(node.getDescription())){
                foldersByDescription.put(node.getDescription(), node);
                System.out.println("Folder " + node.getName() +
                        " will be mapped to domain " + node.getDescription());
            }
        }
    }

    private NodeBm getNodeBm() {
        if (nodeBm == null) {
            nodeBm = EJBUtilitaire.getEJBObjectRef(JNDINames.NODEBM_EJBHOME, NodeBm.class);
        }
        return nodeBm;
    }

}
