import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { OperationsGuidePage } from './guide/OperationsGuidePage';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <OperationsGuidePage />
  </StrictMode>,
);
