import { useId, useState, type FormEvent } from 'react';
import { createRoot } from 'react-dom/client';
import Modal from '../components/Modal';
import './identitySdk.css';

export type MockSdkResult = {
  name: string;
  birthDate: string;
  genderCode: 'M' | 'F';
  phoneNumber: string;
  carrier: string;
};

const CARRIERS = ['SKT', 'KT', 'LG U+', '알뜰폰'];

// Dev-only window mounted in its own root, so fast refresh of this file does not matter.
// eslint-disable-next-line react-refresh/only-export-components
function MockIdentitySdk({
  onDone,
  onCancel,
}: {
  onDone: (result: MockSdkResult) => void;
  onCancel: () => void;
}) {
  const titleId = useId();
  const [form, setForm] = useState<MockSdkResult>({
    carrier: 'SKT',
    name: '홍길동',
    birthDate: '1995-05-05',
    genderCode: 'M',
    phoneNumber: '01012345678',
  });
  const update = (change: Partial<MockSdkResult>) => setForm({ ...form, ...change });
  const invalid = !form.name.trim() || !form.birthDate || !/^01\d{8,9}$/.test(form.phoneNumber);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!invalid) onDone({ ...form, name: form.name.trim() });
  }

  return (
    <Modal open labelledBy={titleId}>
      <form className="mock-sdk" onSubmit={submit}>
        <p className="mock-sdk-badge">개발용 목 인증 · 실제 본인인증이 아니에요</p>
        <h2 className="cm-title" id={titleId}>
          통신사 본인인증
        </h2>
        <fieldset className="mock-sdk-carriers">
          <legend>통신사</legend>
          {CARRIERS.map((carrier) => (
            <label key={carrier}>
              <input
                type="radio"
                name="carrier"
                checked={form.carrier === carrier}
                onChange={() => update({ carrier })}
              />
              {carrier}
            </label>
          ))}
        </fieldset>
        <label className="mock-sdk-field">
          이름
          <input
            className="ui-input"
            value={form.name}
            autoComplete="off"
            onChange={(event) => update({ name: event.target.value })}
          />
        </label>
        <label className="mock-sdk-field">
          생년월일 <span>(만 19세 미만이면 거부 화면으로 가요)</span>
          <input
            className="ui-input"
            type="date"
            value={form.birthDate}
            max={new Date().toISOString().slice(0, 10)}
            onChange={(event) => update({ birthDate: event.target.value })}
          />
        </label>
        <fieldset className="mock-sdk-carriers">
          <legend>성별</legend>
          {(
            [
              ['M', '남성'],
              ['F', '여성'],
            ] as const
          ).map(([code, label]) => (
            <label key={code}>
              <input
                type="radio"
                name="gender"
                checked={form.genderCode === code}
                onChange={() => update({ genderCode: code })}
              />
              {label}
            </label>
          ))}
        </fieldset>
        <label className="mock-sdk-field">
          휴대폰 번호
          <input
            className="ui-input"
            inputMode="numeric"
            value={form.phoneNumber}
            autoComplete="off"
            onChange={(event) => update({ phoneNumber: event.target.value.replace(/\D/g, '') })}
          />
        </label>
        <div className="cm-actions">
          <button type="button" className="ui-button ui-button--secondary" onClick={onCancel}>
            취소
          </button>
          <button
            type="submit"
            className="ui-button cm-confirm cm-confirm--normal"
            disabled={invalid}
          >
            인증하기
          </button>
        </div>
      </form>
    </Modal>
  );
}

/** Mounts the stand-in provider window outside the app tree, like a real SDK would. */
export function showMockIdentitySdk() {
  return new Promise<MockSdkResult>((resolve, reject) => {
    const host = document.createElement('div');
    document.body.append(host);
    const root = createRoot(host);
    const close = () => {
      root.unmount();
      host.remove();
    };
    root.render(
      <MockIdentitySdk
        onDone={(result) => {
          close();
          resolve(result);
        }}
        onCancel={() => {
          close();
          reject(Object.assign(new Error('SDK_CANCELLED'), { cancelled: true }));
        }}
      />,
    );
  });
}
