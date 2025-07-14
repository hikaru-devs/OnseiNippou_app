// Root.jsx
import { Routes, Route } from 'react-router-dom';
import OnseiNippouPage from './OnseiNippouPage';
import RegisterSheetPage from './RegisterSheetPage';
import LoginPage from './LoginPage';

function Root() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route path="/onsei-nippou-page" element={<OnseiNippouPage />} />
      <Route path="/register-sheet-page" element={<RegisterSheetPage />} />
      <Route paht="/login" element={<LoginPage />} />
    </Routes>
  );
}

export default Root;
