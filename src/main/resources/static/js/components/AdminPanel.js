import { h } from 'preact';
import { useState, useEffect } from 'preact/hooks';
import { api } from '../utils/api.js';

export function AdminPanel() {
  const [jobStatus, setJobStatus] = useState(null);
  const [pendingUsers, setPendingUsers] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();

    // Always poll for job status every 2 seconds while on this page
    const interval = setInterval(() => {
      loadJobStatus();
    }, 2000);

    return () => clearInterval(interval);
  }, []);

  const loadData = async () => {
    try {
      const [jobRes, usersRes] = await Promise.all([
        api.get('/api/admin/jobs'),
        api.get('/api/admin/users/pending?limit=50&offset=0')
      ]);
      setJobStatus(jobRes.data || null);
      setPendingUsers(usersRes.data.users || []);
    } catch (err) {
      console.error('Failed to load admin data:', err);
    } finally {
      setLoading(false);
    }
  };

  const loadJobStatus = async () => {
    try {
      const jobRes = await api.get('/api/admin/jobs');
      setJobStatus(jobRes.data || null);
    } catch (err) {
      console.error('Failed to load job status:', err);
    }
  };

  const startImport = async () => {
    if (!confirm('Start a new data import job?')) return;
    try {
      await api.post('/api/admin/import');
      alert('Import job started!');
      await loadJobStatus();
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
    return h('div', { style: { padding: '2rem', textAlign: 'center' } }, 'Loading...');
  }

  return h('div', { style: { padding: '2rem' } },
    h('h2', { style: { color: '#e74c3c' } }, 'Admin Panel'),

    h('div', { style: { marginBottom: '2rem' } },
      h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' } },
        h('h3', null,
          'Import Job ',
          jobStatus?.status === 'RUNNING' && h('span', { style: { color: '#f39c12', fontSize: '0.875rem' } }, '(🔴 Live)')
        ),
        h('button', {
          onClick: startImport,
          disabled: jobStatus?.status === 'RUNNING',
          style: {
            padding: '0.5rem 1rem',
            background: jobStatus?.status === 'RUNNING' ? '#95a5a6' : '#3498db',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: jobStatus?.status === 'RUNNING' ? 'not-allowed' : 'pointer',
            fontWeight: 'bold',
            opacity: jobStatus?.status === 'RUNNING' ? 0.6 : 1
          }
        }, jobStatus?.status === 'RUNNING' ? 'Import Running...' : 'Start Import')
      ),

      jobStatus && h('div', {
        style: {
          padding: '1rem',
          border: jobStatus.status === 'RUNNING' ? '2px solid #3498db' : '1px solid #e1e8ed',
          borderRadius: '8px',
          background: 'white'
        }
      },
        h('div', { style: { display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' } },
          h('div', null,
            h('span', { style: { fontWeight: 'bold' } }, 'Import Job'),
            h('span', {
              style: {
                marginLeft: '0.5rem',
                padding: '0.25rem 0.75rem',
                borderRadius: '4px',
                fontSize: '0.75rem',
                background: jobStatus.status === 'COMPLETED' ? '#d5f4e6' :
                           jobStatus.status === 'FAILED' ? '#fadbd8' :
                           jobStatus.status === 'RUNNING' ? '#fff3cd' : '#e1e8ed',
                color: jobStatus.status === 'COMPLETED' ? '#27ae60' :
                       jobStatus.status === 'FAILED' ? '#e74c3c' :
                       jobStatus.status === 'RUNNING' ? '#f39c12' : '#7f8c8d'
              }
            }, jobStatus.status)
          ),
          h('div', { style: { color: '#7f8c8d', fontSize: '0.875rem' } },
            'Started: ', new Date(jobStatus.startedAt).toLocaleString()
          )
        ),

        Object.keys(jobStatus.messages).length > 0 && h('div', {
          style: { color: '#7f8c8d', fontSize: '0.875rem', marginBottom: '0.5rem' }
        },
          Object.entries(jobStatus.messages).map(([timestamp, msg]) =>
            h('div', { key: timestamp }, msg)
          )
        ),

        h('div', {
          style: {
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
            gap: '0.5rem',
            fontSize: '0.875rem',
            color: '#34495e'
          }
        },
          h('div', null, 'Files: ', jobStatus.inpFilesProcessed),
          h('div', null, 'Added: ', jobStatus.booksAdded),
          h('div', null, 'Deleted: ', jobStatus.bookDeleted),
          h('div', { style: { color: '#e74c3c' } }, 'Book Errors: ', jobStatus.bookErrors)
        ),

        jobStatus.completedAt && h('div', {
          style: { color: '#7f8c8d', fontSize: '0.875rem', marginTop: '0.5rem' }
        }, 'Completed: ', new Date(jobStatus.completedAt).toLocaleString())
      ),

      !jobStatus && h('div', {
        style: { textAlign: 'center', padding: '2rem', color: '#95a5a6' }
      }, 'No job information available')
    ),

    h('div', null,
      h('h3', null, 'Pending User Approvals (', pendingUsers.length, ')'),
      h('div', { style: { display: 'flex', flexDirection: 'column', gap: '0.75rem' } },
        pendingUsers.map(user =>
          h('div', {
            key: user.id,
            style: {
              padding: '1rem',
              border: '1px solid #e1e8ed',
              borderRadius: '8px',
              background: 'white',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }
          },
            h('div', { style: { display: 'flex', gap: '0.75rem', alignItems: 'center' } },
              user.avatarUrl && h('img', {
                src: user.avatarUrl,
                alt: user.name,
                style: { width: '48px', height: '48px', borderRadius: '50%' }
              }),
              h('div', null,
                h('div', { style: { fontWeight: 'bold' } }, user.name),
                h('div', { style: { color: '#7f8c8d', fontSize: '0.875rem' } }, user.email),
                h('div', { style: { color: '#95a5a6', fontSize: '0.75rem' } },
                  'Requested: ', new Date(user.createdAt).toLocaleString()
                )
              )
            ),
            h('div', { style: { display: 'flex', gap: '0.5rem' } },
              h('button', {
                onClick: () => approveUser(user.id),
                style: {
                  padding: '0.5rem 1rem',
                  background: '#27ae60',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }
              }, 'Approve'),
              h('button', {
                onClick: () => rejectUser(user.id),
                style: {
                  padding: '0.5rem 1rem',
                  background: '#e74c3c',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer'
                }
              }, 'Reject')
            )
          )
        )
      ),

      pendingUsers.length === 0 && h('div', {
        style: { textAlign: 'center', padding: '2rem', color: '#95a5a6' }
      }, 'No pending user approvals')
    )
  );
}
