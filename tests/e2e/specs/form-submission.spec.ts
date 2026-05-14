import { test, expect } from '@playwright/test';
import { deriveServiceOrigin, loginAndGetToken } from './utils';

test('submits a health survey form', async ({ request }) => {
  const formOrigin = deriveServiceOrigin('circleguard-form-service');
  const token = await loginAndGetToken(request);

  const submitRes = await request.post(`${formOrigin}/api/v1/surveys`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      symptoms: ['fever', 'cough'],
      temperature: 38.5,
      contactWithInfected: true,
      timestamp: new Date().toISOString(),
    },
  });
  expect(submitRes.ok()).toBeTruthy();
});
