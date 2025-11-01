import { h } from 'preact';
import { useState, useEffect } from 'preact/hooks';
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
    return h('div', { style: { padding: '2rem', textAlign: 'center' } }, 'Loading...');
  }

  return h('div', { style: { padding: '2rem' } },
    h('h2', null, 'Recent Activity'),

    h('div', { style: { marginBottom: '2rem' } },
      h('h3', null, 'Recent Comments'),
      h('div', { style: { display: 'flex', flexDirection: 'column', gap: '1rem' } },
        activity.comments.map(comment =>
          h('div', {
            key: comment.id,
            style: {
              border: '1px solid #e1e8ed',
              borderRadius: '8px',
              padding: '1rem',
              background: 'white'
            }
          },
            h('div', { style: { display: 'flex', gap: '0.75rem', marginBottom: '0.5rem' } },
              comment.userAvatarUrl && h('img', {
                src: comment.userAvatarUrl,
                alt: comment.userName,
                style: { width: '40px', height: '40px', borderRadius: '50%' }
              }),
              h('div', null,
                h('div', { style: { fontWeight: 'bold' } }, comment.userName),
                h('div', { style: { color: '#3498db', fontSize: '0.875rem' } },
                  `on "${comment.bookTitle}"`
                ),
                h('div', { style: { color: '#95a5a6', fontSize: '0.875rem' } },
                  new Date(comment.createdAt).toLocaleString()
                )
              )
            ),
            h('p', { style: { margin: '0', whiteSpace: 'pre-wrap' } }, comment.comment)
          )
        )
      )
    ),

    h('div', null,
      h('h3', null, 'Recent Downloads'),
      h('div', { style: { display: 'flex', flexDirection: 'column', gap: '0.5rem' } },
        activity.downloads.map(download =>
          h('div', {
            key: download.id,
            style: {
              padding: '0.75rem',
              border: '1px solid #e1e8ed',
              borderRadius: '4px',
              background: 'white'
            }
          },
            h('div', { style: { fontWeight: 'bold' } }, download.bookTitle),
            h('div', { style: { color: '#7f8c8d', fontSize: '0.875rem' } },
              `${download.format.toUpperCase()} · ${new Date(download.createdAt).toLocaleString()}`
            )
          )
        )
      )
    )
  );
}
