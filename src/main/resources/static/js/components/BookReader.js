import { h } from 'preact';
import { useState, useEffect, useRef } from 'preact/hooks';
import { api } from '../utils/api.js';

const MIN_QUERY_LENGTH = 2;
const MAX_MATCHES = 2000;
const SEARCH_DEBOUNCE_MS = 200;

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// Wrap case-insensitive occurrences of `query` in <mark class="reader-match"> within
// each text node under `root`. Matches that span inline tags aren't found (an accepted
// reader limitation). Returns the mark elements in document order, capped at MAX_MATCHES.
function highlightMatches(root, query) {
  const matches = [];
  const escapedQuery = escapeRegExp(query);
  if (!escapedQuery) return matches;

  const pattern = new RegExp(escapedQuery, 'gi');
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
  const textNodes = [];
  let node;
  while ((node = walker.nextNode())) textNodes.push(node);

  for (const textNode of textNodes) {
    const text = textNode.nodeValue;
    pattern.lastIndex = 0;
    let match = pattern.exec(text);
    if (!match) continue;

    const fragment = document.createDocumentFragment();
    let last = 0;
    while (match && matches.length < MAX_MATCHES) {
      const idx = match.index;
      const hit = match[0];
      if (idx > last) fragment.appendChild(document.createTextNode(text.slice(last, idx)));
      const mark = document.createElement('mark');
      mark.className = 'reader-match';
      mark.textContent = hit;
      fragment.appendChild(mark);
      matches.push(mark);
      last = idx + hit.length;
      match = pattern.exec(text);
    }
    if (last < text.length) fragment.appendChild(document.createTextNode(text.slice(last)));
    textNode.parentNode.replaceChild(fragment, textNode);
    if (matches.length >= MAX_MATCHES) break;
  }
  return matches;
}

// Unwrap previously inserted marks, restoring the original text so a fresh search
// starts from clean content.
function clearMarks(root) {
  const marks = root.querySelectorAll('mark.reader-match');
  marks.forEach((mark) => {
    const parent = mark.parentNode;
    parent.replaceChild(document.createTextNode(mark.textContent), mark);
    parent.normalize();
  });
}

export function BookReader({ bookId, onBack }) {
  const [content, setContent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [query, setQuery] = useState('');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [matchCount, setMatchCount] = useState(0);
  const [activeIndex, setActiveIndex] = useState(0);

  const contentRef = useRef(null);
  const matchesRef = useRef([]);
  const searchInputRef = useRef(null);
  const lastAppliedQueryRef = useRef('');
  const searchDebounceRef = useRef(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    api.get(`/api/books/${bookId}/content`)
      .then((res) => {
        if (!cancelled) setContent(res.data);
      })
      .catch((err) => {
        console.error('Failed to load book content:', err);
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [bookId]);

  const activate = (matches, index) => {
    matches.forEach((mark, i) => mark.classList.toggle('active', i === index));
    const target = matches[index];
    if (target) target.scrollIntoView({ block: 'center', behavior: 'smooth' });
  };

  const clearSearchDebounce = () => {
    if (searchDebounceRef.current !== null) {
      clearTimeout(searchDebounceRef.current);
      searchDebounceRef.current = null;
    }
  };

  const applySearch = (rawQuery) => {
    const root = contentRef.current;
    const trimmed = rawQuery.trim();

    if (!root) {
      matchesRef.current = [];
      lastAppliedQueryRef.current = trimmed;
      setAppliedQuery(trimmed);
      setMatchCount(0);
      setActiveIndex(0);
      return [];
    }

    clearMarks(root);
    const matches = trimmed.length >= MIN_QUERY_LENGTH ? highlightMatches(root, trimmed) : [];
    matchesRef.current = matches;
    lastAppliedQueryRef.current = trimmed;
    setAppliedQuery(trimmed);
    setMatchCount(matches.length);
    setActiveIndex(0);
    if (matches.length > 0) activate(matches, 0);
    return matches;
  };

  // Close on Escape, unless the focused search box has text to clear.
  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key !== 'Escape') return;
      if (document.activeElement === searchInputRef.current && query.length > 0) {
        event.preventDefault();
        clearSearchDebounce();
        setQuery('');
        applySearch('');
        return;
      }
      onBack();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onBack, query]);

  // Re-run the (debounced) search whenever the query or the loaded content changes.
  useEffect(() => {
    clearSearchDebounce();
    const root = contentRef.current;
    if (!root) return;
    searchDebounceRef.current = setTimeout(() => {
      searchDebounceRef.current = null;
      applySearch(query);
    }, SEARCH_DEBOUNCE_MS);
    return () => clearSearchDebounce();
  }, [query, content && content.html]);

  const goToMatch = (delta, matches = matchesRef.current, currentIndex = activeIndex) => {
    if (matches.length === 0) return;
    const next = (currentIndex + delta + matches.length) % matches.length;
    setActiveIndex(next);
    activate(matches, next);
  };

  const onSearchKeyDown = (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      clearSearchDebounce();
      const trimmed = query.trim();
      let matches = matchesRef.current;
      let currentIndex = activeIndex;
      if (trimmed !== lastAppliedQueryRef.current) {
        matches = applySearch(query);
        currentIndex = 0;
      }
      goToMatch(event.shiftKey ? -1 : 1, matches, currentIndex);
    }
  };

  const hasAppliedQuery = appliedQuery.length >= MIN_QUERY_LENGTH;

  return h('div', { className: 'reader-page' },
    h('div', { className: 'reader-bar' },
      h('button', { className: 'button ghost compact', onClick: onBack }, 'Back'),
      h('span', { className: 'reader-title' }, content ? content.title : ''),
      h('div', { className: 'reader-search' },
        h('input', {
          ref: searchInputRef,
          className: 'reader-search-input',
          type: 'search',
          placeholder: 'Find in book',
          value: query,
          onInput: (event) => setQuery(event.target.value),
          onKeyDown: onSearchKeyDown
        }),
        hasAppliedQuery && h('span', { className: 'reader-match-count' },
          matchCount > 0 ? `${activeIndex + 1}/${matchCount}` : '0/0'
        ),
        h('button', {
          className: 'button ghost compact',
          onClick: () => goToMatch(-1),
          disabled: matchCount === 0,
          'aria-label': 'Previous match'
        }, '‹'),
        h('button', {
          className: 'button ghost compact',
          onClick: () => goToMatch(1),
          disabled: matchCount === 0,
          'aria-label': 'Next match'
        }, '›')
      )
    ),

    content && content.truncated && h('div', { className: 'reader-notice' },
      'Some images were omitted to keep this page light.'
    ),

    loading && h('div', { className: 'loading-state' }, 'Loading...'),
    error && h('div', { className: 'empty-state' }, 'Failed to load book content.'),
    !loading && !error && content && content.html
      ? h('article', {
          className: 'reader-content',
          ref: contentRef,
          dangerouslySetInnerHTML: { __html: content.html }
        })
      : (!loading && !error && h('div', { className: 'empty-state' }, 'No readable content in this book.'))
  );
}
