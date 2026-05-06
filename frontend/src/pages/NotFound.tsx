import React from 'react';
import { Box, Typography, Button, Container, Paper } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';

const NotFound: React.FC = () => {
  const navigate = useNavigate();

  const handleGoHome = () => {
    navigate('/');
  };

  return (
    <Container maxWidth="md">
      <Paper 
        elevation={3} 
        sx={{ 
          padding: 4, 
          marginTop: 8, 
          display: 'flex', 
          flexDirection: 'column', 
          alignItems: 'center' 
        }}
      >
        <ErrorOutlineIcon 
          sx={{ 
            fontSize: 80, 
            color: 'error.main', 
            mb: 2 
          }} 
        />
        
        <Typography variant="h4" component="h1" gutterBottom>
          Page Not Found
        </Typography>
        
        <Typography variant="body1" align="center" paragraph>
          Oops! The page you're looking for doesn't exist or has been moved.
        </Typography>
        
        <Box sx={{ mt: 2, mb: 3 }}>
          <Typography variant="body2" align="center" color="text.secondary">
            Please check the URL or return to the home page to continue browsing.
          </Typography>
        </Box>
        
        <Button 
          variant="contained" 
          color="primary" 
          size="large" 
          onClick={handleGoHome}
          startIcon={<span>🏠</span>}
        >
          Back to Home
        </Button>
      </Paper>
    </Container>
  );
};

export default NotFound;

