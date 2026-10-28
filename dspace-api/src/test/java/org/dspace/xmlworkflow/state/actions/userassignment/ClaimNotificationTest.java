package org.dspace.xmlworkflow.state.actions.userassignment;

import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import javax.mail.MessagingException;
import org.junit.Test;
import org.dspace.core.Context;
import org.dspace.content.Item;
import org.dspace.content.Collection;
import org.dspace.eperson.EPerson;
import org.dspace.xmlworkflow.Role;
import org.dspace.xmlworkflow.RoleMembers;
import org.dspace.xmlworkflow.service.XmlWorkflowService;
import org.dspace.xmlworkflow.state.Step;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

public class ClaimNotificationTest {
    @Test
    public void notifiesEditorialRoleWithClaimantAndTaskDetails() throws Exception {
        exercise(false, false);
    }

    @Test
    public void emptyEditorialRoleDoesNotSendMail() throws Exception {
        exercise(true, false);
    }

    @Test
    public void mailFailureDoesNotUndoClaim() throws Exception {
        exercise(false, true);
    }

    private void exercise(boolean empty, boolean failure) throws Exception {
        ClaimAction action = mock(ClaimAction.class, CALLS_REAL_METHODS);
        // CALLS_REAL_METHODS bypasses constructors, including the base logger.
        for (Class<?> type = ClaimAction.class; type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField("log");
                field.setAccessible(true);
                field.set(action, org.apache.logging.log4j.LogManager.getLogger(ClaimNotificationTest.class));
                break;
            } catch (NoSuchFieldException ignored) {
                // Continue to the action superclass.
            }
        }
        XmlWorkflowService notifications = mock(XmlWorkflowService.class);
        doReturn(notifications).when(action).getNotificationService();
        Context context = mock(Context.class);
        XmlWorkflowItem workflow = mock(XmlWorkflowItem.class);
        Step step = mock(Step.class);
        Role role = mock(Role.class);
        RoleMembers members = mock(RoleMembers.class);
        EPerson editor = mock(EPerson.class);
        Item item = mock(Item.class);
        Collection collection = mock(Collection.class);
        ArrayList<EPerson> recipients = empty ? new ArrayList<>() : new ArrayList<>(List.of(editor));
        when(step.getRole()).thenReturn(role);
        when(role.getMembers(context, workflow)).thenReturn(members);
        when(members.getAllUniqueMembers(context)).thenReturn(recipients);
        when(context.getCurrentUser()).thenReturn(editor);
        when(editor.getFullName()).thenReturn("Test Editor");
        when(workflow.getItem()).thenReturn(item);
        when(workflow.getCollection()).thenReturn(collection);
        when(item.getName()).thenReturn("Test submission");
        when(collection.getName()).thenReturn("Test collection");
        when(notifications.getMyDSpaceLink()).thenReturn("https://example.invalid/dspace/mydspace");
        if (failure) {
            doThrow(new MessagingException("Test SMTP unavailable")).when(notifications)
                .alertUsersOnTaskActivation(context, workflow, "task_claimed", recipients,
                    "Test submission", "Test collection", "Test Editor", "https://example.invalid/dspace/mydspace");
        }
        action.alertUsersOnClaim(context, workflow, step);
        if (empty) {
            verify(notifications, never()).alertUsersOnTaskActivation(any(), any(), anyString(), anyList(), any());
        } else {
            verify(notifications).alertUsersOnTaskActivation(context, workflow, "task_claimed", recipients,
                "Test submission", "Test collection", "Test Editor", "https://example.invalid/dspace/mydspace");
        }
    }
}
