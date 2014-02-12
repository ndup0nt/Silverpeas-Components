package org.silverpeas.component.kmelia.mailbox;

import com.stratelia.silverpeas.silvertrace.SilverTrace;
import com.stratelia.webactiv.util.exception.SilverpeasException;

/**
 * Thrown when an error occurs while handling a mailbox event.
 */
public class MailboxEventHandlingException extends SilverpeasException {

    public MailboxEventHandlingException(String callingClass, String message) {
        super(callingClass, SilverTrace.TRACE_LEVEL_ERROR, message);
    }

    public MailboxEventHandlingException(String callingClass, String message, Exception cause) {
        super(callingClass, SilverTrace.TRACE_LEVEL_ERROR, message, null, cause);
    }

    @Override
    public String getModule() {
        return "kmelia";
    }
}
