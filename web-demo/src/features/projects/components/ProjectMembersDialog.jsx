import { useEffect, useMemo, useRef, useState } from 'react';
import {
  useCancelProjectInvitation,
  useCreateProjectInvitation,
  useProjectInviteeSearch,
  useProjectInvitations,
  useProjectMembers,
} from '../hooks/useProjects.js';
import { getMemberUserId } from '../../cards/memberIdentity.js';
import { getApiErrorMessage } from '../../../shared/services/apiErrors.js';

const EMPTY_ITEMS = Object.freeze([]);

export default function ProjectMembersDialog({ open, onClose, projectId, isOwner }) {
  const dialogRef = useRef(null);
  const returnFocusRef = useRef(null);
  const [search, setSearch] = useState('');
  const membersQuery = useProjectMembers(projectId, { enabled: open });
  const invitationsQuery = useProjectInvitations(projectId, { enabled: open && isOwner });
  const inviteeQuery = useProjectInviteeSearch(search, open && isOwner);
  const createInvitation = useCreateProjectInvitation(projectId);
  const cancelInvitation = useCancelProjectInvitation(projectId);
  const members = membersQuery.data?.items ?? EMPTY_ITEMS;
  const invitations = invitationsQuery.data?.items ?? EMPTY_ITEMS;
  const memberIds = useMemo(
    () => new Set(members.map((member) => String(getMemberUserId(member)))),
    [members],
  );
  const pendingInviteeIds = useMemo(
    () => new Set(invitations.map((invitation) => String(invitation.userId))),
    [invitations],
  );
  const invitees = (inviteeQuery.data ?? []).filter(
    (user) => !memberIds.has(String(user.id)) && !pendingInviteeIds.has(String(user.id)),
  );

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!open || !dialog) return undefined;

    returnFocusRef.current = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    if (!dialog.open) dialog.showModal();
    dialog.querySelector('input')?.focus();

    return () => {
      if (dialog.open) dialog.close();
      if (returnFocusRef.current?.isConnected) returnFocusRef.current.focus();
      returnFocusRef.current = null;
    };
  }, [open]);

  async function invite(userId) {
    try {
      await createInvitation.mutateAsync(userId);
      setSearch('');
    } catch {
      // The mutation error stays visible in the dialog.
    }
  }

  async function cancel(invitationId) {
    try {
      await cancelInvitation.mutateAsync(invitationId);
    } catch {
      // The mutation error stays visible in the dialog.
    }
  }

  return (
    <dialog
      aria-labelledby="project-members-title"
      className="project-members-dialog"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      ref={dialogRef}
    >
      <div className="project-members-content">
        <header className="project-members-heading">
          <div>
            <span className="eyebrow">PROJECT ACCESS</span>
            <h2 id="project-members-title">Project members</h2>
          </div>
          <button
            aria-label="Close members dialog"
            className="icon-button"
            onClick={onClose}
            type="button"
          >
            ×
          </button>
        </header>

        <section aria-label="Current members" className="project-members-section">
          <h3>Members</h3>
          {membersQuery.isPending && <p aria-live="polite">Loading members…</p>}
          {membersQuery.isError && (
            <div className="project-members-error" role="alert">
              <span>{getApiErrorMessage(membersQuery.error, 'Project members could not be loaded.')}</span>
              <button className="secondary-button" onClick={() => membersQuery.refetch()} type="button">
                Try again
              </button>
            </div>
          )}
          {membersQuery.isSuccess && members.length === 0 && <p>No active members were returned.</p>}
          {members.length > 0 && (
            <ul className="project-member-list">
              {members.map((member) => (
                <li key={getMemberUserId(member)}>
                  <span>{member.fullName}</span>
                  <span className="role-chip role-member">{member.role}</span>
                </li>
              ))}
            </ul>
          )}
        </section>

        {isOwner && (
          <>
            <section aria-label="Invite a member" className="project-members-section">
              <h3>Invite a member</h3>
              <label className="visually-hidden" htmlFor="project-invite-search">
                Search accounts by name
              </label>
              <input
                autoComplete="off"
                id="project-invite-search"
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search accounts by name"
                type="search"
                value={search}
              />
              <p className="board-form-note">Enter at least two characters to find an active account.</p>
              {inviteeQuery.isFetching && <p aria-live="polite">Searching accounts…</p>}
              {inviteeQuery.isError && (
                <div className="project-members-error" role="alert">
                  <span>{getApiErrorMessage(inviteeQuery.error, 'Accounts could not be searched.')}</span>
                  <button className="secondary-button" onClick={() => inviteeQuery.refetch()} type="button">
                    Retry search
                  </button>
                </div>
              )}
              {createInvitation.isError && (
                <p className="form-inline-error" role="alert">
                  {getApiErrorMessage(createInvitation.error, 'The invitation could not be sent.')}
                </p>
              )}
              {inviteeQuery.isSuccess && search.trim().length >= 2 && invitees.length === 0 && (
                <p>No eligible accounts match this search.</p>
              )}
              {invitees.length > 0 && (
                <ul className="project-invitee-list">
                  {invitees.map((user) => (
                    <li key={user.id}>
                      <span>{user.fullName}</span>
                      <button
                        className="secondary-button"
                        disabled={createInvitation.isPending}
                        onClick={() => invite(user.id)}
                        type="button"
                      >
                        {createInvitation.isPending && createInvitation.variables === user.id
                          ? 'Sending…'
                          : 'Invite'}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section aria-label="Pending invitations" className="project-members-section">
              <h3>Pending invitations</h3>
              {invitationsQuery.isPending && <p aria-live="polite">Loading invitations…</p>}
              {invitationsQuery.isError && (
                <div className="project-members-error" role="alert">
                  <span>{getApiErrorMessage(invitationsQuery.error, 'Invitations could not be loaded.')}</span>
                  <button className="secondary-button" onClick={() => invitationsQuery.refetch()} type="button">
                    Try again
                  </button>
                </div>
              )}
              {cancelInvitation.isError && (
                <p className="form-inline-error" role="alert">
                  {getApiErrorMessage(cancelInvitation.error, 'The invitation could not be cancelled.')}
                </p>
              )}
              {invitationsQuery.isSuccess && invitations.length === 0 && (
                <p>There are no pending invitations.</p>
              )}
              {invitations.length > 0 && (
                <ul className="project-invitee-list">
                  {invitations.map((invitation) => (
                    <li key={invitation.id}>
                      <span>{invitation.inviteeName}</span>
                      <button
                        className="secondary-button"
                        disabled={cancelInvitation.isPending}
                        onClick={() => cancel(invitation.id)}
                        type="button"
                      >
                        {cancelInvitation.isPending && cancelInvitation.variables === invitation.id
                          ? 'Cancelling…'
                          : 'Cancel invite'}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </>
        )}

        <footer className="form-actions">
          <button className="secondary-button" onClick={onClose} type="button">Done</button>
        </footer>
      </div>
    </dialog>
  );
}
