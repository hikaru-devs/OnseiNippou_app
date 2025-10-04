import React, { useState, useRef } from 'react';
import HamburgerMenu from './HamburgerMenu'; // Import the menu

function OnseiNippou() {
  const [mediaRecorder, setMediaRecorder] = useState(null);
  const [isRecording, setIsRecording] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [isLoading, setIsLoading] = useState(false); // Loading state
  const [isMenuOpen, setIsMenuOpen] = useState(false); // Menu state
  const recordingChunksRef = useRef([]);

  const startRecording = async () => {
    recordingChunksRef.current = [];

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });

      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          recordingChunksRef.current.push(e.data);
        }
      };

      recorder.onstop = async () => {
        setIsLoading(true); // Start loading
        const blob = new Blob(recordingChunksRef.current, { type: 'audio/webm' });
        const formData = new FormData();
        formData.append('audio', blob, 'recording.webm');

        try {
          const response = await fetch(`${import.meta.env.VITE_API_URL}/api/reports/audio-transcribe`, {
            method: 'POST',
            body: formData,
            credentials: 'include',
          });

          if (!response.ok) {
            const text = await response.text();
            console.error('❌ サーバーからのエラーレスポンス:', response.status, text);
            alert('送信に失敗しました（サーバー側エラー）');
            return;
          }

          const result = await response.json();
          setTranscript(result.text);
        } catch (err) {
          console.error('送信エラー:', err);
          alert('音声の送信に失敗しました。');
        } finally {
          setIsLoading(false); // Stop loading
        }
      };

      recorder.start();
      setMediaRecorder(recorder);
      setIsRecording(true);
    } catch (error) {
      console.error("マイクへのアクセス許可が必要です。", error);
      alert("マイクへのアクセスが拒否されました。録音を開始できません。");
    }
  };

  const stopRecording = () => {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
      mediaRecorder.stop();
      setIsRecording(false);
    }
  };

  const handleTextChange = (e) => setTranscript(e.target.value);

  const submitText = async () => {
    setIsLoading(true); // Start loading
    try {
      const response = await fetch(`${import.meta.env.VITE_API_URL}/api/reports/submit-report`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: transcript }),
        credentials: 'include',
      });

      if (response.ok) {
        alert('日報を送信しました！');
        setTranscript('');
      } else {
        const errorText = await response.text();
        console.error('送信エラー：', errorText);
        alert('送信に失敗しました。');
      }
    } catch (error) {
      console.error('送信エラー:', error);
      alert('日報の送信に失敗しました。');
    } finally {
      setIsLoading(false); // Stop loading
    }
  };

  return (
    <>
      <HamburgerMenu isOpen={isMenuOpen} onClose={() => setIsMenuOpen(false)} />
      <div
        className={`min-h-screen bg-gray-50 dark:bg-gray-900 transition-all duration-300 ease-in-out`}>
        {/* Main Content */}
        <div className="p-8 font-sans">
          {/* Hamburger Button */}
          <button onClick={() => setIsMenuOpen(true)} className="absolute top-5 left-5 z-10">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6h16M4 12h16M4 18h16"></path></svg>
          </button>

          <div className="flex flex-col items-center justify-center">
            <h2 className="text-2xl font-bold mb-4 dark:text-white">🎤 音声日報アプリ</h2>

            <div className="flex space-x-4 mb-4">
              <button onClick={startRecording} disabled={isRecording || isLoading}
                className="bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-2 px-4 rounded disabled:opacity-50"
              >
                {isRecording ? '録音中...' : '録音開始'}
              </button>
              <button onClick={stopRecording} disabled={!isRecording || isLoading}
                className="border border-indigo-500 text-indigo-700 font-semibold py-2 px-4 rounded hover:bg-indigo-50 disabled:opacity-50 dark:text-indigo-400 dark:hover:bg-gray-800"
              >
                録音停止
              </button>
            </div>

            {isLoading && <div className="my-2"><span>処理中...</span></div>} {/* Loading Indicator */}

            <div className="mt-4 w-full max-w-lg">
              <label className="block text-base font-medium text-gray-700 dark:text-gray-300">📝 文字起こし結果：</label>
              <textarea
                rows="10"
                placeholder="録音結果がここに表示されます"
                value={transcript}
                onChange={handleTextChange}
                className="mt-1 w-full p-2 border rounded border-gray-300 dark:bg-gray-800 dark:text-gray-200 dark:border-gray-600"
              />
            </div>

            <button onClick={submitText} disabled={!transcript || isLoading} className="mt-4 rounded bg-blue-600 px-4 py-2 font-semibold text-white disabled:opacity-50">
              📤 日報を送信
            </button>
          </div>
        </div>
      </div>
    </>
  );
}

export default OnseiNippou;
