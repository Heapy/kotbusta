import { h } from 'preact';
import { useState, useEffect } from 'preact/hooks';
import { api } from '../utils/api.js';

export function AdminPanel() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('all'); // 'all', 'pending', 'approved', 'rejected', 'deactivated'

  useEffect(() => {
    loadUsers();
  }, []);

  const loadUsers = async () => {
    try {
      const usersRes = await api.get('/api/admin/users');
      setUsers(usersRes.data || []);
    } catch (err) {
      console.error('Failed to load users:', err);
      alert('Failed to load users: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const changeUserStatus = async (userId, newStatus, actionName) => {
    if (!confirm(`${actionName} this user?`)) return;

    try {
      const endpoint = newStatus === 'APPROVED' ? 'approve' :
                      newStatus === 'REJECTED' ? 'reject' :
                      newStatus === 'DEACTIVATED' ? 'deactivate' : null;

      if (!endpoint) {
        alert('Invalid status');
        return;
      }

      // userId is either a number or an object with .value property
      const id = typeof userId === 'object' ? userId.value : userId;
      await api.post(`/api/admin/users/${id}/` + endpoint);
      await loadUsers();
    } catch (err) {
      alert(`Failed to ${actionName.toLowerCase()} user: ` + err.message);
    }
  };

  const getStatusBadge = (status) => {
    const statusConfig = {
      PENDING: { bg: '#fff3cd', color: '#f39c12', text: 'Pending' },
      APPROVED: { bg: '#d5f4e6', color: '#27ae60', text: 'Approved' },
      REJECTED: { bg: '#fadbd8', color: '#e74c3c', text: 'Rejected' },
      DEACTIVATED: { bg: '#e1e8ed', color: '#7f8c8d', text: 'Deactivated' }
    };

    const config = statusConfig[status] || statusConfig.PENDING;

    return h('span', {
      style: {
        padding: '0.25rem 0.75rem',
        borderRadius: '4px',
        fontSize: '0.75rem',
        fontWeight: 'bold',
        background: config.bg,
        color: config.color
      }
    }, config.text);
  };

  const filteredUsers = users.filter(user => {
    if (filter === 'all') return true;
    return user.status === filter.toUpperCase();
  });

  if (loading) {
    return h('div', { style: { padding: '2rem', textAlign: 'center' } }, 'Loading...');
  }

  return h('div', { style: { padding: '2rem', maxWidth: '1200px', margin: '0 auto' } },
    h('h2', { style: { color: '#e74c3c', marginBottom: '1.5rem' } }, 'User Management'),

    // Filter buttons
    h('div', { style: { marginBottom: '1.5rem', display: 'flex', gap: '0.5rem', flexWrap: 'wrap' } },
      ['all', 'pending', 'approved', 'rejected', 'deactivated'].map(filterValue =>
        h('button', {
          key: filterValue,
          onClick: () => setFilter(filterValue),
          style: {
            padding: '0.5rem 1rem',
            background: filter === filterValue ? '#3498db' : 'white',
            color: filter === filterValue ? 'white' : '#34495e',
            border: '1px solid ' + (filter === filterValue ? '#3498db' : '#e1e8ed'),
            borderRadius: '4px',
            cursor: 'pointer',
            fontWeight: filter === filterValue ? 'bold' : 'normal',
            textTransform: 'capitalize'
          }
        },
          filterValue,
          ' (',
          users.filter(u => filterValue === 'all' ? true : u.status === filterValue.toUpperCase()).length,
          ')'
        )
      )
    ),

    // Users table
    h('div', { style: {
      background: 'white',
      borderRadius: '8px',
      border: '1px solid #e1e8ed',
      overflow: 'hidden'
    } },
      // Table header
      h('div', {
        style: {
          display: 'grid',
          gridTemplateColumns: '48px 2fr 2fr 1fr 1fr 200px',
          gap: '1rem',
          padding: '1rem',
          background: '#ecf0f1',
          fontWeight: 'bold',
          fontSize: '0.875rem',
          borderBottom: '2px solid #e1e8ed'
        }
      },
        h('div'),
        h('div', null, 'Name'),
        h('div', null, 'Email'),
        h('div', null, 'Role'),
        h('div', null, 'Status'),
        h('div', null, 'Actions')
      ),

      // Table body
      h('div', { style: { maxHeight: '600px', overflowY: 'auto' } },
        filteredUsers.length === 0
          ? h('div', {
              style: {
                textAlign: 'center',
                padding: '3rem',
                color: '#95a5a6'
              }
            }, 'No users found')
          : filteredUsers.map(user => {
              const userId = typeof user.userId === 'object' ? user.userId.value : user.userId;
              return h('div', {
                key: userId,
                style: {
                  display: 'grid',
                  gridTemplateColumns: '48px 2fr 2fr 1fr 1fr 200px',
                  gap: '1rem',
                  padding: '1rem',
                  borderBottom: '1px solid #ecf0f1',
                  alignItems: 'center',
                  transition: 'background 0.2s',
                  ':hover': {
                    background: '#f8f9fa'
                  }
                }
              },
                // Avatar
                h('div', null,
                  user.avatarUrl && h('img', {
                    src: user.avatarUrl,
                    alt: user.name,
                    style: {
                      width: '48px',
                      height: '48px',
                      borderRadius: '50%',
                      objectFit: 'cover'
                    }
                  })
                ),

                // Name
                h('div', null,
                  h('div', { style: { fontWeight: 'bold', color: '#34495e' } }, user.name)
                ),

                // Email
                h('div', null,
                  h('div', { style: { color: '#7f8c8d', fontSize: '0.875rem' } }, user.email)
                ),

                // Role
                h('div', null,
                  user.isAdmin && h('span', {
                    style: {
                      padding: '0.25rem 0.5rem',
                      borderRadius: '4px',
                      fontSize: '0.75rem',
                      fontWeight: 'bold',
                      background: '#e74c3c',
                      color: 'white'
                    }
                  }, 'Admin')
                ),

                // Status
                h('div', null, getStatusBadge(user.status)),

                // Actions
                h('div', { style: { display: 'flex', gap: '0.5rem', flexWrap: 'wrap' } },
                  user.status !== 'APPROVED' && h('button', {
                    onClick: () => changeUserStatus(userId, 'APPROVED', 'Approve'),
                    style: {
                      padding: '0.375rem 0.75rem',
                      background: '#27ae60',
                      color: 'white',
                      border: 'none',
                      borderRadius: '4px',
                      cursor: 'pointer',
                      fontSize: '0.75rem',
                      fontWeight: 'bold'
                    }
                  }, 'Approve'),

                  user.status !== 'REJECTED' && h('button', {
                    onClick: () => changeUserStatus(userId, 'REJECTED', 'Reject'),
                    style: {
                      padding: '0.375rem 0.75rem',
                      background: '#e74c3c',
                      color: 'white',
                      border: 'none',
                      borderRadius: '4px',
                      cursor: 'pointer',
                      fontSize: '0.75rem',
                      fontWeight: 'bold'
                    }
                  }, 'Reject'),

                  user.status !== 'DEACTIVATED' && user.status === 'APPROVED' && h('button', {
                    onClick: () => changeUserStatus(userId, 'DEACTIVATED', 'Deactivate'),
                    style: {
                      padding: '0.375rem 0.75rem',
                      background: '#95a5a6',
                      color: 'white',
                      border: 'none',
                      borderRadius: '4px',
                      cursor: 'pointer',
                      fontSize: '0.75rem',
                      fontWeight: 'bold'
                    }
                  }, 'Deactivate')
                )
              );
            })
      )
    ),

    // Summary stats
    h('div', {
      style: {
        marginTop: '1.5rem',
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
        gap: '1rem'
      }
    },
      [
        { label: 'Total Users', value: users.length, color: '#3498db' },
        { label: 'Pending', value: users.filter(u => u.status === 'PENDING').length, color: '#f39c12' },
        { label: 'Approved', value: users.filter(u => u.status === 'APPROVED').length, color: '#27ae60' },
        { label: 'Rejected', value: users.filter(u => u.status === 'REJECTED').length, color: '#e74c3c' },
        { label: 'Deactivated', value: users.filter(u => u.status === 'DEACTIVATED').length, color: '#95a5a6' },
        { label: 'Admins', value: users.filter(u => u.isAdmin).length, color: '#e74c3c' }
      ].map(stat =>
        h('div', {
          key: stat.label,
          style: {
            padding: '1rem',
            background: 'white',
            border: '1px solid #e1e8ed',
            borderRadius: '8px',
            textAlign: 'center'
          }
        },
          h('div', { style: { fontSize: '2rem', fontWeight: 'bold', color: stat.color } }, stat.value),
          h('div', { style: { fontSize: '0.875rem', color: '#7f8c8d', marginTop: '0.25rem' } }, stat.label)
        )
      )
    )
  );
}
