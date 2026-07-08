import { h } from 'preact';
import { useEffect } from 'preact/hooks';

// Chapter navigation sidebar: a left-side overlay panel (with backdrop), toggled by
// the "Contents" button in the reader bar. Purely presentational - the parent owns
// fetching the TOC, the open/closed state, and deciding what a page navigation means.
export function BookToc({ open, currentPage, entries, onNavigate, onClose }) {
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  return h('div', { className: 'reader-toc-root' },
    open && h('div', { className: 'reader-toc-backdrop', onClick: onClose }),
    h('aside', { className: 'reader-toc-sidebar' + (open ? ' open' : '') },
      h('div', { className: 'reader-toc-header' },
        h('span', { className: 'reader-toc-heading' }, 'Contents'),
        h('button', {
          className: 'button ghost compact',
          onClick: onClose,
          'aria-label': 'Close contents'
        }, '×')
      ),
      entries.length === 0
        ? h('div', { className: 'reader-toc-empty' }, 'No chapters found')
        : h('nav', { className: 'reader-toc-list' },
          entries.map((entry, index) =>
            h('button', {
              key: index,
              className: 'reader-toc-entry' + (entry.page === currentPage ? ' active' : ''),
              style: { paddingLeft: `${0.75 + (entry.level - 1) * 0.9}rem` },
              onClick: () => onNavigate(entry.page)
            }, entry.title)
          )
        )
    )
  );
}
