"use strict";

const express = require('express');
const { requireAuth } = require('../../auth/middleware');
const { validateSync } = require('./validation');
const { validateQuery } = require('./query.validation');
const controller = require('./controller');
const queryController = require('./query.controller');

function transactionsRouterFor(prisma) {
  const router = express.Router();

  // POST /api/transactions/sync — authenticated, idempotent, merchant isolated
  router.post('/sync', requireAuth, validateSync, controller.sync(prisma));

  // GET /api/transactions — paginated, filtered, merchant isolated
  router.get('/', requireAuth, validateQuery, queryController.list(prisma));

  // GET /api/transactions/summary — dashboard cards, SUCCESS only, transactionTime today (UTC explicit)
  router.get('/summary', requireAuth, queryController.summary(prisma));

  return router;
}

module.exports = { transactionsRouterFor };
