# blind-matching-app
대화 기반 단계적 프로필 공개 매칭 서비스

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
