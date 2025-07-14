import React, { useState, useRef } from 'react';

function App() {
  const [mediaRecorder, setMediaRecorder] = useState(null);
  const [isRecording, setIsRecording] = useState(false);
  const [transcript, setTranscript] = useState('');
  const recordingChunksRef = useRef([]);

  const startRecording = async () => {
    recordingChunksRef.current = [];

    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const recorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });

    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) {
        recordingChunksRef.current.push(e.data);
      }
    };

    recorder.onstop = async () => {
      const blob = new Blob(recordingChunksRef.current, { type: 'audio/webm' });
      const formData = new FormData();
      formData.append('audio', blob, 'recording.webm');

      try {
        const response = await fetch(`${import.meta.env.VITE_API_URL}/upload-audio`, {
          method: 'POST',
          body: formData,
          credentials: 'include', // Include cookies for session management
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
      }

    };

    recorder.start();
    setMediaRecorder(recorder);
    setIsRecording(true);
  };

  const stopRecording = () => {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
      mediaRecorder.stop();
      setIsRecording(false);
    }
  };

  const handleTextChange = (e) => setTranscript(e.target.value);

  const submitText = async () => {
    const response = await fetch(`${import.meta.env.VITE_API_URL}/submit-text`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: transcript }),
      credentials: 'include', // Include cookies for session management
    });

    if (response.ok) {
      alert('日報を送信しました！');
      setTranscript('');
    } else {
      const errorText = await response.text();
      console.error('送信エラー：', errorText);
      alert('送信に失敗しました。');
    }


  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="p-8 font-sans">
        <h2 className="text-2xl font-bold mb-4">🎤 音声日報アプリ</h2>

        <div className="flex space-x-4 mb-4">
          <button onClick={startRecording} disabled={isRecording}
            className="bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-2 px-4 rounded disabled:opacity-50"
          >
            録音開始
          </button>
          <button onClick={stopRecording} disabled={!isRecording}
            className="border border-indigo-500 text-indigo-700 font-semibold py-2 px-4 rounded hover:bg-indigo-50 disabled:opacity-50"
          >
            録音停止
          </button>
        </div>

        <div style={{ marginTop: '1rem' }}>
          <label className="block text-base font-medium text-gray-700">📝 文字起こし結果：</label>
          <br />
          <textarea
            rows="10"
            placeholder="録音結果がここに表示されます"
            value={transcript}
            onChange={handleTextChange}
            className="max-w-full sm:w-[36rem] w-full"
          />
        </div>

        <button onClick={submitText} disabled={!transcript}>
          📤 日報を送信
        </button>
      </div>
    </div>
  );
}

export default App;
