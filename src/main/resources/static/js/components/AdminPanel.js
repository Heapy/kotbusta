import { useState, useEffect } from 'preact/hooks';
import { html } from '../utils/html.js';
import { api } from '../utils/api.js';

export function AdminPanel() {
  const [jobs, setJobs] = useState([]);
  const [liveStats, setLiveStats] = useState([]);
  const [pendingUsers, setPendingUsers] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();

    // Poll for live stats every 2 seconds
    const interval = setInterval(() => {
      loadLiveStats();
    }, 2000);

    return () => clearInterval(interval);
  }, []);

  const loadData = async () => {
    try {
      const [jobsRes, usersRes] = await Promise.all([
        api.get('/api/admin/jobs'),
        api.get('/api/admin/users/pending?limit=50&offset=0')
      ]);
      setJobs(jobsRes.data || []);
      setPendingUsers(usersRes.data.users || []);

      // Also load live stats initially
      await loadLiveStats();
    } catch (err) {
      console.error('Failed to load admin data:', err);
    } finally {
      setLoading(false);
    }
  };

  const loadLiveStats = async () => {
    try {
      const liveRes = await api.get('/api/admin/jobs/live');
      setLiveStats(liveRes.data || []);
    } catch (err) {
      console.error('Failed to load live stats:', err);
    }
  };

  const startImport = async () => {
    if (!confirm('Start a new data import job?')) return;
    try {
      await api.post('/api/admin/import');
      alert('Import job started!');
      await loadData();
    } catch (err) {
      alert('Failed to start import: ' + err.message);
    }
  };

  const approveUser = async (userId) => {
    try {
      await api.post(`/api/admin/users/${userId}/approve`);
      await loadData();
    } catch (err) {
      alert('Failed to approve user: ' + err.message);
    }
  };

  const rejectUser = async (userId) => {
    try {
      await api.post(`/api/admin/users/${userId}/reject`);
      await loadData();
    } catch (err) {
      alert('Failed to reject user: ' + err.message);
    }
  };

  if (loading) {
    return html`<div style=${{ padding: '2rem', textAlign: 'center' }}>Loading...</div>`;
  }

  return html`
    <div style=${{ padding: '2rem' }}>
      <h2 style=${{ color: '#e74c3c' }}>Admin Panel</h2>

      <div style=${{ marginBottom: '2rem' }}>
        <div style=${{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3>Import Jobs ${liveStats.length > 0 ? html`<span style=${{ color: '#f39c12', fontSize: '0.875rem' }}>(🔴 Live)</span>` : ''}</h3>
          <button onClick=${startImport} style=${{
            padding: '0.5rem 1rem',
            background: '#3498db',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
            fontWeight: 'bold'
          }}>
            Start Import
          </button>
        </div>

        <div style=${{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          ${/* Show live stats first for active jobs */ ''}
          ${liveStats.map(job => html`
            <div key=${job.id} style=${{
              padding: '1rem',
              border: '2px solid #3498db',
              borderRadius: '8px',
              background: 'white'
            }}>
              <div style=${{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <div>
                  <span style=${{ fontWeight: 'bold' }}>${job.jobType}</span>
                  <span style=${{
                    marginLeft: '0.5rem',
                    padding: '0.25rem 0.75rem',
                    borderRadius: '4px',
                    fontSize: '0.75rem',
                    background: job.status === 'COMPLETED' ? '#d5f4e6' :
                               job.status === 'FAILED' ? '#fadbd8' : '#fff3cd',
                    color: job.status === 'COMPLETED' ? '#27ae60' :
                           job.status === 'FAILED' ? '#e74c3c' : '#f39c12'
                  }}>
                    ${job.status}
                  </span>
                </div>
                <div style=${{ color: '#7f8c8d', fontSize: '0.875rem' }}>
                  Started: ${new Date(job.startedAt).toLocaleString()}
                </div>
              </div>

              ${job.progress && html`
                <div style=${{ color: '#7f8c8d', fontSize: '0.875rem', marginBottom: '0.5rem' }}>
                  ${job.progress}
                </div>
              `}

              <div style=${{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
                gap: '0.5rem',
                fontSize: '0.875rem',
                color: '#34495e'
              }}>
                <div>Files: ${job.inpFilesProcessed}</div>
                <div>Added: ${job.booksAdded}</div>
                <div>Updated: ${job.booksUpdated}</div>
                <div>Deleted: ${job.booksDeleted}</div>
                <div>Covers: ${job.coversAdded}</div>
                ${job.bookErrors > 0 && html`<div style=${{ color: '#e74c3c' }}>Errors: ${job.bookErrors}</div>`}
              </div>

              ${job.errorMessage && html`
                <div style=${{ color: '#e74c3c', fontSize: '0.875rem', marginTop: '0.5rem' }}>
                  Error: ${job.errorMessage}
                </div>
              `}

              <div style=${{
                marginTop: '0.5rem',
                fontSize: '0.75rem',
                color: '#95a5a6',
                fontStyle: 'italic'
              }}>
                Last updated: ${new Date(job.lastUpdated).toLocaleTimeString()}
              </div>
            </div>
          `)}

          ${/* Show historical completed jobs */ ''}
          ${jobs.map(job => html`
            <div key=${job.id} style=${{
              padding: '1rem',
              border: '1px solid #e1e8ed',
              borderRadius: '8px',
              background: 'white'
            }}>
              <div style=${{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <div>
                  <span style=${{ fontWeight: 'bold' }}>${job.jobType}</span>
                  <span style=${{
                    marginLeft: '0.5rem',
                    padding: '0.25rem 0.75rem',
                    borderRadius: '4px',
                    fontSize: '0.75rem',
                    background: job.status === 'COMPLETED' ? '#d5f4e6' :
                               job.status === 'FAILED' ? '#fadbd8' : '#fff3cd',
                    color: job.status === 'COMPLETED' ? '#27ae60' :
                           job.status === 'FAILED' ? '#e74c3c' : '#f39c12'
                  }}>
                    ${job.status}
                  </span>
                </div>
                <div style=${{ color: '#7f8c8d', fontSize: '0.875rem' }}>
                  Started: ${new Date(job.startedAt).toLocaleString()}
                </div>
              </div>

              ${job.progress && html`
                <div style=${{ color: '#7f8c8d', fontSize: '0.875rem', marginBottom: '0.5rem' }}>
                  ${job.progress}
                </div>
              `}

              <div style=${{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
                gap: '0.5rem',
                fontSize: '0.875rem',
                color: '#34495e'
              }}>
                <div>Files: ${job.inpFilesProcessed}</div>
                <div>Added: ${job.booksAdded}</div>
                <div>Updated: ${job.booksUpdated}</div>
                <div>Deleted: ${job.booksDeleted}</div>
                <div>Covers: ${job.coversAdded}</div>
                ${job.bookErrors > 0 && html`<div style=${{ color: '#e74c3c' }}>Errors: ${job.bookErrors}</div>`}
              </div>

              ${job.errorMessage && html`
                <div style=${{ color: '#e74c3c', fontSize: '0.875rem', marginTop: '0.5rem' }}>
                  Error: ${job.errorMessage}
                </div>
              `}
            </div>
          `)}
        </div>
      </div>

      <div>
        <h3>Pending User Approvals (${pendingUsers.length})</h3>
        <div style=${{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          ${pendingUsers.map(user => html`
            <div key=${user.id} style=${{
              padding: '1rem',
              border: '1px solid #e1e8ed',
              borderRadius: '8px',
              background: 'white',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <div style=${{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
                ${user.avatarUrl && html`
                  <img src=${user.avatarUrl} alt=${user.name}
                    style=${{ width: '48px', height: '48px', borderRadius: '50%' }}
                  />
                `}
                <div>
                  <div style=${{ fontWeight: 'bold' }}>${user.name}</div>
                  <div style=${{ color: '#7f8c8d', fontSize: '0.875rem' }}>${user.email}</div>
                  <div style=${{ color: '#95a5a6', fontSize: '0.75rem' }}>
                    Requested: ${new Date(user.createdAt).toLocaleString()}
                  </div>
                </div>
              </div>
              <div style=${{ display: 'flex', gap: '0.5rem' }}>
                <button onClick=${() => approveUser(user.id)} style=${{
                  padding: '0.5rem 1rem',
                  background: '#27ae60',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }}>
                  Approve
                </button>
                <button onClick=${() => rejectUser(user.id)} style=${{
                  padding: '0.5rem 1rem',
                  background: '#e74c3c',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }}>
                  Reject
                </button>
              </div>
            </div>
          `)}
        </div>

        ${pendingUsers.length === 0 && html`
          <div style=${{ textAlign: 'center', padding: '2rem', color: '#95a5a6' }}>
            No pending user approvals
          </div>
        `}
      </div>
    </div>
  `;
}
