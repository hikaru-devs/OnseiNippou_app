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
        const response = await fetch('/upload-audio', {
          method: 'POST',
          body: formData
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
    await fetch('/submit-text', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: transcript })
    });
    alert('日報を送信しました！');
  };

  return (
    <div style={{ padding: '2rem', fontFamily: 'Arial' }}>
      <h2>🎤 音声日報アプリ（フロント）</h2>

      <button onClick={startRecording} disabled={isRecording}>
        録音開始
      </button>
      <button onClick={stopRecording} disabled={!isRecording}>
        録音停止
      </button>

      <div style={{ marginTop: '1rem' }}>
        <label>📝 文字起こし結果：</label><br />
        <textarea
          rows="10"
          cols="60"
          value={transcript}
          onChange={handleTextChange}
          placeholder="録音結果がここに表示されます"
        />
      </div>

      <button onClick={submitText} disabled={!transcript}>
        📤 日報を送信
      </button>
    </div>
  );
}

export default App;
