import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { readPhotoVerification, verifySelfie } from '../api/photoVerification';
import './PhotoVerificationPage.css';

type Step = 'capture' | 'done' | 'failed';
type Camera = 'starting' | 'ready' | 'unavailable';

const CAPTURE_SIZE = 480;
const errorCode = (error: unknown) =>
  error instanceof Error ? (error.cause as { code?: string } | undefined)?.code : undefined;

export default function PhotoVerificationPage() {
  const navigate = useNavigate();
  const videoRef = useRef<HTMLVideoElement>(null);
  const [step, setStep] = useState<Step>('capture');
  const [camera, setCamera] = useState<Camera>('starting');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ message: string; needsPhoto?: boolean } | null>(null);

  // AC: a verified user is not sent through S16 again.
  useEffect(() => {
    const controller = new AbortController();
    readPhotoVerification(controller.signal)
      .then((status) => {
        if (status.verified) navigate('/profile', { replace: true });
      })
      .catch(() => {
        /* The capture still works; the server answers VERIFY_012 if already verified. */
      });
    return () => controller.abort();
  }, [navigate]);

  // S16-03: live front camera only while the capture state is shown.
  useEffect(() => {
    if (step !== 'capture') return;
    let stream: MediaStream | null = null;
    let cancelled = false;
    const request = navigator.mediaDevices?.getUserMedia
      ? navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false })
      : Promise.reject(new Error('Camera API unavailable'));
    request
      .then((media) => {
        if (cancelled) {
          media.getTracks().forEach((track) => track.stop());
          return;
        }
        stream = media;
        if (videoRef.current) videoRef.current.srcObject = media;
        setCamera('ready');
      })
      .catch(() => {
        if (!cancelled) setCamera('unavailable');
      });
    return () => {
      cancelled = true;
      stream?.getTracks().forEach((track) => track.stop());
    };
  }, [step]);

  function captureFrame() {
    const canvas = document.createElement('canvas');
    canvas.width = CAPTURE_SIZE;
    canvas.height = CAPTURE_SIZE;
    const context = canvas.getContext('2d')!;
    const video = videoRef.current;
    if (camera === 'ready' && video?.videoWidth) {
      // Centre-crop the frame to the square the circular guide shows.
      const side = Math.min(video.videoWidth, video.videoHeight);
      context.drawImage(
        video,
        (video.videoWidth - side) / 2,
        (video.videoHeight - side) / 2,
        side,
        side,
        0,
        0,
        CAPTURE_SIZE,
        CAPTURE_SIZE,
      );
    } else {
      // Dev without a webcam: a blank frame still exercises the MSW flow.
      context.fillStyle = '#f1efe8';
      context.fillRect(0, 0, CAPTURE_SIZE, CAPTURE_SIZE);
    }
    return new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error())), 'image/jpeg', 0.9),
    );
  }

  async function capture() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await verifySelfie(await captureFrame());
      setStep('done');
    } catch (reason) {
      const code = errorCode(reason);
      if (code === 'VERIFY_011') setStep('failed');
      else if (code === 'VERIFY_012') setStep('done');
      else if (code === 'VERIFY_010')
        setError({
          message: '대조할 프로필 사진이 없어요. 프로필 사진을 먼저 등록해주세요.',
          needsPhoto: true,
        });
      else
        setError({
          message:
            reason instanceof Error
              ? reason.message
              : '사진을 확인하지 못했어요. 다시 시도해주세요.',
        });
    } finally {
      setBusy(false);
    }
  }

  function retake() {
    setError(null);
    setCamera('starting');
    setStep('capture');
  }

  const canCapture = camera === 'ready' || (import.meta.env.DEV && camera === 'unavailable');

  return (
    <main className="photo-verify-shell">
      <section className="ui-enter photo-verify-card" aria-labelledby="photo-verify-title">
        <header className="photo-verify-header">
          <button
            type="button"
            className="ui-interactive photo-verify-back"
            aria-label="마이페이지로 돌아가기"
            onClick={() => navigate('/profile')}
          >
            ←
          </button>
          {/* S16-01 */}
          <h1 id="photo-verify-title" className="photo-verify-title">
            사진 인증
          </h1>
        </header>

        {step === 'capture' && (
          <>
            {/* S16-02 */}
            <p className="photo-verify-notice">
              지금 찍는 사진은 본인 확인용으로만 쓰이고, 프로필에 등록되거나 다른 사람에게 보이지
              않아요
            </p>
            <div className="photo-verify-body">
              {/* S16-03 */}
              <div className="photo-verify-preview">
                <video
                  ref={videoRef}
                  autoPlay
                  playsInline
                  muted
                  aria-label="카메라 미리보기"
                  hidden={camera !== 'ready'}
                />
                {camera !== 'ready' && (
                  <span className="photo-verify-preview-text">
                    {camera === 'starting' ? '카메라를 켜고 있어요…' : '카메라를 사용할 수 없어요'}
                  </span>
                )}
              </div>
              <p className="photo-verify-guide">얼굴을 원 안에 맞춰주세요</p>
              {camera === 'unavailable' && (
                <p className="photo-verify-hint">
                  브라우저 설정에서 카메라 권한을 허용해주세요
                  {import.meta.env.DEV && ' (개발 모드에서는 카메라 없이 촬영할 수 있어요)'}
                </p>
              )}
              {error && (
                <p role="alert" className="photo-verify-error">
                  {error.message}
                  {error.needsPhoto && (
                    <>
                      {' '}
                      <Link to="/profile/edit" state={{ from: '/profile' }}>
                        프로필 사진 등록하기
                      </Link>
                    </>
                  )}
                </p>
              )}
            </div>
            {/* S16-04 */}
            <button
              type="button"
              className="ui-button ui-button--primary photo-verify-button"
              disabled={!canCapture || busy}
              onClick={() => void capture()}
            >
              {busy ? '사진을 확인하고 있어요…' : '촬영하기'}
            </button>
          </>
        )}

        {step === 'done' && (
          <>
            <div className="photo-verify-body" role="status">
              {/* S16-05 */}
              <div className="photo-verify-icon photo-verify-icon--done" aria-hidden="true">
                ✓
              </div>
              {/* S16-06 */}
              <h2 className="photo-verify-result-title">인증이 완료됐어요</h2>
              {/* S16-07 */}
              <p className="photo-verify-description">
                프로필에 인증 배지가 표시돼요. 매칭 상대에게 사진은 여전히 공개되지 않아요
              </p>
            </div>
            {/* S16-08 */}
            <button
              type="button"
              className="ui-button ui-button--primary photo-verify-button"
              onClick={() => navigate('/profile', { replace: true })}
            >
              확인
            </button>
          </>
        )}

        {step === 'failed' && (
          <>
            <div className="photo-verify-body" role="alert">
              {/* S16-04a */}
              <div className="photo-verify-icon photo-verify-icon--failed" aria-hidden="true">
                !
              </div>
              {/* S16-04b */}
              <h2 className="photo-verify-result-title">본인 확인에 실패했어요</h2>
              {/* S16-04c */}
              <p className="photo-verify-description">
                프로필 사진과 같은 사람인지 확인하지 못했어요. 밝은 곳에서 얼굴 전체가 원 안에
                들어오도록 다시 찍어주세요
              </p>
            </div>
            {/* S16-04d: 재시도 횟수 제한 없음 */}
            <button
              type="button"
              className="ui-button ui-button--primary photo-verify-button"
              onClick={retake}
            >
              다시 촬영하기
            </button>
          </>
        )}
      </section>
    </main>
  );
}
