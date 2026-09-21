# S13 비밀번호 재설정 UI

로그인(`/login`)의 **비밀번호를 잊으셨나요?** 링크 → `/forgot-password`.
올바른 이메일을 제출하면 MSW가 가입 여부와 관계없이 동일하게 발송 성공을 응답한다.
실제 메일은 보내지 않으며 실제 계정의 비밀번호를 변경하지 않는다.

개발 서버에서 이메일 링크 진입을 다음 주소로 확인할 수 있다.

- 정상: `/reset-password?token=demo-reset-token`
- 만료: `/reset-password?token=expired-reset-token`
- 무효: `/reset-password?token=invalid` 또는 토큰 없이 `/reset-password`

정상 토큰은 MSW 메모리 내에서 1회 사용 후 무효화된다. 페이지를 새로고침하면
모의 상태는 초기화된다. 실제 만료 시간 및 서버 저장 정책은 아직 확정하지 않았다.
비밀번호는 S1과 같은 **8자 이상, 영문과 숫자 포함** 규칙과 안내 컴포넌트를 사용한다.
변경 성공 시 `/login`으로 이동하며 성공 토스트를 유지한다.

임시 MSW 계약(실제 백엔드 연동 티켓에서 합의 필요):

| POST 경로                              | 요청                  |
| -------------------------------------- | --------------------- |
| `/api/v1/auth/password-reset/request`  | `{ email }`           |
| `/api/v1/auth/password-reset/validate` | `{ token }`           |
| `/api/v1/auth/password-reset/confirm`  | `{ token, password }` |

성공은 `{ success: true, code: 'SUCCESS', data: null }`.
토큰 에러 코드는 `RESET_TOKEN_EXPIRED`, `RESET_TOKEN_INVALID`이며 HTTP 400.
검증 및 제출 시 모두 토큰 오류를 처리한다. 네트워크 실패는 다시 시도할 수 있고,
토큰 오류 화면에서는 새 링크 요청 및 로그인 이동이 가능하다.
공통 ERR 화면이 아직 없어 S1 카드 스타일의 오류 화면으로 처리하며,
공통 ERR가 확정되면 loader의 토큰 상태를 연결하면 된다.

원본 PDF/XLSX는 제공되지 않아 기존 S1 스타일과 전달된 요구사항을 기준으로 구현했다.
S13-01~10의 정확한 오브젝트별 배치·문구 대조는 원본 첨부파일 확보 후 가능하다.
실제 API 연동과 정식 QA 테스트 케이스는 별도 서브태스크 범위다.
