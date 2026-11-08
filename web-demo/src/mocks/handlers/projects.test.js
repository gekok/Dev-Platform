import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  MEMBER_TOKEN,
  OUTSIDER_TOKEN,
  OUTSIDER_ID,
  OWNER_TOKEN,
  apiUrl,
  bearer,
  withMockServer,
} from '../testUtils.js';

test('Project Owner and Member can list and read their Project and members', async () => {
  await withMockServer(async ({ repository }) => {
    const project = repository.read().projects[0];

    for (const [token, expectedRole] of [
      [OWNER_TOKEN, 'OWNER'],
      [MEMBER_TOKEN, 'MEMBER'],
    ]) {
      const listResponse = await fetch(apiUrl('/projects'), {
        headers: bearer(token),
      });
      const projects = await listResponse.json();
      assert.equal(listResponse.status, 200);
      assert.equal(projects.length, 1);
      assert.equal(projects[0].id, project.id);
      assert.equal(projects[0].role, expectedRole);
      assert.equal(projects[0].boardCount, 2);

      const detailResponse = await fetch(apiUrl(`/projects/${project.id}`), {
        headers: bearer(token),
      });
      const detail = await detailResponse.json();
      assert.equal(detailResponse.status, 200);
      assert.equal(detail.role, expectedRole);

      const membersResponse = await fetch(
        apiUrl(`/projects/${project.id}/members`),
        { headers: bearer(token) },
      );
      const members = await membersResponse.json();
      assert.equal(membersResponse.status, 200);
      assert.ok(members.length >= 4);
      assert.ok(members.every((member) => member.id && member.email && member.role));
    }
  });
});

test('returns 403 for a non-member and 404 for an unknown Project', async () => {
  await withMockServer(async ({ repository }) => {
    const projectId = repository.read().projects[0].id;

    for (const path of [
      `/projects/${projectId}`,
      `/projects/${projectId}/members`,
    ]) {
      const response = await fetch(apiUrl(path), {
        headers: bearer(OUTSIDER_TOKEN),
      });
      assert.equal(response.status, 403);
      assert.equal((await response.json()).code, 'FORBIDDEN');
    }

    const missingResponse = await fetch(apiUrl('/projects/9999'), {
      headers: bearer(OWNER_TOKEN),
    });
    assert.equal(missingResponse.status, 404);
    assert.equal((await missingResponse.json()).code, 'NOT_FOUND');
  });
});

test('requires a bearer token to list Projects', async () => {
  await withMockServer(async () => {
    const response = await fetch(apiUrl('/projects'));
    const body = await response.json();

    assert.equal(response.status, 401);
    assert.equal(body.code, 'UNAUTHORIZED');
  });
});

test('creates a Project with only the bearer user as Owner, ignoring body userId', async () => {
  await withMockServer(async ({ repository }) => {
    repository.transact((draft) => {
      draft.projects[0].createdAt = '2000-01-01T00:00:00.000Z';
    });
    const response = await fetch(apiUrl('/projects'), {
      method: 'POST',
      headers: {
        ...bearer(OWNER_TOKEN),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ name: 'Created project', userId: OUTSIDER_ID }),
    });
    const created = await response.json();

    assert.equal(response.status, 201);
    assert.equal(created.name, 'Created project');
    assert.equal(created.ownerId, repository.read().projects[0].ownerId);
    assert.equal(created.role, 'OWNER');

    const listResponse = await fetch(apiUrl('/projects'), {
      headers: bearer(OWNER_TOKEN),
    });
    const listedProjects = await listResponse.json();
    assert.equal(listedProjects[0].id, created.id);

    const memberships = repository
      .read()
      .projectMembers.filter((membership) => membership.projectId === created.id);
    assert.equal(memberships.length, 1);
    assert.equal(memberships[0].userId, created.ownerId);
    assert.equal(memberships[0].userId === OUTSIDER_ID, false);
  });
});

test('validates Project names before creation', async () => {
  await withMockServer(async () => {
    const response = await fetch(apiUrl('/projects'), {
      method: 'POST',
      headers: {
        ...bearer(OWNER_TOKEN),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ name: '  ' }),
    });

    assert.equal(response.status, 400);
    assert.equal((await response.json()).code, 'BAD_REQUEST');
  });
});

test('Owner can invite an account and the recipient can accept through Project invitation routes', async () => {
  await withMockServer(async ({ repository }) => {
    const projectId = repository.read().projects[0].id;
    const searchResponse = await fetch(apiUrl('/users?query=outsider&limit=10'), {
      headers: bearer(OWNER_TOKEN),
    });
    const candidates = await searchResponse.json();
    assert.equal(searchResponse.status, 200);
    assert.ok(candidates.some((user) => user.id === OUTSIDER_ID));

    const createResponse = await fetch(apiUrl(`/projects/${projectId}/invitations`), {
      method: 'POST',
      headers: {
        ...bearer(OWNER_TOKEN),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ userId: OUTSIDER_ID }),
    });
    const created = await createResponse.json();
    assert.equal(createResponse.status, 201);
    assert.equal(created.userId, OUTSIDER_ID);
    assert.equal(created.status, 'PENDING');

    const inboxResponse = await fetch(apiUrl('/invitations/me?status=PENDING'), {
      headers: bearer(OUTSIDER_TOKEN),
    });
    const inbox = await inboxResponse.json();
    assert.equal(inboxResponse.status, 200);
    assert.equal(inbox.length, 1);
    assert.equal(inbox[0].id, created.id);
    assert.equal(inbox[0].projectName, repository.read().projects[0].name);

    const acceptResponse = await fetch(apiUrl(`/invitations/${created.id}/accept`), {
      method: 'POST',
      headers: bearer(OUTSIDER_TOKEN),
    });
    const membership = await acceptResponse.json();
    assert.equal(acceptResponse.status, 200);
    assert.equal(membership.userId, OUTSIDER_ID);
    assert.equal(membership.role, 'MEMBER');
    assert.ok(repository.read().projectMembers.some(
      (item) => item.projectId === projectId && item.userId === OUTSIDER_ID,
    ));
  });
});

test('only a Project Owner can send or cancel invitations', async () => {
  await withMockServer(async ({ repository }) => {
    const projectId = repository.read().projects[0].id;
    const deniedCreate = await fetch(apiUrl(`/projects/${projectId}/invitations`), {
      method: 'POST',
      headers: {
        ...bearer(MEMBER_TOKEN),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ userId: OUTSIDER_ID }),
    });
    assert.equal(deniedCreate.status, 403);

    const createdResponse = await fetch(apiUrl(`/projects/${projectId}/invitations`), {
      method: 'POST',
      headers: {
        ...bearer(OWNER_TOKEN),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ userId: OUTSIDER_ID }),
    });
    const created = await createdResponse.json();
    const deniedCancel = await fetch(
      apiUrl(`/projects/${projectId}/invitations/${created.id}/cancel`),
      { method: 'POST', headers: bearer(MEMBER_TOKEN) },
    );

    assert.equal(deniedCancel.status, 403);
    assert.equal(repository.read().invitations[0].status, 'PENDING');
  });
});
