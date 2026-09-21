# 공통 확인 모달 · 토스트 (BMA-58, CM-01~07)

`main.tsx`의 `ToastProvider`가 라우터 전체를 감싼다. 화면은 `useToast()`로
받은 `showToast(message)`를 호출한다. 문구는 자유롭게 주입할 수 있으며,
사양서 기본 문구는 `feedbackContent.ts`의 `toastMessages`에 있다.
하단 중앙에서 3초 동안 표시하고, 연속 호출은 최신 문구로 교체하면서
타이머를 초기화한다. 라우트 변경에도 유지하며 클릭 동작은 없다.

```tsx
const showToast = useToast();
showToast(toastMessages.saved);

<ConfirmModal
  {...confirmationContent.logout}
  open={open}
  onClose={() => setOpen(false)}
  onConfirm={handleLogout}
/>;
```

확인 모달은 `title`, `description`, `confirmLabel`, `cancelLabel`,
`variant` (`normal` / `danger`)을 props로 받는다. 취소는 `onClose`만,
확인은 `onConfirm`을 호출한 뒤 `onClose`를 호출한다. 액션은 호출 화면이
담당하며 컴포넌트는 API를 호출하지 않는다. 비동기 API의 성공·실패 처리도
호출 화면에서 담당한다. 바깥 클릭과 Escape로 닫히지 않는다.

`Modal`은 native `<dialog>.showModal()`로 배경 비활성화, 키보드 포커스
제한, 중앙 배치와 backdrop을 제공한다. 닫을 때 원래 포커스를 복원한다.
추후 CM-08~17의 폼·구매 모달은 이 카드 컨테이너를 재사용할 수 있다.
토스트는 manual popover로 모달 위에서도 표시한다.

연결: S1 소셜 로그인 취소, S5 재매칭, S5~S8 사이드바 로그아웃,
S8 로그아웃·탈퇴·준비중 메뉴. 차단 문구도 재사용 가능한 프리셋으로 제공한다.
로그아웃·탈퇴·재매칭의 기존 화면 동작은 유지하며 실제 API 연동은 별도 티켓 범위다.
세션 만료 2초 후 이동 역시 인증 계층에서 처리해야 하며 UI가 이동시키지 않는다.

검증: `frontend`에서 `npm run build`, `npm run lint`.
앱 서버 실행 후 `qa`에서 `BASE_URL`을 서버 주소로 설정하고
`npx playwright test --project=e2e feedback.spec.ts`로 공통 UI 동작을 검증한다.
