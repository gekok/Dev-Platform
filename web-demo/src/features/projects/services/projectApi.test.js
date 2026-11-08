import assert from 'node:assert/strict';
import test from 'node:test';
import api, { configureApiBaseUrl } from '../../../shared/services/axios.js';
import {
  createProjectInvitation,
  listProjectMembers,
  listProjects,
  respondToProjectInvitation,
} from './projectApi.js';

function response(config, data) {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

async function withAdapter(adapter, run) {
  const previousAdapter = api.defaults.adapter;
  configureApiBaseUrl('/api/v1');
  api.defaults.adapter = adapter;
  try {
    await run();
  } finally {
    api.defaults.adapter = previousAdapter;
  }
}

test('unwraps paged Project envelopes and loads remaining pages', async () => {
  await withAdapter(async (config) => {
    const page = config.params.page;
    return response(config, {
      data: {
        items: page === 0
          ? [{ id: 1, name: 'One', myRole: 'OWNER' }]
          : [{ id: 2, name: 'Two', myRole: 'MEMBER' }],
        page,
        size: 100,
        totalElements: 2,
        totalPages: 2,
      },
      meta: { correlationId: 'test' },
    });
  }, async () => {
    const projects = await listProjects();

    assert.deepEqual(projects.items.map((project) => [project.id, project.role]), [
      [1, 'OWNER'],
      [2, 'MEMBER'],
    ]);
    assert.deepEqual(projects.pagination, {
      page: 0,
      size: 100,
      totalElements: 2,
      totalPages: 2,
      pagesLoaded: 2,
    });
  });
});

test('maps backend Membership userId and User profile displayName for assignee UI', async () => {
  const requested = [];
  await withAdapter(async (config) => {
    requested.push(config.url);
    if (config.url.startsWith('/projects/1/members')) {
      return response(config, {
        data: {
          items: [{ userId: 'member-1', role: 'MEMBER', joinedAt: '2026-11-08T00:00:00Z' }],
          page: 0,
          size: 100,
          totalElements: 1,
          totalPages: 1,
        },
        meta: {},
      });
    }
    return response(config, [{ id: 'member-1', displayName: 'Sam Nguyen' }]);
  }, async () => {
    const members = await listProjectMembers(1);

    assert.equal(members.items[0].id, 'member-1');
    assert.equal(members.items[0].userId, 'member-1');
    assert.equal(members.items[0].fullName, 'Sam Nguyen');
    assert.equal(members.pagination.totalElements, 1);
    assert.match(requested[1], /users\/profiles\?projectId=1&ids=member-1/);
  });
});

test('uses the current Project invitation routes and unwraps their response envelopes', async () => {
  const requests = [];
  await withAdapter(async (config) => {
    requests.push({ url: config.url, method: config.method, data: config.data });
    return response(config, {
      data: {
        id: 22,
        projectId: 7,
        userId: 'member-1',
        status: config.url.endsWith('/accept') ? 'ACCEPTED' : 'PENDING',
      },
      meta: {},
    });
  }, async () => {
    const created = await createProjectInvitation(7, 'member-1');
    const accepted = await respondToProjectInvitation(22, 'accept');

    assert.equal(created.id, 22);
    assert.equal(accepted.status, 'ACCEPTED');
    assert.equal(requests[0].url, '/projects/7/invitations');
    assert.equal(requests[0].method, 'post');
    assert.deepEqual(JSON.parse(requests[0].data), { userId: 'member-1' });
    assert.equal(requests[1].url, '/invitations/22/accept');
    assert.equal(requests[1].method, 'post');
  });
});
