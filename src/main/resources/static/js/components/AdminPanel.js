import { h } from 'preact';
import { useState, useEffect } from 'preact/hooks';
import { api } from '../utils/api.js';

function statusClass(status) {
  if (status === 'COMPLETED') return 'status-badge success';
  if (status === 'FAILED') return 'status-badge danger';
  if (status === 'RUNNING') return 'status-badge warning';
  return 'status-badge';
}

export function AdminPanel() {
  const [jobStatus, setJobStatus] = useState(null);
  const [pendingUsers, setPendingUsers] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();

    // Always poll for job status every 2 seconds while on this page.
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
    return h('main', { className: 'page' },
      h('div', { className: 'loading-state' }, 'Loading...')
    );
  }

  const isRunning = jobStatus?.status === 'RUNNING';
  const messages = Object.entries(jobStatus?.messages || {})
    .sort((a, b) => new Date(a[0]) - new Date(b[0]));

  return h('main', { className: 'page' },
    h('div', { className: 'page-header' },
      h('h2', { className: 'page-title' }, 'Admin'),
      h('button', {
        className: 'button primary',
        onClick: startImport,
        disabled: isRunning
      }, isRunning ? 'Import Running...' : 'Start Import')
    ),

    h('section', { className: 'section' },
      h('div', { className: 'section-header' },
        h('h3', { className: 'section-title' }, 'Import Job'),
        isRunning && h('span', { className: 'status-badge warning' }, 'Live')
      ),

      jobStatus && h('div', {
        className: `panel ${isRunning ? 'running' : ''}`
      },
        h('div', { className: 'section-header' },
          h('div', null,
            h('div', { className: 'item-title' }, 'Import Job'),
            h('div', { className: 'item-subtitle' },
              'Started: ', new Date(jobStatus.startedAt).toLocaleString()
            )
          ),
          h('span', { className: statusClass(jobStatus.status) }, jobStatus.status)
        ),

        messages.length > 0 && h('div', { className: 'job-log' },
          h('div', { className: 'job-log-title' }, 'Import Log'),
          h('div', { className: 'job-log-body' },
            messages.map(([timestamp, message]) =>
              h('div', { key: timestamp, className: 'job-log-row' },
                h('span', { className: 'job-log-time' }, new Date(timestamp).toLocaleTimeString()),
                h('span', { className: 'job-log-message' }, message)
              )
            )
          )
        ),

        h('div', { className: 'metrics-grid' },
          h('div', { className: 'metric' },
            h('div', { className: 'metric-label' }, 'Files'),
            h('div', { className: 'metric-value' }, jobStatus.inpFilesProcessed)
          ),
          h('div', { className: 'metric' },
            h('div', { className: 'metric-label' }, 'Added'),
            h('div', { className: 'metric-value' }, jobStatus.booksAdded)
          ),
          h('div', { className: 'metric' },
            h('div', { className: 'metric-label' }, 'Deleted'),
            h('div', { className: 'metric-value' }, jobStatus.bookDeleted)
          ),
          h('div', { className: 'metric' },
            h('div', { className: 'metric-label' }, 'Book Errors'),
            h('div', { className: 'metric-value danger-text' }, jobStatus.bookErrors)
          )
        ),

        jobStatus.completedAt && h('div', {
          className: 'item-note completed-note'
        }, 'Completed: ', new Date(jobStatus.completedAt).toLocaleString())
      ),

      !jobStatus && h('div', {
        className: 'empty-state'
      }, 'No job information available.')
    ),

    h('section', { className: 'section' },
      h('div', { className: 'section-header' },
        h('h3', { className: 'section-title' }, 'Pending User Approvals'),
        h('span', { className: 'chip' }, pendingUsers.length)
      ),
      h('div', { className: 'list-stack' },
        pendingUsers.map(user =>
          h('div', { key: user.id, className: 'list-item' },
            h('div', { className: 'list-item-main' },
              user.avatarUrl && h('img', {
                className: 'avatar',
                src: user.avatarUrl,
                alt: user.name
              }),
              h('div', null,
                h('div', { className: 'item-title' }, user.name),
                h('div', { className: 'item-subtitle' }, user.email),
                h('div', { className: 'item-note' },
                  'Requested: ', new Date(user.createdAt).toLocaleString()
                )
              )
            ),
            h('div', { className: 'toolbar' },
              h('button', {
                className: 'button success compact',
                onClick: () => approveUser(user.id)
              }, 'Approve'),
              h('button', {
                className: 'button danger compact',
                onClick: () => rejectUser(user.id)
              }, 'Reject')
            )
          )
        )
      ),

      pendingUsers.length === 0 && h('div', {
        className: 'empty-state'
      }, 'No pending user approvals.')
    )
  );
}
