import { h } from 'preact';
import { useState, useEffect } from 'preact/hooks';
import { api } from '../utils/api.js';
import { BookCard } from './BookCard.js';

export function BookDetail({ bookId, onBack, onSelectBook }) {
  const [book, setBook] = useState(null);
  const [similarBooks, setSimilarBooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [sendingToKindle, setSendingToKindle] = useState(false);
  const [kindleDevices, setKindleDevices] = useState([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState('');

  useEffect(() => {
    setLoading(true);
    loadBook();
    loadSimilarBooks();
    loadKindleDevices();
  }, [bookId]);

  const loadBook = async () => {
    try {
      const res = await api.get(`/api/books/${bookId}`);
      setBook(res.data);
    } catch (err) {
      console.error('Failed to load book:', err);
      setBook(null);
    } finally {
      setLoading(false);
    }
  };

  const loadSimilarBooks = async () => {
    try {
      const res = await api.get(`/api/books/${bookId}/similar`);
      setSimilarBooks(res.data || []);
    } catch (err) {
      console.error('Failed to load similar books:', err);
      setSimilarBooks([]);
    }
  };

  const loadKindleDevices = async () => {
    try {
      const res = await api.get('/api/kindle/devices');
      const devices = res.data || [];
      setKindleDevices(devices);
      if (devices.length > 0) setSelectedDeviceId(String(devices[0].id));
    } catch (err) {
      console.error('Failed to load Kindle devices:', err);
    }
  };

  const download = (format) => {
    window.open(`/api/books/${bookId}/download/${format}`, '_blank');
  };

  const sendToKindle = async () => {
    if (!selectedDeviceId) return;
    try {
      setSendingToKindle(true);
      await api.post(`/api/books/${bookId}/send-to-kindle`, {
        deviceId: parseInt(selectedDeviceId, 10),
        format: 'EPUB'
      });
      alert('Book queued for sending to Kindle.');
    } catch (err) {
      alert('Failed to send to Kindle: ' + err.message);
    } finally {
      setSendingToKindle(false);
    }
  };

  if (loading) {
    return h('div', { style: { padding: '2rem', textAlign: 'center' } }, 'Loading...');
  }

  if (!book) {
    return h('div', { style: { padding: '2rem' } }, 'Book not found');
  }

  return h('div', { style: { padding: '2rem' } },
    h('button', {
      onClick: onBack,
      style: {
        marginBottom: '1rem',
        padding: '0.5rem 1rem',
        background: '#95a5a6',
        color: 'white',
        border: 'none',
        borderRadius: '4px',
        cursor: 'pointer'
      }
    }, 'Back'),

    h('div', { style: { display: 'grid', gridTemplateColumns: '300px 1fr', gap: '2rem', marginBottom: '2rem' } },
      h('div', null,
        book.coverImageUrl && h('img', {
          src: book.coverImageUrl,
          alt: book.title,
          style: { width: '100%', borderRadius: '8px', marginBottom: '1rem' }
        }),

        h('div', { style: { marginTop: '1rem' } },
          h('h4', { style: { marginBottom: '0.5rem' } }, 'Download'),
          h('button', buttonStyle('#3498db', { marginBottom: '0.25rem' }, () => download('fb2')), 'Download FB2'),
          h('button', buttonStyle('#3498db', {}, () => download('epub')), 'Download EPUB')
        ),

        h('div', { style: { marginTop: '1rem' } },
          h('h4', { style: { marginBottom: '0.5rem' } }, 'Send to Kindle'),
          kindleDevices.length === 0
            ? h('p', { style: { color: '#95a5a6', fontSize: '0.875rem' } },
                'No Kindle devices yet. Add one in the Kindle section.')
            : h('div', null,
                h('select', {
                  value: selectedDeviceId,
                  onChange: (e) => setSelectedDeviceId(e.target.value),
                  style: {
                    width: '100%',
                    padding: '0.5rem',
                    marginBottom: '0.25rem',
                    border: '1px solid #ddd',
                    borderRadius: '4px',
                    boxSizing: 'border-box'
                  }
                }, kindleDevices.map(d =>
                  h('option', { key: d.id, value: String(d.id) }, `${d.name} (${d.email})`)
                )),
                h('button', {
                  ...buttonStyle(sendingToKindle ? '#95a5a6' : '#9b59b6', {}, sendToKindle),
                  disabled: sendingToKindle || !selectedDeviceId
                }, sendingToKindle ? 'Sending...' : 'Send EPUB to Kindle')
              )
        )
      ),

      h('div', null,
        h('h1', { style: { marginTop: 0 } }, book.title),
        h('p', { style: { fontSize: '1.125rem', color: '#7f8c8d', marginBottom: '1rem' } },
          book.authors.map(a => a.fullName).join(', ')
        ),
        book.series && h('p', { style: { color: '#95a5a6', marginBottom: '1rem' } },
          'Series: ', book.series.name, book.seriesNumber ? ` #${book.seriesNumber}` : ''
        ),
        h('div', { style: { display: 'flex', gap: '0.5rem', marginBottom: '1rem', flexWrap: 'wrap' } },
          (book.genres || []).map(genre =>
            h('span', {
              key: genre,
              style: {
                background: '#ecf0f1',
                padding: '0.5rem 1rem',
                borderRadius: '4px',
                color: '#34495e'
              }
            }, genre)
          ),
          h('span', {
            style: {
              background: '#e8f4f8',
              padding: '0.5rem 1rem',
              borderRadius: '4px',
              color: '#2980b9'
            }
          }, book.language)
        ),
        book.annotation && h('div', {
          style: {
            background: '#f8f9fa',
            padding: '1rem',
            borderRadius: '8px',
            marginBottom: '2rem',
            whiteSpace: 'pre-wrap'
          }
        }, book.annotation)
      )
    ),

    similarBooks.length > 0 && h('div', { style: { marginTop: '3rem' } },
      h('h2', { style: { marginBottom: '1rem' } }, 'Similar Books'),
      h('div', {
        style: {
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))',
          gap: '1rem'
        }
      },
        similarBooks.map(book =>
          h(BookCard, {
            key: book.id,
            book: book,
            onSelect: onSelectBook
          })
        )
      )
    )
  );
}

function buttonStyle(background, extraStyle, onClick) {
  return {
    onClick,
    style: {
      width: '100%',
      padding: '0.5rem',
      background,
      color: 'white',
      border: 'none',
      borderRadius: '4px',
      cursor: 'pointer',
      ...extraStyle
    }
  };
}
