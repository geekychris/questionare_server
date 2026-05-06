import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  Box, 
  Typography, 
  Paper,
  GridLegacy as Grid,
  Button,
  Divider, 
  CircularProgress, 
  Alert, 
  Tabs, 
  Tab, 
  Card, 
  CardContent, 
  List, 
  ListItem, 
  MenuItem, 
  Menu, 
  IconButton,
  Tooltip,
  TableContainer,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell
} from '@mui/material';
import { 
  Share as ShareIcon, 
  MoreVert as MoreVertIcon, 
  Download as DownloadIcon, 
  FileCopy as FileCopyIcon, 
  Visibility as VisibilityIcon,
  PieChart as PieChartIcon,
  BarChart as BarChartIcon,
  DataUsage as DataUsageIcon
} from '@mui/icons-material';
import { Campaign, Question, QuestionType, SurveyResponse } from '../types';
import { getCampaign, getCampaignResponses } from '../services/api';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`campaign-tabpanel-${index}`}
      aria-labelledby={`campaign-tab-${index}`}
      {...other}
    >
      {value === index && (
        <Box sx={{ p: 3 }}>
          {children}
        </Box>
      )}
    </div>
  );
}

function a11yProps(index: number) {
  return {
    id: `campaign-tab-${index}`,
    'aria-controls': `campaign-tabpanel-${index}`,
  };
}

