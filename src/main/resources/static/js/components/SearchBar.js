import { h } from 'preact';
import { useState } from 'preact/hooks';

export function SearchBar({ onSearch, initialQuery = '' }) {
  const [query, setQuery] = useState(initialQuery);
  const [filters, setFilters] = useState({ genre: '', language: '', author: '' });
  const [showFilters, setShowFilters] = useState(false);

  const handleSearch = (event) => {
    event.preventDefault();
    onSearch({ q: query, ...filters });
  };

  return h('div', { className: 'search-shell' },
    h('form', { className: 'search-form', onSubmit: handleSearch },
      h('input', {
        className: 'input',
        type: 'text',
        value: query,
        onInput: (event) => setQuery(event.target.value),
        placeholder: 'Search books...'
      }),
      h('button', {
        className: `button ${showFilters ? 'primary' : ''}`,
        type: 'button',
        onClick: () => setShowFilters(!showFilters)
      }, 'Filters'),
      h('button', {
        className: 'button primary',
        type: 'submit'
      }, 'Search')
    ),

    showFilters && h('div', { className: 'filters-panel' },
      h('input', {
        className: 'input',
        type: 'text',
        value: filters.genre,
        onInput: (event) => setFilters({ ...filters, genre: event.target.value }),
        placeholder: 'Genre'
      }),
      h('input', {
        className: 'input',
        type: 'text',
        value: filters.language,
        onInput: (event) => setFilters({ ...filters, language: event.target.value }),
        placeholder: 'Language'
      }),
      h('input', {
        className: 'input',
        type: 'text',
        value: filters.author,
        onInput: (event) => setFilters({ ...filters, author: event.target.value }),
        placeholder: 'Author'
      })
    )
  );
}
