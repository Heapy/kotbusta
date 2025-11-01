import { h } from 'preact';

export function BookCard({ book, onSelect, onToggleStar }) {
  return h('div', {
    style: {
      border: '1px solid #ddd',
      borderRadius: '8px',
      padding: '1rem',
      background: 'white',
      boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
      cursor: 'pointer',
      transition: 'transform 0.2s, box-shadow 0.2s',
      position: 'relative'
    },
    onMouseOver: (e) => {
      e.currentTarget.style.transform = 'translateY(-2px)';
      e.currentTarget.style.boxShadow = '0 4px 8px rgba(0,0,0,0.1)';
    },
    onMouseOut: (e) => {
      e.currentTarget.style.transform = 'translateY(0)';
      e.currentTarget.style.boxShadow = '0 2px 4px rgba(0,0,0,0.05)';
    }
  },
    h('button', {
      onClick: (e) => {
        e.stopPropagation();
        onToggleStar(book.id, book.isStarred);
      },
      style: {
        position: 'absolute',
        top: '0.5rem',
        right: '0.5rem',
        background: 'none',
        border: 'none',
        fontSize: '1.5rem',
        cursor: 'pointer'
      }
    }, book.isStarred ? '⭐' : '☆'),

    h('div', { onClick: () => onSelect(book.id) },
      book.coverImageUrl && h('img', {
        src: book.coverImageUrl,
        alt: book.title,
        style: {
          width: '100%',
          height: '200px',
          objectFit: 'cover',
          borderRadius: '4px',
          marginBottom: '0.75rem'
        }
      }),
      h('h3', { style: { margin: '0 0 0.5rem 0', fontSize: '1rem' } }, book.title),
      h('p', { style: { margin: '0 0 0.25rem 0', color: '#7f8c8d', fontSize: '0.875rem' } },
        book.authors.join(', ')
      ),
      book.series && h('p', { style: { margin: '0 0 0.25rem 0', color: '#95a5a6', fontSize: '0.75rem' } },
        book.series, book.seriesNumber ? ` #${book.seriesNumber}` : ''
      ),
      h('div', { style: { display: 'flex', gap: '0.5rem', marginTop: '0.5rem', flexWrap: 'wrap' } },
        book.genres && book.genres.map(genre =>
          h('span', {
            key: genre,
            style: {
              background: '#ecf0f1',
              padding: '0.25rem 0.5rem',
              borderRadius: '4px',
              fontSize: '0.75rem',
              color: '#34495e'
            }
          }, genre)
        ),
        h('span', {
          style: {
            background: '#e8f4f8',
            padding: '0.25rem 0.5rem',
            borderRadius: '4px',
            fontSize: '0.75rem',
            color: '#2980b9'
          }
        }, book.language)
      )
    )
  );
}
