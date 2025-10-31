import { useState, useEffect } from 'preact/hooks';
import { html } from '../utils/html.js';
import { api } from '../utils/api.js';
import { SearchBar } from './SearchBar.js';
import { BookCard } from './BookCard.js';

export function BooksList({ starred = false, onSelectBook }) {
  const [books, setBooks] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [offset, setOffset] = useState(0);
  const [searchParams, setSearchParams] = useState(null);
  const limit = 20;

  useEffect(() => {
    loadBooks();
  }, [offset, searchParams, starred]);

  const loadBooks = async () => {
    setLoading(true);
    try {
      let url;
      if (searchParams) {
        const params = new URLSearchParams({
          ...searchParams,
          limit,
          offset
        });
        url = `/api/books/search?${params}`;
      } else if (starred) {
        url = `/api/books/starred?limit=${limit}&offset=${offset}`;
      } else {
        url = `/api/books?limit=${limit}&offset=${offset}`;
      }

      const res = await api.get(url);
      setBooks(res.data.books || []);
      setTotal(res.data.total || 0);
    } catch (err) {
      console.error('Failed to load books:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (params) => {
    setSearchParams(params);
    setOffset(0);
  };

  const toggleStar = async (bookId, isStarred) => {
    try {
      if (isStarred) {
        await api.delete(`/api/books/${bookId}/star`);
      } else {
        await api.post(`/api/books/${bookId}/star`);
      }
      await loadBooks();
    } catch (err) {
      alert('Failed to toggle star: ' + err.message);
    }
  };

  const hasMore = offset + limit < total;

  return html`
    <div style=${{ padding: '2rem' }}>
      <h2>${starred ? 'Starred Books' : 'All Books'}</h2>

      ${!starred && html`
        <${SearchBar} onSearch=${handleSearch} />
      `}

      ${loading ? html`
        <div style=${{ textAlign: 'center', padding: '2rem' }}>Loading...</div>
      ` : html`
        <div style=${{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))',
          gap: '1.5rem',
          marginBottom: '2rem'
        }}>
          ${books.map(book => html`
            <${BookCard}
              key=${book.id}
              book=${book}
              onSelect=${onSelectBook}
              onToggleStar=${toggleStar}
            />
          `)}
        </div>
      `}

      ${books.length === 0 && !loading && html`
        <div style=${{ textAlign: 'center', padding: '2rem', color: '#95a5a6' }}>
          No books found
        </div>
      `}

      <div style=${{ display: 'flex', justifyContent: 'center', gap: '1rem', marginTop: '2rem' }}>
        <button
          onClick=${() => setOffset(Math.max(0, offset - limit))}
          disabled=${offset === 0}
          style=${{
            padding: '0.75rem 1.5rem',
            background: offset === 0 ? '#ecf0f1' : '#3498db',
            color: offset === 0 ? '#95a5a6' : 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: offset === 0 ? 'not-allowed' : 'pointer'
          }}
        >
          Previous
        </button>
        <span style=${{ padding: '0.75rem', color: '#7f8c8d' }}>
          ${offset + 1}-${Math.min(offset + limit, total)} of ${total}
        </span>
        <button
          onClick=${() => setOffset(offset + limit)}
          disabled=${!hasMore}
          style=${{
            padding: '0.75rem 1.5rem',
            background: !hasMore ? '#ecf0f1' : '#3498db',
            color: !hasMore ? '#95a5a6' : 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: !hasMore ? 'not-allowed' : 'pointer'
          }}
        >
          Next
        </button>
      </div>
    </div>
  `;
}
