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

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
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
    private static final int DEFAULT_FREQUENCY = 1;

    private final MailboxReader mailboxReader;
    private final int frequencyInMinutes;
    private final SubscribedComponentListener subscribedComponentListener;

    /**
     * Constructor with all required arguments.
     *
     * @param theMailboxReader the MailboxReader
     * @param kmeliaSettings   kmelia settings
     */
    @Inject
    private MailboxReaderJob(MailboxReader theMailboxReader, ResourceLocator kmeliaSettings,
                             MessageProcessor theMessageProcessor,
                             SubscribedComponentListener theSubscribedComponentListener) {
        this.mailboxReader = theMailboxReader;
        this.frequencyInMinutes = kmeliaSettings.getInteger(FREQUENCY_PROP, DEFAULT_FREQUENCY);
        this.subscribedComponentListener = theSubscribedComponentListener;
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
    private void registerJob() throws SchedulerException {
        mailboxReader.registerListener(subscribedComponentListener);
        SchedulerFactory schedulerFactory = SchedulerFactory.getFactory();
        Scheduler scheduler = schedulerFactory.getScheduler();
        if (scheduler.isJobScheduled(JOB_NAME)) {
            scheduler.unscheduleJob(JOB_NAME);
        }
        JobTrigger trigger = JobTrigger.triggerEvery(frequencyInMinutes, TimeUnit.MINUTE);
        scheduler.scheduleJob(JOB_NAME, trigger, this);
    }
}
