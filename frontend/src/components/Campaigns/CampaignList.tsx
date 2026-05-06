import React, { useState, useEffect } from 'react';
import { 
  Box, 
  Button, 
  Typography, 
  CircularProgress, 
  Alert, 
  Paper, 
  TextField,
  IconButton,
  Tooltip,
  Snackbar
} from '@mui/material';
import { 
  DataGrid, 
  GridColDef, 
  GridValueGetterParams, 
  GridToolbar, 
  GridActionsCellItem
} from '@mui/x-data-grid';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import VisibilityIcon from '@mui/icons-material/Visibility';
import { useNavigate } from 'react-router-dom';
import { Campaign } from '../../types';
import ConfirmationDialog from './ConfirmationDialog';
import axios from 'axios';

const CampaignList: React.FC = () => {
  const [campaigns, setCampaigns] = useState<Campaign[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [deleteDialogOpen, setDeleteDialogOpen] = useState<boolean>(false);
  const [campaignToDelete, setCampaignToDelete] = useState<Campaign | null>(null);
  const [snackbar, setSnackbar] = useState<{open: boolean, message: string, severity: 'success' | 'error'}>({
    open: false,
    message: '',
    severity: 'success'
  });
  
  const navigate = useNavigate();

  // Columns definition for the DataGrid
  const columns: GridColDef[] = [
    { field: 'id', headerName: 'ID', width: 70 },
    { field: 'title', headerName: 'Title', width: 250 },
    { field: 'description', headerName: 'Description', width: 350 },
    { 
      field: 'questionCount', 
      headerName: 'Questions', 
      width: 120,
      valueGetter: (params: GridValueGetterParams) => 
        params.row.questions ? params.row.questions.length : 0
    },
    { 
      field: 'createdAt', 
      headerName: 'Created Date', 
      width: 180,
      valueGetter: (params: GridValueGetterParams) => 
        new Date(params.row.createdAt).toLocaleDateString()
    },
    {
      field: 'actions',
      type: 'actions',
      headerName: 'Actions',
      width: 150,
      getActions: (params: GridValueGetterParams) => [
        <GridActionsCellItem
          icon={<Tooltip title="View"><VisibilityIcon /></Tooltip>}
          label="View"
          onClick={() => handleViewCampaign(params.row)}
        />,
        <GridActionsCellItem
          icon={<Tooltip title="Edit"><EditIcon /></Tooltip>}
          label="Edit"
          onClick={() => handleEditCampaign(params.row)}
        />,
        <GridActionsCellItem
          icon={<Tooltip title="Delete"><DeleteIcon /></Tooltip>}
          label="Delete"
          onClick={() => handleDeleteClick(params.row)}
        />
      ]
    }
  ];

  // Fetch campaigns from API
  useEffect(() => {
    fetchCampaigns();
  }, []);

  const fetchCampaigns = async () => {
    setLoading(true);
    setError(null);
    try {
      // Replace with your actual API endpoint
      const response = await axios.get('/api/campaigns');
      setCampaigns(response.data);
    } catch (err) {
      console.error('Error fetching campaigns:', err);
      setError('Failed to load campaigns. Please try again later.');
      
      // For development, use mock data if API fails
      // Remove this in production
      setCampaigns([
        { 
          id: 1, 
          title: 'Employee Satisfaction Survey 2023', 
          description: 'Annual survey to gather feedback on employee satisfaction',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          startDate: new Date().toISOString(),
          endDate: new Date().toISOString(),
          active: true,
          questions: []
        },
        {
          id: 2,
          title: 'Work-Life Balance Assessment',
          description: 'Survey to evaluate work-life balance of employees',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          startDate: new Date().toISOString(),
          endDate: new Date().toISOString(),
          active: false,
          questions: []
        }
      ]);
    } finally {
      setLoading(false);
    }
  };

  // Filter campaigns based on search query
  const filteredCampaigns = campaigns.filter(campaign => 
    campaign.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
    campaign.description.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // Handlers for campaign actions
  const handleCreateCampaign = () => {
    navigate('/campaigns/new');
  };

  const handleViewCampaign = (campaign: Campaign) => {
    navigate(`/campaigns/${campaign.id}/preview`);
  };

  const handleEditCampaign = (campaign: Campaign) => {
    navigate(`/campaigns/${campaign.id}/edit`);
  };

  const handleDeleteClick = (campaign: Campaign) => {
    setCampaignToDelete(campaign);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = async () => {
    if (!campaignToDelete) return;
    
    try {
      // Replace with your actual API endpoint
      await axios.delete(`/api/campaigns/${campaignToDelete.id}`);
      
      // Update local state after successful deletion
      setCampaigns(campaigns.filter(c => c.id !== campaignToDelete.id));
      
      setSnackbar({
        open: true,
        message: `Campaign "${campaignToDelete.title}" deleted successfully.`,
        severity: 'success'
      });
    } catch (err) {
      console.error('Error deleting campaign:', err);
      setSnackbar({
        open: true,
        message: 'Failed to delete campaign. Please try again.',
        severity: 'error'
      });
    } finally {
      setDeleteDialogOpen(false);
      setCampaignToDelete(null);
    }
  };

  const handleDeleteCancel = () => {
    setDeleteDialogOpen(false);
    setCampaignToDelete(null);
  };

  const handleSnackbarClose = () => {
    setSnackbar({ ...snackbar, open: false });
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ width: '100%', my: 4 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4" component="h1">
          Campaign Management
        </Typography>
        <Button 
          variant="contained" 
          color="primary" 
          startIcon={<AddIcon />}
          onClick={handleCreateCampaign}
        >
          Create New Campaign
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Paper sx={{ width: '100%', mb: 2 }}>
        <Box sx={{ p: 2 }}>
          <TextField
            label="Search Campaigns"
            variant="outlined"
            fullWidth
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            sx={{ mb: 2 }}
          />
        </Box>

        <DataGrid
          rows={filteredCampaigns}
          columns={columns}
          initialState={{
            pagination: {
              paginationModel: { page: 0, pageSize: 10 },
            },
          }}
          pageSizeOptions={[5, 10, 25]}
          checkboxSelection
          disableRowSelectionOnClick
          autoHeight
          slots={{ toolbar: GridToolbar }}
          slotProps={{
            toolbar: {
              showQuickFilter: true,
            },
          }}
          sx={{ minHeight: 400 }}
        />
      </Paper>

      {/* Confirmation Dialog for Delete */}
      <ConfirmationDialog
        open={deleteDialogOpen}
        title="Delete Campaign"
        content={`Are you sure you want to delete "${campaignToDelete?.title}"? This action cannot be undone.`}
        onConfirm={handleDeleteConfirm}
        onCancel={handleDeleteCancel}
      />

      {/* Snackbar for notifications */}
      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={handleSnackbarClose}
      >
        <Alert onClose={handleSnackbarClose} severity={snackbar.severity}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default CampaignList;

