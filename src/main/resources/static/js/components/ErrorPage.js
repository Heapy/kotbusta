import { html } from '../utils/html.js';

export function ErrorPage({ error, onRetry }) {
  return html`
    <div style=${{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '100vh',
      background: '#ecf0f1',
      padding: '2rem'
    }}>
      <div style=${{
        background: 'white',
        borderRadius: '16px',
        padding: '3rem',
        boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
        maxWidth: '500px',
        width: '100%',
        textAlign: 'center'
      }}>
        <div style=${{ fontSize: '4rem', marginBottom: '1rem' }}>⚠️</div>
        <h1 style=${{
          margin: '0 0 1rem 0',
          fontSize: '2rem',
          color: '#e74c3c'
        }}>
          Server Error
        </h1>
        <p style=${{
          margin: '0 0 2rem 0',
          color: '#7f8c8d',
          lineHeight: '1.6'
        }}>
          There was an error connecting to the server. This could be a database connection issue.
        </p>
        <div style=${{
          background: '#f8f9fa',
          padding: '1rem',
          borderRadius: '8px',
          marginBottom: '2rem',
          textAlign: 'left'
        }}>
          <p style=${{ margin: '0 0 0.5rem 0', fontWeight: 'bold', fontSize: '0.875rem' }}>
            Error details:
          </p>
          <p style=${{
            margin: 0,
            fontSize: '0.875rem',
            color: '#e74c3c',
            fontFamily: 'monospace',
            wordBreak: 'break-word'
          }}>
            ${error}
          </p>
        </div>
        <div style=${{ display: 'flex', gap: '1rem', justifyContent: 'center' }}>
          <button onClick=${onRetry} style=${{
            padding: '0.75rem 1.5rem',
            background: '#3498db',
            color: 'white',
            border: 'none',
            borderRadius: '8px',
            fontSize: '1rem',
            fontWeight: '600',
            cursor: 'pointer'
          }}>
            Retry
          </button>
          <a href="/logout" style=${{
            padding: '0.75rem 1.5rem',
            background: '#95a5a6',
            color: 'white',
            border: 'none',
            borderRadius: '8px',
            fontSize: '1rem',
            fontWeight: '600',
            textDecoration: 'none',
            display: 'inline-block'
          }}>
            Logout
          </a>
        </div>
      </div>
    </div>
  `;
}
