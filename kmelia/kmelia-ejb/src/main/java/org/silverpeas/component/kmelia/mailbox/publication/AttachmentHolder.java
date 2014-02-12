package org.silverpeas.component.kmelia.mailbox.publication;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.silverpeas.attachment.model.SimpleDocument;

import java.io.InputStream;


@Getter
@RequiredArgsConstructor
public class AttachmentHolder {
    private final SimpleDocument simpleDocument;
    private final InputStream inputStream;
}
