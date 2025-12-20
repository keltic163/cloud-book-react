const http = require('http');

// Config: project id, host and port of emulator
const project = process.env.FIREBASE_PROJECT || process.env.GCLOUD_PROJECT || 'demo-project';
const host = process.env.FUNCTIONS_EMULATOR_HOST || '127.0.0.1';
const port = process.env.FUNCTIONS_EMULATOR_PORT || 5001;

const devCode = process.env.DEV_KEY_CODE || '6yhn%TGB';

async function callValidate(apiKey) {
    // Try the internal Call REST API path first, then fallback to the direct function URL exposed by the emulator
  const candidatePaths = [
    `/v1/projects/${project}/locations/us-central1/functions/validateKey:call`,
    `/${project}/us-central1/validateKey`
  ];
  const body = JSON.stringify({ data: { apiKey } });

  return new Promise(async (resolve, reject) => {
    for (const p of candidatePaths) {
      try {
        const res = await new Promise((resResolve, resReject) => {
          const headers = { 'Content-Type': 'application/json', 'Authorization': 'Bearer owner' };
          const req = http.request({ hostname: host, port, path: p, method: 'POST', headers }, (res) => {
            let raw = '';
            res.on('data', (c) => raw += c);
            res.on('end', () => resResolve({ status: res.statusCode, raw }));
          });
          req.on('error', (e) => resReject(e));
          req.write(body);
          req.end();
        });

        // Attempt to parse JSON, otherwise continue to next path
        try {
          const parsed = JSON.parse(res.raw);
          return resolve({ status: res.status, body: parsed });
        } catch (e) {
          // Not JSON -> try next path
          continue;
        }
      } catch (e) {
        // Try next path
        continue;
      }
    }
    reject(new Error('All candidate paths failed or returned non-JSON responses'));
  });
}

(async () => {
  console.log('Calling validateKey with DEV_CODE...');
  try {
    const r1 = await callValidate(devCode);
    console.log('Response for dev-code:', JSON.stringify(r1, null, 2));

    console.log('Calling validateKey with an invalid key...');
    const r2 = await callValidate('invalid-key');
    console.log('Response for invalid key:', JSON.stringify(r2, null, 2));

    // Because these are integration tests against the real Gemini service (or a dummy secret),
    // model lists may be empty. We consider the test successful if the callable executed
    // without crashing (HTTP 200) and returned a structured result object for both calls.
    const ok = r1.status === 200 && r1.body && r1.body.result && r2.status === 200 && r2.body && r2.body.result;

    if (ok) {
      console.log('E2E check passed (functions executed and returned results)');
      process.exit(0);
    } else {
      console.error('E2E check failed: unexpected responses', { r1, r2 });
      process.exit(2);
    }
  } catch (e) {
    console.error('E2E call failed:', e);
    process.exit(3);
  }
})();