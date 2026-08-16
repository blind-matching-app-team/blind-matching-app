# blind-matching-app
대화 기반 단계적 프로필 공개 매칭 서비스

## 디렉토리 구조
```
blind-matching-app/
├─ backend/               # 백엔드 서버 (예정)
├─ frontend/              # 프론트엔드 앱 (예정)
├─ qa/                    # Playwright 기반 QA 테스트 스위트
│  ├─ pages/              # Page Object Model 클래스
│  ├─ fixtures/           # 커스텀 Playwright fixture
│  ├─ tests/
│  │  ├─ api/             # API 테스트
│  │  └─ e2e/             # E2E(UI) 테스트
│  ├─ playwright.config.ts
│  ├─ tsconfig.json
│  └─ package.json
└─ README.md
```

- `backend/`, `frontend/`: 각 애플리케이션 코드가 추가될 자리 (현재는 빈 폴더)
- `qa/`: Playwright + TypeScript로 작성된 API/E2E 테스트. 실행은 `qa/` 디렉토리에서 `npm test`(전체), `npm run test:e2e`, `npm run test:api`

## 브랜치 전략
- `main`: 운영 배포 (메인 서버)
- `develop`: 테스트 환경 배포
- `feature/BMA-XX-설명`: 개별 작업 브랜치, develop에서 분기
- `fix/BMA-XX-설명`: 버그 수정 브랜치
- 모든 반영은 PR을 통해서만 (직접 push 금지)
- 커밋 메시지·브랜치명에 Jira 이슈 키 필수 포함

## PR 규칙
- 필수 리뷰어 승인 없음 — GitHub Actions(빌드+테스트) 통과 시 작성자 본인이 머지 가능
- PR 크기 400줄 이하 권장
- 문제 발생 시 git revert로 대응
