import { html } from '../utils/html.js';

export function BookCard({ book, onSelect, onToggleStar }) {
  return html`
    <div style=${{
      border: '1px solid #ddd',
      borderRadius: '8px',
      padding: '1rem',
      background: 'white',
      boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
      cursor: 'pointer',
      transition: 'transform 0.2s, box-shadow 0.2s',
      position: 'relative'
    }}
    onMouseOver=${(e) => {
      e.currentTarget.style.transform = 'translateY(-2px)';
      e.currentTarget.style.boxShadow = '0 4px 8px rgba(0,0,0,0.1)';
    }}
    onMouseOut=${(e) => {
      e.currentTarget.style.transform = 'translateY(0)';
      e.currentTarget.style.boxShadow = '0 2px 4px rgba(0,0,0,0.05)';
    }}>
      <button
        onClick=${(e) => {
          e.stopPropagation();
          onToggleStar(book.id, book.isStarred);
        }}
        style=${{
          position: 'absolute',
          top: '0.5rem',
          right: '0.5rem',
          background: 'none',
          border: 'none',
          fontSize: '1.5rem',
          cursor: 'pointer'
        }}
      >
        ${book.isStarred ? '⭐' : '☆'}
      </button>

      <div onClick=${() => onSelect(book.id)}>
        ${book.coverImageUrl && html`
          <img src=${book.coverImageUrl} alt=${book.title}
            style=${{
              width: '100%',
              height: '200px',
              objectFit: 'cover',
              borderRadius: '4px',
              marginBottom: '0.75rem'
            }}
          />
        `}
        <h3 style=${{ margin: '0 0 0.5rem 0', fontSize: '1rem' }}>${book.title}</h3>
        <p style=${{ margin: '0 0 0.25rem 0', color: '#7f8c8d', fontSize: '0.875rem' }}>
          ${book.authors.join(', ')}
        </p>
        ${book.series && html`
          <p style=${{ margin: '0 0 0.25rem 0', color: '#95a5a6', fontSize: '0.75rem' }}>
            ${book.series}${book.seriesNumber ? ` #${book.seriesNumber}` : ''}
          </p>
        `}
        <div style=${{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
          ${book.genre && html`
            <span style=${{
              background: '#ecf0f1',
              padding: '0.25rem 0.5rem',
              borderRadius: '4px',
              fontSize: '0.75rem',
              color: '#34495e'
            }}>
              ${book.genre}
            </span>
          `}
          <span style=${{
            background: '#e8f4f8',
            padding: '0.25rem 0.5rem',
            borderRadius: '4px',
            fontSize: '0.75rem',
            color: '#2980b9'
          }}>
            ${book.language}
          </span>
        </div>
      </div>
    </div>
  `;
}
