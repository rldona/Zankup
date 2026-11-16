/** The deployed commit (Vercel) or "dev": a new build drops what an older one kept offline. */
declare const __APP_VERSION__: string;

interface ImportMetaEnv {
  /** "1" in `pnpm dev:mock`: the API is answered by the mock handlers (src/mocks), not a backend. */
  readonly VITE_API_MOCK?: string;
}
