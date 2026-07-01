// End-to-end driver for TeamFlow: seeds data through the REST API, then drives
// the running Angular app in a real (headless) Chrome to exercise the per-project
// real-time chat panel (@mention autocomplete + live STOMP delivery).
//
// Prereqs (see SKILL.md): backend + frontend already running, and `playwright-core`
// installed somewhere resolvable (NODE_PATH). Chrome or Edge must be installed.
//
// Env:
//   FE_URL       frontend base URL              (default http://localhost:4200)
//   BE_URL       backend base URL               (default http://localhost:8080)
//   BACKEND_LOG  path to backend stdout log     (REQUIRED — scraped for the emailed
//                                                 verification codes, which the backend
//                                                 logs when SMTP is not configured)
//   OUT_DIR      screenshot output directory    (default ./screenshots)
//
// Exit code 0 = the whole flow worked and screenshots were written.
import { readFileSync, mkdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';

// Resolve playwright-core from wherever it was installed (ESM ignores NODE_PATH).
// PLAYWRIGHT_DIR should be a directory whose node_modules/ contains playwright-core;
// falls back to the current working directory.
const require = createRequire(import.meta.url);
const pwEntry = require.resolve('playwright-core', {
  paths: [process.env.PLAYWRIGHT_DIR || process.cwd(), import.meta.dirname],
});
const pw = await import(pathToFileURL(pwEntry).href);
const chromium = pw.chromium ?? pw.default?.chromium;

const FE = process.env.FE_URL || 'http://localhost:4200';
const BE = process.env.BE_URL || 'http://localhost:8080';
const LOG = process.env.BACKEND_LOG;
const OUT = process.env.OUT_DIR || 'screenshots';
const log = (...a) => console.log('[driver]', ...a);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

if (!LOG) { console.error('BACKEND_LOG env var is required'); process.exit(2); }
mkdirSync(OUT, { recursive: true });

const stamp = Date.now();
const alice = { email: `alice+${stamp}@example.com`, username: `alice${stamp}` };
const bob = { email: `bob+${stamp}@example.com`, username: `bob${stamp}` };

async function api(path, { method = 'GET', token, body } = {}) {
  const res = await fetch(`${BE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}: ${text}`);
  return data;
}

// The backend logs "verification code for <email> is <code>" when SMTP is unset.
async function codeFor(email) {
  for (let i = 0; i < 40; i++) {
    const m = readFileSync(LOG, 'utf8').match(
      new RegExp(`verification code for ${email.replace(/[+.]/g, '\\$&')} is (\\d{6})`),
    );
    if (m) return m[1];
    await sleep(500);
  }
  throw new Error(`no verification code found in ${LOG} for ${email}`);
}

async function signUp(u) {
  await api('/api/auth/register', {
    method: 'POST',
    body: { email: u.email, username: u.username, password: 'password123' },
  });
  const code = await codeFor(u.email);
  const auth = await api('/api/auth/verify-email', {
    method: 'POST',
    body: { email: u.email, code },
  });
  return { ...u, ...auth };
}

async function launchBrowser() {
  for (const channel of ['chrome', 'msedge']) {
    try {
      return await chromium.launch({ channel, headless: true });
    } catch {
      /* try next */
    }
  }
  throw new Error('could not launch Chrome or Edge via playwright-core');
}

const a = await signUp(alice);
const b = await signUp(bob);
log('registered + verified both users');

const ws = await api('/api/workspaces', { method: 'POST', token: a.accessToken, body: { name: 'Acme' } });
await api(`/api/workspaces/${ws.id}/invite`, {
  method: 'POST', token: a.accessToken, body: { email: bob.email },
});
await api(`/api/workspaces/${ws.id}/invite/accept`, { method: 'POST', token: b.accessToken });
const project = await api(`/api/workspaces/${ws.id}/projects`, {
  method: 'POST', token: a.accessToken, body: { name: 'Website Redesign' },
});
await api(`/api/projects/${project.id}/chat/messages`, {
  method: 'POST', token: b.accessToken,
  body: { content: 'Morning! I pushed the new color palette to the shared drive.' },
});
log(`seeded workspace ${ws.id} / project ${project.id}`);

const browser = await launchBrowser();
const page = await (await browser.newContext({ viewport: { width: 1280, height: 900 } })).newPage();
const errors = [];
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));
const shot = (n) => page.screenshot({ path: `${OUT}/${n}` });

try {
  await page.goto(`${FE}/login`);
  await page.evaluate(({ at, rt }) => {
    localStorage.setItem('teamflow.accessToken', at);
    localStorage.setItem('teamflow.refreshToken', rt);
  }, { at: a.accessToken, rt: a.refreshToken });

  await page.goto(`${FE}/projects`);
  await page.waitForSelector('text=Chat', { timeout: 20000 });
  await page.waitForSelector('text=color palette', { timeout: 20000 });
  await shot('01-chat-loaded.png');
  log('chat panel loaded with history');

  const box = page.locator('textarea[placeholder^="Write a message"]');
  await box.click();
  await box.type(`Looks great @${bob.username.slice(0, 6)}`, { delay: 30 });
  await page.waitForSelector(`button:has-text("@${bob.username}")`, { timeout: 5000 });
  await shot('02-mention-autocomplete.png');
  log('@mention autocomplete visible');

  await page.click(`button:has-text("@${bob.username}")`);
  await box.type('— shipping the hero section today.', { delay: 20 });
  await page.click('button:has-text("Send")');
  await page.waitForSelector('text=shipping the hero section today', { timeout: 10000 });
  await shot('03-message-sent.png');
  log('message sent and rendered');

  // Real-time: Bob posts via the API; it must stream into Alice's open panel over STOMP.
  await api(`/api/projects/${project.id}/chat/messages`, {
    method: 'POST', token: b.accessToken, body: { content: 'On it! Pushing the mockups now.' },
  });
  await page.waitForSelector('text=Pushing the mockups now', { timeout: 10000 });
  await shot('04-realtime.png');
  log('real-time message received live');

  if (errors.length) throw new Error(`console errors: ${JSON.stringify(errors)}`);
  log(`SUCCESS — screenshots in ${OUT}/`);
} catch (e) {
  log('FAILED', e.message);
  await shot('99-error.png');
  process.exitCode = 1;
} finally {
  await browser.close();
}
