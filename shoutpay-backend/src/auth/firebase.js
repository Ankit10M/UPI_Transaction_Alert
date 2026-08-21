"use strict";

const { cert, getApps, initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const config = require('../config/config');

function firebaseAuth() {
  if (!getApps().length) {
    initializeApp({
      credential: cert({
        projectId: config.FIREBASE_PROJECT_ID,
        clientEmail: config.FIREBASE_CLIENT_EMAIL,
        privateKey: config.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n'),
      }),
      projectId: config.FIREBASE_PROJECT_ID,
    });
  }
  return getAuth();
}

async function verifyFirebaseIdToken(idToken) {
  return firebaseAuth().verifyIdToken(idToken, true);
}

module.exports = { verifyFirebaseIdToken };
