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

import com.silverpeas.scheduler.*;
import com.silverpeas.scheduler.trigger.JobTrigger;
import com.silverpeas.scheduler.trigger.TimeUnit;
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
 * A SchedulerEventListener that fires mailbox reading using MailboxReader.
 * This Listener can register itself with the registerJob method.
 */
@Named
@Singleton
public class MailboxReaderJob implements SchedulerEventListener {
    private static final String JOB_NAME = "mailboxReaderJob";
    private static final String FREQUENCY_PROP = "kmelia.mailbox.job.frequency";
    private static final String SUBSCRIBED_COMP_PROP = "kmelia.mailbox.job.subscribingComponentId";
    private static final String DEFAULT_TARGET_FOLDER_PROP = "kmelia.mailbox.job.defaultTargetFolderId";
    private static final int DEFAULT_FREQUENCY = 1;

    private final MailboxReader mailboxReader;
    private final int frequencyInMinutes;
    private final MessageProcessor messageProcessor;
    private final String subscribedComponentId;
    private final String defaultTargetFolderId;
    private NodeBm nodeBm;


    /**
     * Constructor with all required arguments.
     *
     * @param theMailboxReader the MailboxReader
     * @param kmeliaSettings   kmelia settings
     */
    @Inject
    public MailboxReaderJob(MailboxReader theMailboxReader, ResourceLocator kmeliaSettings,
                            MessageProcessor theMessageProcessor) {
        this.mailboxReader = theMailboxReader;
        this.frequencyInMinutes = kmeliaSettings.getInteger(FREQUENCY_PROP, DEFAULT_FREQUENCY);
        this.messageProcessor = theMessageProcessor;
        this.subscribedComponentId = kmeliaSettings.getString(SUBSCRIBED_COMP_PROP);
        this.defaultTargetFolderId = kmeliaSettings.getString(DEFAULT_TARGET_FOLDER_PROP);
    }

    @Override
    public synchronized void triggerFired(SchedulerEvent anEvent) throws Exception {
        mailboxReader.readMailbox();
    }

    @Override
    public void jobSucceeded(SchedulerEvent anEvent) {
        SilverTrace.info("kmelia", this.getClass().getName(), "The job '" + anEvent.getJobExecutionContext().getJobName() + "' was successful");
    }

    @Override
    public void jobFailed(SchedulerEvent anEvent) {
        SilverTrace.error("kmelia", this.getClass().getName(), "The job '" + anEvent.getJobExecutionContext().getJobName() + "' was not successful", null, anEvent.getJobThrowable());
    }

    /**
     * Register this object as a scheduled job in Silverpeas job system.
     *
     * @throws SchedulerException if an exception occurs with scheduling
     */
    @PostConstruct
    public void registerJob() throws SchedulerException {
        registerListenersForSubscribedKmelia();
        SchedulerFactory schedulerFactory = SchedulerFactory.getFactory();
        Scheduler scheduler = schedulerFactory.getScheduler();
        if (scheduler.isJobScheduled(JOB_NAME)) {
            scheduler.unscheduleJob(JOB_NAME);
        }
        JobTrigger trigger = JobTrigger.triggerEvery(frequencyInMinutes, TimeUnit.MINUTE);
        scheduler.scheduleJob(JOB_NAME, trigger, this);
    }

    private Map<String, NodeDetail> foldersByDescription = new HashMap<String, NodeDetail>();

    private void initFoldersMapping(){
        ArrayList<NodeDetail> subTree = getNodeBm().getSubTree(new NodePK(NodePK.ROOT_NODE_ID, subscribedComponentId));
        for(NodeDetail node : subTree){
            if(StringUtil.isNotBlank(node.getDescription())){
                foldersByDescription.put(node.getDescription(), node);
                SilverTrace.debug("kmelia", this.getClass().getName(), "Folder " + node.getName() +
                        " will be mapped to domain "+node.getDescription());
            }
        }
    }

    private void registerListenersForSubscribedKmelia() {
        initFoldersMapping();
        mailboxReader.registerListener(new SubscribedComponentListener());
    }

    private final class SubscribedComponentListener implements MessageListener{
        @Override
        public void onMailboxRead(ReadMailboxEvent event) throws MessagingException, IOException {
            for (Message msg : event.getMessages()) {
                MessageDocument doc = messageProcessor.processMessage(msg);
                NodeDetail targetFolder = resolveTargetFolder(doc);
                System.out.println("Target folder : " + targetFolder.getName());
                System.out.println("Message subject : " + doc.getSubject());
                System.out.println("Message content : " + doc.getBody());
                System.out.println("Message attachments count : " + doc.getAttachments().size());
                for (Attachment att : doc.getAttachments()) {
                    StringWriter writer = new StringWriter();
                    IOUtils.copy(att.getInputStream(), writer);
                    System.out.println(att.getFileName() + " : ");
                    System.out.println(writer.toString());
                }
            }
        }
    }

    private NodeDetail resolveTargetFolder(MessageDocument doc){
        if(doc.getSender() != null){
            int indexOfArrob = doc.getSender().indexOf('@');
            if(indexOfArrob != -1 && indexOfArrob < doc.getSender().length() - 1){
                String domainName = doc.getSender().substring(indexOfArrob + 1);
                return resolveFolder(domainName);
            }
        }
        return getNodeBm().getDetail(new NodePK(defaultTargetFolderId, subscribedComponentId));
    }

    private NodeDetail resolveFolder(String domainName) {
        return foldersByDescription.get(domainName);
    }

    private NodeBm getNodeBm() {
        if (nodeBm == null) {
            nodeBm = EJBUtilitaire.getEJBObjectRef(JNDINames.NODEBM_EJBHOME, NodeBm.class);
        }
        return nodeBm;
    }
}
