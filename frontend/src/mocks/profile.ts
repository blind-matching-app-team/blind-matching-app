import { delay, http, HttpResponse } from 'msw';
import { profileKey, type ProfileDraft } from '../lib/profileDraft';
import { setupKey } from '../lib/preferences';
import { emptyLifestyle, validateLifestyle } from '../lib/profileLifestyle';

export const profileHandlers = [
  http.post('/api/mock/profile/photo', async ({ request }) => {
    await delay(250);
    const file = (await request.formData()).get('photo');
    if (
      !(file instanceof File) ||
      !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
      file.size > 2 * 1024 * 1024
    ) {
      return HttpResponse.json(
        { message: '2MB 이하의 JPG, PNG, WebP 사진을 선택해주세요.' },
        { status: 400 },
      );
    }
    const url = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
    return HttpResponse.json({ data: { url } });
  }),
  http.put('/api/mock/profile', async ({ request }) => {
    await delay(350);
    const profile = { ...emptyLifestyle, ...((await request.json()) as ProfileDraft) };
    const lifestyleError = validateLifestyle(profile);
    if (lifestyleError) return HttpResponse.json({ message: lifestyleError }, { status: 400 });
    if (
      !profile.nickname.trim() ||
      Array.from(profile.nickname).length > 10 ||
      !profile.birthDate
    ) {
      return HttpResponse.json({ message: '닉네임과 생년월일을 확인해주세요.' }, { status: 400 });
    }
    try {
      localStorage.setItem(profileKey(), JSON.stringify(profile));
      localStorage.setItem(setupKey('profile-completed'), 'true');
    } catch {
      return HttpResponse.json(
        { message: '저장 공간이 부족합니다. 더 작은 사진을 선택해주세요.' },
        { status: 507 },
      );
    }
    return HttpResponse.json({ success: true, data: profile });
  }),
];
