import { h } from 'preact';

export function LoginPage() {
  return h('div', {
    style: {
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      padding: '2rem'
    }
  },
    h('div', {
      style: {
        background: 'white',
        borderRadius: '16px',
        padding: '3rem',
        boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
        maxWidth: '500px',
        width: '100%',
        textAlign: 'center'
      }
    },
      h('div', { style: { fontSize: '4rem', marginBottom: '1rem' } }, '📚'),
      h('h1', {
        style: {
          margin: '0 0 0.5rem 0',
          fontSize: '2.5rem',
          color: '#2c3e50',
          fontWeight: '700'
        }
      }, 'Kotbusta'),
      h('p', {
        style: {
          margin: '0 0 2rem 0',
          fontSize: '1.125rem',
          color: '#7f8c8d',
          lineHeight: '1.6'
        }
      }, 'Your personal digital library'),

      h('div', {
        style: {
          background: '#f8f9fa',
          borderRadius: '8px',
          padding: '1.5rem',
          marginBottom: '2rem'
        }
      },
        h('p', { style: { margin: '0 0 1rem 0', color: '#34495e', fontSize: '0.875rem' } },
          'Access thousands of books, manage your reading list, and sync to your Kindle device.'
        ),
        h('ul', {
          style: {
            listStyle: 'none',
            padding: 0,
            margin: 0,
            textAlign: 'left',
            color: '#7f8c8d',
            fontSize: '0.875rem'
          }
        },
          h('li', { style: { padding: '0.5rem 0' } }, '✓ Browse and search book collection'),
          h('li', { style: { padding: '0.5rem 0' } }, '✓ Download in multiple formats (EPUB, MOBI, FB2)'),
          h('li', { style: { padding: '0.5rem 0' } }, '✓ Send books directly to Kindle'),
          h('li', { style: { padding: '0.5rem 0' } }, '✓ Add personal notes and comments')
        )
      ),

      h('a', {
        href: '/login',
        style: {
          display: 'inline-flex',
          alignItems: 'center',
          gap: '0.75rem',
          padding: '1rem 2rem',
          background: '#4285f4',
          color: 'white',
          textDecoration: 'none',
          borderRadius: '8px',
          fontSize: '1.125rem',
          fontWeight: '600',
          boxShadow: '0 4px 12px rgba(66, 133, 244, 0.3)',
          transition: 'all 0.3s ease',
          border: 'none',
          cursor: 'pointer'
        },
        onMouseOver: (e) => {
          e.currentTarget.style.background = '#357ae8';
          e.currentTarget.style.transform = 'translateY(-2px)';
          e.currentTarget.style.boxShadow = '0 6px 16px rgba(66, 133, 244, 0.4)';
        },
        onMouseOut: (e) => {
          e.currentTarget.style.background = '#4285f4';
          e.currentTarget.style.transform = 'translateY(0)';
          e.currentTarget.style.boxShadow = '0 4px 12px rgba(66, 133, 244, 0.3)';
        }
      },
        h('svg', { width: '20', height: '20', viewBox: '0 0 24 24', fill: 'white' },
          h('path', { d: 'M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z' }),
          h('path', { d: 'M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z' }),
          h('path', { d: 'M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z' }),
          h('path', { d: 'M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z' })
        ),
        'Sign in with Google'
      ),

      h('p', {
        style: {
          marginTop: '2rem',
          fontSize: '0.75rem',
          color: '#95a5a6'
        }
      }, 'By signing in, you agree to our terms of service')
    )
  );
}
