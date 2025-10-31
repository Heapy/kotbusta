import { useState } from 'preact/hooks';
import { html } from '../utils/html.js';

export function SearchBar({ onSearch, initialQuery = '' }) {
  const [query, setQuery] = useState(initialQuery);
  const [filters, setFilters] = useState({ genre: '', language: '', author: '' });
  const [showFilters, setShowFilters] = useState(false);

  const handleSearch = (e) => {
    e.preventDefault();
    onSearch({ q: query, ...filters });
  };

  return html`
    <div style=${{ marginBottom: '2rem' }}>
      <form onSubmit=${handleSearch} style=${{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
        <input
          type="text"
          value=${query}
          onInput=${(e) => setQuery(e.target.value)}
          placeholder="Search books..."
          style=${{
            flex: 1,
            padding: '0.75rem',
            border: '1px solid #ddd',
            borderRadius: '4px',
            fontSize: '1rem'
          }}
        />
        <button type="button" onClick=${() => setShowFilters(!showFilters)}
          style=${{
            padding: '0.75rem 1rem',
            background: '#95a5a6',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer'
          }}>
          Filters
        </button>
        <button type="submit"
          style=${{
            padding: '0.75rem 1.5rem',
            background: '#3498db',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
            fontWeight: 'bold'
          }}>
          Search
        </button>
      </form>

      ${showFilters && html`
        <div style=${{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '1rem',
          padding: '1rem',
          background: '#ecf0f1',
          borderRadius: '4px'
        }}>
          <input
            type="text"
            value=${filters.genre}
            onInput=${(e) => setFilters({ ...filters, genre: e.target.value })}
            placeholder="Genre"
            style=${{ padding: '0.5rem', border: '1px solid #bdc3c7', borderRadius: '4px' }}
          />
          <input
            type="text"
            value=${filters.language}
            onInput=${(e) => setFilters({ ...filters, language: e.target.value })}
            placeholder="Language"
            style=${{ padding: '0.5rem', border: '1px solid #bdc3c7', borderRadius: '4px' }}
          />
          <input
            type="text"
            value=${filters.author}
            onInput=${(e) => setFilters({ ...filters, author: e.target.value })}
            placeholder="Author"
            style=${{ padding: '0.5rem', border: '1px solid #bdc3c7', borderRadius: '4px' }}
          />
        </div>
      `}
    </div>
  `;
}
