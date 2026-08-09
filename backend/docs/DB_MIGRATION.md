# DB 마이그레이션 가이드 (Flyway)

이 프로젝트의 DB 스키마는 **Flyway**로 버전 관리한다.
스키마를 바꾸는 방법은 **새 마이그레이션 파일을 추가하는 것 하나뿐이다.**

---

## 1. 왜 Flyway 인가

Liquibase 도 후보였지만 다음 이유로 Flyway 를 택했다.

| 판단 근거 | 내용 |
| --- | --- |
| DB 가 MySQL 8.4 로 고정 | Liquibase 의 최대 장점인 DB 중립 changelog 가 이 프로젝트에서는 이점이 없다 |
| BMA-14 스키마가 이미 MySQL DDL | Flyway 는 그대로 옮기면 되지만, Liquibase 는 61KB DDL 을 changelog 로 변환하거나 `<sqlFile>` 로 감싸야 한다 |
| 기존 파일이 이미 Flyway 규칙 | `V1_1__profile_image_object_key.sql` 이 `V{버전}__{설명}.sql` 형식이었다 |
| 리뷰 용이성 | PR 에서 실제 실행될 DDL 을 그대로 읽을 수 있다 |

Liquibase 의 롤백 기능은 채택 사유가 되지 못했다. 운영 DB 는 롤백 대신
**정정 마이그레이션을 앞으로 하나 더 붙이는 방식(forward fix)** 으로 대응한다.
이미 데이터가 쌓인 컬럼을 되돌리면 그 데이터는 복구할 수 없기 때문이다.

Flyway Community Edition 은 무료이며 MySQL 을 지원한다.
유료 기능(Undo, Dry-run)은 사용하지 않는다.

---

## 2. 파일 위치와 이름 규칙

```
backend/src/main/resources/db/migration/
├── V1__init_schema.sql               # 업무 테이블 32개
├── V2__seed_common_code.sql          # 공통 코드 및 Reveal 정책 시드
├── V3__profile_image_object_key.sql  # US_PROFILE_IMAGE 오브젝트 키 전환
└── V4__billing_key_store.sql         # PY_BILLING_KEY 신설, 빌링키를 사용자 단위로 이동
```

이름 규칙은 `V{버전}__{설명}.sql` 이다. **언더스코어 2개**에 주의한다.

- 버전은 정수를 1씩 올린다. 다음은 `V5__` 다.
- 설명은 소문자 snake_case 로, 무엇을 하는지 알 수 있게 쓴다.
- 브랜치를 여러 개 동시에 작업해 버전이 겹치면 머지 시점에 조정한다.
  Flyway 는 같은 버전이 2개면 기동을 거부한다.

---

## 3. 스키마를 바꿔야 할 때

1. `db/migration/` 에 다음 버전 파일을 만든다.

   ```sql
   -- V4__add_meeting_result.sql
   ALTER TABLE MT_MATCH
       ADD COLUMN RESULT_STATUS VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER MATCH_STATUS;
   ```

2. 엔티티 클래스를 함께 수정한다.
3. `docker compose up -d` 로 앱을 재기동한다. 기동 시 Flyway 가 자동 적용한다.
4. 엔티티와 스키마가 어긋나면 `ddl-auto: validate` 가 기동을 막는다. 이때는 마이그레이션을 고친다.

### 반드시 지킬 것

- **이미 적용된 마이그레이션 파일은 절대 수정하지 않는다.**
  Flyway 가 파일 체크섬을 이력 테이블과 비교하므로, 수정하면 다음 기동이 실패한다.
  잘못 넣었더라도 되돌리는 마이그레이션을 새로 추가한다.
- **`ddl-auto` 를 `update` 로 되돌리지 않는다.**
  Hibernate 가 스키마를 말없이 고쳐 마이그레이션 이력과 실제 스키마가 어긋난다.