const ViewCampaign: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [campaign, setCampaign] = useState<Campaign | null>(null);
  const [responses, setResponses] = useState<SurveyResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [tabValue, setTabValue] = useState(0);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [shareAnchorEl, setShareAnchorEl] = useState<null | HTMLElement>(null);
  const [exportAnchorEl, setExportAnchorEl] = useState<null | HTMLElement>(null);
  
  // Stats
  const [completionRate, setCompletionRate] = useState<number>(0);
  const [averageCompletionTime, setAverageCompletionTime] = useState<number>(0);
  const [responseCount, setResponseCount] = useState<number>(0);

  useEffect(() => {
    const fetchCampaignData = async () => {
      if (!id) return;
      
      try {
        setLoading(true);
        // Fetch campaign details
        const campaignData = await getCampaign(parseInt(id));
        setCampaign(campaignData);
        
        // Fetch campaign responses (paginated — unwrap to the items array)
        const responsesPage = await getCampaignResponses(parseInt(id));
        const responsesData = responsesPage.items;
        setResponses(responsesData);

        // Calculate statistics
        setResponseCount(responsesData.length);

        if (responsesData.length > 0) {
          // Calculate completion rate (complete responses / total responses)
          const completeResponses = responsesData.filter(r =>
            r.answers && r.answers.length >= campaignData.questions.length
          );
          setCompletionRate((completeResponses.length / responsesData.length) * 100);
          
          // Calculate average completion time if timestamps are available
          if (responsesData[0].startTime && responsesData[0].endTime) {
            const totalTimeMs = responsesData.reduce((sum, response) => {
              if (response.startTime && response.endTime) {
                return sum + (new Date(response.endTime).getTime() - new Date(response.startTime).getTime());
              }
              return sum;
            }, 0);
            setAverageCompletionTime(totalTimeMs / responsesData.length / 1000 / 60); // Convert to minutes
          }
        }
        
        setLoading(false);
      } catch (err) {
        setError('Failed to load campaign data');
        setLoading(false);
        console.error(err);
      }
    };

    fetchCampaignData();
  }, [id]);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  const handleMenuClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleShareMenuClick = (event: React.MouseEvent<HTMLElement>) => {
    setShareAnchorEl(event.currentTarget);
  };

  const handleShareMenuClose = () => {
    setShareAnchorEl(null);
  };

  const handleExportMenuClick = (event: React.MouseEvent<HTMLElement>) => {
    setExportAnchorEl(event.currentTarget);
  };

  const handleExportMenuClose = () => {
    setExportAnchorEl(null);
  };

  const handleEdit = () => {
    navigate(`/campaigns/edit/${id}`);
    handleMenuClose();
  };

  const handleDelete = () => {
    // Show a confirmation dialog before deleting
    if (window.confirm('Are you sure you want to delete this campaign?')) {
      // Add API call to delete campaign
      navigate('/campaigns');
    }
    handleMenuClose();
  };

  const handleCopyLink = () => {
    const surveyUrl = `${window.location.origin}/survey/${id}`;
    navigator.clipboard.writeText(surveyUrl)
      .then(() => {
        alert('Survey link copied to clipboard!');
      })
      .catch(err => {
        console.error('Failed to copy link: ', err);
      });
    handleShareMenuClose();
  };

  const handleExportCSV = () => {
    if (!campaign || !responses.length) return;
    
    // Create CSV content
    const headers = ['Response ID', 'Submission Time', ...campaign.questions.map(q => q.text)];
    
    const rows = responses.map(response => {
      const row = [
        response.id,
        response.endTime ? new Date(response.endTime).toLocaleString() : 'N/A'
      ];
      
      // Add answers in the order of questions
      campaign.questions.forEach(question => {
        const answer = response.answers?.find(a => a.questionId === question.id);
        const cell = answer
          ? (Array.isArray(answer.value) ? answer.value.join(', ') : String(answer.value))
          : 'No answer';
        row.push(cell);
      });
      
      return row;
    });
    
    // Combine headers and rows
    const csvContent = [
      headers.join(','),
      ...rows.map(row => row.join(','))
    ].join('\n');
    
    // Create and download the file
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `${campaign.title}_responses.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    
    handleExportMenuClose();
  };

  const handleExportJSON = () => {
    if (!campaign || !responses.length) return;
    
    // Create JSON export
    const exportData = {
      campaign: {
        id: campaign.id,
        title: campaign.title,
        description: campaign.description,
        questions: campaign.questions
      },
      responses: responses
    };
    
    const jsonString = JSON.stringify(exportData, null, 2);
    const blob = new Blob([jsonString], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `${campaign.title}_export.json`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    
    handleExportMenuClose();
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="60vh">
        <CircularProgress />
      </Box>
    );
  }

  if (error || !campaign) {
    return (
      <Box p={3}>
        <Alert severity="error">{error || 'Campaign not found'}</Alert>
        <Button variant="contained" sx={{ mt: 2 }} onClick={() => navigate('/campaigns')}>
          Back to Campaigns
        </Button>
      </Box>
    );
  }

  // Calculate response data for each question
  const questionStats = campaign.questions.map(question => {
    const questionResponses = responses.flatMap(response => 
      response.answers?.filter(answer => answer.questionId === question.id) || []
    );

    if (question.type === QuestionType.MULTIPLE_CHOICE && question.options) {
      // For multiple choice, count responses for each option
      const optionCounts = question.options.reduce((acc, option) => {
        acc[option.text] = questionResponses.filter(r => r.value === option.text).length;
        return acc;
      }, {} as Record<string, number>);
      
      return {
        question,
        responseCount: questionResponses.length,
        optionCounts,
        responseRate: (questionResponses.length / responses.length) * 100
      };
    } else {
      // For text questions, just count total responses
      return {
        question,
        responseCount: questionResponses.length,
        responses: questionResponses.map(r => r.value),
        responseRate: (questionResponses.length / responses.length) * 100
      };
    }
  });

  return (
    <Box p={3}>
      {/* Header with actions */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" component="h1">
          {campaign.title}
        </Typography>
        <Box>
          <Tooltip title="Share">
            <IconButton onClick={handleShareMenuClick}>
              <ShareIcon />
            </IconButton>
          </Tooltip>
          <Menu
            anchorEl={shareAnchorEl}
            open={Boolean(shareAnchorEl)}
            onClose={handleShareMenuClose}
          >
            <MenuItem onClick={handleCopyLink}>
              <FileCopyIcon fontSize="small" sx={{ mr: 1 }} />
              Copy survey link
            </MenuItem>
            <MenuItem onClick={handleShareMenuClose}>
              <ShareIcon fontSize="small" sx={{ mr: 1 }} />
              Share via email
            </MenuItem>
          </Menu>

          <Tooltip title="Export">
            <IconButton onClick={handleExportMenuClick}>
              <DownloadIcon />
            </IconButton>
          </Tooltip>
          <Menu
            anchorEl={exportAnchorEl}
            open={Boolean(exportAnchorEl)}
            onClose={handleExportMenuClose}
          >
            <MenuItem onClick={handleExportCSV}>
              Export as CSV
            </MenuItem>
            <MenuItem onClick={handleExportJSON}>
              Export as JSON
            </MenuItem>
          </Menu>

          <Tooltip title="More options">
            <IconButton onClick={handleMenuClick}>
              <MoreVertIcon />
            </IconButton>
          </Tooltip>
          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleMenuClose}
          >
            <MenuItem onClick={handleEdit}>Edit campaign</MenuItem>
            <MenuItem onClick={handleDelete}>Delete campaign</MenuItem>
          </Menu>
        </Box>
      </Box>

      <Typography variant="body1" sx={{ mb: 3 }}>
        {campaign.description}
      </Typography>

      {/* Stats cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={4}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center">
                <DataUsageIcon fontSize="large" color="primary" sx={{ mr: 2 }} />
                <Box>
                  <Typography variant="h5">{responseCount}</Typography>
                  <Typography variant="body2" color="textSecondary">Total Responses</Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center">
                <PieChartIcon fontSize="large" color="primary" sx={{ mr: 2 }} />
                <Box>
                  <Typography variant="h5">{completionRate.toFixed(1)}%</Typography>
                  <Typography variant="body2" color="textSecondary">Completion Rate</Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center">
                <BarChartIcon fontSize="large" color="primary" sx={{ mr: 2 }} />
                <Box>
                  <Typography variant="h5">{averageCompletionTime.toFixed(1)} min</Typography>
                  <Typography variant="body2" color="textSecondary">Avg. Completion Time</Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Tabs */}
      <Box sx={{ width: '100%' }}>
        <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tabs value={tabValue} onChange={handleTabChange} aria-label="campaign tabs">
            <Tab label="Survey Preview" {...a11yProps(0)} />
            <Tab label="Responses" {...a11yProps(1)} />
            <Tab label="Analytics" {...a11yProps(2)} />
          </Tabs>
        </Box>

        {/* Survey Preview Tab */}
        <TabPanel value={tabValue} index={0}>
          <Paper elevation={2} sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>Survey Preview</Typography>
            <Divider sx={{ mb: 3 }} />
            
            <List>
              {campaign.questions.map((question, index) => (
                <ListItem key={question.id} sx={{ display: 'block', mb: 3, p: 2, bgcolor: 'background.paper', borderRadius: 1 }}>
                  <Typography variant="subtitle1" fontWeight="bold">
                    {index + 1}. {question.text}
                  </Typography>
                  <Typography variant="body2" color="textSecondary" sx={{ mt: 1 }}>
                    {question.type === QuestionType.MULTIPLE_CHOICE ? 'Multiple Choice' : 'Text'} Question
                    {question.required && ' (Required)'}
                  </Typography>
                  
                  {question.type === QuestionType.MULTIPLE_CHOICE && question.options && (
                    <Box sx={{ mt: 2 }}>
                      {question.options.map((option, i) => (
                        <Typography key={i} variant="body2" sx={{ mt: 1 }}>
                          {`• ${option.text}`}
                        </Typography>
                      ))}
                    </Box>
                  )}
                </ListItem>
              ))}
            </List>
          </Paper>
        </TabPanel>
        
        {/* Responses Tab */}
        <TabPanel value={tabValue} index={1}>
          <Paper elevation={2} sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>Survey Responses</Typography>
            <Divider sx={{ mb: 3 }} />
            
            {responses.length === 0 ? (
              <Alert severity="info">No responses yet.</Alert>
            ) : (
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>ID</TableCell>
                      <TableCell>Submitted At</TableCell>
                      {campaign.questions.map(question => (
                        <TableCell key={question.id}>{question.text}</TableCell>
                      ))}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {responses.map(response => (
                      <TableRow key={response.id}>
                        <TableCell>{response.id}</TableCell>
                        <TableCell>{response.submittedAt ? new Date(response.submittedAt).toLocaleString() : 'N/A'}</TableCell>
                        {campaign.questions.map(question => {
                          const answer = response.answers?.find(a => a.questionId === question.id);
                          return (
                            <TableCell key={question.id}>{answer ? answer.value : 'No answer'}</TableCell>
                          );
                        })}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Paper>
        </TabPanel>
        
        {/* Analytics Tab */}
        <TabPanel value={tabValue} index={2}>
          <Paper elevation={2} sx={{ p: 3 }}>
            <Typography variant="h6" gutterBottom>Response Analytics</Typography>
            <Divider sx={{ mb: 3 }} />
            
            {questionStats.map((stat, index) => (
              <Box key={stat.question.id} sx={{ mb: 4 }}>
                <Typography variant="subtitle1" fontWeight="bold">
                  {index + 1}. {stat.question.text}
                </Typography>
                <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
                  Response rate: {stat.responseRate.toFixed(1)}% ({stat.responseCount} of {responses.length})
                </Typography>
                
                {stat.question.type === QuestionType.MULTIPLE_CHOICE && stat.question.options && (
                  <Box sx={{ mt: 2 }}>
                    {stat.question.options.map(option => (
                      <Box key={option.text} sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                        <Typography variant="body2" sx={{ width: '30%' }}>
                          {option.text}:
                        </Typography>
                        <Box sx={{ width: '50%', bgcolor: '#f5f5f5', height: 20, borderRadius: 1 }}>
                          <Box 
                            sx={{ 
                              width: `${((stat.optionCounts?.[option.text] ?? 0) / stat.responseCount) * 100}%`,
                              bgcolor: 'primary.main', 
                              height: '100%', 
                              borderRadius: 1 
                            }} 
                          />
                        </Box>
                        <Typography variant="body2" sx={{ ml: 2 }}>
                          {(stat.optionCounts?.[option.text] ?? 0) || 0} ({stat.responseCount ? (((stat.optionCounts?.[option.text] ?? 0) || 0) / stat.responseCount * 100).toFixed(1) : 0}%)
                        </Typography>
                      </Box>
                    ))}
                  </Box>
                )}
                
                {stat.question.type === QuestionType.TEXT && (
                  <Box sx={{ mt: 2 }}>
                    <Typography variant="body2" fontWeight="bold" sx={{ mb: 1 }}>
                      Text Responses:
                    </Typography>
                    {stat.responses && stat.responses.length > 0 ? (
                      <List>
                        {stat.responses.map((response, i) => (
                          <ListItem key={i} sx={{ bgcolor: '#f5f5f5', mb: 1, borderRadius: 1 }}>
                            <Typography variant="body2">{response}</Typography>
                          </ListItem>
                        ))}
                      </List>
                    ) : (
                      <Typography variant="body2">No text responses.</Typography>
                    )}
                  </Box>
                )}
              </Box>
            ))}
          </Paper>
        </TabPanel>
      </Box>
    </Box>
  );
};

export default ViewCampaign;
