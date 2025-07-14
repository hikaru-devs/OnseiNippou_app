import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

const RegisterSheet = () => {
  const [url, setUrl] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const response = await fetch(`${import.meta.env.VITE_API_URL}/submit-sheet`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sheetUrl: url }),
        credentials: 'include',
      });

      if (response.ok) {
        navigate('/OnseiNippou_app');
      } else {
        alert('登録に失敗しました。');
      }
    } catch (err) {
      console.error('通信エラー:', err);
      alert('接続に失敗しました。');
    }
  };

  return (
    <div style={{ padding: '2rem', fontFamily: 'Arial' }}>
      <h2 className="text-2xl font-bold mb-4">📄 あなたの日報のスプレッドシートURLを貼り付けてください。</h2>
      <form onSubmit={handleSubmit}>
        <input
          type="text"
          placeholder="https://docs.google.com/spreadsheets/..."
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          className="w-full"
          required
        />
        <button type="submit" style={{ marginTop: '1rem' }}>
          登録する➡️
        </button>
      </form>
    </div>
  );
};

export default RegisterSheet;
