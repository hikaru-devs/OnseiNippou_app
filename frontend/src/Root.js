import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import OnseiNippouAppPage from './OnseiNippouAppPage';
import RegisterSheetPage from './RegisterSheetPage';

function Root() {
  return (
    <Router>
      <Routes>
        <Route path="/OnseiNippou_app" element={<OnseiNippouAppPage />} />
        <Route path="/sheet-register" element={<RegisterSheetPage />} />
      </Routes>
    </Router>
  );
}

export default Root;
