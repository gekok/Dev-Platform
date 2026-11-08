import { useCurrentUser } from '../../auth/hooks/useCurrentUser.js';
import { getApiErrorMessage } from '../../../shared/services/apiErrors.js';
import {
  useMyProjectInvitations,
  useRespondToProjectInvitation,
} from '../hooks/useProjects.js';

export default function InvitationInbox() {
  const userQuery = useCurrentUser();
  const userId = userQuery.data?.id;
  const invitationsQuery = useMyProjectInvitations(userId);
  const respondMutation = useRespondToProjectInvitation(userId);
  const invitations = invitationsQuery.data?.items ?? [];

  async function respond(invitationId, decision) {
    try {
      await respondMutation.mutateAsync({ invitationId, decision });
    } catch {
      // The failed response is shown beside the invitation.
    }
  }

  return (
    <section aria-labelledby="invitation-inbox-title" className="invitation-inbox">
      <div className="section-heading">
        <div>
          <h2 id="invitation-inbox-title">Project invitations</h2>
          <span>Invitations addressed to your account</span>
        </div>
      </div>

      {invitationsQuery.isPending && (
        <p aria-live="polite" className="invitation-inbox-state">Loading invitations…</p>
      )}
      {invitationsQuery.isError && (
        <div className="invitation-inbox-state" role="alert">
          <span>{getApiErrorMessage(invitationsQuery.error, 'Invitations could not be loaded.')}</span>
          <button className="secondary-button" onClick={() => invitationsQuery.refetch()} type="button">
            Try again
          </button>
        </div>
      )}
      {respondMutation.isError && (
        <p className="form-inline-error" role="alert">
          {getApiErrorMessage(respondMutation.error, 'The invitation response could not be saved.')}
        </p>
      )}
      {invitationsQuery.isSuccess && invitations.length === 0 && (
        <p className="invitation-inbox-state">You have no pending Project invitations.</p>
      )}
      {invitations.length > 0 && (
        <ul className="invitation-inbox-list">
          {invitations.map((invitation) => (
            <li key={invitation.id}>
              <div>
                <strong>{invitation.projectName ?? `Project ${invitation.projectId}`}</strong>
                <span>Invitation to join a Project workspace</span>
              </div>
              <div className="invitation-inbox-actions">
                <button
                  className="secondary-button"
                  disabled={respondMutation.isPending}
                  onClick={() => respond(invitation.id, 'reject')}
                  type="button"
                >
                  Decline
                </button>
                <button
                  className="primary-button"
                  disabled={respondMutation.isPending}
                  onClick={() => respond(invitation.id, 'accept')}
                  type="button"
                >
                  {respondMutation.isPending && respondMutation.variables?.invitationId === invitation.id
                    ? 'Accepting…'
                    : 'Accept'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