- **DB 콘솔에서 직접 `ALTER TABLE` 하지 않는다.**
  내 로컬에서만 반영되고 팀원과 운영 DB 에는 남지 않는다.
- **마이그레이션에 `CREATE DATABASE` / `USE` 를 쓰지 않는다.**
  대상 스키마는 접속 URL(`DB_URL`)이 결정한다. DB 이름을 하드코딩하면
  테스트/운영 스키마에서 같은 파일을 재사용할 수 없다.

---

## 4. 적용 이력 확인

Flyway 는 `flyway_schema_history` 테이블에 적용 이력을 남긴다.

```sql
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

`success = 0` 인 행이 있으면 그 마이그레이션이 실패한 상태다.
MySQL 은 DDL 트랜잭션 롤백을 지원하지 않으므로, 실패한 DDL 이 절반만 적용됐을 수 있다.
로컬이면 5절의 초기화가 가장 빠르다.

---

## 5. 로컬 DB 초기화 및 검증

기존 볼륨에는 예전 `docker-entrypoint-initdb.d` 방식으로 만들어진 테이블이 남아 있다.
이 상태에서는 Flyway 가 "이력 테이블 없이 테이블만 있는 스키마"로 판단해 기동을 거부한다.
(`baseline-on-migrate: false` 로 일부러 실패시킨다. 자동 baseline 은 V1 을 건너뛰어
실제 스키마와 이력이 어긋난 채 굴러가게 만들기 때문이다.)

**볼륨을 지우고 처음부터 다시 만든다.**

```bash
docker compose down -v
docker compose up -d --build
docker compose logs -f app     # "Successfully applied 4 migrations" 확인
```

### 테이블 생성 확인

```bash
docker compose exec mysql mysql -ubma -pbma1234 bma \
  -e "SELECT COUNT(*) AS table_count FROM information_schema.tables
      WHERE table_schema='bma' AND table_name <> 'flyway_schema_history';"
```

`table_count` 는 **V3 까지는 32, V4 적용 후에는 33** 이다.
V4 가 `PY_BILLING_KEY` 를 추가하기 때문이다.
`flyway_schema_history` 는 Flyway 가 만드는 관리용 테이블이라 제외하고 센다.

---

## 6. 기존 운영 DB 에 도입할 때

이미 테이블이 있는 DB 는 볼륨을 지울 수 없으므로 baseline 을 잡는다.

1. 운영 스키마가 최신 마이그레이션까지 적용된 상태와 **동일한지 먼저 확인한다.**
2. `spring.flyway.baseline-on-migrate: true`, `baseline-version: {해당 버전}` 으로 **한 번만** 기동한다.
3. Flyway 가 `flyway_schema_history` 를 만들고 그 버전까지를 적용 완료로 기록한다.
4. 기동이 끝나면 설정을 원래대로(`baseline-on-migrate: false`) 되돌린다.

1번을 건너뛰면 실제 스키마와 이력이 어긋난 채 고정되므로, 반드시 대조 후 진행한다.

---

## 7. 참고

- 테스트 코드는 현재 Spring 컨텍스트를 띄우지 않는 순수 단위 테스트라 Flyway 가 실행되지 않는다.
  추후 `@SpringBootTest` / `@DataJpaTest` 를 도입하면 MySQL 전용 DDL 이 H2 에서 깨지므로,
  Testcontainers 로 실제 MySQL 을 띄우거나 테스트 프로파일에서 `spring.flyway.enabled: false` 로 둔다.
- 원본 스키마 파일(`backend/database/*.sql`)은 이 마이그레이션으로 완전히 대체되어 제거했다.
  스키마가 두 곳에 존재하면 어느 쪽이 진짜인지 알 수 없기 때문이다.
  원본이 필요하면 커밋 `80c49c5` 에서 확인할 수 있다.
- ERD 는 `backend/docs/BMA_ERD_FULL.svg` 를 참고한다.
