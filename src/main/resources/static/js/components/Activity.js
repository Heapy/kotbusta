import { useState, useEffect } from 'preact/hooks';
import { html } from '../utils/html.js';
import { api } from '../utils/api.js';

export function Activity() {
  const [activity, setActivity] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadActivity();
  }, []);

  const loadActivity = async () => {
    try {
      const res = await api.get('/api/activity?limit=50');
      setActivity(res.data);
    } catch (err) {
      console.error('Failed to load activity:', err);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return html`<div style=${{ padding: '2rem', textAlign: 'center' }}>Loading...</div>`;
  }

  return html`
    <div style=${{ padding: '2rem' }}>
      <h2>Recent Activity</h2>

      <div style=${{ marginBottom: '2rem' }}>
        <h3>Recent Comments</h3>
        <div style=${{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          ${activity.comments.map(comment => html`
            <div key=${comment.id} style=${{
              border: '1px solid #e1e8ed',
              borderRadius: '8px',
              padding: '1rem',
              background: 'white'
            }}>
              <div style=${{ display: 'flex', gap: '0.75rem', marginBottom: '0.5rem' }}>
                ${comment.userAvatarUrl && html`
                  <img src=${comment.userAvatarUrl} alt=${comment.userName}
                    style=${{ width: '40px', height: '40px', borderRadius: '50%' }}
                  />
                `}
                <div>
                  <div style=${{ fontWeight: 'bold' }}>${comment.userName}</div>
                  <div style=${{ color: '#3498db', fontSize: '0.875rem' }}>
                    on "${comment.bookTitle}"
                  </div>
                  <div style=${{ color: '#95a5a6', fontSize: '0.875rem' }}>
                    ${new Date(comment.createdAt).toLocaleString()}
                  </div>
                </div>
              </div>
              <p style=${{ margin: '0', whiteSpace: 'pre-wrap' }}>${comment.comment}</p>
            </div>
          `)}
        </div>
      </div>

      <div>
        <h3>Recent Downloads</h3>
        <div style=${{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          ${activity.downloads.map(download => html`
            <div key=${download.id} style=${{
              padding: '0.75rem',
              border: '1px solid #e1e8ed',
              borderRadius: '4px',
              background: 'white'
            }}>
              <div style=${{ fontWeight: 'bold' }}>${download.bookTitle}</div>
              <div style=${{ color: '#7f8c8d', fontSize: '0.875rem' }}>
                ${download.format.toUpperCase()} · ${new Date(download.createdAt).toLocaleString()}
              </div>
            </div>
          `)}
        </div>
      </div>
    </div>
  `;
}
