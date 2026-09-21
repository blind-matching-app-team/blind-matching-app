import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { completeMockVerification, savePreferences } from '../api/preferences';
import { regions } from '../data/regions';
import {
  readPreferences,
  setupKey,
  validatePreferences,
  type Preferences,
} from '../lib/preferences';
import './ProfileSetupPage.css';
import './PreferencesPage.css';

const ages = Array.from({ length: 81 }, (_, i) => i + 19);

export default function PreferencesPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState(readPreferences);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const validation = validatePreferences(form);
  const provinceName = regions.find((r) => r.code === form.province)?.name;
  function update(change: Partial<Preferences>) {
    const next = { ...form, ...change };
    setForm(next);
    setError('');
    try {
      localStorage.setItem(setupKey('preferences-draft'), JSON.stringify(next));
    } catch {
      setError('입력값을 임시 저장하지 못했어요. 저장 공간을 확인해주세요.');
    }
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving || validation) return;
    setSaving(true);
    setError('');
    try {
      const result = await savePreferences(form);
      navigate(result.data.identityVerified ? '/' : '/identity-verification');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '저장하지 못했어요. 다시 시도해주세요.');
    } finally {
      setSaving(false);
    }
  }
  return (
    <main className="profile-setup-shell">
      <section className="profile-setup-card preferences-card" aria-labelledby="preferences-title">
        <h1 id="preferences-title">매칭 선호 조건</h1>
        <p className="preferences-intro">조건은 최소한으로만 — 대화로 나머지를 알아가요</p>
        <form onSubmit={submit}>
          <fieldset className="profile-setup-fields" disabled={saving}>
            <div className="profile-setup-field">
              <label htmlFor="preferred-province">희망 지역</label>
              <div className="profile-region-selects">
                <select
                  id="preferred-province"
                  value={form.province}
                  onChange={(e) => update({ province: e.target.value, district: '' })}
                >
                  <option value="">지역 제한 없음</option>
                  {regions
                    .filter((r) => !r.parent)
                    .map((r) => (
                      <option key={r.code} value={r.code}>
                        {r.name}
                      </option>
                    ))}
                </select>
                <select
                  aria-label="희망 시/군/구"
                  value={form.district}
                  disabled={!form.province || saving}
                  onChange={(e) => update({ district: e.target.value })}
                >
                  <option value="">
                    {provinceName ? `${provinceName} 전체` : '시/군/구 전체'}
                  </option>
                  {regions
                    .filter((r) => r.parent === form.province)
                    .map((r) => (
                      <option key={r.code} value={r.code}>
                        {r.name}
                      </option>
                    ))}
                </select>
              </div>
            </div>
            <div className="profile-setup-field">
              <label htmlFor="preferred-min-age">희망 나이대</label>
              <div className="preferences-age-range">
                <select
                  id="preferred-min-age"
                  aria-label="최소 나이"
                  value={form.minAge}
                  onChange={(e) => update({ minAge: e.target.value })}
                  aria-describedby="age-help"
                  aria-invalid={Boolean(validation)}
                >
                  <option value="">제한 없음</option>
                  {ages.map((age) => (
                    <option key={age} value={age}>
                      {age}세
                    </option>
                  ))}
                </select>
                <span aria-hidden="true">~</span>
                <select
                  aria-label="최대 나이"
                  value={form.maxAge}
                  onChange={(e) => update({ maxAge: e.target.value })}
                  aria-describedby="age-help"
                  aria-invalid={Boolean(validation)}
                >
                  <option value="">제한 없음</option>
                  {ages.map((age) => (
                    <option key={age} value={age}>
                      {age}세
                    </option>
                  ))}
                </select>
              </div>
              <p id="age-help" className="preferences-help">
                19~99세 사이에서 선택할 수 있어요. 제한 없음은 전체 성인 범위예요.
              </p>
            </div>
            <p className="preferences-notice">
              키는 매칭 선호조건에 포함하지 않아요 — 대화 중심의 매칭을 위해서예요
            </p>
            {(error || validation) && (
              <p role="alert" className="profile-setup-error">
                {error || validation}
              </p>
            )}
            <div className="profile-setup-actions">
              <button
                type="button"
                className="profile-previous"
                onClick={() => navigate('/profile/edit')}
              >
                이전
              </button>
              <button type="submit" className="profile-save" disabled={Boolean(validation)}>
                {saving ? '저장 중…' : '매칭 시작하기'}
              </button>
            </div>
          </fieldset>
        </form>
      </section>
    </main>
  );
}

export function IdentityVerificationPage() {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function verifyMock() {
    setBusy(true);
    try {
      await completeMockVerification();
      navigate('/', { replace: true });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '다시 시도해주세요.');
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="profile-setup-shell">
      <section className="profile-setup-card preferences-card">
        <h1>본인인증</h1>
        <p className="preferences-intro">
          선호조건을 저장했어요. 매칭을 시작하려면 최초 1회 본인인증이 필요해요.
        </p>
        <p className="preferences-notice">본인인증 서비스는 준비 중이에요.</p>
        {error && (
          <p role="alert" className="profile-setup-error">
            {error}
          </p>
        )}
        <Link to="/profile/preferences">선호조건으로 돌아가기</Link>
        {import.meta.env.DEV && (
          <div className="verification-preview">
            <p>개발 미리보기 전용 · 실제 본인인증이 아닙니다.</p>
            <button type="button" disabled={busy} onClick={verifyMock}>
              {busy ? '처리 중…' : '목 인증 완료 후 계속'}
            </button>
          </div>
        )}
      </section>
    </main>
  );
}
