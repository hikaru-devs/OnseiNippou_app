import React, { useState } from 'react';

const HamburgerMenu = ({ isOpen, onClose, userName, userEmail, profileImageUrl, sheetId }) => {
  // アップデート情報モーダルの表示状態を管理するstate
  const [isUpdateModalOpen, setIsUpdateModalOpen] = useState(false);

  // Mock data for stats and links, to be replaced with props later
  const stats = {
    submissionCount: 'comingsoon',
    streak: 'comingsoon',
    avgRecordingTime: 'comingsoon',
  };

  // ログアウト処理を行う関数
  const handleLogout = async () => {
    try {
      // Spring SecurityのログアウトエンドポイントにPOSTリクエストを送信
      await fetch('/logout', { method: 'POST' });
      // ログアウト成功後、ページをリロードしてログイン画面にリダイレクトさせる
      window.location.reload();
    } catch (error) {
      console.error('ログアウト処理に失敗しました:', error);
      alert('ログアウトに失敗しました。');
    }
  };

  return (
    <>
      {/* Overlay */}
      <div
        className={`fixed inset-0 z-40 transition-opacity duration-300 ${isOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'}`}
        onClick={onClose}
      >
        <div className="absolute inset-0 bg-black opacity-25" />
      </div>

      {/* Menu Panel */}
      <div
        className={`fixed top-0 left-0 h-full w-72 bg-white dark:bg-gray-800 shadow-xl z-50 transform transition-transform duration-300 ease-in-out ${isOpen ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="flex flex-col h-full">
          {/* Header */}
          <div className="p-4 border-b border-gray-200 dark:border-gray-700">
            <button onClick={onClose} className="absolute top-3 right-3 text-gray-500 dark:text-gray-400 hover:text-red-500 transition-colors">
              &times;
            </button>
            <div className="flex items-center mt-4">
              <img src={profileImageUrl || 'https://via.placeholder.com/40'} alt="Profile" className="w-10 h-10 rounded-full" />
              <div className="ml-3">
                <p className="font-semibold text-gray-800 dark:text-white">{userName || 'User Name'}</p>
              </div>
            </div>
          </div>

          {/* Quick Links */}
          <div className="p-4 border-b border-gray-200 dark:border-gray-700">
            <h3 className="text-xs font-semibold text-gray-400 uppercase">Quick Links</h3>
            <ul className="mt-2 space-y-1">
              <li>
                <a 
                  href={sheetId ? `https://docs.google.com/spreadsheets/d/${sheetId}/edit` : '#'}
                  target="_blank" 
                  rel="noopener noreferrer" 
                  // sheetIdがない場合はクリックできなくし、見た目も変える
                  className={`block p-2 rounded ${sheetId ? 'hover:bg-gray-100 dark:hover:bg-gray-700' : 'text-gray-400 cursor-not-allowed'}`}
                  // sheetIdがない場合はリンクを無効化する
                  onClick={(e) => !sheetId && e.preventDefault()}
                >
                  日報シートを開く
                </a>
              </li>
              {/* アップデート情報モーダルを開くボタン */}
              <li>
                <button onClick={() => setIsUpdateModalOpen(true)} className="w-full text-left p-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700">
                  アップデート情報
                </button>
              </li>
              <li><span className="block p-2 rounded text-gray-400 cursor-not-allowed">給与明細を確認</span></li>
            </ul>
          </div>

          {/* Your Activity */}
          <div className="p-4">
            <h3 className="text-xs font-semibold text-gray-400 uppercase">Your Activity</h3>
            <ul className="mt-2 space-y-2">
              <li className="flex justify-between items-center"><span>日報送信回数</span><span className="text-xs text-gray-400">{stats.submissionCount}</span></li>
              <li className="flex justify-between items-center"><span>連続送信日数</span><span className="text-xs text-gray-400">{stats.streak}</span></li>
              <li className="flex justify-between items-center"><span>平均録音時間</span><span className="text-xs text-gray-400">{stats.avgRecordingTime}</span></li>
            </ul>
          </div>

          {/* Footer */}
          <div className="mt-auto p-4 border-t border-gray-200 dark:border-gray-700">
            {/* [修正] ログアウトボタンに関数を紐付け */}
            <button onClick={handleLogout} className="w-full text-left p-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700">
              ログアウト
            </button>
          </div>
        </div>
      </div>

      {/* アップデート情報モーダルをレンダリング */}
      <UpdateInfoModal isOpen={isUpdateModalOpen} onClose={() => setIsUpdateModalOpen(false)} />
    </>
  );
};

// アップデート情報を表示するためのモーダルコンポーネント
const UpdateInfoModal = ({ isOpen, onClose }) => {
  if (!isOpen) return null;

  return (
    // 親コンテナ：クリックイベントと全体の配置を担当
    <div 
      className="fixed inset-0 z-50 flex items-center justify-center"
      onClick={onClose} // 背景クリックで閉じる
    >
      {/* 背景オーバーレイ用のdiv */}
      <div 
        // 透過度50%
        className="absolute inset-0 bg-black opacity-50" 
        // このレイヤーだけをクリックしても閉じられるように、イベント伝播を止める
        onClick={(e) => e.stopPropagation()} 
      />
      {/* モーダル本体用のdiv（z-indexで背景より手前に表示） */}
      <div 
        className="relative z-10 bg-white dark:bg-gray-800 rounded-lg shadow-xl w-full max-w-md m-4"
        // モーダル内をクリックしても閉じないように、イベント伝播を止める
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-4 border-b border-gray-200 dark:border-gray-700 flex justify-between items-center">
          <h2 className="text-lg font-semibold">アップデート情報</h2>
          <button onClick={onClose} className="text-gray-500 dark:text-gray-400 hover:text-red-500 font-bold text-2xl">&times;</button>
        </div>
        <div className="p-6 text-sm">
          <h3 className="font-bold">バージョン 1.1.0 (2025/10/13)</h3>
          <ul className="list-disc list-inside mt-2 space-y-1">
            <li>自己回復機能の安定性を向上させました。</li>
            <li>回復失敗時にデバイス通知で知らせる機能を追加しました。</li>
            <li>5分間の連続録音上限に対応しました。</li>
          </ul>
          <h3 className="font-bold mt-4">バージョン 1.0.0 (2025/10/12)</h3>
          <ul className="list-disc list-inside mt-2 space-y-1">
            <li>リアルタイム文字起こし機能をリリースしました。</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default HamburgerMenu;
