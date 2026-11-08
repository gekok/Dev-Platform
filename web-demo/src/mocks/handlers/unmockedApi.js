import { http, HttpResponse } from 'msw';

export const missingDemoApiHandler = http.all('*/api/v1/*', ({ request }) => HttpResponse.json({
  code: 'DEMO_API_NOT_MOCKED',
  message: `The Demo API has no handler for ${request.method} ${new URL(request.url).pathname}.`,
}, { status: 501 }));
