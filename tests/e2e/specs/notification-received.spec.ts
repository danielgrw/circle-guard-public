import { test, expect } from '@playwright/test';
import { deriveServiceOrigin, loginAndGetToken, fetchSuspectCount } from './utils';

test('survey submission eventually raises promotion health-status suspectCount', async ({
  request,
}) => {
  const formOrigin = deriveServiceOrigin('circleguard-form-service');

  const before = await fetchSuspectCount(request);
  const token = await loginAndGetToken(request);

  const submitRes = await request.post(`${formOrigin}/api/v1/surveys`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      symptoms: ['fever'],
      temperature: 39.2,
      contactWithInfected: true,
      timestamp: new Date().toISOString(),
    },
  });
  expect(submitRes.ok()).toBeTruthy();

  await expect
    .poll(async () => (await fetchSuspectCount(request)) > before, {
      timeout: 30_000,
    })
    .toBeTruthy();
});
