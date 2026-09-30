import { http, HttpResponse } from 'msw';
import { passwordResetHandlers } from './passwordReset';
import { chatHandlers } from './chat';
import { profileHandlers } from './profile';
import { preferenceHandlers } from './preferences';
import { matchingHandlers } from './matching';

export const handlers = [
  ...matchingHandlers,
  ...preferenceHandlers,
  ...profileHandlers,
  ...chatHandlers,
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
