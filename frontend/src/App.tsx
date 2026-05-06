import React from 'react';
import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout/Layout';

// Placeholder components - will be replaced with actual components
const CampaignList = () => <div>Campaign List Page</div>;
const CampaignCreate = () => <div>Create Campaign Page</div>;
const CampaignEdit = () => <div>Edit Campaign Page</div>;
const CampaignPreview = () => <div>Campaign Preview Page</div>;
const SurveyForm = () => <div>Take Survey Page</div>;

// Define Material-UI theme
const theme = createTheme({
  palette: {
    primary: {
      main: '#1976d2',
    },
    secondary: {
      main: '#dc004e',
    },
    background: {
      default: '#f5f5f5',
    },
  },
  typography: {
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    h1: {
      fontSize: '2.5rem',
      fontWeight: 500,
    },
    h2: {
      fontSize: '2rem',
      fontWeight: 500,
    },
    h3: {
      fontSize: '1.8rem',
      fontWeight: 500,
    },
    h4: {
      fontSize: '1.5rem',
      fontWeight: 500,
    },
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
        },
      },
    },
  },
});

function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Layout />}>
            {/* Campaign management routes */}
            <Route index element={<CampaignList />} />
            <Route path="campaigns/create" element={<CampaignCreate />} />
            <Route path="campaigns/edit/:id" element={<CampaignEdit />} />
            <Route path="campaigns/preview/:id" element={<CampaignPreview />} />
            
            {/* Take survey route */}
            <Route path="survey/:id" element={<SurveyForm />} />
            
            {/* Redirect unknown paths to home */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  );
}

export default App;
