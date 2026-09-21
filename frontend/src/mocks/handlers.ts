import { http, HttpResponse } from 'msw';
import { passwordResetHandlers } from './passwordReset';

export const handlers = [
  ...passwordResetHandlers,
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
