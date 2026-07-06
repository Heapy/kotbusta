import { h } from 'preact';

export function BookCard({ book, onSelect }) {
  const authors = (book.authors || []).join(', ');

  return h('article', {
    className: 'book-card',
    onClick: () => onSelect(book.id),
    tabIndex: 0,
    onKeyDown: (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        onSelect(book.id);
      }
    }
  },
    h('div', { className: 'book-cover' },
      book.coverImageUrl
        ? h('img', { src: book.coverImageUrl, alt: book.title })
        : h('div', { className: 'book-cover-placeholder' }, 'No cover')
    ),
    h('div', { className: 'book-card-body' },
      h('h3', { className: 'book-title' }, book.title),
      authors && h('p', { className: 'book-meta' }, authors),
      book.series && h('p', { className: 'book-series' },
        book.series, book.seriesNumber ? ` #${book.seriesNumber}` : ''
      ),
      h('div', { className: 'chip-row' },
        (book.genres || []).slice(0, 3).map(genre =>
          h('span', { key: genre, className: 'chip' }, genre)
        ),
        book.language && h('span', { className: 'chip accent' }, book.language)
      )
    )
  );
}
