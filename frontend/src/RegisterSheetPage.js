import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

const RegisterSheet = () => {
  const [url, setUrl] = useState('');
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const response = await fetch('/register-sheet', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sheetUrl: url })
      });

      if (response.ok) {
        navigate('/main');
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
      <h2>📄 スプレッドシートURL登録</h2>
      <form onSubmit={handleSubmit}>
        <input
          type="text"
          placeholder="https://docs.google.com/spreadsheets/..."
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          style={{ width: '100%', padding: '0.5rem' }}
          required
        />
        <button type="submit" style={{ marginTop: '1rem' }}>
          登録して開始
        </button>
      </form>
    </div>
  );
};

export default RegisterSheet;
