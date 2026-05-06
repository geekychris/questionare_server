import React from 'react';
import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout/Layout';
import CampaignList from './components/Campaigns/CampaignList';
import CreateCampaign from './pages/CreateCampaign';
import EditCampaign from './pages/EditCampaign';
import ViewCampaign from './pages/ViewCampaign';
import TakeSurvey from './pages/TakeSurvey';
import NotFound from './pages/NotFound';

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
            <Route path="campaigns" element={<CampaignList />} />
            <Route path="campaigns/new" element={<CreateCampaign />} />
            <Route path="campaigns/create" element={<Navigate to="/campaigns/new" replace />} />
            <Route path="campaigns/:id/edit" element={<EditCampaign />} />
            <Route path="campaigns/:id/preview" element={<ViewCampaign />} />
            <Route path="campaigns/:id" element={<ViewCampaign />} />

            {/* Take survey route */}
            <Route path="survey/:id" element={<TakeSurvey />} />

            {/* 404 for everything else */}
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  );
}

export default App;
