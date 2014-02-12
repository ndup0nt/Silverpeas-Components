package org.silverpeas.component.kmelia.mailbox.publication;

import com.stratelia.webactiv.util.publication.model.PublicationDetail;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collection;


@Getter
@RequiredArgsConstructor
public class PublicationHolder {
    private final PublicationDetail publicationDetail;
    private final String wysiwygContent;
}
