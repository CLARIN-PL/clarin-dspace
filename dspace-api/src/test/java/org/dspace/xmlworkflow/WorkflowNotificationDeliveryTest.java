package org.dspace.xmlworkflow;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
import java.util.List;
import javax.mail.MessagingException;
import org.junit.Test;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;

public class WorkflowNotificationDeliveryTest {
    private EPerson recipient(String address) {
        EPerson person = mock(EPerson.class);
        when(person.getEmail()).thenReturn(address);
        return person;
    }

    @Test
    public void ignoresBootstrapAccountAndDeduplicatesRecipients() throws Exception {
        XmlWorkflowServiceImpl service = mock(XmlWorkflowServiceImpl.class, CALLS_REAL_METHODS);
        Context context = mock(Context.class);
        doNothing().when(service).sendTaskNotification(context,"submit_task","editor@example.invalid","title");
        service.sendTaskNotifications(context,"submit_task",List.of(recipient("admin@localhost"),
            recipient(null),recipient(" editor@example.invalid;legacy@example.invalid"),
            recipient("editor@example.invalid")),"title");
        verify(service, times(1)).sendTaskNotification(context,"submit_task","editor@example.invalid","title");
        verify(service, never()).sendTaskNotification(context,"submit_task","admin@localhost","title");
    }

    @Test
    public void smtpRejectionDoesNotPreventOtherRecipients() throws Exception {
        XmlWorkflowServiceImpl service = mock(XmlWorkflowServiceImpl.class, CALLS_REAL_METHODS);
        Context context = mock(Context.class);
        doThrow(new MessagingException("Invalid Addresses")).when(service)
            .sendTaskNotification(context,"submit_task","bad@example.invalid","title");
        doNothing().when(service).sendTaskNotification(context,"submit_task","good@example.invalid","title");
        try {
            service.sendTaskNotifications(context,"submit_task",List.of(recipient("bad@example.invalid"),
                recipient("good@example.invalid")),"title");
            fail("The caller should still receive the delivery error");
        } catch (MessagingException expected) {
            assertEquals("Invalid Addresses",expected.getMessage());
        }
        verify(service).sendTaskNotification(context,"submit_task","good@example.invalid","title");
    }
}
