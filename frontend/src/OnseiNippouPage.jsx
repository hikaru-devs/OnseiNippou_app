import React, { useState, useRef, useEffect } from 'react';
import HamburgerMenu from './HamburgerMenu';

function OnseiNippou() {
    // --- State Hooks: コンポーネントの状態を管理 ---
    // 録音中かどうかを管理する状態 (true: 録音中, false: 停止中)
    const [isRecording, setIsRecording] = useState(false);
    // 文字起こしされたテキストを保持する状態
    const [transcript, setTranscript] = useState('');
    // サーバーへの送信処理など、ローディング中かどうかを管理する状態
    const [isLoading, setIsLoading] = useState(false);
    // ハンバーガーメニューが開いているかどうかを管理する状態
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    // ユーザーに進捗やエラーを通知するためのメッセージを保持する状態
    const [statusMessage, setStatusMessage] = useState('');
    // サーバーから取得したユーザー情報を保持するstate
    const [userInfo, setUserInfo] = useState(null);

    // --- Ref Hooks: 再レンダリングを引き起こさない値を保持 ---
    // Web Audio APIの心臓部であるAudioContextのインスタンスを保持
    const audioContextRef = useRef(null);
    // 音声データを処理するためのScriptProcessorNodeを保持
    const scriptProcessorRef = useRef(null);
    // マイクからの音声入力ソースを保持
    const mediaStreamSourceRef = useRef(null);
    // マイクからのメディアストリーム自体を保持
    const streamRef = useRef(null);
    // WebSocket接続のインスタンスを保持
    const socketRef = useRef(null);
    // サーバーから受信した最新の完全な文字起こしテキストを一時的に保持（stateの非同期更新を回避するため）
    const latestTranscriptRef = useRef('');

    // ページが最初に読み込まれた時に、サーバーからユーザー情報を取得する
    useEffect(() => {
        const fetchUserInfo = async () => {
            try {
                if (import.meta.env.PROD) {
                    // サーバーに現在ログインしているユーザーの情報を問い合わせる
                    const response = await fetch('/api/users/me'); // 実際のAPIエンドポイントに合わせて変更
                    if (!response.ok) {
                        throw new Error('ユーザー情報の取得に失敗しました。');
                    }
                    const data = await response.json();
                    // 取得したユーザー情報をstateに保存する
                    setUserInfo(data);
                } else {
                    // ローカル開発環境(npm run dev)では、仮のデータを設定する
                    console.log("開発モード: 仮のユーザー情報を使用します。");
                    const mockUserInfo = {
                        userName: "開発用 太郎",
                        profileImageUrl: "https://via.placeholder.com/40",
                        sheetId: "1Ly0Y5838eO86gQDnHi6FbKb8KhGr977SPkIKNv8Hrik" // 自分のテスト用シートID
                    };
                    setUserInfo(mockUserInfo);
                }
            } catch (error) {
                console.error(error);
                // エラーが発生した場合の処理（例: エラーメッセージを表示）
                setStatusMessage('ユーザー情報の読み込みに失敗しました。');
            }
        };

        fetchUserInfo();
    }, []);

    // アプリ初回読み込み時に、一度だけ通知の許可をユーザーに尋ねる
    useEffect(() => {
        if ('Notification' in window && Notification.permission === 'default') {
            Notification.requestPermission();
        }
    }, []); // 空の配列[]は「最初の一回だけ実行して」という意味

    // 回復失敗時に通知とバイブレーションを実行する関数
    const notifyUserOfFailure = () => {
        // デバイス通知（ユーザーが許可している場合のみ）
        if ('Notification' in window && Notification.permission === 'granted') {
            new Notification('音声日報アプリ', {
                body: '録音中に問題が発生しました。アプリを確認してください。',
                icon: '/favicon.ico' // publicフォルダなどに置いたアイコンを指定できます
            });
        }
        // バイブレーション（対応デバイスのみ）
        if ('vibrate' in navigator) {
            navigator.vibrate([200, 100, 200]); // [振動, 停止, 振動]
        }
    };

    /**
     * 録音した音声データ(Float32Array)を、サーバーが期待する16ビット整数(Int16Array)形式に変換する。
     * @param {Float32Array} buffer - Web Audio APIから取得した音声データブロック。
     * @returns {ArrayBuffer} - Int16Arrayのデータが入ったArrayBuffer。
     */
    const float32ToInt16 = (buffer) => {
        let l = buffer.length;
        const buf = new Int16Array(l);
        // 各サンプルを-32768から32767の範囲に変換する
        while (l--) {
            buf[l] = Math.min(1, buffer[l]) * 0x7FFF;
        }
        return buf.buffer;
    };

    /**
     * 録音を開始し、マイクからの音声をサーバーにストリーミングする処理。
     */
    const startRecording = async () => {
        // 前回の文字起こし結果が残っている場合があるので、初期化する
        latestTranscriptRef.current = '';
        setTranscript('');

        try {
            // ユーザーにマイクへのアクセス許可を要求し、メディアストリームを取得
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            streamRef.current = stream;

            // 音声処理の全体を管理するAudioContextを初期化（サーバーの仕様に合わせてサンプルレート16000Hzに設定）
            const context = new (window.AudioContext || window.webkitAudioContext)({
                sampleRate: 16000
            });
            audioContextRef.current = context;

            // マイクからのストリームをAudioContextが扱える入力ソースに変換
            const source = context.createMediaStreamSource(stream);
            mediaStreamSourceRef.current = source;

            // 一定量の音声データが溜まるたびにイベントを発火させるためのノードを作成 (バッファサイズ, 入力ch, 出力ch)
            const bufferSize = 4096;
            const scriptProcessor = context.createScriptProcessor(bufferSize, 1, 1);
            scriptProcessorRef.current = scriptProcessor;

            // 環境変数からAPIのURLを取得し、WebSocket用のURL('ws://' or 'wss://')に変換
            const wsUrl = import.meta.env.VITE_API_URL.replace(/^http/, 'ws') + '/ws/transcribe';
            // WebSocketサーバーに接続を開始
            const ws = new WebSocket(wsUrl);
            socketRef.current = ws;

            // --- WebSocketのイベントハンドラを設定 ---

            // 接続が正常に確立されたときに呼ばれる
            ws.onopen = () => {
                console.log('WebSocket connection established.');
                setStatusMessage('サーバーに接続しました。録音中です...');
                // 音声処理のパイプラインを接続: [マイク入力] -> [音声処理ノード] -> [スピーカー出力(ミュート)]
                source.connect(scriptProcessor);
                scriptProcessor.connect(context.destination);
                // 録音中の状態に更新
                setIsRecording(true);
            };

            // サーバーから何らかのメッセージを受信したときに呼ばれる
            ws.onmessage = (event) => {
                const data = JSON.parse(event.data);
                // 'error'キーがあれば、サーバー側で発生したエラーをコンソールと画面に表示
                if (data.error) {
                    console.error('Server error:', data.error);
                    if (data.error === 'RECOVERY_FAILED') {
                        // 1. 回復失敗の専用メッセージを表示
                        setStatusMessage('回復処理が失敗しました。もう一度録音開始してください。');
                        // 2. 失敗直前までのテキストをサーバーから受け取り表示
                        if (data.transcript) {
                            setTranscript(data.transcript);
                        }
                        // 3. 録音を自動停止させる
                        stopRecordingCleanup();
                        // ★★★ [修正点] 通知とバイブレーションを呼び出す ★★★
                        notifyUserOfFailure();
                    } else {
                        // それ以外の通常エラー
                        setStatusMessage(`エラー: ${data.error}`);
                    }
                }
                // 'status'キーがあれば接続状態などの通知メッセージとして表示
                else if (data.status) {
                    if (data.status === 'reconnecting') {
                        setStatusMessage('想定外エラーが発生。回復処理を実行しています...');
                    } else if (data.status === 'recovered') {
                        // 回復完了メッセージを表示する.
                        setStatusMessage('回復処理が完了しました。継続して録音中です...');
                    }
                }
                // 'transcript'キーがあれば、それは最終的な文字起こし結果なのでRefに保存
                if (data.transcript) {
                    // サーバーから送られてくる最終的な完全版テキストで上書きする
                    latestTranscriptRef.current = data.transcript;
                } 
            };

            // 接続エラーが発生したときに呼ばれる
            ws.onerror = (error) => {
                console.error('WebSocket error:', error);
                setStatusMessage('WebSocket接続エラーが発生しました。');
            };

            // ★★★ [修正点] サーバーから接続が切断されたときに最終処理を行う ★★★
            // サーバーが文字起こしを完了し、接続を閉じたときに呼ばれる
            ws.onclose = () => {
                console.log('WebSocket connection closed by server.');
                // サーバーから最後に受け取った完全なテキストを画面のテキストエリアに反映させる
                setTranscript(latestTranscriptRef.current);
                setStatusMessage('録音が完了しました。');
                // 念のため、クライアント側のリソースもクリーンアップする
                stopRecordingCleanup();
            };

            // scriptProcessorのバッファが満たされるたびに呼ばれるイベント
            scriptProcessor.onaudioprocess = (e) => {
                // WebSocketが接続中の場合のみ音声データを送信する
                if (ws.readyState === WebSocket.OPEN) {
                    const inputData = e.inputBuffer.getChannelData(0); // モノラル音声データを取得
                    const int16Buffer = float32ToInt16(inputData); // 16ビット整数形式に変換
                    ws.send(int16Buffer); // サーバーに送信
                }
            };

        } catch (error) {
            // マイクへのアクセスが拒否された場合などのエラー処理
            console.error("マイクへのアクセス許可が必要です。", error);
            alert("マイクへのアクセスが拒否されました。録音を開始できません。");
        }
    };

    /**
     * マイクや音声処理関連のリソースをすべて解放するクリーンアップ関数。
     * メモリリークや不要なリソース消費を防ぐために重要。
     */
    const stopRecordingCleanup = () => {
        // 音声処理ノードの接続を解除
        if (scriptProcessorRef.current) {
            scriptProcessorRef.current.disconnect();
            scriptProcessorRef.current = null;
        }
        // マイク入力ソースの接続を解除
        if (mediaStreamSourceRef.current) {
            mediaStreamSourceRef.current.disconnect();
            mediaStreamSourceRef.current = null;
        }
        // AudioContextを閉じる
        if (audioContextRef.current) {
            audioContextRef.current.close().catch(e => console.error("AudioContext close error", e));
            audioContextRef.current = null;
        }
        // マイクのトラックを停止し、アクセスランプを消す
        if (streamRef.current) {
            streamRef.current.getTracks().forEach(track => track.stop());
            streamRef.current = null;
        }
        // 録音状態を停止中に更新
        setIsRecording(false);
    };

    // ★★★ [修正点] 停止ボタンの役割は「停止要求」をサーバーに送るだけになる ★★★
    /**
     * 録音停止ボタンが押されたときの処理。
     */
    const stopRecording = () => {
        setStatusMessage('文字起こしを処理中です...');
        
        // まず、マイクからの音声取得と音声処理を停止する
        stopRecordingCleanup();
        
        // サーバーに「もうこれ以上、音声データは送りません」という終了の合図を送る
        if (socketRef.current && socketRef.current.readyState === WebSocket.OPEN) {
            console.log('Sending stop signal to the server.');
            // 空のバッファを送信することで、ストリームの終わりをサーバーに通知する
            socketRef.current.send(new ArrayBuffer(0));
        }
        // ★重要★: ここでフロント側から `ws.close()` を呼び出さない。
        // サーバー側が最終的な文字起こしを終えてから接続を閉じるのを待つ。
    };

    /**
     * テキストエリアの内容がユーザーによって変更されたときに呼ばれるハンドラ。
     * @param {React.ChangeEvent<HTMLTextAreaElement>} e - イベントオブジェクト。
     */
    const handleTextChange = (e) => setTranscript(e.target.value);
    
    /**
     * 確定した文字起こしテキストを日報としてサーバーに送信する処理
     */
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

    // useEffectフック: コンポーネントのライフサイクル（マウント、アンマウント）で副作用を実行
    useEffect(() => {
        // この関数はコンポーネントが破棄（アンマウント）されるときに一度だけ実行される
        return () => {
            // WebSocket接続がまだ残っている場合は、明示的に閉じる
            if (socketRef.current) {
                socketRef.current.close();
            }
            // 念のため、すべての音声リソースを解放する
            stopRecordingCleanup();
        };
    }, []); // 空の依存配列は、このeffectがマウント時とアンマウント時にのみ実行されることを意味する

    // --- レンダリングされるJSX ---
    return (
        <>
            {/* 取得したユーザー情報をHamburgerMenuにpropsとして渡す */}
            <HamburgerMenu
                isOpen={isMenuOpen}
                onClose={() => setIsMenuOpen(false)}
                userName={userInfo?.userName}
                profileImageUrl={userInfo?.profileImageUrl}
                sheetId={userInfo?.sheetId}
            />
            <div className={`min-h-screen bg-gray-50 dark:bg-gray-900 transition-all duration-300 ease-in-out`}>
                <div className="p-8 font-sans">
                    {/* ハンバーガーメニューを開くボタン */}
                    <button onClick={() => setIsMenuOpen(true)} className="absolute top-5 left-5 z-10">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6h16M4 12h16M4 18h16"></path></svg>
                    </button>
                    <div className="flex flex-col items-center justify-center">
                        <h2 className="text-2xl font-bold mb-4 dark:text-white">🎤 音声日報アプリ</h2>
                        <div className="flex space-x-4 mb-4">
                            {/* 録音開始ボタン: 録音中やロード中は無効化 */}
                            <button onClick={startRecording} disabled={isRecording || isLoading}
                                className="bg-indigo-600 hover:bg-indigo-700 text-white font-semibold py-2 px-4 rounded disabled:opacity-50"
                            >
                                {isRecording ? '録音中...' : '録音開始'}
                            </button>
                            {/* 録音停止ボタン: 録音中でない時やロード中は無効化 */}
                            <button onClick={stopRecording} disabled={!isRecording || isLoading}
                                className="border border-indigo-500 text-indigo-700 font-semibold py-2 px-4 rounded hover:bg-indigo-50 disabled:opacity-50 dark:text-indigo-400 dark:hover:bg-gray-800"
                            >
                                録音停止
                            </button>
                        </div>
                        {/* ローディング中、または何らかのステータスメッセージがある場合に表示 */}
                        {(isLoading || statusMessage) && <div className="my-2 text-gray-600 dark:text-gray-400"><span>{isLoading ? '処理中...' : statusMessage}</span></div>}
                        <div className="mt-4 w-full max-w-lg">
                            <label className="block text-base font-medium text-gray-700 dark:text-gray-300">📝 文字起こし結果：</label>
                            {/* 文字起こし結果を表示・編集するためのテキストエリア */}
                            <textarea
                                rows="10"
                                placeholder="録音を停止すると、ここに結果が表示されます"
                                value={transcript}
                                onChange={handleTextChange}
                                className="mt-1 w-full p-2 border rounded border-gray-300 dark:bg-gray-800 dark:text-gray-200 dark:border-gray-600"
                            />
                        </div>
                        {/* 日報送信ボタン: 文字起こし結果がない場合やロード中は無効化 */}
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