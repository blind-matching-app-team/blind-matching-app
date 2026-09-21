import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { saveProfile, uploadProfilePhoto } from '../api/profile';
import { regions } from '../data/regions';
import { mbtiOptions, profileKey, readProfile, type ProfileDraft } from '../lib/profileDraft';
import './ProfileSetupPage.css';
import ProfileLifestyleFields from '../components/ProfileLifestyleFields';
import { emptyLifestyle, validateLifestyle, type ProfileLifestyle } from '../lib/profileLifestyle';

const lifestyleSteps: { field: keyof ProfileLifestyle; label: string }[] = [
  { field: 'drinking', label: '음주' },
  { field: 'smoking', label: '흡연' },
  { field: 'hobbies', label: '취미' },
  { field: 'occupation', label: '직업' },
  { field: 'religion', label: '종교' },
  { field: 'relationship', label: '연애관' },
  { field: 'introduction', label: '한 줄 소개' },
];

export default function ProfileSetupPage() {
  const navigate = useNavigate();
  const photoInput = useRef<HTMLInputElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const [step, setStep] = useState(0);
  const currentStep = lifestyleSteps[step - 1];
  useEffect(() => {
    heading.current?.focus();
  }, [step]);
  const [form, setForm] = useState(readProfile);
  const [error, setError] = useState('');
  const [photoError, setPhotoError] = useState('');
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const nicknameTooLong = Array.from(form.nickname).length > 10;
  const busy = uploading || saving;

  function moveTo(next: number) {
    setError('');
    setStep(next);
    window.scrollTo({ top: 0, behavior: 'instant' });
  }

  async function finish(profile: ProfileDraft) {
    const message = validateLifestyle(profile);
    if (message) {
      setError(message);
      return;
    }
    setSaving(true);
    setError('');
    try {
      await saveProfile({ ...profile, nickname: profile.nickname.trim() });
      navigate('/profile/preferences');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '저장하지 못했어요. 다시 시도해주세요.');
    } finally {
      setSaving(false);
    }
  }

  async function skip() {
    if (!currentStep || busy) return;
    const change = { [currentStep.field]: emptyLifestyle[currentStep.field] };
    update(change);
    if (step === lifestyleSteps.length) await finish({ ...form, ...change });
    else moveTo(step + 1);
  }

  function update(change: Partial<ProfileDraft>) {
    const next = { ...form, ...change };
    setForm(next);
    setError('');
    try {
      localStorage.setItem(profileKey(), JSON.stringify(next));
    } catch {
      setError('입력값을 임시 저장하지 못했어요. 더 작은 사진을 선택해주세요.');
    }
  }

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setPhotoError('');
    if (
      !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
      file.size > 2 * 1024 * 1024
    ) {
      setPhotoError('2MB 이하의 JPG, PNG, WebP 사진을 선택해주세요.');
      return;
    }
    setUploading(true);
    try {
      // Reject corrupted files before replacing the existing photo.
      const bitmap = await createImageBitmap(file);
      bitmap.close();
      update({ photo: await uploadProfilePhoto(file) });
    } catch {
      setPhotoError('사진을 불러오지 못했어요. 다른 사진으로 다시 시도해주세요.');
    } finally {
      setUploading(false);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const lifestyleError = currentStep
      ? validateLifestyle({ ...emptyLifestyle, [currentStep.field]: form[currentStep.field] })
      : '';
    if (lifestyleError) {
      setError(lifestyleError);
      return;
    }
    if (!form.nickname.trim() || nicknameTooLong) {
      setError('닉네임을 1~10자로 입력해주세요.');
      return;
    }
    if (form.province && !form.district) {
      setError('시/군/구까지 선택해주세요.');
      return;
    }
    if (step < lifestyleSteps.length) moveTo(step + 1);
    else await finish(form);
  }

  return (
    <main className="profile-setup-shell">
      <section className="profile-setup-card" aria-labelledby="profile-title">
        <h1 id="profile-title" ref={heading} tabIndex={-1}>
          프로필 설정{currentStep ? ` · ${currentStep.label}` : ''}
        </h1>
        <div className="profile-step-progress">
          <p aria-live="polite">
            {currentStep
              ? `나를 알아가는 질문 ${step} / ${lifestyleSteps.length}`
              : '먼저 기본 프로필을 알려주세요'}
          </p>
          <progress
            aria-label="프로필 설정 진행"
            value={step + 1}
            max={lifestyleSteps.length + 1}
          />
        </div>
        <form onSubmit={submit}>
          <fieldset disabled={busy} className="profile-setup-fields">
            <fieldset
              hidden={step !== 0}
              disabled={step !== 0 || busy}
              className="profile-setup-fields"
            >
              <div className="profile-photo-area">
                <button
                  type="button"
                  className="profile-photo-button"
                  onClick={() => photoInput.current?.click()}
                  aria-label={form.photo ? '프로필 사진 변경' : '프로필 사진 업로드'}
                  aria-describedby="photo-format"
                >
                  {form.photo ? (
                    <img src={form.photo} alt="내 프로필 사진" />
                  ) : (
                    <svg width="48" height="48" viewBox="0 0 48 48" fill="none" aria-hidden="true">
                      <circle cx="24" cy="17" r="8" stroke="currentColor" strokeWidth="2" />
                      <path
                        d="M10 40c0-9 6-14 14-14s14 5 14 14"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                      />
                    </svg>
                  )}
                  <span className="profile-photo-badge" aria-hidden="true">
                    ✎
                  </span>
                </button>
                <input
                  ref={photoInput}
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  hidden
                  onChange={upload}
                />
                <p id="photo-format" className="profile-photo-format">
                  {uploading ? '사진을 업로드하고 있어요…' : '사진 1장 · JPG, PNG, WebP · 최대 2MB'}
                </p>
                {photoError && (
                  <p className="profile-setup-error" role="alert">
                    {photoError}
                  </p>
                )}
                <div className="profile-photo-notice">
                  <strong>사진은 이렇게 쓰여요</strong>
                  <ul>
                    <li>매칭 상대에게 처음엔 흐리게만 보여요</li>
                    <li>대화가 쌓일수록 서서히 선명해져요</li>
                    <li>원하면 사진인증에만 활용하고 비공개로 둘 수 있어요</li>
                  </ul>
                </div>
              </div>

              <div className="profile-setup-field">
                <label htmlFor="profile-nickname">
                  닉네임 <span>(필수)</span>
                </label>
                <input
                  id="profile-nickname"
                  value={form.nickname}
                  onChange={(e) => update({ nickname: e.target.value })}
                  required
                  placeholder="어떻게 불러드릴까요?"
                  aria-invalid={nicknameTooLong}
                  aria-describedby="nickname-help"
                  autoComplete="nickname"
                />
                <p
                  id="nickname-help"
                  className={nicknameTooLong ? 'profile-setup-error' : 'profile-field-hint'}
                >
                  {nicknameTooLong
                    ? '닉네임은 최대 10자까지 입력할 수 있어요.'
                    : `${Array.from(form.nickname).length}/10자`}
                </p>
              </div>
              <div className="profile-setup-field">
                <label htmlFor="profile-birth">
                  생년월일 <span>(필수)</span>
                </label>
                <input
                  id="profile-birth"
                  type="date"
                  required
                  value={form.birthDate}
                  onChange={(e) => update({ birthDate: e.target.value })}
                  autoComplete="bday"
                />
              </div>
              <div className="profile-setup-field">
                <label htmlFor="profile-province">지역</label>
                <div className="profile-region-selects">
                  <select
                    id="profile-province"
                    value={form.province}
                    onChange={(e) => update({ province: e.target.value, district: '' })}
                  >
                    <option value="">시/도 선택</option>
                    {regions
                      .filter((r) => !r.parent)
                      .map((r) => (
                        <option key={r.code} value={r.code}>
                          {r.name}
                        </option>
                      ))}
                  </select>
                  <select
                    aria-label="시/군/구"
                    disabled={!form.province || busy}
                    value={form.district}
                    onChange={(e) => update({ district: e.target.value })}
                  >
                    <option value="">시/군/구 선택</option>
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
                <label htmlFor="profile-mbti">MBTI</label>
                <select
                  id="profile-mbti"
                  value={form.mbti}
                  onChange={(e) => update({ mbti: e.target.value })}
                >
                  <option value="">MBTI 선택</option>
                  {mbtiOptions.map((m) => (
                    <option key={m}>{m}</option>
                  ))}
                </select>
              </div>
              <div className="profile-setup-field">
                <label htmlFor="profile-height">
                  키 <span>(선택)</span>
                </label>
                <div className="profile-height-input">
                  <input
                    id="profile-height"
                    type="number"
                    min="1"
                    step="1"
                    inputMode="numeric"
                    value={form.height}
                    onChange={(e) => update({ height: e.target.value })}
                    placeholder="키를 입력해주세요"
                    aria-describedby="height-unit"
                  />
                  <span id="height-unit">cm</span>
                </div>
              </div>
            </fieldset>
            {currentStep && (
              <ProfileLifestyleFields
                key={currentStep.field}
                field={currentStep.field}
                value={form}
                onChange={update}
              />
            )}
            {error && (
              <p role="alert" className="profile-setup-error">
                {error}
              </p>
            )}
            <div className="profile-setup-actions">
              <button
                type="button"
                className="profile-previous"
                onClick={() => (step === 0 ? navigate('/onboarding') : moveTo(step - 1))}
              >
                이전
              </button>
              <button type="submit" className="profile-save" disabled={nicknameTooLong}>
                {saving ? '저장 중…' : step === lifestyleSteps.length ? '저장하고 계속' : '다음'}
              </button>
            </div>
            {currentStep && (
              <button type="button" className="profile-step-skip" onClick={skip}>
                {step === lifestyleSteps.length ? '소개 없이 저장하기' : '이 항목 건너뛰기'}
              </button>
            )}
          </fieldset>
        </form>
      </section>
    </main>
  );
}

// S2 is owned by the onboarding ticket. Preserve navigation until it is connected.
export function OnboardingConnectionPage() {
  return (
    <main className="profile-setup-shell">
      <section className="profile-setup-card">
        <h1>온보딩</h1>
        <p>온보딩 화면은 준비 중이에요. 입력한 프로필은 유지돼요.</p>
        <Link to="/profile/edit">프로필 설정으로 돌아가기</Link>
      </section>
    </main>
  );
}
