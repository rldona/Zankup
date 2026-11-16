/** Session: signed in as the demo athlete from the start; signing out and in again work. */
import { db } from '../db';
import { fail, json, route } from '../route';

export const authHandlers = [
  route('get', '/api/auth/me', () =>
    db.signedIn ? json('/api/auth/me', 'get', db.user) : fail(401, 'Not signed in'),
  ),
  route('post', '/api/auth/logout', () => {
    db.signedIn = false;
    return new Response(null, { status: 204 });
  }),
];
