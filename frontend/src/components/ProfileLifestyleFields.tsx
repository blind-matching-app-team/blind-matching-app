import {
  hobbyOptions,
  lifestyleChoices,
  MAX_HOBBIES,
  MAX_INTRODUCTION,
  type LifestyleChoice,
  type ProfileLifestyle,
} from '../lib/profileLifestyle';

export default function ProfileLifestyleFields({
  value,
  onChange,
  field,
}: {
  value: ProfileLifestyle;
  onChange: (change: Partial<ProfileLifestyle>) => void;
  field: keyof ProfileLifestyle;
}) {
  const introductionLength = Array.from(value.introduction).length;
  return (
    <section className="profile-lifestyle" aria-labelledby="lifestyle-title">
      <h2 id="lifestyle-title">
        {field === 'hobbies'
          ? '어떤 취미를 즐기세요?'
          : field === 'introduction'
            ? '나를 한 줄로 소개해주세요'
            : `${lifestyleChoices[field].label}에 대해 알려주세요`}
      </h2>
      <p className="profile-lifestyle-description">
        모두 선택 항목이에요. 편하게 나누고 싶은 이야기만 담아주세요.
      </p>
      {(Object.keys(lifestyleChoices) as LifestyleChoice[])
        .filter((key) => key === field)
        .map((key) => (
          <div className="profile-setup-field" key={key}>
            <label htmlFor={`profile-${key}`}>
              {lifestyleChoices[key].label} <span>(선택)</span>
            </label>
            <select
              id={`profile-${key}`}
              value={value[key]}
              onChange={(event) => onChange({ [key]: event.target.value })}
            >
              <option value="">선택 안 함</option>
              {lifestyleChoices[key].options.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
        ))}
      {field === 'hobbies' && (
        <fieldset className="profile-hobbies" aria-describedby="hobbies-help">
          <legend>
            취미·관심사 <span>(선택)</span>
          </legend>
          <p id="hobbies-help">
            최대 {MAX_HOBBIES}개 · {value.hobbies.length}개 선택
          </p>
          <div className="profile-hobby-options">
            {hobbyOptions.map((hobby) => {
              const selected = value.hobbies.includes(hobby);
              return (
                <button
                  type="button"
                  key={hobby}
                  aria-pressed={selected}
                  disabled={!selected && value.hobbies.length >= MAX_HOBBIES}
                  onClick={() =>
                    onChange({
                      hobbies: selected
                        ? value.hobbies.filter((item) => item !== hobby)
                        : [...value.hobbies, hobby],
                    })
                  }
                >
                  {hobby}
                </button>
              );
            })}
          </div>
          {value.hobbies.length >= MAX_HOBBIES && (
            <p className="profile-lifestyle-description" role="status">
              5개를 선택했어요. 다른 취미를 고르려면 선택한 취미를 먼저 해제해주세요.
            </p>
          )}
        </fieldset>
      )}
      {field === 'introduction' && (
        <div className="profile-setup-field">
          <label htmlFor="profile-introduction">
            한 줄 소개 <span>(선택)</span>
          </label>
          <textarea
            id="profile-introduction"
            rows={3}
            value={value.introduction}
            onChange={(event) => onChange({ introduction: event.target.value })}
            placeholder="주말엔 동네 카페 찾는 걸 좋아해요"
            aria-describedby="introduction-help"
            aria-invalid={introductionLength > MAX_INTRODUCTION}
          />
          <p
            id="introduction-help"
            className={
              introductionLength > MAX_INTRODUCTION ? 'profile-setup-error' : 'profile-field-hint'
            }
          >
            {introductionLength > MAX_INTRODUCTION
              ? '한 줄 소개는 100자 이내로 입력해주세요. '
              : ''}
            {introductionLength}/{MAX_INTRODUCTION}자
          </p>
        </div>
      )}
    </section>
  );
}
