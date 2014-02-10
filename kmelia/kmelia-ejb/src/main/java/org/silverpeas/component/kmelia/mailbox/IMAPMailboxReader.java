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
import com.stratelia.webactiv.util.ResourceLocator;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.mail.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Read messages in an IMAP mailbox and handle them with the registered listeners.
 * Note : IMAPS is not supported yet.
 */
@Named
@Singleton
public class IMAPMailboxReader implements MailboxReader {
    private static final String IMAP_PROTOCOL = "imap";
    private static final String IMAP_SERVER_PROP = "kmelia.mailbox.imapServerAddress";
    private static final String IMAP_SERVER_PORT_PROP = "kmelia.mailbox.imapServerPort";
    private static final String IMAP_LOGIN_PROP = "kmelia.mailbox.login";
    private static final String IMAP_PWD_PROP = "kmelia.mailbox.password";
    private static final String IMAP_SRC_FOLDER_PROP = "kmelia.mailbox.srcFolder";
    private static final String IMAP_HANDLED_MSGS_FOLDER_PROP = "kmelia.mailbox.handledMessagesFolder";
    private static final int IMAP_DEFAULT_PROP = 143;

    /**
     * The mailbox folder where the messages will be read from.
     */
    private final String srcMessagesFolder;
    /**
     * The mailbox folder where the messages will be moved to once they are handled by the registered listeners.
     */
    private final String handledMessagesFolder;
    /**
     * The registered listeners.
     */
    private final List<MessageListener> listeners = new ArrayList<MessageListener>();
    /**
     * The IMAP server address or host name.
     */
    private final String imapServerAddress;
    /**
     * The IMAP server port.
     */
    private final int imapServerPort;
    /**
     * The login to be used to connect to the mailbox.
     */
    private final String login;
    /**
     * The password to be used to connect to the mailbox.
     */
    private final String password;
    /**
     * The mail Session.
     */
    private final Session mailSession;

    /**
     * Constructor with all required arguments.
     *
     * @param kmeliaSettings                 kmelia settings
     * @param kmeliaMailboxSessionProperties mail session properties
     */
    @Inject
    public IMAPMailboxReader(ResourceLocator kmeliaSettings, Properties kmeliaMailboxSessionProperties) {
        this.imapServerAddress = kmeliaSettings.getString(IMAP_SERVER_PROP);
        this.imapServerPort = kmeliaSettings.getInteger(IMAP_SERVER_PORT_PROP, IMAP_DEFAULT_PROP);
        this.login = kmeliaSettings.getString(IMAP_LOGIN_PROP);
        this.password = kmeliaSettings.getString(IMAP_PWD_PROP);
        this.mailSession = Session.getInstance(kmeliaMailboxSessionProperties);
        this.srcMessagesFolder = kmeliaSettings.getString(IMAP_SRC_FOLDER_PROP);
        this.handledMessagesFolder = kmeliaSettings.getString(IMAP_HANDLED_MSGS_FOLDER_PROP);
    }

    @Override
    public void registerListener(MessageListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void readMailbox() throws MessagingException, IOException {
        if (listeners.size() == 0) {
            return;
        }
        doReadMailbox();
    }

    private void doReadMailbox() throws MessagingException, IOException {
        Store mailAccount = mailSession.getStore(IMAP_PROTOCOL);
        mailAccount.connect(imapServerAddress, imapServerPort, login, password);

        try {
            handleMessages(mailAccount);
        } finally {
            try {
                mailAccount.close();
            } catch (Exception ex) {
                SilverTrace.warn("kmelia", this.getClass().getName() + "#doReadMailbox", "Could not close mailbox connection", null, ex);
            }
        }
    }

    private void handleMessages(Store mailAccount) throws MessagingException, IOException {
        Folder inbox = mailAccount.getFolder(srcMessagesFolder);
        Folder handledFolder = mailAccount.getFolder(handledMessagesFolder);

        inbox.open(Folder.READ_WRITE);
        handledFolder.open(Folder.READ_WRITE);
        try {
            handleMessagesInOpenedFolders(inbox, handledFolder);
        } finally {
            silentlyCloseFolder(inbox, true);
            silentlyCloseFolder(handledFolder, false);
        }
    }

    private void silentlyCloseFolder(Folder folder, boolean expunge) {
        try {
            folder.close(expunge);
        } catch (Exception ex) {
            SilverTrace.warn("kmelia", this.getClass().getName() + "#handleMessages", "Could not close folder " + folder.getName(), null, ex);
        }
    }

    private void handleMessagesInOpenedFolders(Folder inbox, Folder handledFolder) throws MessagingException, IOException {
        // Get the messages and process them
        Message[] msgs = inbox.getMessages();

        for (MessageListener listener : listeners) {
            listener.onMailboxRead(new ReadMailboxEvent(msgs));
        }

        // Mark the messages as handled by moving them to another folder
        inbox.copyMessages(msgs, handledFolder);
        inbox.setFlags(msgs, new Flags(Flags.Flag.DELETED), true);
    }

}
