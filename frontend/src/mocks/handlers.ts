import { http, HttpResponse } from 'msw';

export const handlers = [
  http.post('/api/v1/matching/queue', () => {
    return HttpResponse.json(
      {
        success: true,
        code: 'SUCCESS',
        message: '요청이 정상 처리되었습니다.',
        data: {
        queueId: 1001,
        queueStatus: 'WAITING',
      },
    },
  {
    status: 200,
  },
);
}),
];