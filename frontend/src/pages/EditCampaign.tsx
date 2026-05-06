import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  Box, 
  Typography, 
  CircularProgress, 
  Alert, 
  Snackbar,
  Paper
} from '@mui/material';
import CampaignForm from '../components/Campaigns/CampaignForm';
import { Campaign } from '../types';
import { getCampaign, updateCampaign } from '../services/api';

const EditCampaign = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [campaign, setCampaign] = useState<Campaign | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [notification, setNotification] = useState<{
    open: boolean;
    message: string;
    severity: 'success' | 'error';
  }>({
    open: false,
    message: '',
    severity: 'success',
  });

  useEffect(() => {
    const fetchCampaign = async () => {
      if (!id) return;
      
      try {
        setLoading(true);
        const campaignData = await getCampaign(parseInt(id, 10));
        setCampaign(campaignData);
        setError(null);
      } catch (err) {
        console.error('Error fetching campaign:', err);
        setError('Failed to load campaign data. Please try again.');
      } finally {
        setLoading(false);
      }
    };

    fetchCampaign();
  }, [id]);

  const handleSubmit = async (values: Campaign) => {
    if (!id) return;
    
    try {
      setSubmitting(true);
      await updateCampaign(parseInt(id, 10), values);
      setNotification({
        open: true,
        message: 'Campaign updated successfully!',
        severity: 'success',
      });
      
      // Navigate back to campaigns list after a short delay
      setTimeout(() => {
        navigate('/campaigns');
      }, 1500);
    } catch (err) {
      console.error('Error updating campaign:', err);
      setNotification({
        open: true,
        message: 'Failed to update campaign. Please try again.',
        severity: 'error',
      });
    } finally {
      setSubmitting(false);
    }
  };

  const handleCloseNotification = () => {
    setNotification((prev) => ({ ...prev, open: false }));
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="60vh">
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Box mt={4} mb={4}>
        <Alert severity="error">{error}</Alert>
      </Box>
    );
  }

  if (!campaign) {
    return (
      <Box mt={4} mb={4}>
        <Alert severity="error">Campaign not found</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto', p: 2 }}>
      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Edit Campaign
        </Typography>
        
        <CampaignForm 
          initialValues={campaign} 
          onSubmit={handleSubmit} 
          isSubmitting={submitting}
          isEditMode={true}
        />
      </Paper>
      
      <Snackbar 
        open={notification.open} 
        autoHideDuration={6000} 
        onClose={handleCloseNotification}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert 
          onClose={handleCloseNotification} 
          severity={notification.severity}
          sx={{ width: '100%' }}
        >
          {notification.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default EditCampaign;

