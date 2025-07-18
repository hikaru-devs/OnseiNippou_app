/* RegisterSheet.tsx ― ベストプラクティス版（React 18 + React-Router v6 + TypeScript 推奨構成でも動く書き方） */

import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

const RegisterSheet: React.FC = () => {
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);      // ★ 送信中フラグ
  const navigate = useNavigate();

  /* ❶ 送信ハンドラ */
  const handleSubmit: React.FormEventHandler<HTMLFormElement> = async (e) => {
    e.preventDefault();
    if (loading) return;                              // 二重送信ガード
    setLoading(true);

    try {
      const res = await fetch(
        `${import.meta.env.VITE_API_URL}/api/submit-sheet`, // ★ APIプレフィックス & 環境変数
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ sheetUrl: url.trim() }),
          credentials: 'include',                       // Cookie 認証なら維持
        }
      );

      if (!res.ok) {
        // 422/400/500 それぞれメッセージが来る想定
        const msg = await res.text();
        throw new Error(msg || 'シート登録に失敗しました');
      }

      // 成功メッセージは不要なら読み捨てても可
      const msg = await res.text();
      alert(msg);

      /* ❷ 成功したら /onsei-nippou-page に遷移 */
      navigate('/onsei-nippou-page', { replace: true });
    } catch (err: unknown) {
      console.error(err);
      alert(err instanceof Error ? err.message : '通信エラーが発生しました');
    } finally {
      setLoading(false);
    }
  };


  return (
    <div className="p-8 font-sans">
      <h2 className="text-2xl font-bold mb-4">
        📄 あなたの日報スプレッドシートの URL を入力してください
      </h2>

      <form onSubmit={handleSubmit}>
        <input
          type="url"                                    /* ★ URL 型でバリデーション */
          placeholder="https://docs.google.com/spreadsheets/..."
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          className="w-full border px-2 py-1 rounded"
          required
        />

        <button
          type="submit"
          className="mt-4 px-4 py-2 rounded bg-blue-600 text-white disabled:opacity-40"
          disabled={loading}
        >
          {loading ? '登録中…' : '登録する ➡️'}
        </button>
      </form>
    </div>
  );
};

export default RegisterSheet;
