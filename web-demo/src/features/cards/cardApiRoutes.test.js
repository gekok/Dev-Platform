import assert from 'node:assert/strict';
import test from 'node:test';
import { createCardApiRoutes } from './cardApiRoutes.js';

test('keeps Demo Card routes compatible with MSW', () => {
  const routes = createCardApiRoutes('demo');

  assert.equal(routes.cards(1, 2), '/boards/2/cards');
  assert.equal(routes.card(1, 3), '/cards/3');
  assert.equal(routes.createCard(1, 2), '/boards/2/cards');
  assert.equal(routes.moveCard(1, 3), '/cards/3/move');
  assert.equal(routes.assignCard(1, 3), '/cards/3/assignee');
  assert.equal(routes.comments(1, 3), '/cards/3/comments');
});

test('uses Project-scoped Task routes in Backend mode', () => {
  const routes = createCardApiRoutes('backend');

  assert.equal(routes.card(11, 31), '/projects/11/cards/31');
  assert.equal(routes.comments(11, 31), '/projects/11/cards/31/comments');
  assert.equal(routes.createCard(11, 22), '/projects/11/boards/22/cards');
  assert.equal(routes.moveCard(11, 31), '/projects/11/cards/31/move');
  assert.equal(routes.assignCard(11, 31), '/projects/11/cards/31/assignee');
});

test('builds the project-scoped Card list route for cursor pagination in Backend mode', () => {
  const routes = createCardApiRoutes('backend');

  assert.equal(routes.cards(11, 22), '/projects/11/boards/22/cards');
});
