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
    return h('main', { className: 'page' },
      h('div', { className: 'loading-state' }, 'Loading...')
    );
  }

  if (!book) {
    return h('main', { className: 'page' },
      h('div', { className: 'empty-state' }, 'Book not found')
    );
  }

  return h('main', { className: 'page wide' },
    h('div', { className: 'page-header' },
      h('button', {
        className: 'button ghost',
        onClick: onBack
      }, 'Back'),
      h('span', { className: 'soft' }, book.language)
    ),

    h('div', { className: 'detail-layout' },
      h('aside', null,
        h('div', { className: 'detail-cover' },
          book.coverImageUrl
            ? h('img', { src: book.coverImageUrl, alt: book.title })
            : h('div', { className: 'book-cover-placeholder large' }, 'No cover')
        ),

        h('div', { className: 'side-actions' },
          h('section', { className: 'panel' },
            h('h3', { className: 'section-title' }, 'Download'),
            h('div', { className: 'action-stack' },
              h('button', { className: 'button full', onClick: () => download('fb2') }, 'FB2'),
              h('button', { className: 'button full', onClick: () => download('epub') }, 'EPUB')
            )
          ),

          h('section', { className: 'panel' },
            h('h3', { className: 'section-title' }, 'Send to Kindle'),
            kindleDevices.length === 0
              ? h('p', { className: 'book-meta' },
                  'Add a Kindle device before sending EPUB files.'
                )
              : h('div', { className: 'action-stack' },
                  h('select', {
                    className: 'select',
                    value: selectedDeviceId,
                    onChange: (event) => setSelectedDeviceId(event.target.value)
                  }, kindleDevices.map(device =>
                    h('option', { key: device.id, value: String(device.id) }, `${device.name} (${device.email})`)
                  )),
                  h('button', {
                    className: 'button primary full',
                    onClick: sendToKindle,
                    disabled: sendingToKindle || !selectedDeviceId
                  }, sendingToKindle ? 'Sending...' : 'Send EPUB')
                )
          )
        )
      ),

      h('article', null,
        h('h1', { className: 'detail-title' }, book.title),
        h('p', { className: 'detail-author' },
          book.authors.map(author => author.fullName).join(', ')
        ),
        book.series && h('p', { className: 'book-meta' },
          'Series: ', book.series.name, book.seriesNumber ? ` #${book.seriesNumber}` : ''
        ),
        h('div', { className: 'chip-row' },
          (book.genres || []).map(genre =>
            h('span', { key: genre, className: 'chip' }, genre)
          ),
          book.language && h('span', { className: 'chip accent' }, book.language)
        ),
        book.annotation && h('div', { className: 'annotation' }, book.annotation)
      )
    ),

    similarBooks.length > 0 && h('section', { className: 'section' },
      h('div', { className: 'section-header' },
        h('h2', { className: 'section-title' }, 'Similar Books')
      ),
      h('div', { className: 'book-grid' },
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
