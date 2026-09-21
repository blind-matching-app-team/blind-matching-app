import { getPasswordRules } from '../lib/passwordRules';

export default function PasswordRules({ password, id }: { password: string; id?: string }) {
  const rules = getPasswordRules(password);
  return (
    <div className="rule-box" id={id} aria-label="비밀번호 규칙">
      {(
        [
          ['minLength', '8자 이상'],
          ['hasLetter', '영문 포함'],
          ['hasNumber', '숫자 포함'],
        ] as const
      ).map(([key, label]) => (
        <div key={key} className={`rule-item ${rules[key] ? 'done' : ''}`}>
          <span className="rule-bullet" aria-hidden="true">
            {rules[key] ? '✓' : '·'}
          </span>
          <span>{label}</span>
        </div>
      ))}
    </div>
  );
}
