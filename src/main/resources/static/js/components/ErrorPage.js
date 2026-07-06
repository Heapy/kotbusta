import { h } from 'preact';

export function ErrorPage({ error, onRetry }) {
  return h('main', { className: 'auth-page' },
    h('section', { className: 'error-card' },
      h('div', { className: 'error-mark', 'aria-hidden': 'true' }, '!'),
      h('h1', { className: 'error-title' }, 'Server Error'),
      h('p', { className: 'error-copy' },
        'There was an error connecting to the server.'
      ),
      h('div', { className: 'error-details' }, error),
      h('div', { className: 'form-actions center' },
        h('button', {
          className: 'button primary',
          onClick: onRetry
        }, 'Retry'),
        h('a', {
          href: '/logout',
          className: 'button'
        }, 'Logout')
      )
    )
  );
}
