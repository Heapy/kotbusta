import { h } from 'preact';
import { useState, useEffect } from 'preact/hooks';
import { api } from '../utils/api.js';
import { SearchBar } from './SearchBar.js';
import { BookCard } from './BookCard.js';

export function BooksList({ onSelectBook, initialOffset = 0, onPageChange }) {
  const [books, setBooks] = useState([]);
  const [semanticWindow, setSemanticWindow] = useState(null);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [offset, setOffset] = useState(initialOffset);
  const [searchParams, setSearchParams] = useState(null);
  const limit = 20;

  useEffect(() => {
    setOffset(initialOffset);
  }, [initialOffset]);

  useEffect(() => {
    loadBooks();
  }, [offset, searchParams, semanticWindow]);

  const hasTextQuery = () => !!(searchParams && searchParams.q && searchParams.q.trim());

  const loadBooks = async () => {
    if (semanticWindow && hasTextQuery()) {
      setBooks(semanticWindow.slice(offset, offset + limit));
      setTotal(semanticWindow.length);
      setLoading(false);
      return;
    }

    setLoading(true);
    try {
      let url;
      if (searchParams) {
        const params = new URLSearchParams({
          ...searchParams,
          limit,
          offset
        });
        url = `/api/search/books?${params}`;
      } else {
        url = `/api/books?limit=${limit}&offset=${offset}`;
      }

      const res = await api.get(url);
      const resultBooks = res.data.books || [];

      if (hasTextQuery() && !res.data.hasMore && res.data.total === resultBooks.length) {
        setSemanticWindow(resultBooks);
        setBooks(resultBooks.slice(offset, offset + limit));
        setTotal(resultBooks.length);
      } else {
        setSemanticWindow(null);
        setBooks(resultBooks);
        setTotal(res.data.total || 0);
      }
    } catch (err) {
      console.error('Failed to load books:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (params) => {
    setSemanticWindow(null);
    setSearchParams(params);
    setOffset(0);
  };

  const hasMore = offset + limit < total;

  const handlePreviousPage = () => {
    const newOffset = Math.max(0, offset - limit);
    setOffset(newOffset);
    if (onPageChange) onPageChange(newOffset);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleNextPage = () => {
    const newOffset = offset + limit;
    setOffset(newOffset);
    if (onPageChange) onPageChange(newOffset);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const rangeStart = total === 0 ? 0 : offset + 1;
  const rangeEnd = Math.min(offset + limit, total);

  return h('main', { className: 'page wide' },
    h('div', { className: 'page-header' },
      h('h2', { className: 'page-title' }, 'Books'),
      h('span', { className: 'muted' }, `${total} total`)
    ),
    h(SearchBar, { onSearch: handleSearch }),

    loading
      ? h('div', { className: 'loading-state' }, 'Loading...')
      : h('div', { className: 'book-grid' },
        books.map(book =>
          h(BookCard, {
            key: book.id,
            book: book,
            onSelect: onSelectBook
          })
        )
      ),

    books.length === 0 && !loading && h('div', {
      className: 'empty-state'
    }, 'No books found'),

    h('div', { className: 'pagination' },
      h('button', {
        className: 'button',
        onClick: handlePreviousPage,
        disabled: offset === 0
      }, 'Previous'),
      h('span', { className: 'range-label' },
        `${rangeStart}-${rangeEnd} of ${total}`
      ),
      h('button', {
        className: 'button',
        onClick: handleNextPage,
        disabled: !hasMore
      }, 'Next')
    )
  );
}
