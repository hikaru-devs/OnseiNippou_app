import React, { useState, useRef, useEffect } from 'react';
import HamburgerMenu from './HamburgerMenu'; // Import the menu

function OnseiNippou() {
  const [isRecording, setIsRecording] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');

  const mediaRecorderRef = useRef(null);
  const socketRef = useRef(null);
  const latestTranscriptRef = useRef(''); // To hold the latest full transcript received from server

  const startRecording = async () => {
    setTranscript(''); // Clear previous transcript from UI
    latestTranscriptRef.current = ''; // Clear internal latest transcript

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });
      mediaRecorderRef.current = recorder;

      const wsUrl = import.meta.env.VITE_API_URL.replace(/^http/, 'ws') + '/ws/transcribe';
      const ws = new WebSocket(wsUrl);
      socketRef.current = ws;

      ws.onopen = () => {
        console.log('WebSocket connection established.');
        setStatusMessage('サーバーに接続しました。録音中です...');
        recorder.start(500); // Send audio data every 500ms
      };

      ws.onmessage = (event) => {
        const data = JSON.parse(event.data);
        if (data.transcript) {
          // Server now sends the full accumulated transcript on close, or potentially updates during recording.
          // We store the latest received transcript.
          latestTranscriptRef.current = data.transcript;
        }
        if (data.status) {
          if (data.status === 'reconnecting') {
            setStatusMessage('接続が不安定です。再接続しています...');
          }
        }
        if (data.error) {
          console.error('Server error:', data.error);
          setStatusMessage(`エラー: ${data.error}`);
        }
      };

      ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        setStatusMessage('WebSocket接続エラーが発生しました。');
      };

      ws.onclose = () => {
        console.log('WebSocket connection closed.');
        // Display the final accumulated transcript when the connection closes
        setTranscript(latestTranscriptRef.current);
        setStatusMessage('録音が完了しました。');
        if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
          mediaRecorderRef.current.stop();
        }
        setIsRecording(false);
      };

      recorder.ondataavailable = (e) => {
        if (e.data.size > 0 && ws.readyState === WebSocket.OPEN) {
          ws.send(e.data);
        }
      };
      
      recorder.onstart = () => {
        setIsRecording(true);
      };

    } catch (error) {
      console.error("マイクへのアクセス許可が必要です。", error);
      alert("マイクへのアクセスが拒否されました。録音を開始できません。");
    }
  };

  const stopRecording = () => {
    setStatusMessage('文字起こしを処理中です...');
    if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
      mediaRecorderRef.current.stop();
    }
    // Closing the socket will trigger the onclose event, which handles the final transcript update.
    if (socketRef.current) {
      socketRef.current.close();
    }
    setIsRecording(false);
  };

  const handleTextChange = (e) => setTranscript(e.target.value);

  const submitText = async () => {
    setIsLoading(true);
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
      setIsLoading(false);
    }
  };

  // Cleanup on component unmount
  useEffect(() => {
    return () => {
      if (socketRef.current) {
        socketRef.current.close();
      }
      if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
        mediaRecorderRef.current.stop();
      }
    };
  }, []);

  return (
    <>
      <HamburgerMenu isOpen={isMenuOpen} onClose={() => setIsMenuOpen(false)} />
      <div className={`min-h-screen bg-gray-50 dark:bg-gray-900 transition-all duration-300 ease-in-out`}>
        <div className="p-8 font-sans">
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

            {(isLoading || statusMessage) && <div className="my-2"><span>{isLoading ? '処理中...' : statusMessage}</span></div>}

            <div className="mt-4 w-full max-w-lg">
              <label className="block text-base font-medium text-gray-700 dark:text-gray-300">📝 文字起こし結果：</label>
              <textarea
                rows="10"
                placeholder="録音を停止すると、ここに結果が表示されます"
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
