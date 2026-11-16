/**
 * Mock handlers typed by the API's OpenAPI (`ApiPaths` in core): a handler's answer must match what
 * the backend returns, so a changed field breaks the typecheck here instead of the mocks drifting.
 */
import type { ApiPaths } from '@zankup/core';
import { http, HttpResponse, type DefaultBodyType, type HttpResponseResolver, type PathParams } from 'msw';

type Method = 'get' | 'post' | 'put' | 'patch' | 'delete';
type Op<P extends keyof ApiPaths, M extends Method> = ApiPaths[P][M];

/** The JSON body of an operation's successful answer (200 or 201). */
export type Res<P extends keyof ApiPaths, M extends Method> =
  Op<P, M> extends { responses: { 200: { content: { 'application/json': infer T } } } }
    ? T
    : Op<P, M> extends { responses: { 201: { content: { 'application/json': infer T } } } }
      ? T
      : never;

/** The JSON body an operation takes. */
export type Req<P extends keyof ApiPaths, M extends Method> =
  Op<P, M> extends { requestBody?: { content: { 'application/json': infer T } } } ? T : never;

/** `/api/activities/{activity_id}` → `/api/activities/:activity_id`, MSW's form. */
const mswPath = (path: string) => `*${path.replace(/\{(\w+)\}/g, ':$1')}`;

/** An operation's answer as JSON, checked against its type. */
export const json = <P extends keyof ApiPaths, M extends Method>(
  _path: P,
  _method: M,
  body: Res<P, M>,
  init?: ResponseInit,
) => HttpResponse.json(body as DefaultBodyType, init);

/** An error as the backend writes it (FastAPI's `detail`). */
export const fail = (status: number, detail = 'Mock error') => HttpResponse.json({ detail }, { status });

/** A handler for one operation of the API, matched on any origin. */
export function route<P extends keyof ApiPaths & string, M extends Method>(
  method: M,
  path: P,
  resolver: HttpResponseResolver<PathParams, Req<P, M> extends DefaultBodyType ? Req<P, M> : DefaultBodyType>,
) {
  return http[method](mswPath(path), resolver as HttpResponseResolver);
}
