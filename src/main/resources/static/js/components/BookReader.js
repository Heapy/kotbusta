import { h } from 'preact';
import { useState, useEffect, useRef } from 'preact/hooks';
import { api } from '../utils/api.js';
import { BookToc } from './BookToc.js';

const MIN_QUERY_LENGTH = 2;
const MAX_MATCHES_PER_PAGE = 500;
const SEARCH_DEBOUNCE_MS = 200;

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// Splits `text` on case-insensitive occurrences of `query`, returning an array of
// plain strings interleaved with <mark> vnodes for each match. This runs as part of
// the normal render (not as a post-hoc DOM mutation): the content tree is now
// Preact-managed rather than injected via dangerouslySetInnerHTML, so directly
// poking the DOM afterward (the old TreeWalker approach) would just get reconciled
// away on the next render. `counter` is a { count } shared across the whole page's
// render so each match gets a stable 0-based index in document order, matching what
// `activeIndex` refers to.
function renderTextWithMatches(text, query, activeIndex, counter, key) {
  if (!query) return text;
  const pattern = new RegExp(escapeRegExp(query), 'gi');
  let match = pattern.exec(text);
  if (!match) return text;

  const parts = [];
  let last = 0;
  let partIndex = 0;
  while (match) {
    if (counter.count >= MAX_MATCHES_PER_PAGE) break;
    const idx = match.index;
    const hit = match[0];
    if (idx > last) parts.push(text.slice(last, idx));
    const matchIndex = counter.count++;
    parts.push(
      h('mark', {
        key: `${key}-m${partIndex++}`,
        className: 'reader-match' + (matchIndex === activeIndex ? ' active' : '')
      }, hit)
    );
    last = idx + hit.length;
    match = pattern.exec(text);
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts;
}

// Renders one FbNode (as produced by BookContentService) into a Preact element.
// `text` nodes become plain strings (or match-highlighted fragments, see above);
// `el` nodes map straight to the corresponding HTML tag - no HTML string is ever
// parsed, so there's nothing here that could be mis-escaped into markup injection.
// Internal (#...) anchors route through `ctx.onAnchorClick` instead of navigating.
function renderFbNode(node, key, ctx) {
  if (node.type === 'text') {
    return renderTextWithMatches(node.value, ctx.query, ctx.activeIndex, ctx.counter, key);
  }

  const props = { key };
  if (node.className) props.className = node.className;
  if (node.id) props.id = node.id;

  if (node.tag === 'img') {
    props.src = node.src;
    props.alt = '';
    return h('img', props);
  }

  if (node.tag === 'a') {
    props.href = node.href;
    props.onClick = (event) => {
      event.preventDefault();
      ctx.onAnchorClick(node.href);
    };
  }

  const children = (node.children || []).map((child, index) => renderFbNode(child, index, ctx));
  return h(node.tag, props, children);
}

export function BookReader({ bookId, onBack }) {
  const [content, setContent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [toc, setToc] = useState({ entries: [], anchors: {} });
  const [tocOpen, setTocOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const [searchPages, setSearchPages] = useState([]);
  const [totalMatches, setTotalMatches] = useState(0);

  const contentRef = useRef(null);
  const searchInputRef = useRef(null);
  const lastAppliedQueryRef = useRef('');
  const searchDebounceRef = useRef(null);
  const pendingAnchorIdRef = useRef(null);
  const pendingActiveIndexRef = useRef(null);

  // New book: reset paging and any stale search/TOC state from the previous one.
  useEffect(() => {
    setPage(1);
    setToc({ entries: [], anchors: {} });
    setTocOpen(false);
    setQuery('');
    setAppliedQuery('');
    lastAppliedQueryRef.current = '';
    setActiveIndex(0);
    setSearchPages([]);
    setTotalMatches(0);
  }, [bookId]);

  useEffect(() => {
    let cancelled = false;
    api.get(`/api/books/${bookId}/toc`)
      .then((res) => {
        if (cancelled) return;
        setToc({ entries: res.data.entries || [], anchors: res.data.anchors || {} });
      })
      .catch((err) => console.error('Failed to load table of contents:', err));
    return () => { cancelled = true; };
  }, [bookId]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    api.get(`/api/books/${bookId}/content?page=${page}`)
      .then((res) => {
        if (cancelled) return;
        setContent(res.data);
        setTotalPages(res.data.totalPages || 1);
      })
      .catch((err) => {
        console.error('Failed to load book content:', err);
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [bookId, page]);

  // Resolve a footnote/anchor jump onto a different page once that page's content lands.
  useEffect(() => {
    const pendingId = pendingAnchorIdRef.current;
    if (!pendingId) return;
    pendingAnchorIdRef.current = null;
    const target = document.getElementById(pendingId);
    if (target) target.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, [content]);

  // Resolve a cross-page search jump once the target page's content lands: pick which
  // match (first or last) should be active there, now that we know its real count.
  useEffect(() => {
    const pending = pendingActiveIndexRef.current;
    if (pending === null) return;
    pendingActiveIndexRef.current = null;
    setActiveIndex(pending);
  }, [content]);

  // Scroll the currently-active match into view whenever it changes (including right
  // after a page/content change resolves a pending cross-page jump above).
  useEffect(() => {
    const root = contentRef.current;
    if (!root) return;
    const active = root.querySelector('mark.reader-match.active');
    if (active) active.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, [activeIndex, content]);

  const clearSearchDebounce = () => {
    if (searchDebounceRef.current !== null) {
      clearTimeout(searchDebounceRef.current);
      searchDebounceRef.current = null;
    }
  };

  const fetchSearchPages = (trimmed) => {
    if (trimmed.length < MIN_QUERY_LENGTH) {
      setSearchPages([]);
      setTotalMatches(0);
      return;
    }
    api.get(`/api/books/${bookId}/search?q=${encodeURIComponent(trimmed)}`)
      .then((res) => {
        setSearchPages((res.data && res.data.pages) || []);
        setTotalMatches((res.data && res.data.totalMatches) || 0);
      })
      .catch((err) => {
        console.error('Failed to search book:', err);
        setSearchPages([]);
        setTotalMatches(0);
      });
  };

  const applyQuery = (trimmed) => {
    lastAppliedQueryRef.current = trimmed;
    setAppliedQuery(trimmed);
    setActiveIndex(0);
    fetchSearchPages(trimmed);
  };

  // Debounced: commit the typed query and kick off the whole-book (page/count only)
  // search alongside it.
  useEffect(() => {
    clearSearchDebounce();
    searchDebounceRef.current = setTimeout(() => {
      searchDebounceRef.current = null;
      applyQuery(query.trim());
    }, SEARCH_DEBOUNCE_MS);
    return () => clearSearchDebounce();
  }, [query]);

  // Close on Escape, unless the focused search box has text to clear.
  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key !== 'Escape') return;
      if (document.activeElement === searchInputRef.current && query.length > 0) {
        event.preventDefault();
        clearSearchDebounce();
        setQuery('');
        return;
      }
      onBack();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onBack, query]);

  // Jump to the next/previous page that has a match, wrapping around the book. Used
  // once the local matches on the current page are exhausted (or there are none).
  const jumpToPageWithMatch = (delta) => {
    if (searchPages.length === 0) return;
    const pages = searchPages.map((p) => p.page);
    const currentIdx = pages.indexOf(page);
    let targetIdx;
    if (currentIdx === -1) {
      targetIdx = delta >= 0
        ? pages.findIndex((p) => p > page)
        : (() => {
          for (let i = pages.length - 1; i >= 0; i--) {
            if (pages[i] < page) return i;
          }
          return -1;
        })();
      if (targetIdx === -1) targetIdx = delta >= 0 ? 0 : pages.length - 1;
    } else {
      targetIdx = (currentIdx + delta + pages.length) % pages.length;
    }
    const targetCount = searchPages[targetIdx].count;
    pendingActiveIndexRef.current = delta >= 0 ? 0 : targetCount - 1;
    setPage(pages[targetIdx]);
  };

  const goToMatch = (delta) => {
    const currentPageMatches = searchPages.find((p) => p.page === page);
    const count = currentPageMatches ? currentPageMatches.count : 0;
    const next = activeIndex + delta;
    if (count > 0 && next >= 0 && next < count) {
      setActiveIndex(next);
      return;
    }
    jumpToPageWithMatch(delta);
  };

  const onSearchKeyDown = (event) => {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    const trimmed = query.trim();
    if (trimmed !== lastAppliedQueryRef.current) {
      clearSearchDebounce();
      applyQuery(trimmed);
      return;
    }
    goToMatch(event.shiftKey ? -1 : 1);
  };

  const handleAnchorClick = (href) => {
    const id = (href || '').replace(/^#/, '');
    if (!id) return;
    const targetPage = toc.anchors[id];
    if (targetPage && targetPage !== page) {
      pendingAnchorIdRef.current = id;
      setPage(targetPage);
      return;
    }
    const target = document.getElementById(id);
    if (target) target.scrollIntoView({ block: 'center', behavior: 'smooth' });
  };

  const handlePreviousPage = () => {
    if (page <= 1) return;
    setPage(page - 1);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleNextPage = () => {
    if (page >= totalPages) return;
    setPage(page + 1);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const hasAppliedQuery = appliedQuery.length >= MIN_QUERY_LENGTH;
  const renderCtx = {
    query: hasAppliedQuery ? appliedQuery : '',
    activeIndex,
    counter: { count: 0 },
    onAnchorClick: handleAnchorClick
  };

  return h('div', { className: 'reader-page' },
    h(BookToc, {
      open: tocOpen,
      currentPage: page,
      entries: toc.entries,
      onNavigate: (targetPage) => {
        setTocOpen(false);
        setPage(targetPage);
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      onClose: () => setTocOpen(false)
    }),

    h('div', { className: 'reader-bar' },
      h('button', { className: 'button ghost compact', onClick: onBack }, 'Back'),
      h('button', {
        className: 'button ghost compact',
        onClick: () => setTocOpen(true),
        disabled: toc.entries.length === 0
      }, 'Contents'),
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
          totalMatches > 0 ? `${totalMatches} match${totalMatches === 1 ? '' : 'es'}` : '0 matches'
        ),
        h('button', {
          className: 'button ghost compact',
          onClick: () => goToMatch(-1),
          disabled: totalMatches === 0,
          'aria-label': 'Previous match'
        }, '‹'),
        h('button', {
          className: 'button ghost compact',
          onClick: () => goToMatch(1),
          disabled: totalMatches === 0,
          'aria-label': 'Next match'
        }, '›')
      )
    ),

    loading && h('div', { className: 'loading-state' }, 'Loading...'),
    error && h('div', { className: 'empty-state' }, 'Failed to load book content.'),
    !loading && !error && content && content.nodes && content.nodes.length
      ? h('article', {
          className: 'reader-content',
          ref: contentRef
        }, content.nodes.map((node, index) => renderFbNode(node, index, renderCtx)))
      : (!loading && !error && h('div', { className: 'empty-state' }, 'No readable content in this book.')),

    !loading && !error && content && h('div', { className: 'pagination reader-pagination' },
      h('button', {
        className: 'button',
        onClick: handlePreviousPage,
        disabled: page <= 1
      }, 'Previous'),
      h('span', { className: 'range-label' }, `Page ${page} of ${totalPages}`),
      h('button', {
        className: 'button',
        onClick: handleNextPage,
        disabled: page >= totalPages
      }, 'Next')
    )
  );
}
