import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Typography, Container, Paper, Box, Alert, Snackbar } from '@mui/material';
import CampaignForm from '../components/Campaigns/CampaignForm';
import { Campaign } from '../types';
import { createCampaign } from '../services/api';

const CreateCampaign: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: '',
    severity: 'success' as 'success' | 'error'
  });

  const handleSubmit = async (campaign: Campaign) => {
    setLoading(true);
    setError(null);
    
    try {
      const response = await createCampaign(campaign);
      
      setSnackbar({
        open: true,
        message: 'Campaign created successfully!',
        severity: 'success'
      });
      
      // Navigate after a short delay to allow the user to see the success message
      setTimeout(() => {
        navigate(`/campaigns/${response.id}`);
      }, 1500);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'An unknown error occurred';
      setError(errorMessage);
      setSnackbar({
        open: true,
        message: `Error: ${errorMessage}`,
        severity: 'error'
      });
    } finally {
      setLoading(false);
    }
  };

  const handleCloseSnackbar = () => {
    setSnackbar({ ...snackbar, open: false });
  };

  return (
    <Container maxWidth="lg">
      <Box my={4}>
        <Typography variant="h4" component="h1" gutterBottom>
          Create New Campaign
        </Typography>
        
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        
        <Paper elevation={3} sx={{ p: 3 }}>
          <CampaignForm 
            onSubmit={handleSubmit} 
            isSubmitting={loading} 
          />
        </Paper>
        
        <Snackbar
          open={snackbar.open}
          autoHideDuration={6000}
          onClose={handleCloseSnackbar}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        >
          <Alert 
            onClose={handleCloseSnackbar} 
            severity={snackbar.severity}
            sx={{ width: '100%' }}
          >
            {snackbar.message}
          </Alert>
        </Snackbar>
      </Box>
    </Container>
  );
};

export default CreateCampaign;

