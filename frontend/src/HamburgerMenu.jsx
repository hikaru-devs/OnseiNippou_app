import React from 'react';

const HamburgerMenu = ({ isOpen, onClose, userName, userEmail, profileImageUrl }) => {
  // アップデート情報モーダルの表示状態を管理するstate
  const [isUpdateModalOpen, setIsUpdateModalOpen] = useState(false);
  
  // Mock data for stats and links, to be replaced with props later
  const stats = {
    submissionCount: 'comingsoon',
    streak: 'comingsoon',
    avgRecordingTime: 'comingsoon',
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
              <li><a href="#" className="block p-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700">日報シートを開く</a></li>
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
            <button className="w-full text-left p-2 rounded hover:bg-gray-100 dark:hover:bg-gray-700">ログアウト</button>
          </div>
        </div>
      </div>
    </>
  );
};

export default HamburgerMenu;
