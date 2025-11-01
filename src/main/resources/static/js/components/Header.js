import { h } from 'preact';

export function Header({ user, onNavigate, currentView, isAdmin }) {
  return h('header', {
    style: {
      background: '#2c3e50',
      color: 'white',
      padding: '1rem 2rem',
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
    }
  },
    h('div', null,
      h('h1', { style: { margin: 0, fontSize: '1.5rem' } }, '📚 Kotbusta')
    ),
    h('nav', { style: { display: 'flex', gap: '1rem', alignItems: 'center' } },
      h('button', {
        onClick: () => onNavigate('books'),
        style: {
          background: currentView === 'books' ? '#34495e' : 'transparent',
          border: 'none',
          color: 'white',
          padding: '0.5rem 1rem',
          cursor: 'pointer',
          borderRadius: '4px'
        }
      }, 'Books'),
      h('button', {
        onClick: () => onNavigate('starred'),
        style: {
          background: currentView === 'starred' ? '#34495e' : 'transparent',
          border: 'none',
          color: 'white',
          padding: '0.5rem 1rem',
          cursor: 'pointer',
          borderRadius: '4px'
        }
      }, '⭐ Starred'),
      h('button', {
        onClick: () => onNavigate('activity'),
        style: {
          background: currentView === 'activity' ? '#34495e' : 'transparent',
          border: 'none',
          color: 'white',
          padding: '0.5rem 1rem',
          cursor: 'pointer',
          borderRadius: '4px'
        }
      }, 'Activity'),
      h('button', {
        onClick: () => onNavigate('kindle'),
        style: {
          background: currentView === 'kindle' ? '#34495e' : 'transparent',
          border: 'none',
          color: 'white',
          padding: '0.5rem 1rem',
          cursor: 'pointer',
          borderRadius: '4px'
        }
      }, 'Kindle'),
      isAdmin && h('button', {
        onClick: () => onNavigate('admin'),
        style: {
          background: currentView === 'admin' ? '#e74c3c' : '#c0392b',
          border: 'none',
          color: 'white',
          padding: '0.5rem 1rem',
          cursor: 'pointer',
          borderRadius: '4px'
        }
      }, 'Admin')
    ),
    h('div', { style: { display: 'flex', alignItems: 'center', gap: '1rem' } },
      user && h('div', { style: { display: 'flex', alignItems: 'center', gap: '0.5rem' } },
        user.data.avatarUrl && h('img', {
          src: user.data.avatarUrl,
          alt: user.data.name,
          style: { width: '32px', height: '32px', borderRadius: '50%' }
        }),
        h('span', null, user.data.name)
      ),
      h('a', { href: '/logout', style: { color: 'white', textDecoration: 'none' } }, 'Logout')
    )
  );
}
