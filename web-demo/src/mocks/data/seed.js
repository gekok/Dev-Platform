export const DEMO_CREDENTIALS = Object.freeze({
  email: 'owner@devplatform.demo',
  password: 'kanban-demo',
});

export const DEMO_OWNER_ID = 'a1000000-0000-4000-8000-000000000001';

const RESOURCE_COLLECTIONS = Object.freeze({
  project: 'projects',
  board: 'boards',
  column: 'columns',
  card: 'cards',
  comment: 'comments',
  notification: 'notifications',
});

function getNextIds(data) {
  return Object.fromEntries(
    Object.entries(RESOURCE_COLLECTIONS).map(([type, collection]) => {
      const maxId = data[collection].reduce(
        (currentMax, resource) => Math.max(currentMax, resource.id),
        0,
      );
      return [type, maxId + 1];
    }),
  );
}

export function createSeedData() {
  const ownerId = DEMO_OWNER_ID;
  const members = [
    {
      id: ownerId,
      email: DEMO_CREDENTIALS.email,
      fullName: 'Alex Morgan',
    },
    {
      id: 'a1000000-0000-4000-8000-000000000002',
      email: 'sam.nguyen@devplatform.demo',
      fullName: 'Sam Nguyen',
    },
    {
      id: 'a1000000-0000-4000-8000-000000000003',
      email: 'jordan.tran@devplatform.demo',
      fullName: 'Jordan Tran',
    },
    {
      id: 'a1000000-0000-4000-8000-000000000004',
      email: 'riley.pham@devplatform.demo',
      fullName: 'Riley Pham',
    },
    {
      id: 'a1000000-0000-4000-8000-000000000005',
      email: 'outsider@devplatform.demo',
      fullName: 'Taylor Reed',
    },
  ];

  const data = {
    users: members,
    projects: [
      {
        id: 1,
        name: 'MSS Platform',
        ownerId,
        createdAt: '2026-10-21T08:00:00.000Z',
      },
    ],
    projectMembers: [
      { projectId: 1, userId: ownerId, role: 'OWNER' },
      { projectId: 1, userId: members[1].id, role: 'MEMBER' },
      { projectId: 1, userId: members[2].id, role: 'MEMBER' },
      { projectId: 1, userId: members[3].id, role: 'MEMBER' },
    ],
    boards: [
      {
        id: 11,
        projectId: 1,
        name: 'Platform Roadmap',
        key: 'PLAT',
        ownerId,
        createdAt: '2026-10-21T08:05:00.000Z',
      },
      {
        id: 12,
        projectId: 1,
        name: 'Operations',
        key: 'OPS1',
        ownerId,
        createdAt: '2026-10-21T08:10:00.000Z',
      },
    ],
    columns: [
      { id: 101, boardId: 11, name: 'To Do', position: 0 },
      { id: 102, boardId: 11, name: 'In Progress', position: 1 },
      { id: 103, boardId: 11, name: 'Done', position: 2 },
      { id: 104, boardId: 12, name: 'To Do', position: 0 },
      { id: 105, boardId: 12, name: 'In Progress', position: 1 },
      { id: 106, boardId: 12, name: 'Done', position: 2 },
    ],
    cards: [
      {
        id: 201,
        boardId: 11,
        columnId: 101,
        title: 'Define project-first boundaries',
        description: 'Agree on the Project, Board, and Card ownership model.',
        status: 'TODO',
        position: 0,
        assigneeId: ownerId,
        dueDate: '2026-10-29T17:00:00.000Z',
      },
      {
        id: 202,
        boardId: 11,
        columnId: 102,
        title: 'Map the Kỳ 1 Kanban flow',
        description: 'Connect Projects, Boards, Columns, Cards, and comments.',
        status: 'IN_PROGRESS',
        position: 0,
        assigneeId: members[1].id,
        dueDate: '2026-10-25T17:00:00.000Z',
      },
      {
        id: 203,
        boardId: 11,
        columnId: 103,
        title: 'Review current service topology',
        description: 'Keep the current backend untouched in this demo.',
        status: 'DONE',
        position: 0,
        assigneeId: members[2].id,
        dueDate: null,
      },
      {
        id: 204,
        boardId: 12,
        columnId: 104,
        title: 'Prepare the demo workspace',
        description: 'Validate a responsive workspace for the Kỳ 1 flow.',
        status: 'TODO',
        position: 0,
        assigneeId: members[3].id,
        dueDate: null,
      },
    ],
    comments: [
      {
        id: 301,
        cardId: 201,
        authorId: members[1].id,
        content: 'The Project is the access boundary for every Board.',
        createdAt: '2026-10-21T08:25:00.000Z',
        mentionedUserIds: [],
      },
    ],
    notifications: [
      {
        id: 401,
        recipientId: ownerId,
        type: 'ASSIGNED',
        title: 'A card was assigned to you',
        cardId: 201,
        boardId: 11,
        read: false,
        createdAt: '2026-10-21T08:30:00.000Z',
      },
      {
        id: 402,
        recipientId: members[1].id,
        type: 'MENTIONED',
        title: 'You were mentioned in a comment',
        cardId: 201,
        boardId: 11,
        read: true,
        createdAt: '2026-10-21T08:35:00.000Z',
      },
    ],
    defaultBoardPreferences: [],
  };

  data.nextIds = getNextIds(data);
  return data;
}
